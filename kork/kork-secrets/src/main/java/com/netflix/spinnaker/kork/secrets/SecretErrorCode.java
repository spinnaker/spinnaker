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

package com.netflix.spinnaker.kork.secrets;

import lombok.Getter;

/** Standard error codes and messages for various secret errors. */
public enum SecretErrorCode implements SecretError {
  /** @see com.netflix.spinnaker.kork.secrets.user.InvalidUserSecretReferenceException */
  INVALID_USER_SECRET_URI("user.format.invalid", "Invalid user secret URI format"),
  /** @see com.netflix.spinnaker.kork.secrets.user.MissingUserSecretMetadataException */
  MISSING_USER_SECRET_METADATA("user.metadata.missing", "Missing user secret metadata"),
  /** @see com.netflix.spinnaker.kork.secrets.user.InvalidUserSecretMetadataException */
  INVALID_USER_SECRET_METADATA("user.metadata.invalid", "Invalid user secret metadata"),
  /** @see com.netflix.spinnaker.kork.secrets.user.UnsupportedUserSecretEngineException */
  UNSUPPORTED_USER_SECRET_ENGINE(
      "user.engine.unsupported", "SecretEngine does not support user secrets"),
  /** @see com.netflix.spinnaker.kork.secrets.user.UnsupportedUserSecretEncodingException */
  UNSUPPORTED_USER_SECRET_ENCODING(
      "user.encoding.unsupported", "Unsupported user secret 'encoding'"),
  /** @see com.netflix.spinnaker.kork.secrets.user.UnsupportedUserSecretTypeException */
  UNSUPPORTED_USER_SECRET_TYPE("user.type.unsupported", "Unsupported user secret 'type'"),
  /** @see com.netflix.spinnaker.kork.secrets.user.InvalidUserSecretDataException */
  INVALID_USER_SECRET_DATA("user.data.invalid", "Invalid user secret data"),
  /** @see SecretDecryptionException */
  USER_SECRET_RETRIEVAL_FAILURE("user.retrieve.failure", "Unable to retrieve user secret"),
  DENIED_ACCESS_TO_USER_SECRET("user.access.deny", "Denied access to user secret"),
  MISSING_USER_SECRET_DATA_KEY("user.data.missing", "Missing user secret data for requested key"),
  MISSING_USER_SECRET_KEY_PARAM("user.key.missing", "Missing 'k' parameter for opaque user secret"),
  WRONG_TYPE_USER_SECRET_KEY_PARAM(
      "user.key.wrongtype", "Provided 'k' parameter for user secret with non-opaque type"),
  INVALID_EXTERNAL_SECRET_URI("external.format.invalid", "Invalid external secret URI format"),
  /** @see SecretDecryptionException */
  EXTERNAL_SECRET_DECRYPTION_FAILURE(
      "external.decrypt.failure", "Unable to decrypt external secret"),
  DENIED_ACCESS_TO_EXTERNAL_SECRET("external.access.deny", "Denied access to external secret"),
  /** @see com.netflix.spinnaker.kork.secrets.UnsupportedSecretEngineException */
  UNSUPPORTED_SECRET_ENGINE("engine.unsupported", "Unsupported secret engine identifier"),
  ;

  @Getter private final String errorCode;
  @Getter private final String message;

  SecretErrorCode(String errorCode, String message) {
    this.errorCode = "secrets." + errorCode;
    this.message = message;
  }
}
