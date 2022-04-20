package com.netflix.spinnaker.kork.secrets.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Getter;

/**
 * Opaque user secrets are generic user secrets containing a list of roles allowed to use data from
 * this secret along with maps for string and binary data. This type of user secret has the
 * following fields:
 *
 * <dl>
 *   <dt>type
 *   <dd><tt>opaque</tt>
 *   <dt>roles
 *   <dd>List of Fiat roles allowed to use this secret. When other Spinnaker resources use this user
 *       secret, the intersection between that user's roles and the user secret's roles must be
 *       non-empty.
 *   <dt>data
 *   <dd>Contains base64-encoded values as a map of keys to values.
 *   <dt>stringData
 *   <dd>Contains UTF-8-encoded values as a map of keys to values.
 * </dl>
 */
@JsonTypeName(OpaqueUserSecret.TYPE)
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
public class OpaqueUserSecret implements UserSecret {
  public static final String TYPE = "opaque";

  private final List<String> roles;
  private final Map<String, byte[]> data;
  private final Map<String, String> stringData;

  @Builder
  @JsonCreator
  public OpaqueUserSecret(
      @JsonProperty("roles") List<String> roles,
      @JsonProperty("data") Map<String, byte[]> data,
      @JsonProperty("stringData") Map<String, String> stringData) {
    this.roles = roles != null ? roles : List.of();
    this.data = data != null ? data : Map.of();
    this.stringData = stringData != null ? stringData : Map.of();
  }

  @Override
  @Nonnull
  public String getType() {
    return TYPE;
  }

  @Override
  @Nonnull
  public String getSecretString(@Nonnull String key) {
    var value = stringData.get(key);
    if (value != null) {
      return value;
    }
    throw new NoSuchElementException(key);
  }

  @Override
  @Nonnull
  public byte[] getSecretBytes(@Nonnull String key) {
    var bytes = data.get(key);
    if (bytes != null) {
      return bytes;
    }
    var value = stringData.get(key);
    if (value != null) {
      return value.getBytes(StandardCharsets.UTF_8);
    }
    throw new NoSuchElementException(key);
  }
}
