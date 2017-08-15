/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1

import com.netflix.spinnaker.halyard.config.model.v1.node.Providers
import com.netflix.spinnaker.halyard.config.model.v1.providers.appengine.AppengineProvider
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsProvider
import com.netflix.spinnaker.halyard.config.model.v1.providers.azure.AzureProvider
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSProvider
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryProvider
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleProvider
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesProvider
import com.netflix.spinnaker.halyard.config.model.v1.providers.openstack.OpenstackProvider
import com.netflix.spinnaker.halyard.config.model.v1.providers.oraclebmcs.OracleBMCSProvider
import spock.lang.Specification
import spock.lang.Unroll

class ProvidersSpec extends Specification {

  @Unroll()
  void "children includes #provider.simpleName"() {

    setup:
    def iterator = new Providers().getChildren()

    when:
    List actualProviders = []
    def child
    while (child = iterator.next) {
      actualProviders << child.class
    }

    then:
    actualProviders.contains(provider)

    where:
    provider << [
        AppengineProvider,
        AwsProvider,
        AzureProvider,
        DCOSProvider,
        DockerRegistryProvider,
        GoogleProvider,
        KubernetesProvider,
        OracleBMCSProvider,
        OpenstackProvider
    ]
  }
}
