/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.docker.registry.provider.agent

import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.clouddriver.docker.registry.DockerRegistryCloudProvider
import com.netflix.spinnaker.clouddriver.docker.registry.cache.Keys
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryCredentials
import groovy.util.logging.Slf4j

import static java.util.Collections.unmodifiableSet

@Slf4j
class DockerRegistryHelmOciCachingAgent extends AbstractDockerRegistryCachingAgent {
  static final Set<AgentDataType> types = unmodifiableSet([
    AgentDataType.Authority.AUTHORITATIVE.forType(Keys.Namespace.TAGGED_HELM_OCI_IMAGE.ns),
    AgentDataType.Authority.AUTHORITATIVE.forType(Keys.Namespace.IMAGE_ID.ns)
  ] as Set)

  DockerRegistryHelmOciCachingAgent(DockerRegistryCloudProvider dockerRegistryCloudProvider,
                                    String accountName,
                                    DockerRegistryCredentials credentials,
                                    int index,
                                    int threadCount,
                                    Long intervalSecs,
                                    String registry) {
    super(dockerRegistryCloudProvider, accountName, credentials, index, threadCount, intervalSecs, registry)
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  protected List<String> getRepositories() {
    return credentials.helmOciRepositories
  }

  @Override
  protected String getTaggedImageKey(String account, String repository, String tag) {
    return Keys.getHelmOciTaggedImageKey(account, repository, tag)
  }

  @Override
  protected Keys.Namespace getTaggedImageNamespace() {
    return Keys.Namespace.TAGGED_HELM_OCI_IMAGE
  }
}
