package org.enoria.mockbrevo.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import org.enoria.mockbrevo.domain.AccountRepository;
import org.enoria.mockbrevo.domain.ContactAttributeRepository;
import org.enoria.mockbrevo.domain.ContactListRepository;
import org.enoria.mockbrevo.domain.ContactRepository;
import org.enoria.mockbrevo.domain.EmailCampaignRepository;
import org.enoria.mockbrevo.domain.FolderRepository;
import org.enoria.mockbrevo.domain.SenderRepository;
import org.enoria.mockbrevo.domain.SentEmailRepository;
import org.enoria.mockbrevo.domain.SmtpTemplateRepository;
import org.enoria.mockbrevo.observability.RequestLogStore;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock")
public class MockResetController {

    private final AccountRepository accounts;
    private final SentEmailRepository sentEmails;
    private final ContactRepository contacts;
    private final ContactAttributeRepository attributes;
    private final ContactListRepository lists;
    private final EmailCampaignRepository campaigns;
    private final SmtpTemplateRepository templates;
    private final FolderRepository folders;
    private final SenderRepository senders;
    private final RequestLogStore requestLog;

    public MockResetController(
            AccountRepository accounts,
            SentEmailRepository sentEmails,
            ContactRepository contacts,
            ContactAttributeRepository attributes,
            ContactListRepository lists,
            EmailCampaignRepository campaigns,
            SmtpTemplateRepository templates,
            FolderRepository folders,
            SenderRepository senders,
            RequestLogStore requestLog) {
        this.accounts = accounts;
        this.sentEmails = sentEmails;
        this.contacts = contacts;
        this.attributes = attributes;
        this.lists = lists;
        this.campaigns = campaigns;
        this.templates = templates;
        this.folders = folders;
        this.senders = senders;
        this.requestLog = requestLog;
    }

    @PostMapping("/reset")
    @Transactional
    public ResponseEntity<Map<String, Object>> reset() {
        Map<String, Object> deleted = new LinkedHashMap<>();
        deleted.put("sentEmails", sentEmails.count());
        deleted.put("contacts", contacts.count());
        deleted.put("attributes", attributes.count());
        deleted.put("lists", lists.count());
        deleted.put("campaigns", campaigns.count());
        deleted.put("templates", templates.count());
        deleted.put("senders", senders.count());
        deleted.put("folders", folders.count());
        deleted.put("accounts", accounts.count());

        // Order matters: child rows before parents.
        // contacts.deleteAll() (not deleteAllInBatch) so the @ManyToMany "contact_in_list"
        // join table is cleaned up by Hibernate before we drop the lists.
        sentEmails.deleteAllInBatch();
        contacts.deleteAll();
        contacts.flush();
        attributes.deleteAllInBatch();
        lists.deleteAllInBatch();
        campaigns.deleteAllInBatch();
        templates.deleteAllInBatch();
        senders.deleteAllInBatch();
        folders.deleteAllInBatch();
        accounts.deleteAllInBatch();

        requestLog.clear();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "reset");
        body.put("deleted", deleted);
        return ResponseEntity.ok(body);
    }
}
