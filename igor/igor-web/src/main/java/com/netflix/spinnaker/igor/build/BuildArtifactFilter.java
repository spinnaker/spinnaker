/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.igor.build;

import com.netflix.spinnaker.igor.build.model.GenericArtifact;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class BuildArtifactFilter {
  public static final String MAX_ARTIFACTS_PROP = "BuildArtifactFilter.maxArtifacts";
  public static final String PREFERRED_ARTIFACTS_PROP = "BuildArtifactFilter.preferredArtifacts";

  private static final int MAX_ARTIFACTS_DEFAULT = 20;
  private static final String PREFERRED_ARTIFACTS_DEFAULT =
      String.join(",", "deb", "rpm", "properties", "yml", "json", "xml", "html", "txt", "nupkg");

  private final Environment environment;

  @Autowired
  public BuildArtifactFilter(Environment environment) {
    this.environment = environment;
  }

  public List<GenericArtifact> filterArtifacts(List<GenericArtifact> artifacts) {
    if (artifacts == null || artifacts.size() == 0) {
      return artifacts;
    }

    final int maxArtifacts = getMaxArtifacts();
    final List<String> preferred = getPreferredArtifacts();
    if (artifacts.size() <= maxArtifacts) {
      return artifacts;
    }

    Comparator<GenericArtifact> comparator =
        Comparator.comparing(
            artifact -> {
              String extension = getExtension(artifact);
              return getPriority(extension, preferred);
            });

    return artifacts.stream().sorted(comparator).limit(maxArtifacts).collect(Collectors.toList());
  }

  private int getMaxArtifacts() {
    return environment.getProperty(MAX_ARTIFACTS_PROP, Integer.class, MAX_ARTIFACTS_DEFAULT);
  }

  private List<String> getPreferredArtifacts() {
    return Arrays.asList(
        environment
            .getProperty(PREFERRED_ARTIFACTS_PROP, String.class, PREFERRED_ARTIFACTS_DEFAULT)
            .split(","));
  }

  private static String getExtension(GenericArtifact artifact) {
    String filename = artifact.getFileName();
    if (filename == null) {
      return null;
    }

    int index = filename.lastIndexOf(".");
    if (index == -1) {
      return null;
    }

    return filename.substring(index + 1).toLowerCase();
  }

  private static int getPriority(String extension, List<String> preferred) {
    int priority = preferred.indexOf(extension);
    if (priority == -1) {
      return preferred.size();
    }
    return priority;
  }
}
