package org.enoria.mockbrevo.brevo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class FoldersControllerIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldListFolderListsByFolderId() throws Exception {
        String folderName = "Main Folder " + UUID.randomUUID();

        String folderId = mockMvc.perform(post("/v3/contacts/folders")
                        .header("api-key", "acc-folders-with-list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s"
                                }
                                """.formatted(folderName)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long parsedFolderId = readId(folderId);

        String listId = mockMvc.perform(post("/v3/contacts/lists")
                        .header("api-key", "acc-folders-with-list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Main List",
                                  "folderId": %d
                                }
                                """.formatted(parsedFolderId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long parsedListId = readId(listId);

        mockMvc.perform(get("/v3/contacts/folders/{folderId}/lists", parsedFolderId)
                        .header("api-key", "acc-folders-with-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lists.length()").value(1))
                .andExpect(jsonPath("$.lists[0].id").value(parsedListId));
    }

    @Test
    void shouldReturnEmptyListWhenFolderExistsWithoutLists() throws Exception {
        String folderId = mockMvc.perform(post("/v3/contacts/folders")
                        .header("api-key", "acc-folder-empty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Folder Sans Listes"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long parsedFolderId = readId(folderId);

        mockMvc.perform(get("/v3/contacts/folders/{folderId}/lists", parsedFolderId)
                        .header("api-key", "acc-folder-empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lists.length()").value(0))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void shouldNotResolveFolderByName() throws Exception {
        String folderName = "Folder By Name " + UUID.randomUUID();

        mockMvc.perform(post("/v3/contacts/folders")
                        .header("api-key", "acc-folder-name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s"
                                }
                                """.formatted(folderName)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/v3/contacts/folders/{folderName}/lists", folderName)
                        .header("api-key", "acc-folder-name"))
                .andExpect(status().isBadRequest());
    }

    private long readId(String body) throws Exception {
        return OBJECT_MAPPER.readTree(body).path("id").asLong();
    }
}