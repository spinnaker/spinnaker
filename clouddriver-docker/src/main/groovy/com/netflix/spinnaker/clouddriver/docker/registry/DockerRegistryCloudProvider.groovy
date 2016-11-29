/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.docker.registry

import com.netflix.spinnaker.clouddriver.core.CloudProvider
import org.springframework.stereotype.Component

import java.lang.annotation.Annotation

/**
 * DockerRegistry declaration as a {@link CloudProvider}.
 * Note! This is not a Docker Remote implementation (meaning, this will not be used to deploy docker containers).
 *       Instead, the intent is to provide an interface to query for docker tags hosted by the supplied docker registry.
 */
@Component
class DockerRegistryCloudProvider implements CloudProvider {
  final static String DOCKER_REGISTRY = "dockerRegistry"
  final String id = DOCKER_REGISTRY
  final String displayName = "Docker Registry"
  // The docker registry is only used for caching, so none of the op/ endpoints will be hit.
  final Class<Annotation> operationAnnotationType = Annotation
}
