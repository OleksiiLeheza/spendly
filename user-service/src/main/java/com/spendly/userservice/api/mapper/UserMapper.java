package com.spendly.userservice.api.mapper;

import com.spendly.userservice.api.dto.UpdateProfileRequest;
import com.spendly.userservice.api.dto.UserResponse;
import com.spendly.userservice.domain.User;
import com.spendly.userservice.service.ProfileUpdate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    @Mapping(target = "displayName", expression = "java(user.resolvedDisplayName())")
    UserResponse toResponse(User user);

    ProfileUpdate toCommand(UpdateProfileRequest request);
}
