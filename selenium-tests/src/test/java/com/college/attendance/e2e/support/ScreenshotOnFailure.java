package com.college.attendance.e2e.support;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Captures the browser's state at the moment a journey fails.
 *
 * <p>A browser test that fails in CI with nothing but a stack trace is
 * usually unactionable: the stack says which assertion failed, not what
 * the page looked like. This writes, side by side:
 *
 * <ul>
 *   <li>a PNG of the viewport,</li>
 *   <li>the page source, because a rendered error page rarely shows the
 *       message that matters in a screenshot,</li>
 *   <li>the URL, title and failure, so the artefact is self-describing.</li>
 * </ul>
 *
 * <p>This implements {@link AfterTestExecutionCallback} rather than
 * {@code TestWatcher}. A {@code TestWatcher} fires <em>after</em> the
 * {@code @AfterEach} callbacks have run, by which time the driver has
 * already been quit and there is no browser left to photograph — the
 * mechanism appears to be wired up and silently produces nothing.
 * {@code AfterTestExecutionCallback} runs before {@code @AfterEach}, while
 * the browser is still alive.
 */
public class ScreenshotOnFailure implements AfterTestExecutionCallback {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    @Override
    public void afterTestExecution(ExtensionContext context) {
        Optional<Throwable> failure = context.getExecutionException();
        if (failure.isEmpty()) {
            return;
        }

        Object instance = context.getTestInstance().orElse(null);
        if (!(instance instanceof BaseJourney journey)) {
            return;
        }
        WebDriver driver = journey.driver();
        if (driver == null) {
            return;
        }

        String testClass = context.getTestClass().map(Class::getSimpleName).orElse("unknown");
        String testMethod = context.getTestMethod().map(m -> m.getName()).orElse("unknown");
        String name = testClass + "." + testMethod + "-" + LocalDateTime.now().format(STAMP);

        Path dir = Paths.get(System.getProperty("selenium.screenshot.dir", "target/screenshots"));
        try {
            Files.createDirectories(dir);

            Path png = dir.resolve(name + ".png");
            if (driver instanceof TakesScreenshot shooter) {
                Files.write(png, shooter.getScreenshotAs(OutputType.BYTES));
            }

            String report = """
                    Journey : %s
                    Test    : %s
                    URL     : %s
                    Title   : %s
                    Failure : %s

                    ---- page source ----
                    %s
                    """.formatted(testClass, context.getDisplayName(),
                    driver.getCurrentUrl(), driver.getTitle(),
                    failure.get().toString(), driver.getPageSource());

            Files.writeString(dir.resolve(name + ".txt"), report, StandardCharsets.UTF_8);

            System.err.println("[selenium] failure captured: " + png.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[selenium] could not write the failure artefacts: " + e.getMessage());
        }
    }
}
