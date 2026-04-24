package org.enoria.mockbrevo.brevo;

import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.enoria.mockbrevo.auth.CurrentAccount;
import org.enoria.mockbrevo.brevo.dto.ContactListsResponse;
import org.enoria.mockbrevo.brevo.dto.ContactsResponse;
import org.enoria.mockbrevo.brevo.dto.CreateListRequest;
import org.enoria.mockbrevo.brevo.dto.IdResponse;
import org.enoria.mockbrevo.brevo.dto.ImportContactsRequest;
import org.enoria.mockbrevo.brevo.dto.ProcessIdResponse;
import org.enoria.mockbrevo.brevo.dto.RemoveContactsRequest;
import org.enoria.mockbrevo.brevo.dto.UpdateContactRequest;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.Contact;
import org.enoria.mockbrevo.domain.ContactList;
import org.enoria.mockbrevo.domain.ContactListRepository;
import org.enoria.mockbrevo.domain.ContactRepository;
import org.enoria.mockbrevo.domain.Folder;
import org.enoria.mockbrevo.domain.FolderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v3/contacts")
public class ContactsController {

    private final ContactRepository contacts;
    private final ContactListRepository lists;
    private final FolderRepository folders;

    public ContactsController(
            ContactRepository contacts,
            ContactListRepository lists,
            FolderRepository folders) {
        this.contacts = contacts;
        this.lists = lists;
        this.folders = folders;
    }

