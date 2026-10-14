package com.silverhelper.user;

import com.silverhelper.common.exception.BusinessException;
import com.silverhelper.common.exception.ErrorCode;
import com.silverhelper.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
public class MeController {
    private final UserStore users;
    public MeController(UserStore users) {this.users=users;}
    public record ProfileWrite(@NotBlank @Size(max=100) String name,@NotNull @Min(0) @Max(120) Integer age,
            @NotBlank @Size(max=50) String ctpvName,@Size(max=50) String sggName,@Min(1) @Max(6) Integer ltcGrade,Boolean hasNoLtcGrade,
            @Min(0) @Max(1000) Integer incomeMedianPct,Boolean basicLivelihood,Boolean nearPoverty,
            Boolean basicPension,Boolean disability,Boolean veteran,Boolean livingAlone) {
        UserStore.Profile profile() {return new UserStore.Profile(0,name,age,ctpvName,sggName,ltcGrade,hasNoLtcGrade,
                incomeMedianPct,basicLivelihood,nearPoverty,basicPension,disability,veteran,livingAlone);}
    }
    public record BenefitWrite(@NotNull Long benefitId) {}
    private long id(Jwt jwt) {return Long.parseLong(jwt.getSubject());}
    private UserStore.Profile profile(long userId,long profileId) {
        return users.profile(userId,profileId).orElseThrow(()->new BusinessException(ErrorCode.NOT_FOUND));
    }
    @GetMapping
    public ApiResponse<UserStore.User> me(@AuthenticationPrincipal Jwt jwt) {return ApiResponse.ok(users.find(id(jwt)).orElseThrow());}
    @GetMapping("/profiles")
    public ApiResponse<List<UserStore.Profile>> profiles(@AuthenticationPrincipal Jwt jwt) {return ApiResponse.ok(users.profiles(id(jwt)));}
    @PostMapping("/profiles")
    public ResponseEntity<ApiResponse<UserStore.Profile>> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ProfileWrite body) {
        if (Boolean.TRUE.equals(body.hasNoLtcGrade()) && body.ltcGrade()!=null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return ResponseEntity.status(201).body(ApiResponse.ok(users.create(id(jwt),body.profile())));
    }
    @GetMapping("/profiles/{profileId}")
    public ApiResponse<UserStore.Profile> get(@AuthenticationPrincipal Jwt jwt,@PathVariable long profileId) {
        return ApiResponse.ok(profile(id(jwt),profileId));
    }
    @PutMapping("/profiles/{profileId}")
    public ApiResponse<UserStore.Profile> update(@AuthenticationPrincipal Jwt jwt,@PathVariable long profileId,@Valid @RequestBody ProfileWrite body) {
        if (Boolean.TRUE.equals(body.hasNoLtcGrade()) && body.ltcGrade()!=null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (!users.update(id(jwt),profileId,body.profile())) throw new BusinessException(ErrorCode.NOT_FOUND);
        return ApiResponse.ok(profile(id(jwt),profileId));
    }
    @DeleteMapping("/profiles/{profileId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt,@PathVariable long profileId) {
        if (!users.delete(id(jwt),profileId)) throw new BusinessException(ErrorCode.NOT_FOUND);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/saved-benefits")
    public ApiResponse<List<Long>> saved(@AuthenticationPrincipal Jwt jwt) {return ApiResponse.ok(users.saved(id(jwt)));}
    @PostMapping("/saved-benefits")
    public ResponseEntity<Void> save(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody BenefitWrite body) {
        if (!users.benefitExists(body.benefitId())) throw new BusinessException(ErrorCode.BENEFIT_NOT_FOUND);
        users.save(id(jwt),body.benefitId());return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/saved-benefits/{benefitId}")
    public ResponseEntity<Void> unsave(@AuthenticationPrincipal Jwt jwt,@PathVariable long benefitId) {
        users.unsave(id(jwt),benefitId);return ResponseEntity.noContent().build();
    }
}
