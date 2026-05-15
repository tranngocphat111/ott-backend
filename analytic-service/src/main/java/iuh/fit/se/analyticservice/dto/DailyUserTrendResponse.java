package iuh.fit.se.analyticservice.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyUserTrendResponse {
    private LocalDate date;
    private long registrations;
    private long logins;
}
