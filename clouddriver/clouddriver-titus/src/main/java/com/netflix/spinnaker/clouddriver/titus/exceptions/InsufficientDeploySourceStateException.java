/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.titus.exceptions;

import com.netflix.spinnaker.clouddriver.titus.TitusException;
import java.util.HashMap;
import java.util.Map;

/**
 * Thrown when a Titus deployment does not have sufficient information from a source server group.
 */
public class InsufficientDeploySourceStateException extends TitusException {
  private final Map<String, Object> sourceState = new HashMap<>();

  public InsufficientDeploySourceStateException(
      String message, String account, String region, String asgName) {
    super(message);
    sourceState.put("account", account);
    sourceState.put("region", region);
    sourceState.put("asgName", asgName);
    setRetryable(false);
  }

  @Override
  public Map<String, Object> getAdditionalAttributes() {
    return sourceState;
  }
}
