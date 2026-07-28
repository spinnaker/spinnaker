/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.security.token;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Immutable representation of the identity claims carried by a signed Spinnaker identity token.
 *
 * <p>The token carries the caller's subject ({@code sub}), their resolved {@code roles}, and the
 * Spinnaker-specific {@code admin}/{@code accountManager} flags, along with the standard JWT
 * registered claims ({@code iss}, {@code aud}, {@code iat}, {@code exp}). Services verify the
 * signature and the registered claims, then populate Spring authorities from {@link #getRoles()}
 * plus the admin/account-manager flags.
 */
public final class SpinnakerTokenClaims {
  /**
   * Custom JWT claim carrying the caller's role names, DEFLATE-compressed and base64url-encoded
   * (see {@link RoleClaimCodec}). Compression keeps the propagated identity-token header within
   * HTTP header-size limits for callers with large role sets.
   */
  public static final String CLAIM_ROLES = "roles";

  /** Custom JWT claim name carrying the admin flag. */
  public static final String CLAIM_ADMIN = "admin";

  /** Custom JWT claim name carrying the account-manager flag. */
  public static final String CLAIM_ACCOUNT_MANAGER = "account_manager";

  private final String subject;
  private final List<String> roles;
  private final boolean admin;
  private final boolean accountManager;
  private final String issuer;
  private final List<String> audience;
  private final Instant issuedAt;
  private final Instant expiresAt;

  private SpinnakerTokenClaims(Builder builder) {
    this.subject = Objects.requireNonNull(builder.subject, "subject is required");
    this.roles = List.copyOf(builder.roles);
    this.admin = builder.admin;
    this.accountManager = builder.accountManager;
    this.issuer = builder.issuer;
    this.audience = builder.audience == null ? List.of() : List.copyOf(builder.audience);
    this.issuedAt = builder.issuedAt;
    this.expiresAt = builder.expiresAt;
  }

  @Nonnull
  public String getSubject() {
    return subject;
  }

  @Nonnull
  public List<String> getRoles() {
    return roles;
  }

  public boolean isAdmin() {
    return admin;
  }

  public boolean isAccountManager() {
    return accountManager;
  }

  @Nullable
  public String getIssuer() {
    return issuer;
  }

  @Nonnull
  public List<String> getAudience() {
    return audience;
  }

  @Nullable
  public Instant getIssuedAt() {
    return issuedAt;
  }

  @Nullable
  public Instant getExpiresAt() {
    return expiresAt;
  }

  public static Builder builder(@Nonnull String subject) {
    return new Builder().subject(subject);
  }

  /**
   * Decode the claims from an <em>already-trusted</em> signed identity token <strong>without
   * verifying its signature</strong>. Intended only for callers that have already verified the
   * token through the normal inbound chain (e.g. Orca capturing the launching subject's roles off a
   * request the {@code IdentityTokenAuthenticationFilter} already authenticated). Never use this to
   * make a trust decision on an unverified token.
   *
   * @throws IllegalArgumentException if the token is not a well-formed signed JWT
   */
  @Nonnull
  public static SpinnakerTokenClaims fromTrustedToken(@Nonnull String token) {
    try {
      JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
      Builder builder = builder(claims.getSubject());
      Object rolesClaim = claims.getClaim(CLAIM_ROLES);
      if (rolesClaim instanceof String) {
        builder.roles(RoleClaimCodec.decode((String) rolesClaim));
      }
      builder.admin(Boolean.TRUE.equals(claims.getBooleanClaim(CLAIM_ADMIN)));
      builder.accountManager(Boolean.TRUE.equals(claims.getBooleanClaim(CLAIM_ACCOUNT_MANAGER)));
      return builder.build();
    } catch (ParseException e) {
      throw new IllegalArgumentException("Not a well-formed signed JWT", e);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SpinnakerTokenClaims that = (SpinnakerTokenClaims) o;
    return admin == that.admin
        && accountManager == that.accountManager
        && subject.equals(that.subject)
        && roles.equals(that.roles)
        && Objects.equals(issuer, that.issuer)
        && audience.equals(that.audience)
        && Objects.equals(issuedAt, that.issuedAt)
        && Objects.equals(expiresAt, that.expiresAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        subject, roles, admin, accountManager, issuer, audience, issuedAt, expiresAt);
  }

  @Override
  public String toString() {
    return "SpinnakerTokenClaims{subject='"
        + subject
        + "', roles="
        + roles
        + ", admin="
        + admin
        + ", accountManager="
        + accountManager
        + ", issuer='"
        + issuer
        + "', audience="
        + audience
        + ", expiresAt="
        + expiresAt
        + '}';
  }

  public static final class Builder {
    private String subject;
    private List<String> roles = new ArrayList<>();
    private boolean admin;
    private boolean accountManager;
    private String issuer;
    private List<String> audience;
    private Instant issuedAt;
    private Instant expiresAt;

    public Builder subject(String subject) {
      this.subject = subject;
      return this;
    }

    public Builder roles(List<String> roles) {
      this.roles = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
      return this;
    }

    public Builder admin(boolean admin) {
      this.admin = admin;
      return this;
    }

    public Builder accountManager(boolean accountManager) {
      this.accountManager = accountManager;
      return this;
    }

    public Builder issuer(String issuer) {
      this.issuer = issuer;
      return this;
    }

    public Builder audience(List<String> audience) {
      this.audience = audience;
      return this;
    }

    public Builder issuedAt(Instant issuedAt) {
      this.issuedAt = issuedAt;
      return this;
    }

    public Builder expiresAt(Instant expiresAt) {
      this.expiresAt = expiresAt;
      return this;
    }

    public SpinnakerTokenClaims build() {
      return new SpinnakerTokenClaims(this);
    }
  }
}
