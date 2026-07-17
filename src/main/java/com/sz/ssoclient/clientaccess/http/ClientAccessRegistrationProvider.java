package com.sz.ssoclient.clientaccess.http;

@FunctionalInterface
public interface ClientAccessRegistrationProvider {

    ClientAccessRegistration current();
}
