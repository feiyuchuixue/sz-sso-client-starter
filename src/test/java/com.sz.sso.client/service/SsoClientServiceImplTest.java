package com.sz.sso.client.service;

import cn.dev33.satoken.sso.model.SaCheckTicketResult;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.sz.sso.client.SsoLoginHandler;
import com.sz.sso.client.SsoSessionCreator;
import com.sz.sso.client.pojo.SsoLoginResult;
import com.sz.sso.client.service.impl.SsoClientServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SsoClientServiceImpl} 单元测试.
 * <p>
 * 验证 login() 方法的 SPI 调用链、参数透传、loginId 类型转换等核心逻辑。
 * 使用 Mockito mock 两个 SPI，不依赖 Spring 上下文或 Sa-Token 真实运行环境。
 * </p>
 */
@DisplayName("SsoClientServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class SsoClientServiceImplTest {

    /** 测试用的虚拟用户类型 */
    record TestUser(Long userId, String name) {}

    @Mock
    private SsoLoginHandler<TestUser> loginHandler;

    @Mock
    private SsoSessionCreator<TestUser> sessionCreator;

    private SsoClientServiceImpl<TestUser> service;

    @BeforeEach
    void setUp() {
        service = new SsoClientServiceImpl<>(loginHandler, sessionCreator);
    }

    // ---------------------------------------------------------------
    // 辅助方法：构建 SaCheckTicketResult
    // ---------------------------------------------------------------

    private SaCheckTicketResult buildCtr(Object loginId, String deviceId, long remainTimeout) {
        SaCheckTicketResult ctr = new SaCheckTicketResult();
        ctr.loginId = loginId;
        ctr.deviceId = deviceId;
        ctr.remainTokenTimeout = remainTimeout;
        ctr.centerId = loginId; // 测试中 centerId 与 loginId 相同
        return ctr;
    }

    // ---------------------------------------------------------------
    // 正常路径：SPI 调用链
    // ---------------------------------------------------------------

    @Test
    @DisplayName("login() 应调用 SsoLoginHandler.buildLoginUser 并传入正确 Long userId")
    void login_shouldCallBuildLoginUser_withCorrectUserId() {
        Long userId = 123L;
        TestUser fakeUser = new TestUser(userId, "Alice");
        SsoLoginResult fakeResult = SsoLoginResult.of("token-abc", 3600L, fakeUser);

        when(loginHandler.buildLoginUser(userId)).thenReturn(fakeUser);
        when(sessionCreator.createSession(eq(fakeUser), any(SaLoginParameter.class), eq(userId)))
                .thenReturn(fakeResult);

        SaCheckTicketResult ctr = buildCtr(userId, "device-001", 3600L);
        SsoLoginResult result = service.login(ctr);

        verify(loginHandler).buildLoginUser(userId);
        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo("token-abc");
    }

    @Test
    @DisplayName("login() 应调用 SsoSessionCreator.createSession 并传入正确参数")
    void login_shouldCallCreateSession_withCorrectParameters() {
        Long userId = 456L;
        TestUser fakeUser = new TestUser(userId, "Bob");
        SsoLoginResult fakeResult = SsoLoginResult.of("token-xyz", 7200L, fakeUser);

        when(loginHandler.buildLoginUser(userId)).thenReturn(fakeUser);
        when(sessionCreator.createSession(any(), any(), any())).thenReturn(fakeResult);

        SaCheckTicketResult ctr = buildCtr(userId, "device-002", 7200L);
        service.login(ctr);

        verify(sessionCreator).createSession(eq(fakeUser), any(SaLoginParameter.class), eq(userId));
    }

    @Test
    @DisplayName("login() 返回值应直接来自 SsoSessionCreator.createSession")
    void login_shouldReturnResultFromSessionCreator() {
        Long userId = 789L;
        TestUser fakeUser = new TestUser(userId, "Charlie");
        SsoLoginResult expected = SsoLoginResult.of("my-token", 1800L, fakeUser);

        when(loginHandler.buildLoginUser(userId)).thenReturn(fakeUser);
        when(sessionCreator.createSession(any(), any(), any())).thenReturn(expected);

        SaCheckTicketResult ctr = buildCtr(userId, "device-003", 1800L);
        SsoLoginResult actual = service.login(ctr);

        assertThat(actual).isSameAs(expected);
    }

    // ---------------------------------------------------------------
    // SaLoginParameter 参数透传验证
    // ---------------------------------------------------------------

    @Test
    @DisplayName("login() 应将 ctr.deviceId 透传到 SaLoginParameter")
    void login_shouldPassDeviceIdToParameter() {
        Long userId = 1L;
        String expectedDeviceId = "my-device-id";
        TestUser fakeUser = new TestUser(userId, "Dave");
        SsoLoginResult fakeResult = SsoLoginResult.of("t", 100L, fakeUser);

        when(loginHandler.buildLoginUser(userId)).thenReturn(fakeUser);
        when(sessionCreator.createSession(any(), any(), any())).thenReturn(fakeResult);

        SaCheckTicketResult ctr = buildCtr(userId, expectedDeviceId, 100L);
        service.login(ctr);

        ArgumentCaptor<SaLoginParameter> paramCaptor = ArgumentCaptor.forClass(SaLoginParameter.class);
        verify(sessionCreator).createSession(any(), paramCaptor.capture(), any());

        SaLoginParameter captured = paramCaptor.getValue();
        assertThat(captured.getDeviceId()).isEqualTo(expectedDeviceId);
    }

    @Test
    @DisplayName("login() 应将 ctr.remainTokenTimeout 设置到 SaLoginParameter 的 timeout 和 activeTimeout")
    void login_shouldPassRemainTimeoutToParameter() {
        Long userId = 2L;
        long remainTimeout = 5000L;
        TestUser fakeUser = new TestUser(userId, "Eve");
        SsoLoginResult fakeResult = SsoLoginResult.of("t", remainTimeout, fakeUser);

        when(loginHandler.buildLoginUser(userId)).thenReturn(fakeUser);
        when(sessionCreator.createSession(any(), any(), any())).thenReturn(fakeResult);

        SaCheckTicketResult ctr = buildCtr(userId, "d", remainTimeout);
        service.login(ctr);

        ArgumentCaptor<SaLoginParameter> paramCaptor = ArgumentCaptor.forClass(SaLoginParameter.class);
        verify(sessionCreator).createSession(any(), paramCaptor.capture(), any());

        SaLoginParameter captured = paramCaptor.getValue();
        assertThat(captured.getTimeout()).isEqualTo(remainTimeout);
        assertThat(captured.getActiveTimeout()).isEqualTo(remainTimeout);
    }

    // ---------------------------------------------------------------
    // loginId 类型转换（Long.valueOf("" + loginId)）
    // ---------------------------------------------------------------

    @Test
    @DisplayName("login() 应将 String 类型的 loginId 正确转换为 Long 传给 buildLoginUser")
    void login_stringLoginId_shouldBeConvertedToLong() {
        // ctr.loginId 可能是 String（来自 JSON 反序列化场景）
        String loginIdStr = "999";
        Long expectedLong = 999L;
        TestUser fakeUser = new TestUser(expectedLong, "Frank");
        SsoLoginResult fakeResult = SsoLoginResult.of("t", 100L, fakeUser);

        when(loginHandler.buildLoginUser(expectedLong)).thenReturn(fakeUser);
        when(sessionCreator.createSession(any(), any(), any())).thenReturn(fakeResult);

        SaCheckTicketResult ctr = buildCtr(loginIdStr, "d", 100L);
        service.login(ctr);

        verify(loginHandler).buildLoginUser(expectedLong);
    }

    @Test
    @DisplayName("login() 传给 createSession 的 loginId 应为原始 ctr.loginId（未做类型转换）")
    void login_originalLoginId_shouldBePassedToCreateSession() {
        Long userId = 77L;
        TestUser fakeUser = new TestUser(userId, "Grace");
        SsoLoginResult fakeResult = SsoLoginResult.of("t", 100L, fakeUser);

        when(loginHandler.buildLoginUser(userId)).thenReturn(fakeUser);
        when(sessionCreator.createSession(any(), any(), eq(userId))).thenReturn(fakeResult);

        SaCheckTicketResult ctr = buildCtr(userId, "d", 100L);
        service.login(ctr);

        // 第三个参数是原始 ctr.loginId（Object），不做额外转换
        verify(sessionCreator).createSession(any(), any(), eq(userId));
    }

}
