package com.sz.ssoclient.api;

import com.sz.ssocore.SsoMessageResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SsoPublicApiContractTest {

    @Test
    void hostCallableApisExposeOnlyTheFrozenTypedOperations() throws Exception {
        Method sync = SsoClientSuperAdminSyncOperations.class.getMethod(
                "syncSuperAdmin", String.class, boolean.class);
        assertEquals(SsoMessageResult.class, sync.getReturnType());
        assertEquals("com.sz.ssocore.SsoMessageResult<java.lang.Void>", sync.getGenericReturnType().getTypeName());
        assertEquals(1, SsoClientSuperAdminSyncOperations.class.getDeclaredMethods().length);

        Method sendCustom = SsoClientMessageSender.class.getMethod(
                "sendCustom", String.class, Map.class, Class.class);
        assertEquals(SsoMessageResult.class, sendCustom.getReturnType());
        assertEquals(1, sendCustom.getTypeParameters().length);
        assertEquals("com.sz.ssocore.SsoMessageResult<T>", sendCustom.getGenericReturnType().getTypeName());
        assertEquals("java.util.Map<java.lang.String, ?>",
                sendCustom.getGenericParameterTypes()[1].getTypeName());
        assertEquals(1, SsoClientMessageSender.class.getDeclaredMethods().length);
    }

    @Test
    void publicApiSignaturesContainNoFrameworkOrInternalTypes() {
        Set<String> signatures = Set.of(SsoClientSuperAdminSyncOperations.class, SsoClientMessageSender.class).stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .flatMap(method -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(method.getGenericReturnType().getTypeName()),
                        Arrays.stream(method.getGenericParameterTypes()).map(java.lang.reflect.Type::getTypeName)))
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        assertFalse(signatures.stream().anyMatch(signature -> signature.contains("cn.dev33")), signatures.toString());
        assertFalse(signatures.stream().anyMatch(signature -> signature.contains("jackson")), signatures.toString());
        assertFalse(signatures.stream().anyMatch(signature -> signature.contains("springframework")), signatures.toString());
        assertFalse(signatures.stream().anyMatch(signature -> signature.contains(".internal.")), signatures.toString());
    }
}
