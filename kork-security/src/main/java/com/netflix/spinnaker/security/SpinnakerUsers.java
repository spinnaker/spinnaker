/*
 * Copyright 2023 Apple, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.security;

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import javax.annotation.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Constants and utilities related to Spinnaker users (AKA principals). */
@NonnullByDefault
public class SpinnakerUsers {
  /** String constant for the anonymous userid. */
  public static final String ANONYMOUS = "anonymous";

  /** Gets the userid of the provided authentication token. */
  public static String getUserId(@Nullable Authentication authentication) {
    return authentication != null ? authentication.getName() : ANONYMOUS;
  }

  /** Gets the current Spinnaker userid. */
  public static String getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null) {
      return getUserId(authentication);
    }
    // fall back to request header context if relevant (AuthenticatedRequestFilter)
    return AuthenticatedRequest.getSpinnakerUser().orElse(ANONYMOUS);
  }
}
