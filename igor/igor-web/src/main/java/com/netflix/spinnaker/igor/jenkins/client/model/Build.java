/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.jenkins.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.netflix.spinnaker.igor.build.model.GenericArtifact;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.Result;
import lombok.Data;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a build in Jenkins
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@XmlRootElement
public class Build {
  private boolean building;
  private Integer number;

  @XmlElement(required = false)
  private String result;

  private String timestamp;

  @XmlElement(required = false)
  private Long duration;

  @XmlElement(required = false)
  private Integer estimatedDuration;

  @XmlElement(required = false)
  private String id;

  private String url;

  @XmlElement(required = false)
  private String builtOn;

  @XmlElement(required = false)
  private String fullDisplayName;

  @JacksonXmlElementWrapper(useWrapping = false)
  @XmlElement(name = "artifact", required = false)
  private List<BuildArtifact> artifacts;

  /*
  We need to dump this into a list first since the Jenkins query returns
  multiple action elements, with all but the test run one empty.  We then filter it into a testResults var
   */
  @JacksonXmlElementWrapper(useWrapping = false)
  @XmlElement(name = "action", required = false)
  private List<TestResults> testResults;

  public GenericBuild genericBuild(String jobName) {
    GenericBuild genericBuild = new GenericBuild(
      jobName + "-" + String.valueOf(number),
      building,
      number.intValue(),
      duration.intValue(),
      Result.valueOf(result),
      jobName,
      url,
      timestamp,
      fullDisplayName
    );

    if (artifacts != null) {
      genericBuild.setArtifacts(artifacts.stream().map(buildArtifact -> {
        GenericArtifact artifact = buildArtifact.getGenericArtifact();
        artifact.setName(jobName);
        artifact.setVersion(number.toString());
        return artifact;
      }).collect(Collectors.toList()));
    }

    if (testResults != null) {
      genericBuild.setTestResults(testResults.stream()
        .map(tr -> new GenericBuild.TestResults(tr.getFailCount(), tr.getSkipCount(), tr.getTotalCount(), tr.getUrlName()))
        .collect(Collectors.toList()));
    }

    return genericBuild;
  }
}
