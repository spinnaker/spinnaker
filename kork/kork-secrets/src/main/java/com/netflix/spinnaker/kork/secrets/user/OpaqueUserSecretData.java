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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Map;
import java.util.NoSuchElementException;
import lombok.Getter;

/**
 * Opaque user secrets are generic user secrets encoded as a map of key/value pairs (all strings).
 */
@NonnullByDefault
@UserSecretType("opaque")
public class OpaqueUserSecretData implements UserSecretData {
  @Getter(onMethod = @__({@JsonValue}))
  private final Map<String, String> data;

  @JsonCreator
  public OpaqueUserSecretData(Map<String, String> data) {
    this.data = data;
  }

  @Override
  public String getSecretString(String key) {
    String value = data.get(key);
    if (value == null) {
      throw new NoSuchElementException(key);
    }
    return value;
  }
}
