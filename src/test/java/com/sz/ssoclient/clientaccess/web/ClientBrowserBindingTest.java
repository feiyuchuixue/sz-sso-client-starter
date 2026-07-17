package com.sz.ssoclient.clientaccess.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientBrowserBindingTest {

    private static final String TOKEN = "A".repeat(43);

    @Test
    void createsRandomHttpOnlySameSiteCookieWithoutHttpSession() {
        ClientBrowserBinding binding = new ClientBrowserBinding(() -> TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(binding.resolveOrCreate(request, response)).isEqualTo(TOKEN);

        assertThat(response.getHeader("Set-Cookie"))
                .contains(ClientBrowserBinding.COOKIE_NAME + "=" + TOKEN)
                .contains("Path=/")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .doesNotContain("Max-Age");
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void reusesValidBindingAndRequiresItOnCallback() {
        ClientBrowserBinding binding = new ClientBrowserBinding(() -> "B".repeat(43));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(ClientBrowserBinding.COOKIE_NAME, TOKEN));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(binding.resolveOrCreate(request, response)).isEqualTo(TOKEN);
        assertThat(binding.require(request)).isEqualTo(TOKEN);
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void rejectsMissingOrMalformedBindingOnCallback() {
        ClientBrowserBinding binding = new ClientBrowserBinding(() -> TOKEN);
        MockHttpServletRequest missing = new MockHttpServletRequest();
        MockHttpServletRequest malformed = new MockHttpServletRequest();
        malformed.setCookies(new Cookie(ClientBrowserBinding.COOKIE_NAME, "attacker-controlled"));

        assertThatThrownBy(() -> binding.require(missing))
                .isInstanceOf(ClientLoginTransactionException.class)
                .extracting("code")
                .isEqualTo("CAP-3002");
        assertThatThrownBy(() -> binding.require(malformed))
                .isInstanceOf(ClientLoginTransactionException.class)
                .extracting("code")
                .isEqualTo("CAP-3002");
    }
}
