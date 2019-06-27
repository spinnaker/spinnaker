package com.netflix.spinnaker.igor.build.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Wither;

@Getter
@EqualsAndHashCode(of = "sha1")
@Builder
@Wither
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
