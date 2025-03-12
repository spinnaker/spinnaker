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

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers
import com.netflix.spinnaker.halyard.config.model.v1.providers.appengine.AppengineProvider
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsProvider
import com.netflix.spinnaker.halyard.config.model.v1.providers.azure.AzureProvider
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSProvider
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryProvider
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleProvider
import com.netflix.spinnaker.halyard.config.model.v1.providers.huaweicloud.HuaweiCloudProvider
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesProvider
import com.netflix.spinnaker.halyard.config.model.v1.providers.oracle.OracleProvider
import com.netflix.spinnaker.halyard.config.model.v1.providers.tencentcloud.TencentCloudProvider
import spock.lang.Specification
import spock.lang.Unroll

class ProvidersSpec extends Specification {

  @Unroll()
  void "children includes #provider.simpleName"() {

    setup:
    def deploymentEnvironment = new DeploymentEnvironment()
    def deploymentConfiguration = new DeploymentConfiguration()
    deploymentConfiguration.deploymentEnvironment = deploymentEnvironment
    deploymentEnvironment.parent = deploymentConfiguration
    def providers = new Providers()
    providers.parent = deploymentEnvironment
    def iterator = providers.getChildren()

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
        HuaweiCloudProvider,
        KubernetesProvider,
        OracleProvider,
        TencentCloudProvider
    ]
  }
}
