package iuh.fit.se.analyticservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailDTO {
    private String id;
    private String email;
    private String fullName;
    private String phone;
}
