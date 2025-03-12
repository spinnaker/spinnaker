package com.netflix.spinnaker.echo.artifacts

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import spock.lang.Specification


class DockerRegistryArtifactExtractorSpec extends Specification {

  ObjectMapper mapper = EchoObjectMapper.getInstance()
  DockerRegistryArtifactExtractor dockerRegistryArtifactExtractor = new DockerRegistryArtifactExtractor(mapper)

  void 'extracts artifact from Docker registry event in official documentation'() {
    given:
    String rawPayload = '''
{
   "events": [
      {
         "id": "asdf-asdf-asdf-asdf-0",
         "timestamp": "2006-01-02T15:04:05Z",
         "action": "push",
         "target": {
            "mediaType": "application/vnd.docker.distribution.manifest.v1+json",
            "length": 1,
            "digest": "sha256:fea8895f450959fa676bcc1df0611ea93823a735a01205fd8622846041d0c7cf",
            "repository": "library/test",
            "url": "http://example.com/v2/library/test/manifests/sha256:c3b3692957d439ac1928219a83fac91e7bf96c153725526874673ae1f2023f8d5",
            "tag": "latest"
         },
         "request": {
            "id": "asdfasdf",
            "addr": "client.local",
            "host": "registrycluster.local",
            "method": "PUT",
            "useragent": "test/0.1"
         },
         "actor": {
            "name": "test-actor"
         },
         "source": {
            "addr": "hostname.local:port"
         }
      },
      {
         "id": "asdf-asdf-asdf-asdf-1",
         "timestamp": "2006-01-02T15:04:05Z",
         "action": "push",
         "target": {
            "mediaType": "application/vnd.docker.container.image.rootfs.diff+x-gtar",
            "length": 2,
            "digest": "sha256:c3b3692957d439ac1928219a83fac91e7bf96c153725526874673ae1f2023f8d5",
            "repository": "library/test",
            "url": "http://example.com/v2/library/test/blobs/sha256:c3b3692957d439ac1928219a83fac91e7bf96c153725526874673ae1f2023f8d5"
         },
         "request": {
            "id": "asdfasdf",
            "addr": "client.local",
            "host": "registrycluster.local",
            "method": "PUT",
            "useragent": "test/0.1"
         },
         "actor": {
            "name": "test-actor"
         },
         "source": {
            "addr": "hostname.local:port"
         }
      },
      {
         "id": "asdf-asdf-asdf-asdf-2",
         "timestamp": "2006-01-02T15:04:05Z",
         "action": "push",
         "target": {
            "mediaType": "application/vnd.docker.container.image.rootfs.diff+x-gtar",
            "length": 3,
            "digest": "sha256:c3b3692957d439ac1928219a83fac91e7bf96c153725526874673ae1f2023f8d5",
            "repository": "library/test",
            "url": "http://example.com/v2/library/test/blobs/sha256:c3b3692957d439ac1928219a83fac91e7bf96c153725526874673ae1f2023f8d5"
         },
         "request": {
            "id": "asdfasdf",
            "addr": "client.local",
            "host": "registrycluster.local",
            "method": "PUT",
            "useragent": "test/0.1"
         },
         "actor": {
            "name": "test-actor"
         },
         "source": {
            "addr": "hostname.local:port"
         }
      }
   ]
}
'''
    Map postedEvent = mapper.readValue(rawPayload, Map)

    when:
    List<Artifact> artifacts = dockerRegistryArtifactExtractor.getArtifacts(
      'dockerregistry',
      postedEvent
    )

    then:
    artifacts.size() == 1
    artifacts.get(0).getVersion() == 'latest'
    artifacts.get(0).getName() == 'registrycluster.local/library/test'
    artifacts.get(0).getReference() == 'registrycluster.local/library/test:latest'
  }

