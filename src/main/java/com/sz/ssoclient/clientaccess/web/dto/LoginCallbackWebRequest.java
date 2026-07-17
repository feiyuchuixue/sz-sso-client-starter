package com.sz.ssoclient.clientaccess.web.dto;

/** Browser delivery of the short-lived Login Ticket and its state. */
public record LoginCallbackWebRequest(String ticket, String state) {
}
