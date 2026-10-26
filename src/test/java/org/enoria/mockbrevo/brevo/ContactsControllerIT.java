package org.enoria.mockbrevo.brevo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContactsControllerIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateContactAndManageMembershipViaAddAndRemoveEndpoints() throws Exception {
        String email = "john.doe+" + UUID.randomUUID() + "@example.test";

        String listId = mockMvc.perform(post("/v3/contacts/lists")
                        .header("api-key", "acc-step2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Main List"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long parsedListId = readId(listId);

        String createContactPayload = """
                {
                  "email": "%s",
                  "listIds": [%d],
                  "attributes": {
                    "FIRSTNAME": "John",
                    "LASTNAME": "Doe"
                  }
                }
                """.formatted(email, parsedListId);

        String contactId = mockMvc.perform(post("/v3/contacts")
                        .header("api-key", "acc-step2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createContactPayload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long parsedContactId = readId(contactId);

        mockMvc.perform(post("/v3/contacts/lists/{listId}/contacts/remove", parsedListId)
                        .header("api-key", "acc-step2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ids": [%d]
                                }
                                """.formatted(parsedContactId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contacts.success[0]").value(String.valueOf(parsedContactId)));

        mockMvc.perform(post("/v3/contacts/lists/{listId}/contacts/add", parsedListId)
                        .header("api-key", "acc-step2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "emails": ["%s"]
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contacts.success[0]").value(email));
    }

    @Test
    void shouldExposeGetContactInfoAndUpdateAttributesAndSmsBlacklist() throws Exception {
        String email = "alice+" + UUID.randomUUID() + "@example.test";
        String createBody = """
                {
                  "email": "%s",
                  "attributes": {
                    "FIRST_NAME": "Alice",
                    "LAST_NAME": "Wonder"
                  },
                  "smsBlacklisted": true
                }
                """.formatted(email);

        String created = mockMvc.perform(post("/v3/contacts")
                        .header("api-key", "acc-step3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long contactId = readId(created);

        mockMvc.perform(get("/v3/contacts/{email}", email)
                        .header("api-key", "acc-step3")
                        .param("identifierType", "email_id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(contactId))
                .andExpect(jsonPath("$.smsBlacklisted").value(true))
                .andExpect(jsonPath("$.attributes.FIRST_NAME").value("Alice"));

        mockMvc.perform(get("/v3/contacts/{id}", contactId)
                        .header("api-key", "acc-step3")
                        .param("identifierType", "contact_id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        String updateBody = """
                {
                  "smsBlacklisted": false,
                  "attributes": {
                    "FIRST_NAME": "Alicia",
                    "CITY": "Paris"
                  }
                }
                """;

        mockMvc.perform(put("/v3/contacts/{email}", email)
                        .header("api-key", "acc-step3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v3/contacts/{email}", email)
                        .header("api-key", "acc-step3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsBlacklisted").value(false))
                .andExpect(jsonPath("$.attributes.FIRST_NAME").value("Alicia"))
                .andExpect(jsonPath("$.attributes.CITY").value("Paris"));
    }

    @Test
    void shouldMapImportColumnsToUnifiedAttributesInListResponse() throws Exception {
        String listPayload = """
                {
                  "name": "Import List"
                }
                """;
        String listResponse = mockMvc.perform(post("/v3/contacts/lists")
                        .header("api-key", "acc-step3-import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(listPayload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long listId = readId(listResponse);

        String importPayload = """
                {
                  "listIds": [%d],
                  "fileBody": "EMAIL,FIRST_NAME,LAST_NAME,CITY\\nbob@example.test,Bob,Marley,Kingston"
                }
                """.formatted(listId);

        mockMvc.perform(post("/v3/contacts/import")
                        .header("api-key", "acc-step3-import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importPayload))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/v3/contacts/lists/{listId}/contacts", listId)
                        .header("api-key", "acc-step3-import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contacts.length()").value(1))
                .andExpect(jsonPath("$.contacts[0].smsBlacklisted").value(false))
                .andExpect(jsonPath("$.contacts[0].attributes.CITY").value("Kingston"))
                .andExpect(jsonPath("$.contacts[0].attributes.FIRST_NAME").value("Bob"));
    }

    private long readId(String body) throws Exception {
        return OBJECT_MAPPER.readTree(body).path("id").asLong();
    }
}