package iuh.fit.se.analyticservice.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyActivityResponse {
    private LocalDate date;
    private long posts;
    private long messages;
}