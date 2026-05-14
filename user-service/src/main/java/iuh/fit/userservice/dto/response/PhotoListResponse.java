
package iuh.fit.userservice.dto.response;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoListResponse {
    private List<UserPhotoResponse> avatars;
    private List<UserPhotoResponse> covers;
    private String activeAvatarUrl;
    private String activeCoverUrl;
}