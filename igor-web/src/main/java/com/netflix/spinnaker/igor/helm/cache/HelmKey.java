/*
 * Copyright 2020 Apple, Inc.
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

package com.netflix.spinnaker.igor.helm.cache;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class HelmKey {
  private final String prefix;
  private final String id;
  private final String account;
  private final String digest;

  public String toString() {
    if (digest != null) {
      return String.format("%s:%s:%s:%s", prefix, id, account, digest);
    } else {
      return String.format("%s:%s:%s", prefix, id, account);
    }
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof HelmKey && toString().equals(obj.toString());
  }
}
