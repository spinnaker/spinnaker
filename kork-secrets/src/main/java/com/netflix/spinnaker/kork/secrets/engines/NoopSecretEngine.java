/*
 * Copyright 2019 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.SecretEngine;
import org.springframework.stereotype.Component;

/**
 * Secret engine that returns its value ("v") parameter which can be null if not provided. Used for
 * testing.
 */
@Component
public class NoopSecretEngine implements SecretEngine {
  private static final String IDENTIFIER = "noop";
  private static final String PARAM_VALUE = "v";

  @Override
  public String identifier() {
    return IDENTIFIER;
  }

  @Override
  public byte[] decrypt(EncryptedSecret encryptedSecret) {
    return encryptedSecret.getParams().get(PARAM_VALUE).getBytes();
  }

  @Override
  public void validate(EncryptedSecret encryptedSecret) {}

  @Override
  public void clearCache() {}
}
