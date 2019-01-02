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

import com.netflix.discovery.DiscoveryClient
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.config.DockerRegistryProperties
import com.netflix.spinnaker.igor.docker.model.DockerRegistryAccounts
import com.netflix.spinnaker.igor.docker.service.TaggedImage
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.DockerEvent
import com.netflix.spinnaker.igor.polling.LockService
import spock.lang.Specification
import spock.lang.Unroll

class DockerMonitorSpec extends Specification {

    def properties = new IgorConfigurationProperties()
    def registry = new NoopRegistry()
    Optional<DiscoveryClient> discoveryClient = Optional.empty()
    Optional<LockService> lockService = Optional.empty()
    def dockerRegistryCache = Mock(DockerRegistryCache)
    def dockerRegistryAccounts = Mock(DockerRegistryAccounts)
    def echoService = Mock(EchoService)
    Optional<DockerRegistryCacheV2KeysMigration> keysMigration = Optional.empty()
    def dockerRegistryProperties = new DockerRegistryProperties(enabled: true, itemUpperThreshold: 5)

    @Unroll
    void 'should only publish events if account has been indexed previously'() {
        given:
        def taggedImage = new TaggedImage(
            tag: "tag",
            account: "account",
            registry: "registry",
            repository: "repository",
            digest: "digest"
        )

        when:
        new DockerMonitor(properties, registry, discoveryClient, lockService, dockerRegistryCache, dockerRegistryAccounts, Optional.of(echoService), Optional.empty(), dockerRegistryProperties)
            .postEvent(cachedImages, taggedImage, "imageId")

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
        createSubject().postEvent(["imageId"] as Set, taggedImage, "imageId")

        then:
        notThrown(NullPointerException)

        where:
        cachedImages    || echoServiceCallCount
        null            || 0
        [] as Set       || 0
        ["job1"] as Set || 1

    }

    void 'should include decorated artifact in the payload'() {
        given:
        def taggedImage = new TaggedImage(
            tag: "tag",
            account: "account",
            registry: "registry",
            repository: "repository",
            digest: "digest"
        )

        when:
        createSubject().postEvent(["job1"] as Set, taggedImage, "imageId")

        then:
        1 * echoService.postEvent({ DockerEvent event ->
            assert event.artifact.version           == taggedImage.tag
            assert event.artifact.name              == taggedImage.repository
            assert event.artifact.type              == "docker"
            assert event.artifact.reference         == "registry/repository:tag"
            assert event.artifact.metadata.registry == taggedImage.registry
            return true
        })

    }

    @Unroll
    void "should update cache if image is not already cached"() {
        given:
        def subject = createSubject()
        Set<String> cachedImages = [
            'prefix:dockerRegistry:v2:account:registry:tag',
            'prefix:dockerRegistry:v2:account:anotherregistry:tag',
        ]

        when:
        def taggedImage = new TaggedImage(tag: tag, account: "account", registry: "registry", repository: "repository", digest: digest)
        def result = subject.getUpdateType(cachedImages, keyFromTaggedImage(taggedImage), taggedImage, trackDigest)

        then:
        dockerRegistryCache.getLastDigest(_, _, _) >> cachedDigest
        assert result.updateCache == updateCache
        assert result.sendEvent == sendEvent

        where:
        tag   | digest    | cachedDigest | trackDigest || updateCache | sendEvent
        "tag" | "digest"  | "digest"     | false       || false       | false
        "new" | "digest"  | "digest"     | false       || true        | true
        "tag" | "digest2" | "digest"     | true        || true        | true
        "tag" | null      | "digest"     | true        || false       | false
        "tag" | "digest"  | null         | true        || true        | false
        "tag" | null      | null         | true        || false       | false
        "tag" | null      | "digest"     | false       || false       | false
        "tag" | "digest"  | null         | false       || false       | false
        "tag" | null      | null         | false       || false       | false
    }

    @Unroll
    def "should retrieve itemUpperThreshold #upperThreshold for #partition with fallback value #fallbackThreshold from igor properties"() {
        given:
        def subject = createSubject()
        dockerRegistryProperties.setItemUpperThreshold(fallbackThreshold)

        when:
        def result = subject.getPartitionUpperThreshold(partition)

        then:
        1 * dockerRegistryAccounts.accounts >> [
          [name: 'partition1', itemUpperThreshold: 10],
          [name: 'partition2', itemUpperThreshold: 20],
          [name: 'partition3']
        ]
        assert result == upperThreshold

        where:
        partition    | fallbackThreshold || upperThreshold
        'partition1' | 100               || 10
        'partition1' | null              || 10
        'partition2' | 100               || 20
        'partition3' | 100               || 100
        'partition4' | 100               || 100
        'partition4' | null              || null
    }

    private DockerMonitor createSubject() {
        return new DockerMonitor(properties, registry, discoveryClient, lockService, dockerRegistryCache, dockerRegistryAccounts, Optional.of(echoService), keysMigration, dockerRegistryProperties)
    }

    private static String keyFromTaggedImage(TaggedImage taggedImage) {
        return new DockerRegistryV2Key("prefix", DockerRegistryCache.ID, taggedImage.account, taggedImage.registry, taggedImage.tag).toString()
    }
}
