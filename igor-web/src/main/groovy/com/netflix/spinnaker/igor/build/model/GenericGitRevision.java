package com.netflix.spinnaker.igor.build.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;

@Getter
@EqualsAndHashCode(of = "sha1")
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenericGitRevision {
  private String name;
  private String branch;
  private String sha1;
  private String committer;
  private String compareUrl;
  private String message;
  private Instant timestamp;
  private String remoteUrl;
}
