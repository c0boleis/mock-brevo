package org.enoria.mockbrevo.brevo;

import java.util.List;
import java.util.Random;
import org.enoria.mockbrevo.auth.CurrentAccount;
import org.enoria.mockbrevo.brevo.dto.SendersResponse;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.SenderRepository;
import org.enoria.mockbrevo.util.MockData;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v3/senders")
public class SendersController {

    private final SenderRepository senders;

    public SendersController(SenderRepository senders) {
        this.senders = senders;
    }

    @GetMapping
    public SendersResponse list() {
        Account a = CurrentAccount.require();
        return new SendersResponse(
                senders.findByAccountOrderByIdAsc(a).stream()
                        .map(s -> {
                            Random r = MockData.seededFrom(a.getApiKey(), "sender", s.getId());
                            List<SendersResponse.Ip> ips = List.of(
                                    new SendersResponse.Ip(
                                            MockData.ipAddress(r),
                                            MockData.senderDomain(r),
                                            100));
                            return new SendersResponse.Sender(
                                    s.getId(), s.getName(), s.getEmail(), s.isActive(), ips);
                        })
                        .toList()
        );
    }
}
