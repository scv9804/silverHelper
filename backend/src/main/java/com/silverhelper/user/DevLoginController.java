package com.silverhelper.user;

import com.silverhelper.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Profile("local & !prod")
@RestController
public class DevLoginController {
    private final UserStore users;
    private final AuthService auth;
    public DevLoginController(UserStore users,AuthService auth) {this.users=users;this.auth=auth;}
    public record Login(@NotBlank @Size(max=100) String name) {}
    @PostMapping("/api/dev/login")
    public ResponseEntity<ApiResponse<AuthService.Token>> login(@Valid @RequestBody Login body) {
        String name=body.name().trim();
        UserStore.User user=users.social("DEV",name,name,null);
        // 개발 계정의 DB 역할이 변경되어도 관리자 토큰을 발급하지 않는다.
        AuthService.Issued issued=auth.issue(new UserStore.User(user.id(),user.displayName(),user.email(),"USER"));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,issued.cookie().toString()).body(ApiResponse.ok(issued.token()));
    }
}
