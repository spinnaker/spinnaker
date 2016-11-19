/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1;

import com.netflix.spinnaker.clouddriver.kubernetes.provider.KubernetesProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * This validator runs validations that do not need a network connection, trying to infer errors only from reading your
 * provided config.
 */
@Slf4j
public class StaticValidator extends Validator {
  @Override
  public void validate(Object subject) {
    try {
      getValidateMethod(subject.getClass()).invoke(this, subject);
    } catch (Exception e) {
      log.info("No Static Validator for " + subject.getClass(), e);
    }
  }

  public void validateKubernetesProvider(KubernetesProvider kubernetesProvider) {
    return;
  }
}
