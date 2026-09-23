package com.ultravyx.leaks.api;

import com.ultravyx.leaks.api.ApiDtos.*;
import com.ultravyx.leaks.domain.LeadStatus;
import com.ultravyx.leaks.domain.LeakType;
import com.ultravyx.leaks.service.CsvImportService;
import com.ultravyx.leaks.service.LeadService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/leads")
public class LeadsController {
    private final LeadService leads;
    private final CsvImportService importer;
    public LeadsController(LeadService leads, CsvImportService importer) { this.leads = leads; this.importer = importer; }

    @GetMapping public PagedResponse<LeadDto> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) LeakType leakType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return leads.search(search, status, source, leakType, page, size, sort);
    }
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) LeakType leakType,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return LeadCsvExport.response(leads.export(search, status, source, leakType, sort), "ultravyx-leads.csv");
    }
    @GetMapping("/{id}") public LeadDto detail(@PathVariable UUID id) { return leads.find(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public LeadDto create(@Valid @RequestBody CreateLeadRequest request) {
        return leads.create(request);
    }
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResult upload(@RequestPart("file") MultipartFile file) { return importer.importFile(file); }
}
