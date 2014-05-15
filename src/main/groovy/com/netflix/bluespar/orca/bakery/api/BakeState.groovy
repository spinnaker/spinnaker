package com.netflix.bluespar.orca.bakery.api

import groovy.transform.CompileStatic

@CompileStatic
enum BakeState {
    PENDING, RUNNING, COMPLETED, SUSPENDED, CANCELLED
}