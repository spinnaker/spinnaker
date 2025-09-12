/*
 * Copyright 2023 Apple Inc.
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

import com.netflix.spinnaker.kork.secrets.InvalidSecretFormatException;
import com.netflix.spinnaker.kork.secrets.SecretError;
import com.netflix.spinnaker.kork.secrets.SecretErrorCode;
import java.net.URI;
import lombok.Getter;

/** Exception thrown when an invalid {@link UserSecretReference} is attempted to be parsed. */
@Getter
public class InvalidUserSecretReferenceException extends InvalidSecretFormatException
    implements SecretError {

  public InvalidUserSecretReferenceException(String input, Throwable cause) {
    super("Unable to parse input into a URI", cause);
    getAdditionalAttributes().put("input", input);
  }

  public InvalidUserSecretReferenceException(String message, URI uri) {
    super(message);
    getAdditionalAttributes().put("input", uri);
  }

  @Override
  public String getErrorCode() {
    return SecretErrorCode.INVALID_USER_SECRET_URI.getErrorCode();
  }
}
