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

package com.netflix.spinnaker.security;

import java.util.Locale;
import javax.annotation.Nullable;
import org.springframework.security.core.Authentication;

/**
 * Defines types of authorizations supported by {@link AccessControlled#isAuthorized(Authentication,
 * Object)}.
 */
public enum Authorization {
  READ,
  WRITE,
  EXECUTE,
  CREATE,
  ;

  public static @Nullable Authorization parse(@Nullable Object o) {
    if (o == null) {
      return null;
    }
    String name = o.toString().toUpperCase(Locale.ROOT);
    try {
      return valueOf(name);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
