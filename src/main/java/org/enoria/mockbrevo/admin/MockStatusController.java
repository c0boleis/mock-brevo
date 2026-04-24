package org.enoria.mockbrevo.admin;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.enoria.mockbrevo.config.MockBrevoProperties;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.AccountRepository;
import org.enoria.mockbrevo.domain.ContactListRepository;
import org.enoria.mockbrevo.domain.ContactRepository;
import org.enoria.mockbrevo.domain.EmailCampaignRepository;
import org.enoria.mockbrevo.domain.FolderRepository;
import org.enoria.mockbrevo.domain.SenderRepository;
import org.enoria.mockbrevo.domain.SentEmail;
import org.enoria.mockbrevo.domain.SentEmailRepository;
import org.enoria.mockbrevo.domain.SmtpTemplateRepository;
import org.enoria.mockbrevo.faker.CampaignFaker;
import org.enoria.mockbrevo.observability.RequestLogStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock-status")
public class MockStatusController {

    private final AccountRepository accounts;
    private final SentEmailRepository sentEmails;
    private final ContactRepository contacts;
    private final ContactListRepository lists;
    private final EmailCampaignRepository campaigns;
    private final SmtpTemplateRepository templates;
    private final FolderRepository folders;
    private final SenderRepository senders;
    private final MockBrevoProperties properties;
    private final RequestLogStore requestLog;
    private final CampaignFaker campaignFaker;

    public MockStatusController(
            AccountRepository accounts,
            SentEmailRepository sentEmails,
            ContactRepository contacts,
            ContactListRepository lists,
            EmailCampaignRepository campaigns,
            SmtpTemplateRepository templates,
            FolderRepository folders,
            SenderRepository senders,
            MockBrevoProperties properties,
            RequestLogStore requestLog,
            CampaignFaker campaignFaker) {
        this.accounts = accounts;
        this.sentEmails = sentEmails;
        this.contacts = contacts;
        this.lists = lists;
        this.campaigns = campaigns;
        this.templates = templates;
        this.folders = folders;
        this.senders = senders;
        this.properties = properties;
        this.requestLog = requestLog;
        this.campaignFaker = campaignFaker;
    }

    @PostMapping("/accounts/{apiKey}/campaigns")
    public ResponseEntity<Map<String, Object>> createCampaign(
            @PathVariable String apiKey,
            @RequestBody(required = false) CreateCampaignForm form) {
        var accountOpt = accounts.findByApiKey(apiKey);
        if (accountOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CreateCampaignForm f = form != null ? form : new CreateCampaignForm();
        var campaign = campaignFaker.create(accountOpt.get(), new CampaignFaker.Spec(
                f.name, f.subject, f.senderName, f.senderEmail,
                f.utmCampaign, f.listIdsCsv, f.status));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", campaign.getId());
        body.put("name", campaign.getName());
        body.put("subject", campaign.getSubject() != null ? campaign.getSubject() : "");
        body.put("status", campaign.getStatus());
        body.put("utmCampaign", campaign.getUtmCampaign() != null ? campaign.getUtmCampaign() : "");
        return ResponseEntity.status(201).body(body);
    }

    public static class CreateCampaignForm {
        public String name;
        public String subject;
        public String senderName;
        public String senderEmail;
        public String utmCampaign;
        public String listIdsCsv;
        public String status;
    }

    @GetMapping("/requests")
    public Map<String, Object> requests(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String apiKey) {
        var entries = requestLog.snapshot(Math.min(Math.max(1, limit), 500), apiKey).stream()
                .map(org.enoria.mockbrevo.observability.RequestLogEntry::withoutBodies)
                .toList();
        return Map.of(
                "count", entries.size(),
                "total", requestLog.size(),
                "requests", entries
        );
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<org.enoria.mockbrevo.observability.RequestLogEntry> requestDetail(@PathVariable long id) {
        return requestLog.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public Map<String, Object> status() {
        List<Account> all = accounts.findAllByOrderByCreatedAtAsc();
        return Map.of(
                "service", "mock-brevo",
                "accountsCount", all.size(),
                "accounts", all.stream().map(this::toSummary).toList()
        );
    }

    @GetMapping("/accounts/{apiKey}/emails")
    public ResponseEntity<Map<String, Object>> emails(
            @PathVariable String apiKey,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {
        return accounts.findByApiKey(apiKey)
                .map(account -> {
                    var page = sentEmails.findByAccountOrderByIdDesc(
                            account, PageRequest.of(offset / Math.max(1, limit), Math.max(1, limit)));
                    List<Map<String, Object>> items = page.getContent().stream()
                            .map(this::toEmailSummary)
                            .toList();
                    Map<String, Object> body = Map.of(
                            "count", page.getTotalElements(),
                            "emails", items
                    );
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> toSummary(Account a) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (properties.isRevealKeys()) {
            m.put("apiKey", a.getApiKey());
        }
        m.put("apiKeyPreview", maskKey(a.getApiKey()));
        m.put("createdAt", a.getCreatedAt().toString());
        m.put("lastSeenAt", a.getLastSeenAt().toString());
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("firstName", a.getFirstName());
        account.put("lastName", a.getLastName());
        account.put("email", a.getEmail());
        account.put("companyName", a.getCompanyName());
        account.put("organizationId", a.getOrganizationId());
        account.put("credits", a.getCredits());
        m.put("account", account);
        m.put("counters", Map.of(
                "emailsSent", sentEmails.countByAccount(a),
                "contacts", contacts.countByAccount(a),
                "lists", lists.countByAccount(a),
                "campaigns", campaigns.countByAccount(a),
                "templates", templates.countByAccount(a),
                "folders", folders.countByAccount(a),
                "senders", senders.countByAccount(a)
        ));
        return m;
    }

    private Map<String, Object> toEmailSummary(SentEmail e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("messageId", e.getMessageId());
        m.put("subject", e.getSubject());
        m.put("senderEmail", e.getSenderEmail());
        m.put("senderName", e.getSenderName());
        m.put("replyTo", e.getReplyTo());
        m.put("recipients", e.getRecipients());
        m.put("templateId", e.getTemplateId());
        m.put("sentAt", e.getSentAt() != null ? e.getSentAt().toString() : Instant.now().toString());
        m.put("smtpForwardedAt", e.getSmtpForwardedAt() != null ? e.getSmtpForwardedAt().toString() : null);
        m.put("smtpForwardError", e.getSmtpForwardError());
        m.put("payload", e.getPayloadJson());
        return m;
    }

    private static String maskKey(String key) {
        if (key == null || key.length() <= 8) return "***";
        return key.substring(0, 6) + "…" + key.substring(key.length() - 4);
    }
}
