package com.netflix.spinnaker.front50.model.notification;

public enum HierarchicalLevel {
  APPLICATION,
  GLOBAL;

  public static HierarchicalLevel fromString(final String level) {
    return valueOf(level.toUpperCase());
  }
}
