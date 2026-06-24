package cupk.smartcontract.service;

import cupk.smartcontract.entity.FileInfo;
import cupk.smartcontract.entity.ContractVersion;
import cupk.smartcontract.mapper.ContractVersionMapper;
import cupk.smartcontract.mapper.FileInfoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractVersionServiceTest {

    @Test
    void sanitizeHtmlKeepsSupportedEditorStyles() {
        ContractVersionService service =
                new ContractVersionService(null, null, null, null, null, null, null);

        String html = service.sanitizeHtml("""
                <p class="clause-title"
                   style="text-indent:2em;text-align:center;font-size:16px;font-weight:700;color:rgb(255, 0, 0)">
                  <span style="background-color:#ffff00;font-family:FangSong">正文</span>
                </p>
                """);

        assertThat(html).contains(
                "class=\"clause-title\"",
                "text-indent:2em",
                "text-align:center",
                "font-size:16px",
                "font-weight:700",
                "color:rgb(255, 0, 0)",
                "background-color:#ffff00",
                "font-family:FangSong"
        );
    }

    @Test
    void createAlwaysComputesHashFromSanitizedContent() throws Exception {
        ContractVersionMapper versionMapper = mock(ContractVersionMapper.class);
        FileInfoMapper fileInfoMapper = mock(FileInfoMapper.class);
        FileStorageService storageService = mock(FileStorageService.class);
        WordArchiveService wordArchiveService = mock(WordArchiveService.class);
        ContractManagementService managementService = mock(ContractManagementService.class);
        ContractDocumentImportService documentImportService = mock(ContractDocumentImportService.class);
        ContractVersionService service = new ContractVersionService(
                versionMapper, fileInfoMapper, storageService, wordArchiveService,
                null, managementService, documentImportService);

        when(documentImportService.normalizeEditorHtml(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(wordArchiveService.toDocx(any())).thenReturn(new byte[]{1, 2, 3});
        when(storageService.store(any(byte[].class), any(), any()))
                .thenReturn(new FileStorageService.StoredFile("versions/test.docx", "file-hash", "docx", 3));
        doAnswer(invocation -> {
            FileInfo file = invocation.getArgument(0);
            file.setFileId(42L);
            return 1;
        }).when(fileInfoMapper).insert(any(FileInfo.class));
        doAnswer(invocation -> {
            ContractVersion version = invocation.getArgument(0);
            version.setVersionId(9L);
            return 1;
        }).when(versionMapper).insert(any(ContractVersion.class));
        when(versionMapper.selectList(any())).thenReturn(java.util.List.of());

        TransactionSynchronizationManager.initSynchronization();
        try {
            ContractVersion version = service.create(7L, "<p>正文</p>", "forged", "SAVE", "tester");
            String expected = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(version.getContent().getBytes(StandardCharsets.UTF_8)));
            assertThat(version.getContentHash()).isEqualTo(expected).isNotEqualTo("forged");
            assertThat(version.getFileId()).isEqualTo(42L);
            verify(managementService).lockContract(7L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void restoreDeclaresTransactionBoundary() throws Exception {
        Transactional transactional = ContractVersionService.class
                .getMethod("restore", Long.class, Long.class, String.class)
                .getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
    }
}
