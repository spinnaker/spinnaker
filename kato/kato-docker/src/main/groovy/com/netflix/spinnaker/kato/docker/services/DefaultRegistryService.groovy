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

package com.netflix.spinnaker.kato.docker.services

import com.netflix.spinnaker.kato.docker.model.Deployable
import com.netflix.spinnaker.kato.docker.model.Image
import com.netflix.spinnaker.kato.docker.security.Docker
import groovy.json.JsonSlurper
import org.springframework.stereotype.Service

@Service
class DefaultRegistryService implements RegistryService {
  static JsonSlurper jsonSlurper = new JsonSlurper()

  @Override
  Image getImage(Docker docker, String application, String version) {
    getDeployable(docker, application).images?.find { it.version == version }
  }

  private Deployable getDeployable(Docker docker, String name) {
    getDeployables(docker).find { it.name == name }
  }

  private Set<Deployable> getDeployables(Docker docker) {
    def body = "${docker.registry}/v1/search".toURL().text
    def json = jsonSlurper.parseText(body) as Map
    def results = []
    for (result in json.results) {
      def (String library, String repoName) = result.name.split('/')
      results << getDeployable(docker.registry, library, repoName)
    }
    results
  }

  private static Deployable getDeployable(String registryUrl, String library, String name) {
    def deployable = new Deployable(library: library, name: name, images: [])

    def detailsBody = "${registryUrl}/v1/repositories/${library}/${name}/tags".toURL().text
    def detailsJson = jsonSlurper.parseText(detailsBody)
    for (Map.Entry<String, String> entry in detailsJson) {
      def version = entry.key
      def id = entry.value
      def image = new Image(id: id, version: version)
      deployable.images << image
    }
    deployable
  }
}
