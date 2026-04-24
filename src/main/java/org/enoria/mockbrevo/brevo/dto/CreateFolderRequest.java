package org.enoria.mockbrevo.brevo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateFolderRequest(String name) {}
