/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.model

class JenkinsTriggerSpec extends AbstractTriggerSpec<JenkinsTrigger> {
  @Override
  protected Class<JenkinsTrigger> getType() {
    return JenkinsTrigger
  }

  @Override
  protected String getTriggerJson() {
    return '''
{
  "propertyFile": "",
  "buildInfo": {
    "building": false,
    "fullDisplayName": "SPINNAKER-package-orca #1509",
    "name": "SPINNAKER-package-orca",
    "number": 1509,
    "duration": 124941,
    "timestamp": "1513230062314",
    "result": "SUCCESS",
    "artifacts": [
      {
        "fileName": "orca.properties",
        "relativePath": "repo/build/manifest/orca.properties"
      },
      {
        "fileName": "dependencies.txt",
        "relativePath": "repo/orca-netflix/build/reports/project/dependencies.txt"
      },
      {
        "fileName": "properties.txt",
        "relativePath": "repo/orca-netflix/build/reports/project/properties.txt"
      },
      {
        "fileName": "com.netflix.spinnaker.orca.builds.tasks.BeginBuildTaskTest.html",
        "relativePath": "repo/orca-netflix/build/reports/tests/classes/com.netflix.spinnaker.orca.builds.tasks.BeginBuildTaskTest.html"
      },
      {
        "fileName": "com.netflix.spinnaker.orca.chap.tasks.MonitorChapTaskTest.html",
        "relativePath": "repo/orca-netflix/build/reports/tests/classes/com.netflix.spinnaker.orca.chap.tasks.MonitorChapTaskTest.html"
      },
      {
        "fileName": "com.netflix.spinnaker.orca.configbin.UpsertConfigBinTaskSpec.html",
        "relativePath": "repo/orca-netflix/build/reports/tests/classes/com.netflix.spinnaker.orca.configbin.UpsertConfigBinTaskSpec.html"
      },
      {
        "fileName": "com.netflix.spinnaker.orca.gutenberg.tasks.GutenbergFileUploadTaskSpec.html",
        "relativePath": "repo/orca-netflix/build/reports/tests/classes/com.netflix.spinnaker.orca.gutenberg.tasks.GutenbergFileUploadTaskSpec.html"
      },
      {
        "fileName": "com.netflix.spinnaker.orca.gutenberg.tasks.GutenbergPublishTaskSpec.html",
        "relativePath": "repo/orca-netflix/build/reports/tests/classes/com.netflix.spinnaker.orca.gutenberg.tasks.GutenbergPublishTaskSpec.html"
      },
      {
        "fileName": "com.netflix.spinnaker.orca.mahe.cleanup.FastPropertyCleanupListenerSpec.html",
        "relativePath": "repo/orca-netflix/build/reports/tests/classes/com.netflix.spinnaker.orca.mahe.cleanup.FastPropertyCleanupListenerSpec.html"
      },
      {
        "fileName": "com.netflix.spinnaker.orca.mahe.cleanup.FastPropertyRollbackSupportSpec.html",
        "relativePath": "repo/orca-netflix/build/reports/tests/classes/com.netflix.spinnaker.orca.mahe.cleanup.FastPropertyRollbackSupportSpec.html"
      },
      {
        "fileName": "com.netflix.spinnaker.orca.mahe.tasks.CreatePropertiesTaskSpec.html",
        "relativePath": "repo/orca-netflix/build/reports/tests/classes/com.netflix.spinnaker.orca.mahe.tasks.CreatePropertiesTaskSpec.html"
      },
      {
        "fileName": "com.netflix.spinnaker.orca.mahe.tasks.DeletePropertiesTaskSpec.html",
        "relativePath": "repo/orca-netflix/build/reports/tests/classes/com.netflix.spinnaker.orca.mahe.tasks.DeletePropertiesTaskSpec.html"
      },
      {
        "fileName": "com.netflix.spinnaker.orca.mahe.tasks.MonitorPropertiesTaskSpec.html",
        "relativePath": "repo/orca-netflix/build/reports/tests/classes/com.netflix.spinnaker.orca.mahe.tasks.MonitorPropertiesTaskSpec.html"
      },
      {
        "fileName": "base-style.css",
        "relativePath": "repo/orca-netflix/build/reports/tests/css/base-style.css"
      },
      {
        "fileName": "style.css",
        "relativePath": "repo/orca-netflix/build/reports/tests/css/style.css"
      },
      {
        "fileName": "index.html",
        "relativePath": "repo/orca-netflix/build/reports/tests/index.html"
      },
      {
        "fileName": "report.js",
        "relativePath": "repo/orca-netflix/build/reports/tests/js/report.js"
      },
      {
        "fileName": "com.netflix.spinnaker.orca.builds.tasks.html",
        "relativePath": "repo/orca-netflix/build/reports/tests/packages/com.netflix.spinnaker.orca.builds.tasks.html"
      },
      {
        "fileName": "com.netflix.spinnaker.orca.chap.tasks.html",
        "relativePath": "repo/orca-netflix/build/reports/tests/packages/com.netflix.spinnaker.orca.chap.tasks.html"
      },
      {
        "fileName": "com.netflix.spinnaker.orca.configbin.html",
        "relativePath": "repo/orca-netflix/build/reports/tests/packages/com.netflix.spinnaker.orca.configbin.html"
      },
      {
        "fileName": "com.netflix.spinnaker.orca.gutenberg.tasks.html",
        "relativePath": "repo/orca-netflix/build/reports/tests/packages/com.netflix.spinnaker.orca.gutenberg.tasks.html"
      },
      {
        "fileName": "com.netflix.spinnaker.orca.mahe.cleanup.html",
        "relativePath": "repo/orca-netflix/build/reports/tests/packages/com.netflix.spinnaker.orca.mahe.cleanup.html"
      },
      {
        "fileName": "com.netflix.spinnaker.orca.mahe.tasks.html",
        "relativePath": "repo/orca-netflix/build/reports/tests/packages/com.netflix.spinnaker.orca.mahe.tasks.html"
      },
      {
        "fileName": "orca_1.1370.0-h1509.126cade_all.deb",
        "relativePath": "repo/orca-package/build/distributions/orca_1.1370.0-h1509.126cade_all.deb"
      },
      {
        "fileName": "dependencies.txt",
        "relativePath": "repo/orca-package/build/reports/project/dependencies.txt"
      },
      {
        "fileName": "properties.txt",
        "relativePath": "repo/orca-package/build/reports/project/properties.txt"
      }
    ],
    "url": "https://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-orca/1509/",
    "scm": [
      {
        "name": "origin/master",
        "branch": "master",
        "sha1": "126cadeadf1dd7f202c320a98c3b7f1566708a49"
      }
    ]
  },
  "type": "jenkins",
  "job": "SPINNAKER-package-orca",
  "buildNumber": 1509,
  "parameters": {},
  "user": "[anonymous]",
  "enabled": true,
  "master": "spinnaker"
}
'''
  }

  def "can parse a Travis CI trigger"() {
    given:
    def travisTrigger = mapper.readValue(triggerJson, Map)
    travisTrigger.type = "travis"

    when:
    def trigger = mapper.convertValue(travisTrigger, Trigger)

    then:
    noExceptionThrown()

    and:
    trigger.type == "travis"
    trigger instanceof TravisTrigger
  }
}
