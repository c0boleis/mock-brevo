package org.enoria.mockbrevo.brevo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.enoria.mockbrevo.auth.CurrentAccount;
import org.enoria.mockbrevo.brevo.dto.CreateEmailCampaignRequest;
import org.enoria.mockbrevo.brevo.dto.EmailCampaignsResponse;
import org.enoria.mockbrevo.brevo.dto.IdResponse;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.EmailCampaign;
import org.enoria.mockbrevo.domain.EmailCampaignRepository;
import org.enoria.mockbrevo.util.MockData;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v3/emailCampaigns")
public class EmailCampaignsController {

    private final EmailCampaignRepository campaigns;
    private final ObjectMapper objectMapper;

    public EmailCampaignsController(EmailCampaignRepository campaigns, ObjectMapper objectMapper) {
        this.campaigns = campaigns;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @Transactional
    public EmailCampaignsResponse list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100") int limit) {
        Account a = CurrentAccount.require();
        final String apiKey = a.getApiKey();
        var page = campaigns.findByAccountOrderByIdDesc(
                a, PageRequest.of(offset / Math.max(1, limit), Math.max(1, limit)));
        List<EmailCampaignsResponse.CampaignItem> items = page.getContent().stream()
                .map(c -> toItem(c, apiKey))
                .toList();
        return new EmailCampaignsResponse(items, page.getTotalElements());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse create(@RequestBody CreateEmailCampaignRequest req) {
        Account a = CurrentAccount.require();
        EmailCampaign c = new EmailCampaign();
        c.setAccount(a);
        c.setName(req.name() != null ? req.name() : "Untitled campaign");
        c.setSubject(req.subject());
        if (req.sender() != null) {
            c.setSenderName(req.sender().name());
            c.setSenderEmail(req.sender().email());
        }
        c.setReplyTo(req.replyTo());
        c.setTemplateId(req.templateId());
        c.setUtmCampaign(req.utmCampaign());
        if (req.recipients() != null && req.recipients().listIds() != null) {
            c.setRecipientListIdsCsv(req.recipients().listIds().stream()
                    .map(String::valueOf).collect(Collectors.joining(",")));
        }
        c.setParamsJson(serializeParams(req.params()));
        c.setStatus("draft");
        c.setCreatedAt(Instant.now());
        return new IdResponse(campaigns.save(c).getId());
    }

    @PostMapping("/{id}/sendNow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public ResponseEntity<Void> sendNow(@PathVariable Long id) {
        Account a = CurrentAccount.require();
        return campaigns.findByIdAndAccount(id, a)
                .map(c -> {
                    c.setStatus("sent");
                    c.setSentDate(Instant.now());
                    c.setDeliveredCount(estimateDelivered(c));
                    campaigns.save(c);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private EmailCampaignsResponse.CampaignItem toItem(EmailCampaign c, String apiKey) {
        Random r = MockData.seededFrom(apiKey, "campaign", c.getId());
        List<Long> listIds = parseListIds(c.getRecipientListIdsCsv());

        boolean sent = "sent".equalsIgnoreCase(c.getStatus());
        EmailCampaignsResponse.CampaignStats global;
        List<EmailCampaignsResponse.CampaignStats> perList;

        if (sent) {
            int total = c.getDeliveredCount() != null ? c.getDeliveredCount() : MockData.bounded(r, 200, 5000);
            int hard = MockData.bounded(r, 0, Math.max(1, total / 200));
            int soft = MockData.bounded(r, 0, Math.max(1, total / 150));
            int delivered = total - hard - soft;
            int unique = MockData.bounded(r, delivered / 3, Math.max(delivered / 2, delivered / 3 + 1));
            int clickers = MockData.bounded(r, unique / 8, Math.max(unique / 4, unique / 8 + 1));
            int complaints = MockData.bounded(r, 0, Math.max(1, delivered / 500));
            int unsubs = MockData.bounded(r, 0, Math.max(1, delivered / 200));
            global = new EmailCampaignsResponse.CampaignStats(
                    clickers, complaints, delivered, hard, total, soft,
                    unique, unique, unique, unsubs, delivered, null);
            perList = listIds.isEmpty()
                    ? List.of(new EmailCampaignsResponse.CampaignStats(
                            clickers, complaints, delivered, hard, total, soft,
                            unique, unique, unique, unsubs, delivered, 0L))
                    : listIds.stream()
                            .map(id -> {
                                int n = listIds.size();
                                return new EmailCampaignsResponse.CampaignStats(
                                        clickers / n, complaints / n, delivered / n, hard / n,
                                        total / n, soft / n, unique / n, unique / n,
                                        unique / n, unsubs / n, delivered / n, id);
                            })
                            .toList();
        } else {
            global = new EmailCampaignsResponse.CampaignStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null);
            perList = List.of(new EmailCampaignsResponse.CampaignStats(
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, listIds.isEmpty() ? 0L : listIds.get(0)));
        }

        Instant modified = c.getSentDate() != null ? c.getSentDate() : c.getCreatedAt();
        String shareLink = "https://my.mock-brevo.local/share/"
                + String.format("%08x", (int) (c.getId() * 0x9E3779B1L));

        EmailCampaignsResponse.Statistics stats = new EmailCampaignsResponse.Statistics(
                perList,
                global,
                new EmailCampaignsResponse.LinksStats(),
                0L,
                0L,
                Map.of(),
                new EmailCampaignsResponse.StatsByDevice(Map.of(), Map.of(), Map.of(), Map.of()),
                Map.of());

        String senderName = c.getSenderName() != null ? c.getSenderName() : "Mock Sender";
        String senderEmail = c.getSenderEmail() != null ? c.getSenderEmail() : "no-reply@mock-brevo.local";
        String replyTo = c.getReplyTo() != null ? c.getReplyTo() : senderEmail;
        String createdAt = c.getCreatedAt() != null ? c.getCreatedAt().toString() : Instant.now().toString();
        String modifiedAt = modified != null ? modified.toString() : createdAt;
        String tag = MockData.tag(r);
        String htmlContent = "<html><body><p>Mock campaign body for #" + c.getId() + "</p></body></html>";
        String header = "<p>Mock header</p>";
        String footer = "<p>Mock footer — <a href=\"{{unsubscribe}}\">se désabonner</a></p>";

        return new EmailCampaignsResponse.CampaignItem(
                c.getId(),
                c.getName() != null ? c.getName() : "Untitled campaign",
                c.getSubject(),
                "classic",
                c.getStatus() != null ? c.getStatus() : "draft",
                createdAt,
                false,
                c.getSentDate() != null ? c.getSentDate().toString() : null,
                createdAt,
                modifiedAt,
                false,
                false,
                sent,
                tag,
                shareLink,
                c.getUtmCampaign() != null ? c.getUtmCampaign() : "",
                htmlContent,
                header,
                footer,
                new EmailCampaignsResponse.SenderRef(1L, senderName, senderEmail),
                replyTo,
                new EmailCampaignsResponse.Recipients(listIds, List.of()),
                stats);
    }

    private List<Long> parseListIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<Long> out = new ArrayList<>();
        for (String s : Arrays.asList(csv.split(","))) {
            try { out.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    private Integer estimateDelivered(EmailCampaign c) {
        if (c.getRecipientListIdsCsv() == null || c.getRecipientListIdsCsv().isBlank()) {
            return 0;
        }
        Random r = MockData.seededFrom(c.getId(), "campaign-delivered");
        int lists = c.getRecipientListIdsCsv().split(",").length;
        return MockData.bounded(r, lists * 50, lists * 500);
    }

    private String serializeParams(Object params) {
        if (params == null) return null;
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
