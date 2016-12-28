/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.igor.docker

import com.netflix.spinnaker.igor.docker.service.TaggedImage
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.DockerEvent
import spock.lang.Specification
import spock.lang.Unroll

class DockerMonitorSpec extends Specification {
    @Unroll
    void 'should only publish events if account has been indexed previously'() {
        def echoService = Mock(EchoService)
        def taggedImage = new TaggedImage(
            tag: "tag",
            account: "account",
            registry: "registry",
            repository: "repository",
            digest: "digest"
        )

        when:
        DockerMonitor.postEvent(
            echoService, cachedImages, taggedImage, "imageId"
        )

        then:
        echoServiceCallCount * echoService.postEvent({ DockerEvent event ->
            assert event.content.tag == taggedImage.tag
            assert event.content.account == taggedImage.account
            assert event.content.registry == taggedImage.registry
            assert event.content.repository == taggedImage.repository
            assert event.content.digest == taggedImage.digest
            return true
        })

        when: "should short circuit if `echoService` is not available"
        DockerMonitor.postEvent(
            null, ["imageId"], taggedImage, "imageId"
        )

        then:
        notThrown(NullPointerException)

        where:
        cachedImages || echoServiceCallCount
        null         || 0
        []           || 0
        ["job1"]     || 1

    }
}
