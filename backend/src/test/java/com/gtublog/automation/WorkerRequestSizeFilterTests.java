package com.gtublog.automation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WorkerRequestSizeFilterTests {

    @Test
    void rejectsAnOversizedChunkedBodyWhenContentLengthIsUnknown() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v2/internal/generation-jobs/13/submit") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContentType("application/json");
        request.setContent(new byte[WorkerRequestSizeFilter.MAX_BODY_BYTES + 1]);
        request.addHeader("Transfer-Encoding", "chunked");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        new WorkerRequestSizeFilter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }
}
