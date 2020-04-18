package com.netflix.spinnaker.gradle

import org.gradle.api.Project

class Flags {

  public static String DEFAULT_VERSION_WHEN_UNSPECIFIED = '867.5309-SNAPSHOT'

  /**
   * Whether cross-compilation should be enabled.
   *
   * Determined by the project property 'enableCrossCompilerPlugin', and
   * disabled by default.
   *
   * @param project the project from which to read the property
   * @return whether cross-compilation should be enabled
   */
  static boolean shouldEnableCrossCompilation(Project project) {
    return Boolean.valueOf(project.findProperty("enableCrossCompilerPlugin")?.toString())
  }

}
