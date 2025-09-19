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
import com.netflix.spinnaker.kork.secrets.SecretReference;

/**
 * Exception thrown when a decrypted {@link UserSecret} does not have any {@link UserSecretMetadata}
 * defined.
 */
public class MissingUserSecretMetadataException extends InvalidSecretFormatException
    implements SecretError {
  public MissingUserSecretMetadataException(SecretReference secretReference) {
    super(
        String.format(
            "%s in %s",
            SecretErrorCode.MISSING_USER_SECRET_METADATA.getMessage(), secretReference.getUri()));
    getAdditionalAttributes().put("secretReference", secretReference);
  }

  @Override
  public String getErrorCode() {
    return SecretErrorCode.MISSING_USER_SECRET_METADATA.getErrorCode();
  }
}
