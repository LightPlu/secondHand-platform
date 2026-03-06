package com.example.auction.domain.user.controller;

import com.example.auction.domain.user.dto.LoginRequest;
import com.example.auction.domain.user.dto.LoginResponse;
import com.example.auction.domain.user.dto.SignUpRequest;
import com.example.auction.domain.user.dto.UpdateProfileRequest;
import com.example.auction.domain.user.dto.UserResponse;
import com.example.auction.domain.user.service.UserService;
import com.example.auction.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "유저 회원가입 / 로그인 / 로그아웃 / 정보 수정 API")
public class UserController {

    private final UserService userService;

    /**
     * 회원가입
     */
    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 이름, 닉네임으로 회원가입합니다.")
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        log.info("POST /api/users/signup - 회원가입 요청");
        UserResponse response = userService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 로그인
     */
    @Operation(summary = "로그인", description = "이메일, 비밀번호로 로그인 후 JWT 토큰을 발급받습니다.")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/users/login - 로그인 요청");
        LoginResponse response = userService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 로그아웃
     */
    @Operation(summary = "로그아웃", description = "JWT 토큰을 무효화하여 로그아웃합니다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String bearerToken) {
        log.info("POST /api/users/logout - 로그아웃 요청");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            userService.logout(bearerToken.substring(7));
        }
        return ResponseEntity.ok().build();
    }

    /**
     * 내 정보 조회 (인증 필요)
     */
    @Operation(summary = "내 정보 조회", description = "로그인한 사용자의 정보를 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyInfo(@AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("GET /api/users/me - 내 정보 조회");
        UserResponse response = userService.getUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    /**
     * 내 정보 수정 (인증 필요)
     */
    @Operation(summary = "내 정보 수정", description = "닉네임, 전화번호, 주소를 수정합니다. 변경하지 않을 항목은 기존 값을 그대로 보내면 됩니다.")
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMyInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        log.info("PATCH /api/users/me - 내 정보 수정 요청");
        UserResponse response = userService.updateProfile(userDetails.getUsername(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * 회원 탈퇴 (인증 필요)
     */
    @Operation(summary = "회원 탈퇴", description = "계정을 탈퇴 처리합니다. 실제 삭제가 아닌 상태값(DELETED)으로 변경됩니다.")
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("DELETE /api/users/me - 회원 탈퇴 요청");
        userService.withdraw(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    /**
     * 특정 사용자 정보 조회 (인증 필요)
     */
    @Operation(summary = "특정 사용자 정보 조회", description = "userId로 특정 사용자의 정보를 조회합니다.")
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long userId) {
        log.info("GET /api/users/{} - 사용자 정보 조회", userId);
        UserResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(response);
    }
}

