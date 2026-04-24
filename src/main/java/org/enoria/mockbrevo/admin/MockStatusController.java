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
import org.springframework.transaction.annotation.Transactional;
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

    @GetMapping("/accounts/{apiKey}/campaigns")
    public ResponseEntity<Map<String, Object>> accountCampaigns(@PathVariable String apiKey) {
        return accounts.findByApiKey(apiKey)
                .map(account -> {
                    var page = campaigns.findByAccountOrderByIdDesc(account, PageRequest.of(0, 200));
                    List<Map<String, Object>> items = page.getContent().stream()
                            .map(c -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("id", c.getId());
                                m.put("name", c.getName());
                                m.put("subject", c.getSubject());
                                m.put("status", c.getStatus());
                                m.put("sentDate", c.getSentDate() != null ? c.getSentDate().toString() : null);
                                m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
                                m.put("deliveredCount", c.getDeliveredCount());
                                m.put("utmCampaign", c.getUtmCampaign());
                                return m;
                            })
                            .toList();
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("count", page.getTotalElements());
                    body.put("campaigns", items);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/accounts/{apiKey}/lists")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> accountLists(@PathVariable String apiKey) {
        return accounts.findByApiKey(apiKey)
                .map(account -> {
                    var page = lists.findByAccountOrderByIdAsc(account, PageRequest.of(0, 200));
                    List<Map<String, Object>> items = page.getContent().stream()
                            .map(l -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("id", l.getId());
                                m.put("name", l.getName());
                                m.put("folderId", l.getFolder() != null ? l.getFolder().getId() : null);
                                m.put("folderName", l.getFolder() != null ? l.getFolder().getName() : null);
                                long subs = contacts.countByAccountAndListsContaining(account, l);
                                m.put("uniqueSubscribers", subs);
                                return m;
                            })
                            .toList();
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("count", page.getTotalElements());
                    body.put("lists", items);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
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

    // ---- Deep-link detail endpoints (for /marketing-campaign/edit/{id} etc.) ----

    @GetMapping("/campaigns/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> campaignDetail(@PathVariable Long id) {
        return campaigns.findById(id)
                .map(c -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("id", c.getId());
                    body.put("name", c.getName());
                    body.put("subject", c.getSubject());
                    body.put("status", c.getStatus());
                    body.put("utmCampaign", c.getUtmCampaign());
                    body.put("templateId", c.getTemplateId());
                    body.put("senderName", c.getSenderName());
                    body.put("senderEmail", c.getSenderEmail());
                    body.put("replyTo", c.getReplyTo());
                    body.put("recipientListIdsCsv", c.getRecipientListIdsCsv());
                    body.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
                    body.put("sentDate", c.getSentDate() != null ? c.getSentDate().toString() : null);
                    body.put("deliveredCount", c.getDeliveredCount());
                    Account a = c.getAccount();
                    body.put("account", Map.of(
                            "apiKeyPreview", maskKey(a.getApiKey()),
                            "apiKey", properties.isRevealKeys() ? a.getApiKey() : null,
                            "email", a.getEmail(),
                            "firstName", a.getFirstName(),
                            "lastName", a.getLastName(),
                            "organizationId", a.getOrganizationId() != null ? a.getOrganizationId() : ""
                    ));
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/lists/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> listDetail(@PathVariable Long id) {
        return lists.findById(id)
                .map(l -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("id", l.getId());
                    body.put("name", l.getName());
                    Long folderId = l.getFolder() != null ? l.getFolder().getId() : 0L;
                    body.put("folderId", folderId);
                    body.put("folderName", l.getFolder() != null ? l.getFolder().getName() : null);
                    Account a = l.getAccount();
                    body.put("account", Map.of(
                            "apiKeyPreview", maskKey(a.getApiKey()),
                            "apiKey", properties.isRevealKeys() ? a.getApiKey() : null,
                            "email", a.getEmail(),
                            "firstName", a.getFirstName(),
                            "lastName", a.getLastName()
                    ));
                    List<Map<String, Object>> contactList = contacts.findByListsContainingOrderByIdAsc(l).stream()
                            .map(c -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("id", c.getId());
                                m.put("email", c.getEmail());
                                m.put("firstName", c.getFirstName());
                                m.put("lastName", c.getLastName());
                                m.put("emailBlacklisted", c.isEmailBlacklisted());
                                return m;
                            })
                            .toList();
                    body.put("contactsCount", contactList.size());
                    body.put("contacts", contactList);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
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
