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

import com.netflix.spinnaker.kork.secrets.SecretDecryptionException;
import com.netflix.spinnaker.kork.secrets.SecretError;
import com.netflix.spinnaker.kork.secrets.SecretErrorCode;

/**
 * Exception thrown when a {@link UserSecretSerde} encounters an unsupported {@linkplain
 * UserSecretMetadata#getEncoding() encoding type}.
 */
public class UnsupportedUserSecretEncodingException extends SecretDecryptionException
    implements SecretError {
  public UnsupportedUserSecretEncodingException() {
    super("No secret encoding specified");
  }

  public UnsupportedUserSecretEncodingException(String encoding) {
    super(String.format("Unsupported user secret encoding '%s'", encoding));
  }

  @Override
  public String getErrorCode() {
    return SecretErrorCode.UNSUPPORTED_USER_SECRET_ENCODING.getErrorCode();
  }
}
