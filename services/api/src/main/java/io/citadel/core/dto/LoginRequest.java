package io.citadel.core.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** password 为信封加密传输（见 PasswordEnvelope），服务端解密后才进入哈希比对流程。 */
public record LoginRequest(@NotBlank @Email String email, @Valid PasswordEnvelope password) {}
