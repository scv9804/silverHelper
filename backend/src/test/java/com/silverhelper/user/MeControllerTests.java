package com.silverhelper.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.silverhelper.common.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class MeControllerTests {
    private final UserStore store=mock(UserStore.class);
    private final MeController controller=new MeController(store);
    private final Jwt jwt=Jwt.withTokenValue("test").header("alg","none").subject("7").build();

    @Test void 다른_사용자의_프로필은_조회와_수정이_불가능하다() {
        when(store.profile(7,21)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class,()->controller.get(jwt,21));
        MeController.ProfileWrite input=new MeController.ProfileWrite("부모님",78,"서울특별시",null,null,null,
                null,null,null,null,null,null,null);
        assertThrows(BusinessException.class,()->controller.update(jwt,21,input));
        verify(store).profile(7,21);
        verify(store).update(eq(7L),eq(21L),any());
    }

    @Test void 삭제와_관심_혜택은_인증된_소유자_아이디를_사용한다() {
        when(store.delete(7,21)).thenReturn(true);
        controller.delete(jwt,21);
        controller.unsave(jwt,42);
        verify(store).delete(7,21);
        verify(store).unsave(7,42);
    }
}
