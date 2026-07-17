package com.sz.ssoclient.clientaccess.http;

@FunctionalInterface
public interface ClientAccessHttpTransport {

    ClientAccessHttpResponse exchange(ClientAccessHttpRequest request);
}
