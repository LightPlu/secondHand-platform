package com.example.auction.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
    @Schema(example = "수정할닉네임")
    private String nickname;

    @Pattern(regexp = "^01[0-9]-?[0-9]{3,4}-?[0-9]{4}$",
            message = "올바른 전화번호 형식이 아닙니다.")
    @Schema(example = "010-1111-1111")
    private String phoneNumber;

    @Schema(example = "OO시 OO구 OO동")
    private String address;
}

