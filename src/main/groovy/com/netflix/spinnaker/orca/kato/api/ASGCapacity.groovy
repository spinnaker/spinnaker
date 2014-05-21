package com.netflix.spinnaker.orca.kato.api

import groovy.transform.CompileStatic

@CompileStatic
class ASGCapacity {
    int min
    int max
    int desired
}
