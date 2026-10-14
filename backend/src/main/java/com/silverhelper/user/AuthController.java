package com.silverhelper.user;

import com.silverhelper.common.exception.ErrorCode;
import com.silverhelper.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) {this.auth=auth;}
    public record CodeRequest(@NotBlank String code) {}
    @PostMapping("/exchange")
    public ResponseEntity<ApiResponse<AuthService.Token>> exchange(@Valid @RequestBody CodeRequest body) {
        return result(auth.exchange(body.code()));
    }
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthService.Token>> refresh(@CookieValue(name="refreshToken",required=false) String raw) {
        return result(auth.refresh(raw));
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name="refreshToken",required=false) String raw) {
        auth.logout(raw);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,auth.clearCookie().toString()).build();
    }
    private ResponseEntity<ApiResponse<AuthService.Token>> result(AuthService.Issued issued) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,issued.cookie().toString()).body(ApiResponse.ok(issued.token()));
    }
    @ExceptionHandler(AuthService.InvalidToken.class)
    ResponseEntity<ApiResponse<Void>> invalid() {
        return ResponseEntity.status(401).body(ApiResponse.fail(ErrorCode.UNAUTHORIZED));
    }
}
