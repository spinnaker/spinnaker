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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Compresses the identity token's role list into a single compact string claim and back.
 *
 * <p>A caller can belong to hundreds of roles whose names are highly repetitive (shared prefixes
 * like {@code "raven exclusion - perm_..."} or {@code "security group - ..."}). Embedding them as a
 * raw JSON array inflates the token past typical HTTP header-size limits (e.g. Tomcat's 8&nbsp;KB
 * {@code max-http-header-size}), which rejects the propagated {@code X-SPINNAKER-IDENTITY-TOKEN}
 * header with a {@code 400}. DEFLATE collapses that redundancy (typically 5-7x), keeping the header
 * comfortably within limits.
 *
 * <p>Roles are joined with {@code '\n'} (group/role names never contain newlines), raw-DEFLATE
 * compressed, then base64url-encoded (no padding) so the result is a safe single JWT string claim.
 */
public final class RoleClaimCodec {

  /**
   * Upper bound on the inflated payload, a defense-in-depth guard against a decompression bomb.
   * Tokens are signature-verified before decoding so the content is already trusted; this simply
   * bounds pathological input. Generous enough for tens of thousands of roles.
   */
  private static final int MAX_DECOMPRESSED_BYTES = 4 * 1024 * 1024;

  private static final String DELIMITER = "\n";

  private RoleClaimCodec() {}

  /** Compress the role list to a base64url string suitable for use as a JWT string claim. */
  public static String encode(List<String> roles) {
    if (roles == null || roles.isEmpty()) {
      return "";
    }
    byte[] raw = String.join(DELIMITER, roles).getBytes(StandardCharsets.UTF_8);
    Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
    try {
      deflater.setInput(raw);
      deflater.finish();
      ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(32, raw.length / 4));
      byte[] buffer = new byte[1024];
      while (!deflater.finished()) {
        int produced = deflater.deflate(buffer);
        out.write(buffer, 0, produced);
      }
      return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    } finally {
      deflater.end();
    }
  }

  /**
   * Reverse of {@link #encode(List)}.
   *
   * @throws IllegalArgumentException if the input is not valid base64url or not valid DEFLATE data
   */
  public static List<String> decode(String encoded) {
    if (encoded == null || encoded.isEmpty()) {
      return List.of();
    }
    byte[] compressed;
    try {
      compressed = Base64.getUrlDecoder().decode(encoded);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Roles claim is not valid base64url", e);
    }

    Inflater inflater = new Inflater(true);
    inflater.setInput(compressed);
    ByteArrayOutputStream out = new ByteArrayOutputStream(compressed.length * 4);
    byte[] buffer = new byte[1024];
    try {
      while (!inflater.finished()) {
        int produced = inflater.inflate(buffer);
        if (produced == 0) {
          if (inflater.finished()) {
            break;
          }
          // No progress and not finished means the input was truncated or corrupt.
          throw new IllegalArgumentException("Roles claim is truncated or corrupt");
        }
        out.write(buffer, 0, produced);
        if (out.size() > MAX_DECOMPRESSED_BYTES) {
          throw new IllegalArgumentException("Decompressed roles claim exceeds maximum size");
        }
      }
    } catch (DataFormatException e) {
      throw new IllegalArgumentException("Roles claim is not valid DEFLATE data", e);
    } finally {
      inflater.end();
    }

    String joined = new String(out.toByteArray(), StandardCharsets.UTF_8);
    if (joined.isEmpty()) {
      return List.of();
    }
    return List.of(joined.split(DELIMITER, -1));
  }
}
