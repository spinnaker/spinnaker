package com.netflix.spinnaker.orca.test

import groovy.transform.TupleConstructor

@TupleConstructor(includeFields = true)
class ResponseConfiguration {

    private final ResponseBuilder responseBuilder

    ResponseBuilder andRespond() {
        responseBuilder
    }

}
