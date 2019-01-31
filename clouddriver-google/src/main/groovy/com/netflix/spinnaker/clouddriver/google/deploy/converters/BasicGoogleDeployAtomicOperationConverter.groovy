/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.converters

import com.netflix.spinnaker.clouddriver.deploy.DeployAtomicOperation
import com.netflix.spinnaker.clouddriver.google.GoogleOperation
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@GoogleOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("basicGoogleDeployDescription")
@Slf4j
class BasicGoogleDeployAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  AtomicOperation convertOperation(Map input) {
    new DeployAtomicOperation(convertDescription(input))
  }

  BasicGoogleDeployDescription convertDescription(Map input) {
    // Note: Deploy descriptions are stored in pipelines such that namedPort ports are cast to doubles. This is a bug.
    // As a result, pipelines in the wild have namedPort ports stored as doubles.
    // If a pipeline containing a deploy is executed with a trigger (e.g. a cron trigger), the description converter
    // chokes and the pipeline fails. The next block handles double ports for namedPorts gracefully.
    def namedPorts = input?.loadBalancingPolicy?.namedPorts
    if (namedPorts && namedPorts.any { np -> np.port instanceof Double }) {
      log.warn("Deploy description contained named ports with ports of type Double." +
        " Converting Double port value to Integer and continuing deploy.")
      namedPorts.each { np ->
        if (np.port instanceof Double) {
          np.port = new Integer((np.port as Double).intValue())
        }
      }
    }

    def acceleratorConfigs = input?.acceleratorConfigs;
    if (acceleratorConfigs && !acceleratorConfigs.isEmpty()) {
      input.acceleratorConfigs = acceleratorConfigs.collect {
        [
          acceleratorType: it.acceleratorType,
          acceleratorCount: new Integer((it.acceleratorCount as Double).intValue())
        ]
      }
    }

    GoogleAtomicOperationConverterHelper.convertDescription(input, this, BasicGoogleDeployDescription)
  }
}
