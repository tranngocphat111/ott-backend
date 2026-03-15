package iuh.fit.userservice.dto.request;

import iuh.fit.userservice.entity.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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