/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.deploy;

import java.util.List;

/**
 * A DeployHandler takes a parameterized description object and performs some deployment operation
 * based off of its detail. These objects may most often be derived from a {@link
 * DeployHandlerRegistry} implementation.
 *
 * @param <T> the type of the {@link DeployDescription}
 * @see DeployDescription
 */
public interface DeployHandler<T> {
  /**
   * A method that performs the deployment action described by the description object and returns
   * its results as an implementation of {@link DeploymentResult}
   *
   * @param description
   * @param priorOutputs from prior operations
   * @return deployment result object
   */
  DeploymentResult handle(T description, List priorOutputs);

  /**
   * Used to determine if this handler is suitable for processing the supplied description object.
   *
   * @param description
   * @return true/false
   */
  boolean handles(DeployDescription description);
}
