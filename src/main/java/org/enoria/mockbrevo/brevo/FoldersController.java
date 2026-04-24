package org.enoria.mockbrevo.brevo;

import java.util.List;
import org.enoria.mockbrevo.auth.CurrentAccount;
import org.enoria.mockbrevo.brevo.dto.CreateFolderRequest;
import org.enoria.mockbrevo.brevo.dto.FoldersResponse;
import org.enoria.mockbrevo.brevo.dto.IdResponse;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.Folder;
import org.enoria.mockbrevo.domain.FolderRepository;
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
@RequestMapping("/v3/contacts/folders")
public class FoldersController {

    private final FolderRepository folders;

    public FoldersController(FolderRepository folders) {
        this.folders = folders;
    }

    @GetMapping
    public FoldersResponse list(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        Account a = CurrentAccount.require();
        int pageSize = Math.max(1, limit);
        int pageIndex = offset / pageSize;
        var page = folders.findByAccountOrderByIdAsc(a, PageRequest.of(pageIndex, pageSize));
        List<FoldersResponse.FolderItem> items = page.getContent().stream()
                .map(f -> new FoldersResponse.FolderItem(f.getId(), f.getName(), 0, 0, 0))
                .toList();
        return new FoldersResponse(items, page.getTotalElements());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse create(@RequestBody CreateFolderRequest req) {
        Account a = CurrentAccount.require();
        Folder f = new Folder();
        f.setAccount(a);
        f.setName(req.name() != null ? req.name() : "Untitled folder");
        return new IdResponse(folders.save(f).getId());
    }
}
