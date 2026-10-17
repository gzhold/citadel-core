package io.citadel.core.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** password 为信封加密传输（见 PasswordEnvelope）；长度策略在服务端解密后校验（PASSWORD_POLICY）。 */
public record RegisterRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @Valid PasswordEnvelope password,
    @NotBlank @Size(min = 1, max = 100) String displayName) {}
