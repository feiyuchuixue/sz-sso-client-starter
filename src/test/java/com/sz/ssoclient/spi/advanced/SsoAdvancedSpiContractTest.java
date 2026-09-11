package com.sz.ssoclient.spi.advanced;

import com.sz.ssoclient.spi.SsoClientSessionHandle;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SsoAdvancedSpiContractTest {

    @Test
    void customHandlerAndLocalSessionAccessorExposeOnlyNeutralTypedMethods() throws Exception {
        assertMethod(SsoClientMessageHandler.class, "messageType", String.class);
        assertMethod(SsoClientMessageHandler.class, "handle", com.sz.ssocore.SsoMessageResult.class,
                SsoClientMessageContext.class);
        assertEquals(2, SsoClientMessageHandler.class.getDeclaredMethods().length);

        assertMethod(SsoClientLocalSessionAccessor.class, "current", SsoClientLocalSession.class);
        assertMethod(SsoClientLocalSessionAccessor.class, "revokeExact",
                com.sz.ssocore.signout.SsoLocalOutcome.class, SsoClientSessionHandle.class);
        assertMethod(SsoClientLocalSessionAccessor.class, "revokeDevice",
                com.sz.ssocore.signout.SsoLocalOutcome.class, String.class, String.class);
        assertMethod(SsoClientLocalSessionAccessor.class, "revokeAccount",
                com.sz.ssocore.signout.SsoLocalOutcome.class, String.class);
        assertEquals(4, SsoClientLocalSessionAccessor.class.getDeclaredMethods().length);
    }

    @Test
    void stateRepositoryKeepsAtomicTtlAndCompareOperations() throws Exception {
        assertMethod(SsoClientStateRepository.class, "get", String.class, String.class);
        assertMethod(SsoClientStateRepository.class, "putIfAbsent", boolean.class,
                String.class, String.class, Duration.class);
        assertMethod(SsoClientStateRepository.class, "compareAndSet", boolean.class,
                String.class, String.class, String.class, Duration.class);
        assertMethod(SsoClientStateRepository.class, "compareAndDelete", boolean.class,
                String.class, String.class);
        assertMethod(SsoClientStateRepository.class, "shared", boolean.class);
        assertMethod(SsoClientStateRepository.class, "description", String.class);
        assertEquals(6, SsoClientStateRepository.class.getDeclaredMethods().length);
    }

    @Test
    void messageContextRecursivelyCopiesNeutralPayloadAndRejectsHostObjects() {
        List<Object> nestedList = new ArrayList<>(List.of("value", 42L));
        Map<String, Object> nestedMap = new LinkedHashMap<>();
        nestedMap.put("items", nestedList);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("nested", nestedMap);

        SsoClientMessageContext context = new SsoClientMessageContext("CUSTOM_AUDIT", source);
        nestedList.clear();
        nestedMap.clear();
        source.clear();

        Map<?, ?> copiedNested = (Map<?, ?>) context.payload().get("nested");
        assertEquals(List.of("value", 42L), copiedNested.get("items"));
        assertThrows(UnsupportedOperationException.class, () -> context.payload().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new SsoClientMessageContext("CUSTOM_AUDIT", Map.of("host", new Object())));
    }

    @Test
    void authenticatedLocalSessionRequiresAllTrustedHandles() {
        SsoClientSessionHandle handle = new SsoClientSessionHandle("session-1");
        SsoClientLocalSession session = new SsoClientLocalSession(true, "local-1", "device-a", handle);

        assertEquals("local-1", session.localUserId());
        assertThrows(IllegalArgumentException.class,
                () -> new SsoClientLocalSession(true, "local-1", " ", handle));
        assertThrows(IllegalArgumentException.class,
                () -> new SsoClientLocalSession(true, "local-1", "device-a", null));
    }

    private static void assertMethod(Class<?> owner, String name, Class<?> returnType, Class<?>... parameterTypes)
            throws Exception {
        Method method = owner.getMethod(name, parameterTypes);
        assertEquals(returnType, method.getReturnType(), owner.getName() + "#" + name);
    }
}
