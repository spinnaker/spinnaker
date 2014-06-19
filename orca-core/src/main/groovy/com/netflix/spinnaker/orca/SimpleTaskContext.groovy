package com.netflix.spinnaker.orca

import com.google.common.collect.ForwardingMap
import com.google.common.collect.ImmutableMap
import groovy.transform.CompileStatic

// TODO: should really move this to the orca-test subproject but that would create a circular dependency.
/**
 * A convenient implementation of {@link TaskContext} that just wraps a plain <em>Map</em>. Use this for testing.
 */
@CompileStatic
class SimpleTaskContext extends ForwardingMap<String, Object> implements TaskContext {

    private final Map<String, Object> delegate = [:]

    @Override
    ImmutableMap<String, Object> getInputs() {
        ImmutableMap.copyOf(delegate)
    }

    @Override
    protected Map<String, Object> delegate() {
        delegate
    }
}
