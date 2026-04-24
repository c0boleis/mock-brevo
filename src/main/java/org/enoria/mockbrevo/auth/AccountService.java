package org.enoria.mockbrevo.auth;

import java.time.Instant;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.AccountRepository;
import org.enoria.mockbrevo.domain.Sender;
import org.enoria.mockbrevo.domain.SenderRepository;
import org.enoria.mockbrevo.faker.CampaignFaker;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final SenderRepository senders;
    private final CampaignFaker campaignFaker;

    public AccountService(
            AccountRepository accounts,
            SenderRepository senders,
            @Lazy CampaignFaker campaignFaker) {
        this.accounts = accounts;
        this.senders = senders;
        this.campaignFaker = campaignFaker;
    }

    @Transactional
    public Account resolveOrProvision(String apiKey) {
        return accounts.findByApiKey(apiKey)
                .map(a -> {
                    if (a.getOrganizationId() == null) {
                        a.setOrganizationId(Account.deriveOrganizationId(apiKey));
                    }
                    a.setLastSeenAt(Instant.now());
                    return a;
                })
                .orElseGet(() -> provision(apiKey));
    }

    private Account provision(String apiKey) {
        Account a = accounts.save(Account.provision(apiKey));
        Sender s = new Sender();
        s.setAccount(a);
        s.setName(a.getFirstName() + " " + a.getLastName());
        s.setEmail(a.getEmail());
        s.setActive(true);
        senders.save(s);
        campaignFaker.seedDefaults(a);
        return a;
    }
}
