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
package com.netflix.spinnaker.front50.model.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.front50.api.model.Timestamped
import com.netflix.spinnaker.front50.jackson.mixins.TimestampedMixins
import spock.lang.Specification

class ApplicationModelSpec extends Specification {

  void 'should support adding dynamic properties'() {
    def application = new Application()
    application.pdApiKey = ''
    application.owner = null
    application.repoProjectKey = "project-key"
    application.repoSlug = "repo"
    application.repoType = "github"

    def props = application.details()

    expect:
    props != null
    props.size() == 5
    props['pdApiKey'] == ''
    props['owner'] == null
    props['repoProjectKey'] == "project-key"
    props['repoSlug'] == "repo"
    props['repoType'] == 'github'
  }

  void 'should convert cloudProviders to a String if it is a list'() {
    def listApp = new Application(cloudProviders: ['a','b'])
    def normalApp = new Application(cloudProviders: 'a,b,c')

    expect:
    listApp.cloudProviders == 'a,b'
    normalApp.cloudProviders == 'a,b,c'
  }

  void 'ignores correct timestamped properties when serializing Application to JSON'() {

    given:
    def application = new Application()
    application.setCreatedAt(new Long(1))
    application.setLastModifiedBy("foo")
    application.setLastModified(new Long(1))

    ObjectMapper mapper = new ObjectMapper().addMixIn(Timestamped.class, TimestampedMixins.class)
    String appJSON = mapper.writeValueAsString(application)

    expect:
    appJSON == '{"name":null,"description":null,"email":null,"updateTs":"1","createTs":"1","lastModifiedBy":"foo","cloudProviders":null,"trafficGuards":[]}'
  }
}
