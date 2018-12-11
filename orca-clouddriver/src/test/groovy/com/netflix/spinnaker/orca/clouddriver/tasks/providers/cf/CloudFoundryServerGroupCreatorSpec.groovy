/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf

import com.netflix.spinnaker.orca.pipeline.model.JenkinsTrigger
import spock.lang.Specification

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class CloudFoundryServerGroupCreatorSpec extends Specification {

  def "should get operations when an artifact is specified as the deployable"() {
    given:
    def ctx = [
      application      : "abc",
      account          : "abc",
      region           : "org > space",
      deploymentDetails: [[imageId: "testImageId", zone: "north-pole-1"]],
      artifact         : [type: "artifact", account: "count-von-count", reference: "some-reference"],
      manifest      : [
        type     : "artifact",
        account  : "dracula",
        reference: "https://example.com/mani-pedi.yml"
      ],
      startApplication : true,
    ]
    def stage = stage {
      context.putAll(ctx)
    }

    when:
    def ops = new CloudFoundryServerGroupCreator().getOperations(stage)

    then:
    ops == [
      [
        "createServerGroup": [
          application   : "abc",
          credentials   : "abc",
          manifest      : null,
          region        : "org > space",
          startApplication: true,
          artifact: [
            type     : "artifact",
            account  : "count-von-count",
            reference: "some-reference"
          ],
          manifest      : [
            type     : "artifact",
            account  : "dracula",
            reference: "https://example.com/mani-pedi.yml"
          ],
        ]
      ]
    ]
  }

  def "should get operations when a triggered artifact is specified as the deployable"() {
    given:
    def ctx = [
      application      : "abc",
      account          : "abc",
      region           : "org > space",
      deploymentDetails: [[imageId: "testImageId", zone: "north-pole-1"]],
      artifact         : [type: "trigger", account: "count-von-count", pattern: "that_s.*a.*m.ore.*.jar"],
      manifest      : [
        type     : "artifact",
        account  : "dracula",
        reference: "https://example.com/mani-pedi.yml"
      ],
      startApplication : true,
    ]
    JenkinsTrigger.BuildInfo info = new JenkinsTrigger.BuildInfo(
      "my-name",
      0,
      "https://example.com/",
      [new JenkinsTrigger.JenkinsArtifact("that_s_father_m_oregon_to_you.jar", "sister_path/that_s_father_m_oregon_to_you.jar")],
      [],
      false,
      ""
    )
    JenkinsTrigger trig = new JenkinsTrigger("master", "job", 1, "propertyfile")
    trig.buildInfo = info
    def stage = stage {
      context.putAll(ctx)
      execution.trigger = trig
    }

    when:
    def ops = new CloudFoundryServerGroupCreator().getOperations(stage)

    then:
    ops == [
      [
        "createServerGroup": [
          application   : "abc",
          credentials   : "abc",
          manifest      : [
            type     : "artifact",
            account  : "dracula",
            reference: "https://example.com/mani-pedi.yml"
          ],
          region        : "org > space",
          startApplication: true,
          artifact: [
            type     : "artifact",
            account  : "count-von-count",
            reference: "https://example.com/artifact/sister_path/that_s_father_m_oregon_to_you.jar"
          ]
        ],
      ]
    ]
  }

  def "should get operations when a package artifact is specified as the deployable"() {
    given:
    def ctx = [
      application      : "abc",
      account          : "abc",
      region           : "org > space",
      deploymentDetails: [[imageId: "testImageId", zone: "north-pole-1"]],
      artifact         : [type: "package",
                          cluster: [name: "my-cloister"],
                          serverGroupName: "s-club-7",
                          account: "my-account",
                          region: "saar-region",
                         ],
      startApplication: true,
    ]
    def stage = stage {
      context.putAll(ctx)
    }

    when:
    def ops = new CloudFoundryServerGroupCreator().getOperations(stage)

    then:
    ops == [
      [
        "createServerGroup": [
          application   : "abc",
          credentials   : "abc",
          manifest      : null,
          region        : "org > space",
          startApplication: true,
          artifact: [type: "package",
                           cluster: [name: "my-cloister"],
                           serverGroupName: "s-club-7",
                           account: "my-account",
                           region: "saar-region",
          ]
        ]
      ]
    ]
  }

  def "should get operations when an artifact is specified as the configuration"() {
    given:
    def ctx = [
      application      : "abc",
      account          : "abc",
      region           : "org > space",
      deploymentDetails: [[imageId: "testImageId", zone: "north-pole-1"]],
      artifact         : [type: "artifact", account: "count-von-count", reference: "some-reference"],
      manifest         : [type: "artifact", account: "dracula", reference: "https://example.com/mani-pedi.yml"],
      startApplication : true,
    ]
    def stage = stage {
      context.putAll(ctx)
    }

    when:
    def ops = new CloudFoundryServerGroupCreator().getOperations(stage)

    then:
    ops == [
      [
        "createServerGroup": [
          application   : "abc",
          credentials   : "abc",
          manifest      : [
            type     : "artifact",
            account  : "dracula",
            reference: "https://example.com/mani-pedi.yml"
          ],
          region        : "org > space",
          startApplication: true,
          artifact: [
            type     : "artifact",
            account  : "count-von-count",
            reference: "some-reference"
          ]
        ]
      ]
    ]
  }

  def "should get operations when a triggered manifest regex is specified as the configuration"() {
    given:
    def ctx = [
      application      : "abc",
      account          : "abc",
      region           : "org > space",
      deploymentDetails: [[imageId: "testImageId", zone: "north-pole-1"]],
      artifact         : [type: "artifact", account: "count-von-count", reference: "some-reference"],
      manifest         : [type: "trigger", account: "count-von-count", pattern: ".*.yml"],
      startApplication : true,
    ]
    JenkinsTrigger.BuildInfo info = new JenkinsTrigger.BuildInfo(
      "my-name",
      0,
      "https://example.com/",
      [new JenkinsTrigger.JenkinsArtifact("deploy.yml", "deployment/deploy.yml")],
      [],
      false,
      ""
    )
    JenkinsTrigger trig = new JenkinsTrigger("master", "job", 1, "propertyfile")
    trig.buildInfo = info

    def stage = stage {
      context.putAll(ctx)
      execution.trigger = trig
    }

    when:
    def ops = new CloudFoundryServerGroupCreator().getOperations(stage)

    then:
    ops == [
      [
        "createServerGroup": [
          application      : "abc",
          credentials      : "abc",
          manifest      : [
            type     : "artifact",
            account  : "count-von-count",
            reference: "https://example.com/artifact/deployment/deploy.yml"
          ],
          region           : "org > space",
          startApplication : true,
          artifact: [
            type     : "artifact",
            account  : "count-von-count",
            reference: "some-reference"
          ]
        ],
      ]
    ]
  }

  def "should get operations when a direct attributes are specified as the configuration"() {
    given:
    def ctx = [
      application      : "abc",
      account          : "abc",
      region           : "org > space",
      deploymentDetails: [[imageId: "testImageId", zone: "north-pole-1"]],
      artifact         : [type: "artifact", account: "count-von-count", reference: "some-reference"],
      manifest         : [
        type: "direct",
        memory: "1024M",
        diskQuota: "1024M",
        instances: "1"
      ],
      startApplication : true,
    ]
    def stage = stage {
      context.putAll(ctx)
    }

    when:
    def ops = new CloudFoundryServerGroupCreator().getOperations(stage)

    then:
    ops == [
      [
        "createServerGroup": [
          application      : "abc",
          credentials      : "abc",
          manifest         : [
            type: "direct",
            memory: "1024M",
            diskQuota: "1024M",
            instances: "1"
          ],
          region           : "org > space",
          startApplication : true,
          artifact: [
            type     : "artifact",
            account  : "count-von-count",
            reference: "some-reference"
          ]
        ]
      ]
    ]
  }

}
