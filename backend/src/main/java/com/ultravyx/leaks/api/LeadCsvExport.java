package com.ultravyx.leaks.api;

import com.ultravyx.leaks.api.ApiDtos.LeadDto;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

final class LeadCsvExport {
    private LeadCsvExport() {}
    static ResponseEntity<StreamingResponseBody> response(List<LeadDto> records, String filename) {
        StreamingResponseBody body = output -> {
            output.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            try (Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                writer.write("lead_id,name,status,created_at,contacted_at,assigned_to,source,campaign,appointment_at,attended_at,revenue,detected_problems\r\n");
                for (LeadDto lead : records) {
                    String problems = String.join("|", lead.detectedProblems().stream().map(f -> f.type().name()).toList());
                    writeRow(writer, lead.externalLeadId(), lead.name(), lead.status(), lead.createdAt(), lead.contactedAt(),
                            lead.assignedTo(), lead.source(), lead.campaign(), lead.appointmentAt(), lead.attendedAt(),
                            lead.revenue(), problems);
                }
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(body);
    }
    private static void writeRow(Writer writer, Object... fields) throws IOException {
        for (int i = 0; i < fields.length; i++) {
            if (i != 0) writer.write(',');
            writer.write(escape(fields[i] == null ? "" : fields[i].toString()));
        }
        writer.write("\r\n");
    }
    static String escape(String value) {
        // Prefix spreadsheet formula-like values so exports remain safe when opened in Excel.
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) value = "'" + value;
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            return "\"" + value.replace("\"", "\"\"") + "\"";
        return value;
    }
}
