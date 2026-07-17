package com.sz.ssoclient.clientaccess.web;

/** Business-session boundary used by CAP browser endpoints and SLO callbacks. */
public interface ClientLocalSessionAccessor {

    ClientLocalSession current();

    void logoutCurrentSession();

    void logoutCurrentDevice(Object localUserId, String deviceId);

    void logoutAccount(Object localUserId);
}
