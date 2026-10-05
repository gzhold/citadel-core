package io.citadel.core.dto;

import io.citadel.core.entity.MemberRole;

/** 登录/注册后返回的默认工作区上下文（含当前用户角色）。 */
public record WorkspaceDto(Long id, String name, String slug, MemberRole role) {}
