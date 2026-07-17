package com.sz.ssoclient.clientaccess.web.dto;

/** Browser request to create a backend-bound login transaction. */
public record LoginTransactionWebRequest(String back, String mode, String theme) {
}
