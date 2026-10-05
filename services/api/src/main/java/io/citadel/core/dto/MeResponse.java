package io.citadel.core.dto;

import java.util.List;

public record MeResponse(UserDto user, List<MembershipDto> memberships) {}
