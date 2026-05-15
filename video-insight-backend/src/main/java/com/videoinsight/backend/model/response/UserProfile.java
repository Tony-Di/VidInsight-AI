package com.videoinsight.backend.model.response;

import com.videoinsight.backend.entity.AppUser;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Public user profile (no secrets)")
public class UserProfile {

    @Schema(description = "User id.")
    private Long id;

    @Schema(description = "Login email.")
    private String email;

    @Schema(description = "Display name.")
    private String displayName;

    public static UserProfile from(AppUser user) {
        return new UserProfile(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
