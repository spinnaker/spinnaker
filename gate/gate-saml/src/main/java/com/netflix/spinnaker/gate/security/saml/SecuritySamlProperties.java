/*
 * Copyright 2023 Apple, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.gate.security.saml;

import com.netflix.spinnaker.kork.annotations.NullableByDefault;
import jakarta.annotation.Nonnull;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * Spinnaker-specific SAML configuration properties.
 *
 * <p>SAML connection settings (IdP metadata URI, entity ID, credentials) are configured via native
 * Spring Boot properties under {@code spring.security.saml2.relyingparty.registration.*}. See
 * {@code gate/gate-saml/docs/saml-migration.md} for a migration guide.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties("saml")
@NullableByDefault
public class SecuritySamlProperties {

  /** Whether SAML authentication is enabled. */
  private boolean enabled = false;

  /** Optional list of roles required for authentication to succeed. */
  private List<String> requiredRoles;

  /** Determines whether to sort the roles returned from the SAML provider. */
  private boolean sortRoles = false;

  /** Toggles whether role names should be converted to lowercase. */
  private boolean forceLowercaseRoles = true;

  @Nonnull @NestedConfigurationProperty
  private UserAttributeMapping userAttributeMapping = new UserAttributeMapping();

  @Getter
  @Setter
  @Validated
  public static class UserAttributeMapping {
    @NotEmpty private String firstName = "User.FirstName";
    @NotEmpty private String lastName = "User.LastName";
    @NotEmpty private String roles = "memberOf";
    @NotEmpty private String rolesDelimiter = ";";
    private String username;
    private String email;
  }
}
