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

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
// not using @UserSecretType as this is an unstructured type
// see StringUserSecretSerde
public class StringUserSecretData implements UserSecretData {
  private final String data;

  @Override
  public String getSecretString(String key) {
    return data;
  }

  @Override
  public String getSecretString() {
    return data;
  }
}
