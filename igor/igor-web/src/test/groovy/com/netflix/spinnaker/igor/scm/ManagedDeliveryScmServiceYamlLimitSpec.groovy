/*
 * Copyright 2024 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.igor.scm

import com.netflix.spinnaker.igor.config.ManagedDeliveryConfigProperties
import com.netflix.spinnaker.igor.scm.stash.client.StashClient
import com.netflix.spinnaker.igor.scm.stash.client.StashMaster
import com.netflix.spinnaker.igor.scm.stash.client.model.TextLinesResponse
import com.netflix.spinnaker.kork.yaml.YamlHelper
import com.netflix.spinnaker.kork.yaml.YamlParserProperties
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.igor.scm.stash.client.StashMaster.DEFAULT_PAGED_RESPONSE_LIMIT

/**
 * Verifies that ManagedDeliveryScmService respects the YAML code-point limit configured
 * via the injected YamlHelper, allowing operators to control maximum YAML document size.
 */
class ManagedDeliveryScmServiceYamlLimitSpec extends Specification {

  static final int CODE_POINT_LIMIT = 200

  @Subject
  ManagedDeliveryScmService service

  StashClient client = Mock(StashClient)

  void setup() {
    YamlParserProperties props = new YamlParserProperties()
    props.setCodePointLimit(CODE_POINT_LIMIT)

    service = new ManagedDeliveryScmService(
      Optional.of(new ManagedDeliveryConfigProperties(manifestBasePath: ".spinnaker")),
      Optional.of(new StashMaster(stashClient: client, baseUrl: "https://stash.example.com")),
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      new YamlHelper(props)
    )
  }

  void 'getDeliveryConfigManifest throws when YAML content exceeds configured code-point limit'() {
    given:
    String largeYaml = "value: " + ("a" * (CODE_POINT_LIMIT + 50))
    1 * client.getTextFileContents(project, repo, ".spinnaker/manifest.yml", ref, DEFAULT_PAGED_RESPONSE_LIMIT, 0) >>
      Calls.response(new TextLinesResponse(lines: [[text: largeYaml]], size: 1, isLastPage: true))

    when:
    service.getDeliveryConfigManifest(scmType, project, repo, null, 'manifest.yml', ref, false)

    then:
    thrown(IllegalArgumentException)

    where:
    scmType = 'stash'
    project = 'proj'
    repo    = 'repo'
    ref     = 'refs/heads/master'
  }

  void 'getDeliveryConfigManifest succeeds when YAML content is within configured code-point limit'() {
    given:
    1 * client.getTextFileContents(project, repo, ".spinnaker/manifest.yml", ref, DEFAULT_PAGED_RESPONSE_LIMIT, 0) >>
      Calls.response(new TextLinesResponse(lines: [[text: "apiVersion: foo\nkind: Bar"]], size: 2, isLastPage: true))

    when:
    Map<String, Object> result = service.getDeliveryConfigManifest(scmType, project, repo, null, 'manifest.yml', ref, false)

    then:
    result != null
    result['apiVersion'] == 'foo'

    where:
    scmType = 'stash'
    project = 'proj'
    repo    = 'repo'
    ref     = 'refs/heads/master'
  }
}
