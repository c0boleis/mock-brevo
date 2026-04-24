package org.enoria.mockbrevo.brevo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateListRequest(String name, Long folderId) {}
