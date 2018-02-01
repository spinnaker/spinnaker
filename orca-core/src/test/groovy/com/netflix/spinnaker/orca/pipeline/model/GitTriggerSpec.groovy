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

class GitTriggerSpec extends AbstractTriggerSpec<GitTrigger> {
  @Override
  protected Class<GitTrigger> getType() {
    GitTrigger
  }

  @Override
  protected String getTriggerJson() {
    return '''
{
  "project": "CAD",
  "source": "stash",
  "type": "git",
  "job": "CAD-BLADERUNNER-RELEASE-SPINNAKER-TRIGGER",
  "branch": "bladerunner-release",
  "parameters": {
    "ALCHEMIST_GIT_BRANCH": "release",
    "CADMIUM_GIT_BRANCH": "bladerunner-release",
    "ALCHEMIST_JIRA_VERIFICATIONS": "false",
    "ALCHEMIST_DRY_RUN": "false",
    "TEST_FILTER": "/UX|FUNCTIONAL/i",
    "TEST_FILTER_SAFARI": "/UX/i",
    "ALCHEMIST_VERSION": "latest",
    "IGNORE_PLATFORMS_CHROME": "chromeos,linux",
    "IGNORE_PLATFORMS_SAFARI": "none",
    "IGNORE_PLATFORMS_FIREFOX": "linux",
    "IGNORE_PLATFORMS_MSIE": "none",
    "IGNORE_PLATFORMS_CHROMECAST": "none"
  },
  "user": "[anonymous]",
  "enabled": true,
  "slug": "Main",
  "hash": "adb2554e870ae86622f05de2a15f4539030d87a7",
  "master": "cbp"
}
'''
  }
}
