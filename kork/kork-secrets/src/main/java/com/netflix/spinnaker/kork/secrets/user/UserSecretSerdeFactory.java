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

import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserSecretSerdeFactory {
  private final Iterable<UserSecretSerde> serdes;

  /**
   * Locates the first supported serde that supports the provided metadata.
   *
   * @param metadata metadata of the user secret to find a serde for
   * @return the found serde
   * @throws UnsupportedUserSecretTypeException if no serde supports the type given in the metadata
   */
  public UserSecretSerde serdeFor(UserSecretMetadata metadata) {
    return StreamSupport.stream(serdes.spliterator(), false)
        .filter(serde -> serde.supports(metadata))
        .findFirst()
        .orElseThrow(() -> new UnsupportedUserSecretTypeException(metadata.getType()));
  }
}
