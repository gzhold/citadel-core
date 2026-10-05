package io.citadel.core.dto;

import io.citadel.core.entity.MemberRole;

/** 用户在某工作区（租户）中的成员关系。 */
public record MembershipDto(Long workspaceId, String name, String slug, MemberRole role) {}
