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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleClaimCodecTest {

  @Test
  void roundTrip() {
    List<String> roles = List.of("dev", "ops", "security group - foo");
    assertThat(RoleClaimCodec.decode(RoleClaimCodec.encode(roles))).isEqualTo(roles);
  }

  @Test
  void emptyAndNullEncodeToEmptyStringAndDecodeToEmptyList() {
    assertThat(RoleClaimCodec.encode(List.of())).isEmpty();
    assertThat(RoleClaimCodec.encode(null)).isEmpty();
    assertThat(RoleClaimCodec.decode("")).isEmpty();
    assertThat(RoleClaimCodec.decode(null)).isEmpty();
  }

  @Test
  void preservesOrderDuplicatesUnicodeAndSpaces() {
    List<String> roles =
        List.of("u.s. all", "u.s. all", "café — users", "  leading/trailing  ", "a,b;c");
    assertThat(RoleClaimCodec.decode(RoleClaimCodec.encode(roles))).isEqualTo(roles);
  }

  @Test
  void compressesLargeRepetitiveRoleSetWellBelowItsRawSize() {
    List<String> roles = new ArrayList<>();
    for (int i = 0; i < 200; i++) {
      roles.add("raven exclusion - perm_some_long_repetitive_role_name_" + i);
    }
    int rawSize = String.join("\n", roles).length();
    int encodedSize = RoleClaimCodec.encode(roles).length();

    // highly repetitive names should compress dramatically
    assertThat(encodedSize).isLessThan(rawSize / 3);
    assertThat(RoleClaimCodec.decode(RoleClaimCodec.encode(roles))).isEqualTo(roles);
  }

  @Test
  void decodeRejectsNonBase64() {
    assertThatThrownBy(() -> RoleClaimCodec.decode("not valid base64 !!!"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void decodeRejectsValidBase64ThatIsNotDeflateData() {
    // valid base64url, but the bytes are not a DEFLATE stream
    String garbage = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[64]);
    assertThatThrownBy(() -> RoleClaimCodec.decode(garbage))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
