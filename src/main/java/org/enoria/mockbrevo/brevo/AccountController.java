package org.enoria.mockbrevo.brevo;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import org.enoria.mockbrevo.auth.CurrentAccount;
import org.enoria.mockbrevo.brevo.dto.AccountResponse;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.util.MockData;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v3/account")
public class AccountController {

    @GetMapping
    public AccountResponse getAccount() {
        Account a = CurrentAccount.require();
        Random r = MockData.seededFrom(a.getApiKey(), "account");
        LocalDate start = a.getCreatedAt().atOffset(ZoneOffset.UTC).toLocalDate();
        LocalDate end = start.plusYears(1);
        return new AccountResponse(
                a.getEmail(),
                a.getFirstName(),
                a.getLastName(),
                a.getCompanyName(),
                a.getOrganizationId(),
                a.getId(),
                false,
                new AccountResponse.Address(
                        MockData.street(r),
                        MockData.city(r),
                        MockData.zipCode(r),
                        MockData.country(r)),
                new AccountResponse.DateTimePreferences(
                        "Europe/Paris",
                        "24h",
                        "DD/MM/YYYY"),
                List.of(new AccountResponse.Plan(
                        "payAsYouGo",
                        a.getCredits(),
                        "sendLimit",
                        start.toString(),
                        end.toString())),
                new AccountResponse.Relay(
                        true,
                        new AccountResponse.RelayData(
                                a.getEmail(),
                                "smtp-relay." + MockData.senderDomain(r),
                                587)),
                new AccountResponse.MarketingAutomation(
                        String.format("%016x%016x", r.nextLong(), r.nextLong()),
                        true)
        );
    }
}
