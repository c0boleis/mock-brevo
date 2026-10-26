package org.enoria.mockbrevo.brevo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class ContactAttributesControllerIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateListUpdateAndDeleteAttributePerAccount() throws Exception {
        String createPayload = """
                {
                  "type": "text",
                  "enumeration": ["A", "B"],
                  "value": "default",
                  "isRecurring": true,
                  "multiCategoryOptions": ["vip", "new"],
                  "calculatedValue": "none"
                }
                """;

        mockMvc.perform(post("/v3/contacts/attributes/normal/LOYALTY")
                        .header("api-key", "acc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/v3/contacts/attributes").header("api-key", "acc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes.length()").value(1))
                .andExpect(jsonPath("$.attributes[0].name").value("LOYALTY"))
                .andExpect(jsonPath("$.attributes[0].category").value("normal"))
                .andExpect(jsonPath("$.attributes[0].type").value("text"))
                .andExpect(jsonPath("$.attributes[0].enumeration[0]").value("A"))
                .andExpect(jsonPath("$.attributes[0].value").value("default"))
                .andExpect(jsonPath("$.attributes[0].multiCategoryOptions[0]").value("vip"));

        mockMvc.perform(get("/v3/contacts/attributes").header("api-key", "acc-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes.length()").value(0));

        String updatePayload = """
                {
                  "type": "number",
                  "value": 42,
                  "enumeration": ["X"],
                  "multiCategoryOptions": ["premium"]
                }
                """;

        mockMvc.perform(put("/v3/contacts/attributes/normal/LOYALTY")
                        .header("api-key", "acc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/v3/contacts/attributes/normal/LOYALTY/premium")
                        .header("api-key", "acc-1"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v3/contacts/attributes").header("api-key", "acc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes[0].type").value("number"))
                .andExpect(jsonPath("$.attributes[0].value").value(42))
                .andExpect(jsonPath("$.attributes[0].multiCategoryOptions.length()").value(0));

        mockMvc.perform(delete("/v3/contacts/attributes/normal/LOYALTY")
                        .header("api-key", "acc-1"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v3/contacts/attributes").header("api-key", "acc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes.length()").value(0));
    }

    private long readId(String body) throws Exception {
        return OBJECT_MAPPER.readTree(body).path("id").asLong();
    }
}
