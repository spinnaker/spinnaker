package com.netflix.spinnaker.echo.model;

import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

@ToString(of = {"master", "job", "buildNumber"}, includeFieldNames = false)
@Value public class JenkinsTrigger {
  @NonNull String type;
  @NonNull String master;
  @NonNull String job;
  int buildNumber;
  String propertyFile;
}
