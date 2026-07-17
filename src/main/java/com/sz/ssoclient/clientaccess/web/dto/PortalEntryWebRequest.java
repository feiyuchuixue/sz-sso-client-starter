package com.sz.ssoclient.clientaccess.web.dto;

/** Browser-selected Portal path; user and Client identity are never accepted here. */
public record PortalEntryWebRequest(String targetPath) {
}
