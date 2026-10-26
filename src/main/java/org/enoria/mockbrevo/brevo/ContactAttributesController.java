package org.enoria.mockbrevo.brevo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import org.enoria.mockbrevo.auth.CurrentAccount;
import org.enoria.mockbrevo.brevo.dto.CreateAttributeRequest;
import org.enoria.mockbrevo.brevo.dto.GetAttributesResponse;
import org.enoria.mockbrevo.brevo.dto.UpdateAttributeRequest;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.ContactAttribute;
import org.enoria.mockbrevo.domain.ContactAttributeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v3/contacts/attributes")
public class ContactAttributesController {

    private final ContactAttributeRepository attributes;
    private final ObjectMapper objectMapper;

    public ContactAttributesController(ContactAttributeRepository attributes, ObjectMapper objectMapper) {
        this.attributes = attributes;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public GetAttributesResponse list() {
        Account account = CurrentAccount.require();
        List<GetAttributesResponse.AttributeItem> items = attributes.findByAccountOrderByCategoryAscNameAsc(account).stream()
                .map(this::toResponseItem)
                .toList();
        return new GetAttributesResponse(items);
    }

    @PostMapping("/{attributeCategory}/{attributeName}")
    @ResponseStatus(HttpStatus.CREATED)
    public void create(
            @PathVariable String attributeCategory,
            @PathVariable String attributeName,
            @RequestBody CreateAttributeRequest req) {
        Account account = CurrentAccount.require();
        if (attributes.findByAccountAndCategoryAndName(account, attributeCategory, attributeName).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attribute already exists");
        }
        ContactAttribute attribute = new ContactAttribute();
        attribute.setAccount(account);
        attribute.setCategory(attributeCategory);
        attribute.setName(attributeName);
        applyCreatePayload(attribute, req);
        attributes.save(attribute);
    }

    @PutMapping("/{attributeCategory}/{attributeName}")
    public ResponseEntity<Void> update(
            @PathVariable String attributeCategory,
            @PathVariable String attributeName,
            @RequestBody UpdateAttributeRequest req) {
        Account account = CurrentAccount.require();
        ContactAttribute attribute = attributes
                .findByAccountAndCategoryAndName(account, attributeCategory, attributeName)
                .orElse(null);
        if (attribute == null) {
            return ResponseEntity.notFound().build();
        }
        applyUpdatePayload(attribute, req);
        attributes.save(attribute);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{attributeCategory}/{attributeName}")
    public ResponseEntity<Void> delete(@PathVariable String attributeCategory, @PathVariable String attributeName) {
        Account account = CurrentAccount.require();
        ContactAttribute attribute = attributes
                .findByAccountAndCategoryAndName(account, attributeCategory, attributeName)
                .orElse(null);
        if (attribute == null) {
            return ResponseEntity.notFound().build();
        }
        attributes.delete(attribute);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{attributeType}/{multipleChoiceAttribute}/{multipleChoiceAttributeOption}")
    public ResponseEntity<Void> deleteMultipleChoiceOption(
            @PathVariable String attributeType,
            @PathVariable String multipleChoiceAttribute,
            @PathVariable String multipleChoiceAttributeOption) {
        Account account = CurrentAccount.require();
        ContactAttribute attribute = attributes
                .findByAccountAndCategoryAndName(account, attributeType, multipleChoiceAttribute)
                .orElse(null);
        if (attribute == null) {
            return ResponseEntity.notFound().build();
        }
        List<String> options = readStringList(attribute.getMultiCategoryOptionsJson());
        boolean removed = options.removeIf(v -> v.equalsIgnoreCase(multipleChoiceAttributeOption));
        if (!removed) {
            options = readStringList(attribute.getEnumerationJson());
            options.removeIf(v -> v.equalsIgnoreCase(multipleChoiceAttributeOption));
            attribute.setEnumerationJson(writeAsJson(options));
        } else {
            attribute.setMultiCategoryOptionsJson(writeAsJson(options));
        }
        attributes.save(attribute);
        return ResponseEntity.noContent().build();
    }

    private GetAttributesResponse.AttributeItem toResponseItem(ContactAttribute attribute) {
        return new GetAttributesResponse.AttributeItem(
                attribute.getName(),
                attribute.getCategory(),
                attribute.getType(),
                readStringList(attribute.getEnumerationJson()),
                attribute.getCalculatedValue(),
                readObject(attribute.getValueJson()),
                readStringList(attribute.getMultiCategoryOptionsJson()));
    }

    private void applyCreatePayload(ContactAttribute attribute, CreateAttributeRequest req) {
        attribute.setType(req.type());
        attribute.setEnumerationJson(writeAsJson(req.enumeration()));
        attribute.setValueJson(writeAsJson(req.value()));
        attribute.setRecurring(req.isRecurring());
        attribute.setMultiCategoryOptionsJson(writeAsJson(req.multiCategoryOptions()));
        attribute.setCalculatedValue(req.calculatedValue());
    }

    private void applyUpdatePayload(ContactAttribute attribute, UpdateAttributeRequest req) {
        if (req.type() != null) {
            attribute.setType(req.type());
        }
        if (req.enumeration() != null) {
            attribute.setEnumerationJson(writeAsJson(req.enumeration()));
        }
        if (req.value() != null) {
            attribute.setValueJson(writeAsJson(req.value()));
        }
        if (req.isRecurring() != null) {
            attribute.setRecurring(req.isRecurring());
        }
        if (req.multiCategoryOptions() != null) {
            attribute.setMultiCategoryOptionsJson(writeAsJson(req.multiCategoryOptions()));
        }
        if (req.calculatedValue() != null) {
            attribute.setCalculatedValue(req.calculatedValue());
        }
    }

    private String writeAsJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payload", e);
        }
    }

    private List<String> readStringList(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid stored payload", e);
        }
    }

    private Object readObject(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid stored payload", e);
        }
    }
}
