package iuh.fit.se.analyticservice.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedAuditLogsResponse {
    private List<AuditLogDTO> items;
    private long totalElements;
    private int page;
    private int size;
    private int totalPages;
}
