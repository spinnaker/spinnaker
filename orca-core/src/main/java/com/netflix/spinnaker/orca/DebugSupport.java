package com.netflix.spinnaker.orca;

import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class that aids in debugging Maps in the logs.
 * <p>
 * Created by ttomsu on 8/20/15.
 */
public class DebugSupport {
  /**
   * @return a prettier, loggable string version of a Map.
   */
  public static String prettyPrint(final Map m) {
    try {
      return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(m);
    } catch (Exception ignored) {}

    return "Could not pretty print map: " + String.valueOf(m);
  }

}
