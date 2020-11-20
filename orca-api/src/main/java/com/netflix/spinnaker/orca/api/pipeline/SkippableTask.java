package com.netflix.spinnaker.orca.api.pipeline;

/**
 * A skippable task can be configured via properties to go directly from NOT_STARTED to SKIPPED. By
 * default, the property name is:
 *
 * <p>tasks.$taskId.enabled
 *
 * <p>where `taskId` corresponds to the simple class name (without the package) with a lower case
 * first character. For example, a skippable class `com.foo.DummySkippableTask` could be disabled
 * via property
 *
 * <p>tasks.dummySkippableTask.enabled
 *
 * @see StartTaskHandler
 */
public interface SkippableTask extends Task {
  static String isEnabledPropertyName(String name) {
    String loweredName = Character.toLowerCase(name.charAt(0)) + name.substring(1);
    return String.format("tasks.%s.enabled", loweredName);
  }

  default String isEnabledPropertyName() {
    return isEnabledPropertyName(
        getClass().getSimpleName().isBlank() ? getClass().getName() : getClass().getSimpleName());
  }
}
