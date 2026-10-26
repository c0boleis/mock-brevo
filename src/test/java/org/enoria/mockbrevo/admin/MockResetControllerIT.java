package org.enoria.mockbrevo.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class MockResetControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldDeleteContactAttributesOnReset() throws Exception {
        String attributeName = "CITY_" + UUID.randomUUID().toString().replace("-", "");

        mockMvc.perform(post("/v3/contacts/attributes/normal/{attributeName}", attributeName)
                        .header("api-key", "acc-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "text"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/mock/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted.attributes").isNumber());

        mockMvc.perform(get("/v3/contacts/attributes")
                        .header("api-key", "acc-reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes.length()").value(0));
    }
}