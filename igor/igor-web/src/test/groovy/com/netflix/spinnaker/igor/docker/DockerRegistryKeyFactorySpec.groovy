package com.netflix.spinnaker.igor.docker

import com.netflix.spinnaker.igor.IgorConfigurationProperties
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class DockerRegistryKeyFactorySpec extends Specification {
    @Shared
    def properties = new IgorConfigurationProperties()

    @Shared
    def keyFactory = new DockerRegistryKeyFactory(properties)

    void 'encoding and decoding should be transparent'() {
        def prefix = properties.getSpinnaker().getJedis().getPrefix()

        when:
        def originalKey = new DockerRegistryV1Key(prefix, DockerRegistryKeyFactory.ID, "account", "registry", "repository", "tag")
        def decodedKey = keyFactory.parseV1Key(originalKey.toString())

        then:
        decodedKey == originalKey
    }

    @Unroll
    void 'repository url #repository can be reconstituted'() {
        def prefix = properties.getSpinnaker().getJedis().getPrefix()

        when:
        def originalKey = new DockerRegistryV1Key(prefix, DockerRegistryKeyFactory.ID, "account", "registry", repository, "tag")
        def decodedKey = keyFactory.parseV1Key(originalKey.toString())

        then:
        decodedKey.getRepository() == repository

        where:
        repository << [
            "registry.main.us-east-1.prod.example.net",
            "registry.main.us-east-1.prod.example.net:7002",
            "https://registry.main.us-east-1.prod.example.net:7002"
        ]
    }

    @Unroll
    void 'should be able to convert from v1 to v2 with includeRepository=#includeRepository'() {
        def prefix = properties.getSpinnaker().getJedis().getPrefix()
        def v1KeyStr = new DockerRegistryV1Key(prefix, DockerRegistryKeyFactory.ID, "account", "registry", "repository", "tag").toString()

        when: // simulate having to parse an existing key string
        def v1Key = keyFactory.parseV1Key(v1KeyStr, includeRepository)
        def v2Key = keyFactory.convert(v1Key)

        then:
        v1Key.getPrefix() == v2Key.getPrefix()
        v1Key.getId() == v2Key.getId()
        v1Key.getAccount() == v2Key.getAccount()
        v1Key.getRegistry() == v2Key.getRegistry()
        v1Key.getTag() == v2Key.getTag()

        where:
        includeRepository << [true, false]
    }
}
