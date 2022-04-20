package com.netflix.spinnaker.kork.secrets.user;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Jackson mixin for {@link UserSecret} to support encoding and decoding user secrets based on a
 * provided {@code type} property.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type")
public interface UserSecretMixin {}
