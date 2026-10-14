package com.silverhelper.user;

import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

class OAuthConfigTests {
    @Test void 카카오_검증된_이메일만_회원_주소로_전달한다() throws Exception {
        kakaoEmail(true,"verified@example.com");
        kakaoEmail(false,null);
        kakaoEmail(null,null);
    }

    private void kakaoEmail(Boolean verified,String expected) throws Exception {
        UserStore users=mock(UserStore.class);
        AuthService auth=mock(AuthService.class);
        HttpServletRequest request=mock(HttpServletRequest.class);
        HttpServletResponse response=mock(HttpServletResponse.class);
        when(users.social(anyString(),anyString(),anyString(),nullable(String.class)))
                .thenReturn(new UserStore.User(7,"사용자",null,"USER"));
        when(auth.authorizationCode(7)).thenReturn("code");
        Map<String,Object> account=new HashMap<>();
        account.put("email","verified@example.com");
        account.put("profile",Map.of("nickname","사용자"));
        if (verified!=null) account.put("is_email_verified",verified);
        Map<String,Object> attributes=Map.of("id",123,"kakao_account",account);
        var principal=new DefaultOAuth2User(Set.of(new SimpleGrantedAuthority("ROLE_USER")),attributes,"id");
        var authentication=new OAuth2AuthenticationToken(principal,principal.getAuthorities(),"kakao");
        new OAuthConfig().oauthSuccess(users,auth,"http://localhost/auth/callback")
                .onAuthenticationSuccess(request,response,authentication);
        verify(users).social("KAKAO","123","사용자",expected);
        verify(response).sendRedirect("http://localhost/auth/callback?code=code");
    }
}
