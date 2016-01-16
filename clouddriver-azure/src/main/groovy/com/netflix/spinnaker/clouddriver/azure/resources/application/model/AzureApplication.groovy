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

package com.netflix.spinnaker.clouddriver.azure.resources.application.model

import com.netflix.spinnaker.clouddriver.model.Application
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
@EqualsAndHashCode(includes = ["name"])
class AzureApplication implements Application, Serializable {
  String name
  Map<String, Set<String>> clusterNames = Collections.synchronizedMap(new HashMap<String, Set<String>>())
  Map<String, String> attributes = Collections.synchronizedMap(new HashMap<String, String>())

  AzureApplication() {
    log.info("Constructor....AzureApplication")
  }
}
