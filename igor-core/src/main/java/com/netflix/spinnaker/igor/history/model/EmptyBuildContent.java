package com.netflix.spinnaker.igor.history.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Move any invocation of this class to a monitor-specific BuildContent implementation. */
@Deprecated
@Data
@AllArgsConstructor
public class EmptyBuildContent implements BuildContent {

  public static String TYPE = "empty";

  @Override
  public String getType() {
    return TYPE;
  }
}
