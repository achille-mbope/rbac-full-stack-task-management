package net.mbope.taskmanager.common.internal.presentation;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class DiagnosticsTests {
    @Test
    void unexpectedFailuresLogLocationAndCorrelationWithoutMessagesOrRequestSecrets(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("GET", "/private-secret-path");
        request.setQueryString("password=secret-query");
        request.addHeader("Authorization", "Bearer secret-token");
        request.addHeader("X-Request-ID", "secret-user-id");
        var response = new MockHttpServletResponse();
        new RequestDiagnosticsFilter().doFilter(request, response, (req, res) -> {
            var result = new HttpProblemAdvice().unexpected(
                    new IllegalStateException("secret-exception", new RuntimeException("secret-cause")), new ServletWebRequest(request));
            assertThat(result.getStatusCode().value()).isEqualTo(500);
            assertThat(result.getBody().getDetail()).isEqualTo("An unexpected error occurred.");
        });
        String id = response.getHeader("X-Request-ID");
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(output.getOut()).contains(id, "IllegalStateException", "DiagnosticsTests")
                .doesNotContain("secret-exception", "secret-cause", "secret-token", "secret-query", "secret-user-id", "private-secret-path");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void previousMdcIsRestoredEvenWhenTheChainThrows() {
        MDC.put("requestId", "previous");
        try {
            assertThatThrownBy(() -> new RequestDiagnosticsFilter().doFilter(new MockHttpServletRequest(),
                    new MockHttpServletResponse(), (req, res) -> { throw new jakarta.servlet.ServletException("failure"); }))
                    .isInstanceOf(jakarta.servlet.ServletException.class);
            assertThat(MDC.get("requestId")).isEqualTo("previous");
        } finally { MDC.remove("requestId"); }
    }
}
