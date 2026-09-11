package com.sz.ssoclient.spi;

import com.sz.ssocore.SsoUserMeta;
import com.sz.ssocore.provisioning.SsoClientGrantPurpose;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SsoPublicSpiContractTest {

    private static final Set<Class<?>> PUBLIC_SPI_INTERFACES = Set.of(
            SsoClientIdentityAdapter.class,
            SsoClientLoginAdapter.class,
            SsoDefaultAccessInitializer.class,
            SsoSuperAdminAuthorityAdapter.class,
            SsoClientIdentityPreparationService.class,
            SsoSuperAdminSnapshotProvider.class);

    @Test
    void identityAdapterUsesOpaqueLocalIdsAndBatchReverseMapping() throws Exception {
        assertMethod(SsoClientIdentityAdapter.class, "findSsoUserId", Optional.class, String.class);
        assertMethod(SsoClientIdentityAdapter.class, "findSsoUserIds", Map.class, Collection.class);
        assertMethod(SsoClientIdentityAdapter.class, "findLocalUserId", Optional.class, long.class);
        assertMethod(SsoClientIdentityAdapter.class, "resolveOrProvision", String.class, SsoUserMeta.class);
        assertMethod(SsoClientIdentityAdapter.class, "completeDefaultAccessInitialization", void.class, String.class);
        assertEquals(5, SsoClientIdentityAdapter.class.getDeclaredMethods().length);

        Method batch = SsoClientIdentityAdapter.class.getMethod("findSsoUserIds", Collection.class);
        assertEquals("java.util.Collection<java.lang.String>", batch.getGenericParameterTypes()[0].getTypeName());
        assertEquals("java.util.Map<java.lang.String, java.lang.Long>", batch.getGenericReturnType().getTypeName());
    }

    @Test
    void loginAndOptionalBusinessSpisExposeOnlyFrozenMethods() throws Exception {
        assertEquals(1, SsoClientLoginAdapter.class.getTypeParameters().length);
        assertMethod(SsoClientLoginAdapter.class, "loadLoginUser", Object.class, String.class);
        assertMethod(SsoClientLoginAdapter.class, "establishSession", SsoClientLoginResult.class,
                Object.class, SsoClientLoginContext.class);
        assertEquals(2, SsoClientLoginAdapter.class.getDeclaredMethods().length);

        assertMethod(SsoDefaultAccessInitializer.class, "initialize", SsoDefaultAccessStatus.class, String.class);
        assertMethod(SsoSuperAdminAuthorityAdapter.class, "apply", void.class, String.class, boolean.class);
        assertMethod(SsoClientIdentityPreparationService.class, "checkUsers",
                com.sz.ssocore.provisioning.SsoClientUserReadinessBatchResult.class,
                Collection.class, SsoClientGrantPurpose.class);
        assertMethod(SsoClientIdentityPreparationService.class, "prepareUsers",
                com.sz.ssocore.provisioning.SsoClientUserPreparationBatchResult.class,
                Collection.class, SsoClientGrantPurpose.class);
        assertMethod(SsoSuperAdminSnapshotProvider.class, "listAllSsoSuperAdminUserIds",
                Collection.class);

        Method snapshot = SsoSuperAdminSnapshotProvider.class.getMethod("listAllSsoSuperAdminUserIds");
        assertEquals("java.util.Collection<java.lang.String>", snapshot.getGenericReturnType().getTypeName());
    }

    @Test
    void loginRecordsAndDefaultAccessStatusesEnforceMinimalPublicContract() {
        SsoClientSessionHandle handle = new SsoClientSessionHandle("session-1");
        SsoClientLoginContext context = new SsoClientLoginContext(42L, "tenant:user-42", "device-a", true);
        SsoClientLoginResult result = new SsoClientLoginResult("token-1", handle);

        assertEquals("tenant:user-42", context.localUserId());
        assertEquals("device-a", context.deviceId());
        assertEquals("token-1", result.accessToken());
        assertEquals(handle, result.sessionHandle());
        assertEquals(List.of("APPLIED", "EXISTING_ACCESS", "ALREADY_COMPLETED", "DISABLED"),
                Arrays.stream(SsoDefaultAccessStatus.values()).map(Enum::name).toList());

        assertThrows(IllegalArgumentException.class, () -> new SsoClientSessionHandle(" "));
        assertThrows(IllegalArgumentException.class,
                () -> new SsoClientLoginContext(42L, " ", "device-a", false));
        assertThrows(IllegalArgumentException.class,
                () -> new SsoClientLoginContext(42L, "tenant:user-42", " ", false));
        assertThrows(IllegalArgumentException.class, () -> new SsoClientLoginResult(" ", handle));
        assertThrows(IllegalArgumentException.class, () -> new SsoClientLoginResult("token-1", null));
    }

    @Test
    void publicSpiSignaturesContainNoFrameworkTransportTypes() {
        Set<String> signatures = PUBLIC_SPI_INTERFACES.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .flatMap(method -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(method.getGenericReturnType().getTypeName()),
                        Arrays.stream(method.getGenericParameterTypes()).map(java.lang.reflect.Type::getTypeName)))
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        assertFalse(signatures.stream().anyMatch(signature -> signature.contains("cn.dev33")), signatures.toString());
        assertFalse(signatures.stream().anyMatch(signature -> signature.contains("jackson")), signatures.toString());
        assertFalse(signatures.stream().anyMatch(signature -> signature.contains("springframework")), signatures.toString());
    }

    private static void assertMethod(Class<?> owner, String name, Class<?> returnType, Class<?>... parameterTypes)
            throws Exception {
        Method method = owner.getMethod(name, parameterTypes);
        assertEquals(returnType, method.getReturnType(), owner.getName() + "#" + name);
    }
}
