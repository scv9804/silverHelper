package com.silverhelper.user;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration(proxyBeanMethods=false)
public class OAuthConfig {
    public static class Enabled implements Condition {
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var e=context.getEnvironment();
            return !e.getProperty("app.auth.google-client-id","").isBlank() && !e.getProperty("app.auth.google-client-secret","").isBlank()
                    || !e.getProperty("app.auth.kakao-client-id","").isBlank();
        }
    }
    @Bean
    @Conditional(Enabled.class)
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${app.auth.google-client-id:}") String googleId,
            @Value("${app.auth.google-client-secret:}") String googleSecret,
            @Value("${app.auth.kakao-client-id:}") String kakaoId,
            @Value("${app.auth.kakao-client-secret:}") String kakaoSecret) {
        List<ClientRegistration> clients=new ArrayList<>();
        if (!googleId.isBlank() && !googleSecret.isBlank()) clients.add(ClientRegistration.withRegistrationId("google")
                .clientId(googleId).clientSecret(googleSecret).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .userNameAttributeName("sub").scope("openid","profile","email").clientName("Google").build());
        if (!kakaoId.isBlank()) clients.add(ClientRegistration.withRegistrationId("kakao")
                .clientId(kakaoId).clientSecret(kakaoSecret)
                .clientAuthenticationMethod(kakaoSecret.isBlank()?ClientAuthenticationMethod.NONE:ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .userInfoUri("https://kapi.kakao.com/v2/user/me")
                .userNameAttributeName("id").scope("profile_nickname","account_email").clientName("Kakao").build());
        return new InMemoryClientRegistrationRepository(clients);
    }
    @Bean
    AuthenticationSuccessHandler oauthSuccess(UserStore users,AuthService auth,
            @Value("${app.auth.callback-url:http://localhost:5173/auth/callback}") String callback) {
        return (request,response,authentication)->{
            var oauth=(org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken)authentication;
            String provider=oauth.getAuthorizedClientRegistrationId().toUpperCase();
            Map<String,Object> attributes=oauth.getPrincipal().getAttributes();
            String providerId=String.valueOf(attributes.get("id"));
            String name;
            String email;
            if (provider.equals("GOOGLE")) {
                providerId=String.valueOf(attributes.get("sub"));
                name=String.valueOf(attributes.getOrDefault("name","Google 사용자"));
                email=(String)attributes.get("email");
            } else {
                Map<?,?> account=attributes.get("kakao_account") instanceof Map<?,?> m?m:Map.of();
                Map<?,?> profile=account.get("profile") instanceof Map<?,?> m?m:Map.of();
                name=String.valueOf(profile.containsKey("nickname")?profile.get("nickname"):"Kakao 사용자");
                email=(String)account.get("email");
            }
            UserStore.User user=users.social(provider,providerId,name,email);
            String code=auth.authorizationCode(user.id());
            var session=request.getSession(false);
            if (session!=null) session.invalidate();
            response.sendRedirect(callback+(callback.contains("?")?"&":"?")+"code="+URLEncoder.encode(code,StandardCharsets.UTF_8));
        };
    }
}
