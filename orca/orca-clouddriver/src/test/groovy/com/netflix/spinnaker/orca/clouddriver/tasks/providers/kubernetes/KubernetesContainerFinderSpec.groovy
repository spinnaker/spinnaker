/*
 * Copyright 2016 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.kubernetes

import spock.lang.Specification

class KubernetesContainerFinderSpec extends Specification {

  def 'Should successfully parse a fully qualified docker container name'() {
    given:
      def tag = "latest"
      def repository = "myTeam/myImage"
      def registry = "containers.docker.io"
      def fullName = "${registry}/${repository}:${tag}"

    when:
      Map<String, String> nameParts = KubernetesContainerFinder.parseContainerPartsFrom(fullName)

    then:
      nameParts.repository == repository
      nameParts.registry == registry
      nameParts.tag == tag
  }

  def 'Should successfully parse a container with no tag'() {
    given:
      def tag = "latest"
      def repository = "myTeam/myImage"
      def registry = "containers.docker.io"
      def fullName = "${registry}/${repository}"

    when:
      Map<String, String> nameParts = KubernetesContainerFinder.parseContainerPartsFrom(fullName)

    then:
      nameParts.repository == repository
      nameParts.tag == tag
      nameParts.registry == registry
  }

  def 'Should fail to parse a container with no registry'() {
    given:
      def tag = "latest"
      def repository = "myTeam/myImage"
      def fullName = "${repository}:${tag}"
    when:
      KubernetesContainerFinder.parseContainerPartsFrom(fullName)
    then:
      IllegalStateException ex = thrown()
  }
}
