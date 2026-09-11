## Summary

<!-- What does this pull request change, and why? One short paragraph. -->

## Related issue

Closes #

## Backlog item / stage

- Backlog ID: <!-- e.g. US-06 -->
- Project stage: <!-- e.g. stage-06 -->

## Type of change

- [ ] `feat` — new capability
- [ ] `fix` — defect correction
- [ ] `test` — tests only
- [ ] `ci` / `build` — pipeline or build change
- [ ] `docs` — documentation only
- [ ] `refactor` — no behaviour change

## Acceptance criteria covered

<!-- List each AC from the issue and the test that proves it. -->

| Acceptance criterion | Verified by |
|---|---|
|  |  |

## How this was verified

```
mvn -B clean verify
```

<!-- Paste the relevant result lines, and the Jenkins build number. -->

## Definition of Done checklist

- [ ] Branch follows the naming rules in `CONTRIBUTING.md`
- [ ] Commit messages follow Conventional Commits
- [ ] Every acceptance criterion has a passing automated test
- [ ] `mvn clean verify` is green locally
- [ ] Jenkins pipeline is green on this branch
- [ ] Selenium quality gate passed (where applicable)
- [ ] Stage documentation updated under `docs/`
- [ ] Evidence captured under `proofs/`
- [ ] No secrets, build output or H2 data committed
- [ ] Branch will be deleted after merge

## Reviewer notes

<!-- Anything a reviewer should look at closely: a trade-off, a shortcut,
     a deliberate omission. -->
