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


package com.netflix.spinnaker.orca.igor

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class BuildArtifactFilter {
  static final String MAX_ARTIFACTS_PROP = BuildArtifactFilter.simpleName + ".maxArtifacts"
  static final int MAX_ARTIFACTS_DEFAULT = 20

  static final String PREFERRED_ARTIFACTS_PROP = BuildArtifactFilter.simpleName + ".preferredArtifacts"
  static final String PREFERRED_ARTIFACTS_DEFAULT = ['deb', 'rpm', 'properties', 'yml', 'json', 'xml', 'html', 'txt', 'nupkg'].join(',')

  @Autowired
  Environment environment

  private int getMaxArtifacts() {
    environment.getProperty(MAX_ARTIFACTS_PROP, Integer, MAX_ARTIFACTS_DEFAULT)
  }

  private List<String> getPreferredArtifacts() {
    environment.getProperty(PREFERRED_ARTIFACTS_PROP, String, PREFERRED_ARTIFACTS_DEFAULT).split(',')
  }

  public List<Map> filterArtifacts(List<Map> artifacts) {
    if (!artifacts) {
      return artifacts
    }

    final int maxArtifacts = getMaxArtifacts()
    final List<String> preferred = getPreferredArtifacts()

    if (artifacts.size() < maxArtifacts) {
      return artifacts
    }

    def ext = { String filename ->
      if (!filename) {
        return null
      }
      int idx = filename.lastIndexOf('.')
      if (idx == -1) {
        return null
      }
      filename.substring(idx + 1).toLowerCase()
    }

    def pri = { String extension ->
      int pri = preferred.indexOf(extension)
      if (pri == -1) {
        return preferred.size() + 1
      }
      return pri
    }

    artifacts.sort { Map a, Map b ->
      pri(ext(a?.fileName)) <=> pri(ext(b?.fileName))
    }.take(maxArtifacts)
  }
}
