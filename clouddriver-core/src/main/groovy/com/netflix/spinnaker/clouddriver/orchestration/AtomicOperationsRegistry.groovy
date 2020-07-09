/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.orchestration

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator

import javax.annotation.Nullable

/**
 * A registry which does a lookup of AtomicOperationConverters and DescriptionValidators based on their names and
 * cloud providers
 *
 */
interface AtomicOperationsRegistry {

  /**
   *
   * @param description
   * @param cloudProvider
   * @return
   */
  AtomicOperationConverter getAtomicOperationConverter(String description, String cloudProvider)

  /**
   *
   * @param validator
   * @param cloudProvider
   * @return
   */
  @Nullable DescriptionValidator getAtomicOperationDescriptionValidator(String validator, String cloudProvider)
}
