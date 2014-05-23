package com.netflix.spinnaker.orca

import com.google.common.collect.ImmutableMap

interface TaskContext {

    ImmutableMap<String, Object> getInputs()

}