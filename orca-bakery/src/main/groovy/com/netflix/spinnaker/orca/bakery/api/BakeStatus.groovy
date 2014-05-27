package com.netflix.spinnaker.orca.bakery.api

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@CompileStatic
@EqualsAndHashCode(includes = "id")
@ToString(includeNames = true)
class BakeStatus {

    String id
    State state

    static enum State {
        PENDING, RUNNING, COMPLETED, SUSPENDED, CANCELLED
    }
}
