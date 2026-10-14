package com.college.attendance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.jackson.WebauthnJackson2Module;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Proving, at the moment of an action, that the enrolled authenticator is
 * present.
 *
 * <p>Signing in with a passkey proves the authenticator was present when the
 * session began. That is not the same claim as "the authenticator is present
 * now": a session outlives the ceremony that created it, so a browser tab
 * left open in a corridor carries the same authority as the student sitting
 * in the room. For marking attendance the distinction is the whole point, so
 * this service runs a fresh assertion each time.
 *
 * <p>Two checks make the assertion meaningful, and both are needed:
 *
 * <ul>
 *   <li>The challenge is generated here, kept in the server-side session and
 *       <em>consumed</em> on use. A signature over a challenge the server did
 *       not issue, or over one already spent, is refused. That is what stops
 *       a captured assertion being replayed.</li>
 *   <li>The account the verified credential belongs to must be the account
 *       making the request. Without this a student could sign in as
 *       themselves and present a classmate's passkey: the signature would
 *       verify perfectly, because it is a genuine signature — just not
 *       theirs.</li>
 * </ul>
 *
 * <p>The cryptography itself is Spring Security's, by way of webauthn4j.
 * Re-implementing signature verification here would add risk without adding
 * capability.
 */
@Service
public class PasskeyAssertionService {

    private static final Logger log = LoggerFactory.getLogger(PasskeyAssertionService.class);

    /**
     * Where the issued challenge waits for the browser to come back with a
     * signature over it. Held server-side, so the value the verifier compares
     * against is never one the client could have chosen.
     */
    static final String PENDING_CHALLENGE = PasskeyAssertionService.class.getName() + ".PENDING";

    private final boolean requiredToMark;
    private final WebAuthnRelyingPartyOperations relyingParty;
    private final PublicKeyCredentialUserEntityRepository userEntities;
    private final UserCredentialRepository credentials;
    private final ObjectMapper mapper;

    public PasskeyAssertionService(
            @Value("${attendance.passkey.required-to-mark:false}") boolean requiredToMark,
            WebAuthnRelyingPartyOperations relyingParty,
            PublicKeyCredentialUserEntityRepository userEntities,
            UserCredentialRepository credentials) {
        this.requiredToMark = requiredToMark;
        this.relyingParty = relyingParty;
        this.userEntities = userEntities;
        this.credentials = credentials;
        // A mapper of its own, carrying Spring Security's WebAuthn module.
        // The shared MVC mapper is configured for this application's own DTOs
        // and adding WebAuthn's mixins to it would change how every other
        // endpoint serialises.
        this.mapper = new ObjectMapper().registerModule(new WebauthnJackson2Module());
    }

    /**
     * Issue a challenge for the signed-in user, as JSON the browser can hand
     * to {@code navigator.credentials.get()}.
     *
     * @throws PasskeyRequiredException if the user has no registered passkey,
     *         which is refused here rather than later: an assertion request
     *         with an empty {@code allowCredentials} list invites the browser
     *         to offer <em>any</em> passkey it holds, so a student without one
     *         of their own would be prompted to use whichever account was
     *         enrolled on the machine.
     */
    public String challengeJson(HttpSession httpSession) {
        Authentication authentication = requireAuthentication();
        String username = authentication.getName();

        PublicKeyCredentialUserEntity entity = userEntities.findByUsername(username);
        List<CredentialRecord> enrolled =
                entity == null ? List.of() : credentials.findByUserId(entity.getId());
        if (enrolled.isEmpty()) {
            throw new PasskeyRequiredException(
                    "You have not registered a passkey yet. Open Passkeys from the "
                    + "menu, register this device, then mark attendance.");
        }

        PublicKeyCredentialRequestOptions issued = relyingParty.createCredentialRequestOptions(
                new ImmutablePublicKeyCredentialRequestOptionsRequest(authentication));

        // Rebuilt with user verification REQUIRED rather than the relying
        // party's default of PREFERRED. "Preferred" lets an authenticator
        // skip the biometric and assert mere presence, which here would
        // reduce the prompt to a click -- exactly the property this check
        // exists to rule out. REQUIRED makes the authenticator either ask for
        // Touch ID or the device PIN, or refuse outright.
        PublicKeyCredentialRequestOptions options = PublicKeyCredentialRequestOptions.builder()
                .challenge(issued.getChallenge())
                .timeout(issued.getTimeout())
                .rpId(issued.getRpId())
                .allowCredentials(issued.getAllowCredentials())
                .extensions(issued.getExtensions())
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build();

        httpSession.setAttribute(PENDING_CHALLENGE, options);
        try {
            return mapper.writeValueAsString(options);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise the passkey challenge", e);
        }
    }

    /**
     * Verify an assertion against the challenge issued to this session, and
     * confirm it belongs to the user making the request.
     *
     * <p>The pending challenge is removed before verification, not after, so
     * that a failed attempt cannot be retried against the same challenge and
     * a successful one cannot be submitted twice.
     */
    public void verify(String assertionJson, HttpSession httpSession) {
        Authentication authentication = requireAuthentication();
        String username = authentication.getName();

        Object pending = httpSession.getAttribute(PENDING_CHALLENGE);
        httpSession.removeAttribute(PENDING_CHALLENGE);
        if (!(pending instanceof PublicKeyCredentialRequestOptions options)) {
            throw new PasskeyRequiredException(
                    "That passkey prompt has expired. Try marking attendance again.");
        }

        PublicKeyCredential<AuthenticatorAssertionResponse> credential;
        try {
            credential = mapper.readValue(assertionJson, new TypeReference<>() { });
        } catch (Exception e) {
            log.warn("Unreadable passkey assertion from {}: {}", username, e.getMessage());
            throw new PasskeyRequiredException(
                    "That passkey response could not be read. Try again.");
        }

        PublicKeyCredentialUserEntity verified;
        try {
            verified = relyingParty.authenticate(
                    new RelyingPartyAuthenticationRequest(options, credential));
        } catch (RuntimeException e) {
            log.warn("Passkey assertion failed verification for {}: {}", username, e.getMessage());
            throw new PasskeyRequiredException(
                    "That passkey could not be verified. Try again, or register this "
                    + "device under Passkeys.");
        }

        // The signature is genuine; this is the separate question of whose it
        // is. A mismatch is the proxy-marking case, so it is logged loudly
        // rather than folded into the generic failure above.
        if (verified == null || !username.equals(verified.getName())) {
            log.warn("Passkey belonging to {} was presented by {}; refused",
                    verified == null ? "an unknown account" : verified.getName(), username);
            throw new PasskeyRequiredException(
                    "That passkey belongs to a different account. Mark attendance with "
                    + "your own device.");
        }

        log.info("Passkey assertion verified for {} (user verification: {})",
                username, options.getUserVerification());
    }

    /**
     * Whether marking attendance demands a fresh assertion.
     *
     * <p>Off by default. Requiring it is the stronger control, but it means
     * every student must enrol a passkey before they can be marked present,
     * and on a single shared machine that enrolment is a step in its own
     * right. Left as a switch rather than a decision baked into the code, so
     * the stricter behaviour can be turned on for a demonstration without
     * becoming a prerequisite for the system to work at all.
     */
    public boolean isRequiredToMark() {
        return requiredToMark;
    }

    private Authentication requireAuthentication() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new NotPermittedException("Sign in before using a passkey.");
        }
        return authentication;
    }
}
