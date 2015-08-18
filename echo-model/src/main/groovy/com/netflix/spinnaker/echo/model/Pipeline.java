package com.netflix.spinnaker.echo.model;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Wither;

@Builder
@Wither
@ToString(of = {"application", "name", "id"}, includeFieldNames = false)
@Value public class Pipeline {
  @NonNull String application;
  @NonNull String name;
  @NonNull String id;
  boolean parallel;
  List<Trigger> triggers;
  List<Map<String, Object>> stages;
  JenkinsTrigger trigger;

  @Wither
  @Value public static class Trigger {
    boolean enabled;
    @NonNull String type;
    String master;
    String job;
    String propertyFile;

    public JenkinsTrigger atBuildNumber(final int buildNumber) {
      return new JenkinsTrigger(type, master, job, buildNumber, propertyFile);
    }
  }
}
