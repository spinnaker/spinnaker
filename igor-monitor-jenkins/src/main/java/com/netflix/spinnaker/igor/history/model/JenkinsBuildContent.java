package com.netflix.spinnaker.igor.history.model;

import com.netflix.spinnaker.igor.jenkins.client.model.Project;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Encapsulates a build content block */
@Data
@AllArgsConstructor
public class JenkinsBuildContent implements BuildContent {
  private Project project;
  private String master;
}
