/*
 * Copyright 2022 Apple Inc.
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

package com.netflix.spinnaker.kork.secrets.user;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class StringUserSecretSerde implements UserSecretSerde {
  @Override
  public boolean supports(UserSecretMetadata metadata) {
    return metadata.getType().equals("string");
  }

  @Override
  public UserSecret deserialize(byte[] encoded, UserSecretMetadata metadata) {
    String data = new String(encoded, StandardCharsets.UTF_8);
    return UserSecret.builder().metadata(metadata).data(new StringUserSecretData(data)).build();
  }

  @Override
  public byte[] serialize(UserSecretData secret, UserSecretMetadata metadata) {
    return secret.getSecretString().getBytes(StandardCharsets.UTF_8);
  }
}
