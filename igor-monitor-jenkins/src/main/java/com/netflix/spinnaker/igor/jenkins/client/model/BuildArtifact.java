package com.netflix.spinnaker.igor.jenkins.client.model;

import com.netflix.spinnaker.igor.build.model.GenericArtifact;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/** Represents a build artifact */
@XmlRootElement(name = "artifact")
public class BuildArtifact {
  public GenericArtifact getGenericArtifact() {
    GenericArtifact artifact = new GenericArtifact(fileName, displayPath, relativePath);
    artifact.setType("jenkins/file");
    artifact.setReference(relativePath);
    return artifact;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getDisplayPath() {
    return displayPath;
  }

  public void setDisplayPath(String displayPath) {
    this.displayPath = displayPath;
  }

  public String getRelativePath() {
    return relativePath;
  }

  public void setRelativePath(String relativePath) {
    this.relativePath = relativePath;
  }

  @XmlElement(required = false)
  private String fileName;

  @XmlElement(required = false)
  private String displayPath;

  @XmlElement(required = false)
  private String relativePath;
}
