package org.enoria.mockbrevo.brevo.dto;

import java.util.List;

public record SendersResponse(List<Sender> senders) {
    public record Sender(
            Long id,
            String name,
            String email,
            Boolean active,
            List<Ip> ips) {}

    public record Ip(String ip, String domain, Integer weight) {}
}
