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

package com.netflix.spinnaker.oort.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.oort.applications.Application
import com.netflix.spinnaker.oort.applications.ApplicationProvider
import com.netflix.spinnaker.oort.clusters.Cluster
import com.netflix.spinnaker.oort.clusters.ClusterProvider
import com.netflix.spinnaker.oort.clusters.Clusters
import com.netflix.spinnaker.oort.clusters.ServerGroup
import org.springframework.context.MessageSource
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

class ClusterControllerSpec extends Specification {

  void "cluster list is resolved from Application rich model"() {
    setup:
    def appProvider = Mock(ApplicationProvider)
    def mvc = MockMvcBuilders.standaloneSetup(new ClusterController(applicationProviders: [appProvider])).setMessageConverters(new MappingJackson2HttpMessageConverter()).build()
    def cluster = Mock(Cluster)
    def clusters = Mock(Clusters)

    when:
    mvc.perform(MockMvcRequestBuilders.get("/applications/foo/clusters/test")).andReturn()

    then:
    1 * appProvider.get("foo") >> {
      def app = Mock(Application)
      app.getName() >> "foo"
      app.getClusters("test") >> clusters
      app
    }
    1 * clusters.list() >> [cluster]
  }

  void "clusters resolve by zone when requested"() {
    setup:
    def clusterProvider = Mock(ClusterProvider)
    def mvc = MockMvcBuilders.standaloneSetup(new ClusterController(clusterProviders: [clusterProvider])).setMessageConverters(new MappingJackson2HttpMessageConverter()).build()

    when:
    mvc.perform(MockMvcRequestBuilders.get("/applications/foo/clusters/test/app-stack")).andReturn()

    then:
    1 * clusterProvider.getByName("foo", "test", "app-stack")

    when:
    mvc.perform(MockMvcRequestBuilders.get("/applications/foo/clusters/test/app-stack?zone=us-east-1")).andReturn()

    then:
    1 * clusterProvider.getByNameAndZone("foo", "test", "app-stack", "us-east-1")
  }

  void "requesting server groups filters from cluster"() {
    setup:
    def clusterProvider = Mock(ClusterProvider)
    def mvc = MockMvcBuilders.standaloneSetup(new ClusterController(clusterProviders: [clusterProvider])).setMessageConverters(new MappingJackson2HttpMessageConverter()).build()
    def cluster = Mock(Cluster)
    def serverGroup = Mock(ServerGroup)
    cluster.getServerGroups() >> [serverGroup]
    serverGroup.getName() >> "app-stack-v000"

    when:
    mvc.perform(MockMvcRequestBuilders.get("/applications/foo/clusters/test/app-stack/serverGroups/app-stack-v000")).andReturn()

    then:
    1 * clusterProvider.getByName("foo", "test", "app-stack") >> [cluster]
  }

  void "requesting server groups by zone filters from server group list"() {
    setup:
    def clusterProvider = Mock(ClusterProvider)
    def mvc = MockMvcBuilders.standaloneSetup(new ClusterController(clusterProviders: [clusterProvider])).setMessageConverters(new MappingJackson2HttpMessageConverter()).build()
    def cluster = Mock(Cluster)
    def serverGroup = Mock(ServerGroup)
    cluster.getServerGroups() >> [serverGroup]
    serverGroup.getName() >> "app-stack-v000"

    when:
    mvc.perform(MockMvcRequestBuilders.get("/applications/foo/clusters/test/app-stack/serverGroups/app-stack-v000/us-east-1")).andReturn()

    then:
    1 * clusterProvider.getByNameAndZone("foo", "test", "app-stack", "us-east-1") >> [cluster]
  }

  void "request for missing server group returns meaningful response"() {
    setup:
    def objectMapper = new ObjectMapper()
    def clusterProvider = Mock(ClusterProvider)
    def mvc = MockMvcBuilders.standaloneSetup(new ClusterController(clusterProviders: [clusterProvider], messageSource: Mock(MessageSource)))
      .setMessageConverters(new MappingJackson2HttpMessageConverter()).build()
    def cluster = Mock(Cluster)

    when:
    def result = mvc.perform(MockMvcRequestBuilders.get("/applications/foo/clusters/test/app-stack/serverGroups/app-stack-v000")).andReturn()
    def resp = objectMapper.readValue(result.response.contentAsString, Map)

    then:
    1 * clusterProvider.getByName("foo", "test", "app-stack") >> [cluster]
    result.response.status == 404
    resp.error == "Server group not found"
  }
}
