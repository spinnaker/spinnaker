/*
 * Copyright 2022 Apple, Inc.
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
 */

package com.netflix.spinnaker.kork.secrets.engines;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.netflix.spinnaker.kork.secrets.SecretReference;
import java.util.Map;

public interface SecretsManagerClientProvider {
  /**
   * Gets a configured AWS Secrets Manager client for the provided secret parameters. These
   * parameters correspond to those given in the {@link
   * com.netflix.spinnaker.kork.secrets.EncryptedSecret} or {@link
   * com.netflix.spinnaker.kork.secrets.user.UserSecretReference} URI.
   */
  AWSSecretsManager getClientForSecretParameters(Map<String, String> parameters);

  default AWSSecretsManager getClientForSecret(SecretReference reference) {
    throw new UnsupportedOperationException();
  }
}
