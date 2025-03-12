/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.model;

import com.netflix.spinnaker.clouddriver.documentation.Empty;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public interface FunctionProvider {
  Collection<? extends Function> getAllFunctions();

  Function getFunction(String account, String region, String functionName);

  /**
   * Returns all functions related to an application based on one of the following criteria: - the
   * load balancer name follows the Frigga naming conventions for load balancers (i.e., the load
   * balancer name starts with the application name, followed by a hyphen)
   *
   * @param applicationName the name of the application
   * @return a collection of functions.
   */
  @Empty
  default Set<? extends Function> getApplicationFunctions(String applicationName) {
    return Collections.emptySet();
  }
}
