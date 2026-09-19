package com.spendly.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void reusesAnInboundCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "trace-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("trace-abc-123");
        assertThat(request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE)).isEqualTo("trace-abc-123");
    }

    @Test
    void generatesOneWhenTheCallerSendsNone() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        String generated = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(generated).isNotNull();
        assertThat(UUID.fromString(generated)).isNotNull();
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {"has space", "new\nline", "semi;colon", "<script>", "  "})
    void replacesIdsThatWouldPolluteALogLine(String hostile) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, hostile);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotEqualTo(hostile);
    }

    @Test
    void replacesAnOverlongId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "x".repeat(65));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).hasSize(36);
    }

    @Test
    void exposesTheIdToLoggingDuringTheRequestAndClearsItAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "during-request");

        FilterChain assertingChain = (req, res) -> assertThat(MDC.get(CorrelationIdFilter.MDC_KEY))
                .isEqualTo("during-request");

        filter.doFilter(request, new MockHttpServletResponse(), assertingChain);

        // Servlet threads are pooled, so a leaked id would mislabel the next request's logs.
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void clearsTheIdEvenWhenTheChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        FilterChain boom = (req, res) -> {
            throw new IllegalStateException("downstream failure");
        };

        try {
            filter.doFilter(request, new MockHttpServletResponse(), boom);
        } catch (Exception expected) {
            // the filter must not swallow it
        }

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
