package iuh.fit.ottbackend.dto.request;

import iuh.fit.ottbackend.entity.enums.Gender;
import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    private String fullName;
    private String bio;
    private LocalDate dateOfBirth;
    private Gender gender;
    private String avatarUrl;
    private String coverUrl;
}