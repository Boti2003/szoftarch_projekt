package hu.bme.szoftarch.identity.api;

import hu.bme.szoftarch.identity.api.dto.PublicProfileDto;
import hu.bme.szoftarch.identity.api.dto.UpdateProfileRequest;
import hu.bme.szoftarch.identity.api.dto.UserProfileResponse;
import hu.bme.szoftarch.identity.application.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Retrieves the profile, level, points, and next-level point threshold of the current user.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        UUID currentUserId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(userService.getProfile(currentUserId));
    }

    /**
     * Updates display name and/or username for the current user.
     */
    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {
        UUID currentUserId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(userService.updateProfile(currentUserId, request));
    }

    /**
     * Prefix search for users by username (max 20 results).
     */
    @GetMapping("/search")
    public ResponseEntity<List<PublicProfileDto>> searchUsers(@RequestParam("username") String username) {
        return ResponseEntity.ok(userService.searchUsers(username));
    }

    /**
     * Retrieves public profile and statistics for another user.
     */
    @GetMapping("/{id}/public")
    public ResponseEntity<PublicProfileDto> getPublicProfile(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(userService.getPublicProfile(id));
    }
}