    @GetMapping("/lists")
    @Transactional
    public ContactListsResponse getLists(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {
        Account a = CurrentAccount.require();
        var page = lists.findByAccountOrderByIdAsc(
                a, PageRequest.of(offset / Math.max(1, limit), Math.max(1, limit)));
        List<ContactListsResponse.ListItem> items = page.getContent().stream()
                .map(l -> {
                    long subs = contacts.countByAccountAndListsContaining(a, l);
                    Long folderId = l.getFolder() != null ? l.getFolder().getId() : 0L;
                    return new ContactListsResponse.ListItem(
                            l.getId(), l.getName(), 0, subs, subs, folderId);
                })
                .toList();
        return new ContactListsResponse(items, page.getTotalElements());
    }

    @PostMapping("/lists")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse createList(@RequestBody CreateListRequest req) {
        Account a = CurrentAccount.require();
        ContactList l = new ContactList();
        l.setAccount(a);
        l.setName(req.name() != null ? req.name() : "Untitled list");
        if (req.folderId() != null) {
            folders.findById(req.folderId())
                    .filter(f -> f.getAccount().getId().equals(a.getId()))
                    .ifPresent(l::setFolder);
        }
        return new IdResponse(lists.save(l).getId());
    }

    @GetMapping("/lists/{listId}/contacts")
    @Transactional
    public ResponseEntity<ContactsResponse> contactsInList(
            @PathVariable Long listId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {
        Account a = CurrentAccount.require();
        return lists.findByIdAndAccount(listId, a)
                .map(list -> {
                    var page = contacts.findByAccountAndListsContainingOrderByIdAsc(
                            a, list, PageRequest.of(offset / Math.max(1, limit), Math.max(1, limit)));
                    List<ContactsResponse.ContactItem> items = page.getContent().stream()
                            .map(c -> {
                                var r = org.enoria.mockbrevo.util.MockData.seededFrom(
                                        a.getApiKey(), "contact", c.getId());
                                long createdOffset = r.nextInt(3600 * 24 * 30);
                                long modifiedOffset = createdOffset + r.nextInt(3600 * 24);
                                return new ContactsResponse.ContactItem(
                                        c.getId(),
                                        c.getEmail(),
                                        c.isEmailBlacklisted(),
                                        false,
                                        a.getCreatedAt().plusSeconds(createdOffset).toString(),
                                        a.getCreatedAt().plusSeconds(modifiedOffset).toString(),
                                        c.getLists().stream().map(ContactList::getId).toList(),
                                        java.util.List.of(),
                                        buildAttributes(c));
                            })
                            .toList();
                    return ResponseEntity.ok(new ContactsResponse(items, page.getTotalElements()));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public ProcessIdResponse importContacts(@RequestBody ImportContactsRequest req) {
        Account a = CurrentAccount.require();

        List<ContactList> targetLists = new ArrayList<>();
        if (req.listIds() != null) {
            for (Long listId : req.listIds()) {
                ContactList list = lists.findByIdAndAccount(listId, a)
                        .orElseGet(() -> autoCreateList(a, "List " + listId));
                targetLists.add(list);
            }
        }
        if (req.newList() != null && req.newList().listName() != null) {
            ContactList created = new ContactList();
            created.setAccount(a);
            created.setName(req.newList().listName());
            if (req.newList().folderId() != null) {
                folders.findById(req.newList().folderId())
                        .filter(f -> f.getAccount().getId().equals(a.getId()))
                        .ifPresent(created::setFolder);
            }
            targetLists.add(lists.save(created));
        }

        if (req.fileBody() != null && !req.fileBody().isBlank()) {
            parseAndStore(a, req.fileBody(), targetLists,
                    Boolean.TRUE.equals(req.updateExistingContacts()),
                    Boolean.TRUE.equals(req.emailBlacklist()));
        }

        return new ProcessIdResponse(ThreadLocalRandom.current().nextLong(1_000, 9_999_999));
    }

    @DeleteMapping("/lists/{listId}/contacts")
    @Transactional
    public ResponseEntity<Map<String, Object>> removeFromList(
            @PathVariable Long listId,
            @RequestBody RemoveContactsRequest req) {
        Account a = CurrentAccount.require();
        var listOpt = lists.findByIdAndAccount(listId, a);
        if (listOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ContactList list = listOpt.get();

        List<String> success = new ArrayList<>();
        List<Map<String, String>> failure = new ArrayList<>();

        List<String> emails = req.emails() != null ? req.emails() : List.of();
        if (!emails.isEmpty()) {
            List<Contact> found = contacts.findByAccountAndEmailIn(a, emails);
            Map<String, Contact> byEmail = new HashMap<>();
            for (Contact c : found) byEmail.put(c.getEmail(), c);
            for (String email : emails) {
                Contact c = byEmail.get(email);
                if (c == null) {
                    failure.add(Map.of("email", email, "message", "Contact not found"));
                    continue;
                }
                c.getLists().remove(list);
                contacts.save(c);
                success.add(email);
            }
        }

        return ResponseEntity.ok(Map.of("contacts", Map.of("success", success, "failure", failure)));
    }

    @PutMapping("/{identifier}")
    @Transactional
    public ResponseEntity<Void> updateContact(
            @PathVariable String identifier,
            @RequestBody UpdateContactRequest req) {
        Account a = CurrentAccount.require();
        Contact contact = contacts.findByAccountAndEmail(a, identifier).orElse(null);
        if (contact == null) {
            return ResponseEntity.notFound().build();
        }
        if (req.emailBlacklisted() != null) {
            contact.setEmailBlacklisted(req.emailBlacklisted());
        }
        if (req.listIds() != null) {
            for (Long listId : req.listIds()) {
                lists.findByIdAndAccount(listId, a).ifPresent(l -> contact.getLists().add(l));
            }
        }
        if (req.unlinkListIds() != null) {
            for (Long listId : req.unlinkListIds()) {
                lists.findByIdAndAccount(listId, a).ifPresent(l -> contact.getLists().remove(l));
            }
        }
        contacts.save(contact);
        return ResponseEntity.noContent().build();
    }

    private ContactList autoCreateList(Account a, String name) {
        ContactList l = new ContactList();
        l.setAccount(a);
        l.setName(name);
        return lists.save(l);
    }

    private void parseAndStore(
            Account a,
            String csv,
            List<ContactList> targetLists,
            boolean updateExisting,
            boolean blacklist) {
        String[] lines = csv.split("\\r?\\n");
        if (lines.length == 0) return;
        char delim = detectDelimiter(lines[0]);
        String[] headers = splitRow(lines[0], delim);
        int emailIdx = indexOf(headers, "EMAIL");
        if (emailIdx < 0) return;
        int firstNameIdx = firstIndex(headers, "FIRST_NAME", "PRENOM", "FIRSTNAME");
        int lastNameIdx = firstIndex(headers, "LAST_NAME", "NOM", "LASTNAME");

        for (int i = 1; i < lines.length; i++) {
            String row = lines[i];
            if (row == null || row.isBlank()) continue;
            String[] cols = splitRow(row, delim);
            if (emailIdx >= cols.length) continue;
            String email = cols[emailIdx].trim();
            if (email.isEmpty()) continue;

            Contact c = contacts.findByAccountAndEmail(a, email).orElse(null);
            if (c == null) {
                c = new Contact();
                c.setAccount(a);
                c.setEmail(email);
            } else if (!updateExisting) {
                // still link to target lists so subscriptions work, but don't overwrite attributes
            }
            if (firstNameIdx >= 0 && firstNameIdx < cols.length && (c.getFirstName() == null || updateExisting)) {
                c.setFirstName(cols[firstNameIdx].trim());
            }
            if (lastNameIdx >= 0 && lastNameIdx < cols.length && (c.getLastName() == null || updateExisting)) {
                c.setLastName(cols[lastNameIdx].trim());
            }
            if (blacklist) c.setEmailBlacklisted(true);
            c.getLists().addAll(targetLists);
            contacts.save(c);
        }
    }

    private static char detectDelimiter(String headerLine) {
        if (headerLine.indexOf(';') >= 0) return ';';
        if (headerLine.indexOf('\t') >= 0) return '\t';
        return ',';
    }

    private static String[] splitRow(String row, char delim) {
        return row.split(java.util.regex.Pattern.quote(String.valueOf(delim)), -1);
    }

    private static int indexOf(String[] headers, String needle) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(needle)) return i;
        }
        return -1;
    }

    private static int firstIndex(String[] headers, String... needles) {
        for (String n : needles) {
            int idx = indexOf(headers, n);
            if (idx >= 0) return idx;
        }
        return -1;
    }

    private Map<String, Object> buildAttributes(Contact c) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (c.getFirstName() != null) m.put("FIRST_NAME", c.getFirstName());
        if (c.getLastName() != null) m.put("LAST_NAME", c.getLastName());
        return m;
    }
}
