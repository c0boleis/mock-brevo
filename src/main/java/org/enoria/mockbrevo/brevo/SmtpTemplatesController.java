package org.enoria.mockbrevo.brevo;

import java.time.Instant;
import java.util.List;
import org.enoria.mockbrevo.auth.CurrentAccount;
import org.enoria.mockbrevo.brevo.dto.CreateSmtpTemplateRequest;
import org.enoria.mockbrevo.brevo.dto.IdResponse;
import org.enoria.mockbrevo.brevo.dto.SmtpTemplatesResponse;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.SmtpTemplate;
import org.enoria.mockbrevo.domain.SmtpTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v3/smtp/templates")
public class SmtpTemplatesController {

    private final SmtpTemplateRepository templates;

    public SmtpTemplatesController(SmtpTemplateRepository templates) {
        this.templates = templates;
    }

    @GetMapping
    public SmtpTemplatesResponse list(
            @RequestParam(required = false) Boolean templateStatus,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {
        Account a = CurrentAccount.require();
        int pageSize = Math.max(1, limit);
        int pageIndex = offset / pageSize;
        Page<SmtpTemplate> page = templateStatus != null
                ? templates.findByAccountAndActiveOrderByIdAsc(a, templateStatus, PageRequest.of(pageIndex, pageSize))
                : templates.findByAccountOrderByIdAsc(a, PageRequest.of(pageIndex, pageSize));
        List<SmtpTemplatesResponse.TemplateItem> items = page.getContent().stream()
                .map(t -> {
                    String senderName = t.getSenderName() != null ? t.getSenderName() : a.getFirstName() + " " + a.getLastName();
                    String senderEmail = t.getSenderEmail() != null ? t.getSenderEmail() : a.getEmail();
                    String toField = t.getToField() != null ? t.getToField() : "{{contact.EMAIL}}";
                    String tag = t.getTag() != null ? t.getTag() : org.enoria.mockbrevo.util.MockData.tag(
                            org.enoria.mockbrevo.util.MockData.seededFrom(a.getApiKey(), "template", t.getId()));
                    String replyTo = t.getReplyTo() != null ? t.getReplyTo() : senderEmail;
                    String subject = t.getSubject() != null ? t.getSubject() : "Template #" + t.getId();
                    String html = t.getHtmlContent() != null
                            ? t.getHtmlContent()
                            : "<html><body><p>Mock template #" + t.getId() + "</p></body></html>";
                    return new SmtpTemplatesResponse.TemplateItem(
                            t.getId(),
                            t.getName(),
                            subject,
                            t.isActive(),
                            true,
                            new SmtpTemplatesResponse.SenderRef(senderName, senderEmail),
                            replyTo,
                            toField,
                            tag,
                            html,
                            t.getCreatedAt() != null ? t.getCreatedAt().toString() : null,
                            t.getModifiedAt() != null ? t.getModifiedAt().toString() : null);
                })
                .toList();
        return new SmtpTemplatesResponse(items, page.getTotalElements());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse create(@RequestBody CreateSmtpTemplateRequest req) {
        Account a = CurrentAccount.require();
        SmtpTemplate t = new SmtpTemplate();
        t.setAccount(a);
        t.setName(req.templateName() != null ? req.templateName() : "Untitled template");
        t.setSubject(req.subject());
        t.setHtmlContent(req.htmlContent());
        if (req.sender() != null) {
            t.setSenderName(req.sender().name());
            t.setSenderEmail(req.sender().email());
        }
        t.setReplyTo(req.replyTo());
        t.setToField(req.toField());
        t.setTag(req.tag());
        t.setActive(req.isActive() == null || req.isActive());
        Instant now = Instant.now();
        t.setCreatedAt(now);
        t.setModifiedAt(now);
        return new IdResponse(templates.save(t).getId());
    }
}
