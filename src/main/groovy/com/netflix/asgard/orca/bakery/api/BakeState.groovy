package com.netflix.asgard.orca.bakery.api

import groovy.transform.CompileStatic

@CompileStatic
enum BakeState {
    PENDING, RUNNING, COMPLETED, SUSPENDED, CANCELLED
}