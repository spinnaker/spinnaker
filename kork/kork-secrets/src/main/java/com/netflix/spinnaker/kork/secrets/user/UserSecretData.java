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

import java.util.NoSuchElementException;

public interface UserSecretData {
  /**
   * Gets the value of this secret with the provided key and returns a string encoding of it.
   *
   * @param key the key to look up the secret value for in this data; can be an empty string for
   *     flat secrets
   * @return the secret value encoded as a string
   * @throws NoSuchElementException if no secret value exists for the given key
   */
  String getSecretString(String key);

  /**
   * Gets the value of this secret as a single string if the underlying secret data supports it.
   *
   * @return the secret payload as a string
   * @throws UnsupportedOperationException if this secret doesn't support scalar strings
   */
  default String getSecretString() {
    throw new UnsupportedOperationException();
  }
}
