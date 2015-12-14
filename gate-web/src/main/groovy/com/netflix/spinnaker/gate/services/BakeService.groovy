/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.internal.RoscoService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Slf4j
@Component
@ConfigurationProperties('services.rosco.defaults')
class BakeService {

  @Autowired(required = false)
  RoscoService roscoService

  // Default bake options from configuration.
  BakeOptions bakeOptions

  def bakeOptions() {
    roscoService ? roscoService.bakeOptions() : [bakeOptions]
  }

  def bakeOptions(String cloudProvider) {
    if (roscoService) {
      return roscoService.bakeOptions(cloudProvider)
    } else if (cloudProvider == bakeOptions.cloudProvider) {
      return bakeOptions
    }
    throw new IllegalArgumentException("Bake options for cloud provider ${cloudProvider} not found")
  }

  static class BakeOptions {
    String cloudProvider
    List<BaseImage> baseImages
  }

  static class BaseImage {
    String id
    String shortDescription
    String detailedDescription
    String packageType
  }
}
