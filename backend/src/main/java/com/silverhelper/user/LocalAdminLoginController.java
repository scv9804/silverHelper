package com.silverhelper.user;

import com.silverhelper.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 로컬 데모 DB에만 존재하는 개발 관리자 계정. */
@Profile("local & !prod")
@RestController
public class LocalAdminLoginController {
    private final UserStore users;
    private final AuthService auth;

    public LocalAdminLoginController(UserStore users, AuthService auth) {
        this.users = users;
        this.auth = auth;
    }

    public record Login(@NotBlank @Size(max = 100) String name) {}

    @PostMapping("/api/dev/admin-login")
    public ResponseEntity<ApiResponse<AuthService.Token>> login(@Valid @RequestBody Login body) {
        AuthService.Issued issued = auth.issue(users.localAdmin(body.name().trim()));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, issued.cookie().toString())
                .body(ApiResponse.ok(issued.token()));
    }
}
