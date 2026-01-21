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

/** Exception thrown when an unsupported secret engine is called upon for decrypting a secret. */
public class UnsupportedSecretEngineException extends SecretDecryptionException
    implements SecretError {
  public UnsupportedSecretEngineException(String engine) {
    super(String.format("Unsupported secret engine identifier '%s'", engine));
  }

  @Override
  public String getErrorCode() {
    return SecretErrorCode.UNSUPPORTED_SECRET_ENGINE.getErrorCode();
  }
}
