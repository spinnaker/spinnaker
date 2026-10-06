/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.echo.artifacts

import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import spock.lang.Specification

class GiteaArtifactExtractorSpec extends Specification {
  GiteaArtifactExtractor extractor = new GiteaArtifactExtractor(EchoObjectMapper.getInstance())

  Map pushPayload(String htmlUrl) {
    return [
      after     : "abc123",
      commits   : [
        [added: ["manifests/app.yaml"], modified: ["README.md"]],
        [added: [], modified: ["manifests/app.yaml", "docs/my file.md"]],
      ],
      repository: [html_url: htmlUrl, full_name: "spinnaker/echo"],
    ]
  }

  void "only handles git webhooks from gitea"() {
    expect:
    extractor.handles("git", "gitea")
    !extractor.handles("git", "github")
    !extractor.handles("docker", "gitea")
  }

  void "creates one deduplicated gitea/file artifact per added or modified file"() {
    when:
    def artifacts = extractor.getArtifacts("gitea", pushPayload("https://gitea.example.com/spinnaker/echo"))

    then:
    artifacts*.name as Set == ["manifests/app.yaml", "README.md", "docs/my file.md"] as Set
    artifacts.every { it.type == "gitea/file" && it.version == "abc123" }
    artifacts.find { it.name == "manifests/app.yaml" }.reference ==
      "https://gitea.example.com/api/v1/repos/spinnaker/echo/raw/manifests/app.yaml"
    artifacts.find { it.name == "docs/my file.md" }.reference ==
      "https://gitea.example.com/api/v1/repos/spinnaker/echo/raw/docs/my%20file.md"
  }

  void "keeps the sub-path of a Gitea server that is not served from the root"() {
    when:
    def artifacts = extractor.getArtifacts("gitea", pushPayload("https://example.com/gitea/spinnaker/echo"))

    then:
    artifacts.find { it.name == "README.md" }.reference ==
      "https://example.com/gitea/api/v1/repos/spinnaker/echo/raw/README.md"
  }

  void "returns no artifacts when the API url can not be derived"() {
    expect:
    extractor.getArtifacts("gitea", pushPayload("https://gitea.example.com/somewhere/else")) == []
    extractor.getArtifacts("gitea", [after: "abc"]) == []
  }

  void "ignores file paths that would escape the repository"() {
    given:
    def payload = pushPayload("https://gitea.example.com/spinnaker/echo")
    payload.commits = [[added: ["../../../../user", "a/../../b", "./c", "a//b", "ok/file.yaml"], modified: [""]]]

    expect:
    extractor.getArtifacts("gitea", payload)*.name == ["ok/file.yaml"]
  }

  void "ignores a repository full_name or html_url that is not what Gitea would send"() {
    given:
    def payload = pushPayload(htmlUrl)
    payload.repository.full_name = fullName

    expect:
    extractor.getArtifacts("gitea", payload) == []

    where:
    htmlUrl                                         | fullName
    "https://evil.example.com/x/../../admin?q="     | "x/../../admin?q="
    "https://gitea.example.com/spinnaker/echo"      | "../echo"
    "https://gitea.example.com/spinnaker/echo?x=1"  | "spinnaker/echo?x=1"
    "https://gitea.example.com/spinnaker/echo#frag" | "spinnaker/echo#frag"
    "https://gitea.example.com/a b/echo"            | "a b/echo"
    "file:///spinnaker/echo"                        | "spinnaker/echo"
    "javascript:alert(1)//spinnaker/echo"           | "spinnaker/echo"
    "https://gitea.example.com/spinnaker/echo"      | "spinnaker/echo/extra"
  }
}
