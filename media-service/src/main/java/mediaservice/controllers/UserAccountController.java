package mediaservice.controllers;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.responses.UserAccountResponse;
import mediaservice.services.UserAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserAccountController {

    private final UserAccountService userAccountService;

    /** GET /users – trả về tất cả user */
    @GetMapping
    public ResponseEntity<List<UserAccountResponse>> getAllUsers() {
        return ResponseEntity.ok(userAccountService.getAllUserAccounts());
    }

    /** GET /users/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<UserAccountResponse> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(userAccountService.getUserAccountById(id));
    }

    /** GET /users/username/{username} */
    @GetMapping("/username/{username}")
    public ResponseEntity<UserAccountResponse> getUserByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userAccountService.getUserAccountByUsername(username));
    }
}
