package com.netflix.spinnaker.orca

import com.google.common.collect.ImmutableMap
import groovy.transform.CompileStatic

@CompileStatic
final class DefaultTaskResult implements TaskResult {

    final TaskResult.Status status
    final ImmutableMap<String, Object> outputs

    DefaultTaskResult(TaskResult.Status status) {
        this(status, [:])
    }

    DefaultTaskResult(TaskResult.Status status, Map<String, ? extends Object> outputs) {
        this.status = status
        this.outputs = ImmutableMap.copyOf(outputs)
    }

}
