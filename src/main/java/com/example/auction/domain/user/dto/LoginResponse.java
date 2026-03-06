package com.example.auction.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    private String accessToken;
    private String tokenType;
    private Long userId;
    private String email;
    private String name;
    private String nickname;

    public static LoginResponse of(String accessToken, Long userId, String email, String name, String nickname) {
        return LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .userId(userId)
                .email(email)
                .name(name)
                .nickname(nickname)
                .build();
    }
}

