package com.netflix.spinnaker.kork.secrets.user;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.secrets.InvalidSecretFormatException;
import com.netflix.spinnaker.kork.secrets.SecretDecryptionException;
import com.netflix.spinnaker.kork.secrets.SecretException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nonnull;

/**
 * Maps decrypted secret data to a corresponding {@link UserSecret} using different encoding
 * formats. Encoding formats are specified by an {@link ObjectMapper}'s {@link
 * JsonFactory#getFormatName()} use case-insensitive string comparison.
 *
 * @see UserSecretReference
 */
public class UserSecretMapper {
  private final Map<String, ObjectMapper> mappersByEncodingFormat =
      new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  public UserSecretMapper(@Nonnull List<ObjectMapper> mappers) {
    mappers.forEach(
        mapper -> mappersByEncodingFormat.put(mapper.getFactory().getFormatName(), mapper));
  }

  @Nonnull
  public UserSecret deserialize(@Nonnull byte[] input, @Nonnull String encoding) {
    var mapper = mappersByEncodingFormat.get(encoding);
    if (mapper == null) {
      throw new InvalidSecretFormatException(
          String.format(
              "Unsupported user secret encoding: %s. Known encoding formats: %s",
              encoding, mappersByEncodingFormat.keySet()));
    }
    try {
      return mapper.readValue(input, UserSecret.class);
    } catch (IOException e) {
      throw new SecretDecryptionException(e);
    }
  }

  @Nonnull
  public byte[] serialize(@Nonnull UserSecret secret, @Nonnull String encoding) {
    var mapper = mappersByEncodingFormat.get(encoding);
    if (mapper == null) {
      throw new InvalidSecretFormatException(
          String.format(
              "Unsupported user secret encoding: %s. Known encoding formats: %s",
              encoding, mappersByEncodingFormat.keySet()));
    }
    try {
      return mapper.writeValueAsBytes(secret);
    } catch (JsonProcessingException e) {
      throw new SecretException(e);
    }
  }
}
