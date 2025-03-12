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

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.secrets.InvalidSecretFormatException;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;

@NonnullByDefault
@RequiredArgsConstructor
public class UserSecretSerdeFactory {
  private final Iterable<UserSecretSerde> serdes;

  public UserSecretSerde serdeFor(UserSecretMetadata metadata) {
    return StreamSupport.stream(serdes.spliterator(), false)
        .filter(serde -> serde.supports(metadata))
        .findFirst()
        .orElseThrow(
            () ->
                new InvalidSecretFormatException("Unsupported user secret metadata: " + metadata));
  }
}
