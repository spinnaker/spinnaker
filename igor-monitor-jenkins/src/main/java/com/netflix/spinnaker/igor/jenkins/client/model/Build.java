package com.netflix.spinnaker.igor.jenkins.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.netflix.spinnaker.igor.build.model.GenericArtifact;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.Result;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/** Represents a build in Jenkins */
@JsonInclude(JsonInclude.Include.NON_NULL)
@XmlRootElement
public class Build {
  public GenericBuild genericBuild(final String jobName) {
    GenericBuild.GenericBuildBuilder builder =
        GenericBuild.builder()
            .building(building)
            .number(number)
            .duration(duration.intValue())
            // TODO(rz): Groovyism. What does Groovy do when `result` is null and you cast it to an
            // enum? WHO KNOWS, but
            //  all of the igor tests depend on _something_ being set from null.
            .result((result == null) ? Result.NOT_BUILT : Result.valueOf(result))
            .name(jobName)
            .url(url)
            .timestamp(timestamp)
            .fullDisplayName(fullDisplayName);

    if (artifacts != null && !artifacts.isEmpty()) {
      builder.artifacts(
          artifacts.stream()
              .map(
                  buildArtifact -> {
                    GenericArtifact artifact = buildArtifact.getGenericArtifact();
                    artifact.setName(jobName);
                    artifact.setVersion(getNumber().toString());
                    return artifact;
                  })
              .collect(Collectors.toList()));
    }

    if (testResults != null && !testResults.isEmpty()) {
      builder.testResults(testResults);
    }

    return builder.build();
  }

  public boolean getBuilding() {
    return building;
  }

  public boolean isBuilding() {
    return building;
  }

  public void setBuilding(boolean building) {
    this.building = building;
  }

  public Integer getNumber() {
    return number;
  }

  public void setNumber(Integer number) {
    this.number = number;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  public Long getDuration() {
    return duration;
  }

  public void setDuration(Long duration) {
    this.duration = duration;
  }

  public Integer getEstimatedDuration() {
    return estimatedDuration;
  }

  public void setEstimatedDuration(Integer estimatedDuration) {
    this.estimatedDuration = estimatedDuration;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getBuiltOn() {
    return builtOn;
  }

  public void setBuiltOn(String builtOn) {
    this.builtOn = builtOn;
  }

  public String getFullDisplayName() {
    return fullDisplayName;
  }

  public void setFullDisplayName(String fullDisplayName) {
    this.fullDisplayName = fullDisplayName;
  }

  public List<BuildArtifact> getArtifacts() {
    return artifacts;
  }

  public void setArtifacts(List<BuildArtifact> artifacts) {
    this.artifacts = artifacts;
  }

  public List<TestResults> getTestResults() {
    return testResults;
  }

  public void setTestResults(List<TestResults> testResults) {
    this.testResults = testResults;
  }

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

  @JacksonXmlElementWrapper(useWrapping = false)
  @XmlElement(name = "action", required = false)
  private List<TestResults> testResults;
}
