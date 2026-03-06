package com.example.auction.domain.user.service;

import com.example.auction.domain.user.dto.LoginRequest;
import com.example.auction.domain.user.dto.LoginResponse;
import com.example.auction.domain.user.dto.SignUpRequest;
import com.example.auction.domain.user.dto.UpdateProfileRequest;
import com.example.auction.domain.user.dto.UserResponse;
import com.example.auction.domain.user.entity.User;
import com.example.auction.domain.user.enums.UserRole;
import com.example.auction.domain.user.enums.UserStatus;
import com.example.auction.domain.user.repository.UserRepository;
import com.example.auction.global.exception.DuplicateNicknameException;
import com.example.auction.global.exception.DuplicateEmailException;
import com.example.auction.global.exception.InvalidCredentialsException;
import com.example.auction.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public UserResponse signUp(SignUpRequest request) {
        log.info("회원가입 시도: email={}", request.getEmail());

        // 이메일 중복 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("이미 사용 중인 이메일입니다: " + request.getEmail());
        }

        // 닉네임 중복 확인
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new DuplicateNicknameException("이미 사용 중인 닉네임입니다: " + request.getNickname());
        }

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // User 엔티티 생성
        User user = User.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .name(request.getName())
                .nickname(request.getNickname())
                .phoneNumber(request.getPhoneNumber())
                .address(request.getAddress())
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        log.info("회원가입 완료: userId={}, email={}", savedUser.getId(), savedUser.getEmail());

        return UserResponse.from(savedUser);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.info("로그인 시도: email={}", request.getEmail());

        // 사용자 조회
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다."));

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        // 계정 상태 확인
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException("비활성화된 계정입니다.");
        }

        // JWT 토큰 생성
        String token = jwtTokenProvider.generateToken(user.getEmail(), user.getId());

        log.info("로그인 완료: userId={}, email={}", user.getId(), user.getEmail());

        return LoginResponse.of(
                token,
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getNickname()
        );
    }



    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        return UserResponse.from(user);
    }

    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        return UserResponse.from(user);
    }

    // 로그아웃 - 클라이언트 토큰 삭제 위임 방식 (추후 Redis 블랙리스트 도입 예정)
    public void logout(String token) {
        if (!jwtTokenProvider.validateToken(token)) {
            throw new RuntimeException("유효하지 않은 토큰입니다.");
        }
        String email = jwtTokenProvider.extractEmail(token);
        log.info("로그아웃 완료: email={}", email);
        // 클라이언트에서 토큰을 삭제하면 로그아웃 처리됨
        // TODO: 추후 Redis 블랙리스트 방식으로 전환
    }

    // 회원 탈퇴 - 소프트 딜리트 (status = DELETED)
    // 추후 하드 딜리트(DB 데이터 삭제)로 변경 가능
    @Transactional
    public void withdraw(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        if (user.getStatus() == UserStatus.DELETED) {
            throw new RuntimeException("이미 탈퇴한 계정입니다.");
        }

        user.changeStatus(UserStatus.DELETED);
        log.info("회원 탈퇴 완료: email={}", email);
        // TODO: 추후 탈퇴 시 관련 데이터 처리 로직 추가 (경매, 입찰 등)
    }

    // 정보 수정 - 닉네임, 전화번호, 주소 변경
    @Transactional
    public UserResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        // 닉네임 중복 확인 (변경하는 경우 + 기존 닉네임과 다른 경우)
        if (request.getNickname() != null
                && !request.getNickname().equals(user.getNickname())
                && userRepository.existsByNickname(request.getNickname())) {
            throw new DuplicateNicknameException("이미 사용 중인 닉네임입니다: " + request.getNickname());
        }

        user.updateProfile(request.getNickname(), request.getPhoneNumber(), request.getAddress());
        log.info("정보 수정 완료: email={}", email);

        return UserResponse.from(user);
    }
}

