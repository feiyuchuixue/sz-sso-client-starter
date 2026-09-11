package com.sz.ssoclient.internal.firstparty;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SsoFirstPartyContractTest {

    @Test
    void contributionAndTransportExposeOnlyTypedNeutralContracts() throws Exception {
        Method messageType = SsoFirstPartyMessageContribution.class.getMethod("messageType");
        Method handle = SsoFirstPartyMessageContribution.class.getMethod(
                "handle", SsoFirstPartyMessageContext.class);
        assertEquals(String.class, messageType.getReturnType());
        assertEquals(com.sz.ssocore.SsoMessageResult.class, handle.getReturnType());
        assertEquals(2, SsoFirstPartyMessageContribution.class.getDeclaredMethods().length);

        Method send = SsoFirstPartyMessageTransport.class.getMethod(
                "sendToServer", String.class, Map.class, Class.class);
        assertEquals(com.sz.ssocore.SsoMessageResult.class, send.getReturnType());
        assertEquals(1, send.getTypeParameters().length);
        assertEquals("com.sz.ssocore.SsoMessageResult<T>", send.getGenericReturnType().getTypeName());
        assertEquals(1, SsoFirstPartyMessageTransport.class.getDeclaredMethods().length);
    }

    @Test
    void friendContextRecursivelyCopiesNeutralPayload() {
        List<Object> nested = new ArrayList<>(List.of("a", true));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("items", nested);

        SsoFirstPartyMessageContext context = new SsoFirstPartyMessageContext("PLATFORM_CONFIG_QUERY", source);
        nested.clear();
        source.clear();

        assertEquals(List.of("a", true), context.payload().get("items"));
        assertThrows(UnsupportedOperationException.class, () -> context.payload().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new SsoFirstPartyMessageContext("PLATFORM_CONFIG_QUERY", Map.of("host", new Object())));
    }
}
