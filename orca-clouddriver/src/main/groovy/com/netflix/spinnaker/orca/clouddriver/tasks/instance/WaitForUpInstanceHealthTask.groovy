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

package com.netflix.spinnaker.orca.clouddriver.tasks.instance

import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import groovy.transform.CompileStatic
import org.springframework.stereotype.Component

@Component
@CompileStatic
class WaitForUpInstanceHealthTask extends AbstractWaitForInstanceHealthChangeTask {
  @Override
  boolean hasSucceeded(Map instance, Collection<String> interestedHealthProviderNames) {
    HealthHelper.someAreUpAndNoneAreDownOrStarting(instance, interestedHealthProviderNames)
  }
}
