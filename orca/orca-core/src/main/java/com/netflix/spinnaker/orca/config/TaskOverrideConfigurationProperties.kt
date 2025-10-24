package com.netflix.spinnaker.orca.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Task override configuration to use while planning stages.
 */
@ConfigurationProperties("task-overrides")
public class TaskOverrideConfigurationProperties(
  /**
   * list of task overrides.
   */
  public var overrideDefinitions: List<TaskOverrideDefinition> = listOf()
) {

  public class TaskOverrideDefinition(
    /**
     * Candidate stage in which we are looking to replace task definition
     */
    public var stageName: String,

    /**
     * Attribute values to consider in the stage context for overriding the task.
     * For eg: this task override is only applicable for a particular cloud provider.
     */
    public var overrideCriteriaAttributes: List<String>,

    /**
     * Original task implementation class name as given while building the stage task graph
     */
    public var originalTaskImplementingClassName: String,

    /**
     * Implementation class name to use while resolving the task via Task Resolver.
     */
    public var newTaskImplementingClassName: String
  )
}