  void 'extracts artifact from Docker registry event with tag'() {
    given:
    String rawPayload = '''
{
  "events": [
    {
      "id": "6fb5fd5c-a2f7-4d96-bd77-6de873a21e5f",
      "timestamp": "2018-03-25T23:25:46.7738079Z",
      "action": "push",
      "target": {
        "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
        "size": 1576,
        "digest": "sha256:2c39235310cfed3fc661bf899a4b79894327220d4947edbf952af8941614feea",
        "length": 1576,
        "repository": "wjoel/gitweb",
        "url": "http://registry.default.svc/v2/wjoel/gitweb/manifests/sha256:2c39235310cfed3fc661bf899a4b79894327220d4947edbf952af8941614feea",
        "tag": "2018-03-26-b1"
      },
      "request": {
        "id": "f0be0c76-bd2c-4422-87f6-1d20b70cf318",
        "addr": "192.168.1.193:59692",
        "host": "registry.default.svc",
        "method": "PUT",
        "useragent": "docker/17.12.1-ce go/go1.9.4 git-commit/7390fc6 kernel/4.14.0-3-amd64 os/linux arch/amd64 UpstreamClient(Docker-Client/17.12.1-ce (linux))"
      },
      "actor": {"":""},
      "source": {
        "addr": "registry-5b74898d77-xm6k6:80",
        "instanceID": "738f8347-a04f-4577-9674-d3a8cdcc18e1"
      }
    }
  ]
}
'''
    Map postedEvent = mapper.readValue(rawPayload, Map)

    when:
    List<Artifact> artifacts = dockerRegistryArtifactExtractor.getArtifacts(
      'dockerregistry',
      postedEvent
    )

    then:
    artifacts.size() == 1
    artifacts.get(0).getVersion() == '2018-03-26-b1'
    artifacts.get(0).getName() == 'registry.default.svc/wjoel/gitweb'
    artifacts.get(0).getReference() == 'registry.default.svc/wjoel/gitweb:2018-03-26-b1'
  }

  void 'extracts artifact from Docker registry event without tag'() {
    given:
    String rawPayload = '''
{
  "events": [
    {
      "id": "6fb5fd5c-a2f7-4d96-bd77-6de873a21e5f",
      "timestamp": "2018-03-25T23:25:46.7738079Z",
      "action": "push",
      "target": {
        "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
        "size": 1576,
        "digest": "sha256:2c39235310cfed3fc661bf899a4b79894327220d4947edbf952af8941614feea",
        "length": 1576,
        "repository": "wjoel/gitweb",
        "url": "http://registry.default.svc/v2/wjoel/gitweb/manifests/sha256:2c39235310cfed3fc661bf899a4b79894327220d4947edbf952af8941614feea"
      },
      "request": {
        "id": "f0be0c76-bd2c-4422-87f6-1d20b70cf318",
        "addr": "192.168.1.193:59692",
        "host": "registry.default.svc",
        "method": "PUT",
        "useragent": "docker/17.12.1-ce go/go1.9.4 git-commit/7390fc6 kernel/4.14.0-3-amd64 os/linux arch/amd64 UpstreamClient(Docker-Client/17.12.1-ce (linux))"
      },
      "actor": {"":""},
      "source": {
        "addr": "registry-5b74898d77-xm6k6:80",
        "instanceID": "738f8347-a04f-4577-9674-d3a8cdcc18e1"
      }
    }
  ]
}
'''
    Map postedEvent = mapper.readValue(rawPayload, Map)

    when:
    List<Artifact> artifacts = dockerRegistryArtifactExtractor.getArtifacts(
      'dockerregistry',
      postedEvent
    )

    then:
    artifacts.size() == 1
    artifacts.get(0).getVersion() == 'sha256:2c39235310cfed3fc661bf899a4b79894327220d4947edbf952af8941614feea'
    artifacts.get(0).getName() == 'registry.default.svc/wjoel/gitweb'
    artifacts.get(0).getReference() == 'registry.default.svc/wjoel/gitweb@sha256:2c39235310cfed3fc661bf899a4b79894327220d4947edbf952af8941614feea'
  }
}
