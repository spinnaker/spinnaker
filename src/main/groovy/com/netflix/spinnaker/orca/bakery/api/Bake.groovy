package com.netflix.spinnaker.orca.bakery.api

import com.google.gson.annotations.SerializedName
import groovy.transform.CompileStatic
import groovy.transform.Immutable

@Immutable
@CompileStatic
class Bake {

    String user
    @SerializedName("package") String packageName
    Label baseLabel
    OperatingSystem baseOs

    enum Label {
        release, candidate
    }

    enum OperatingSystem {
        centos, ubuntu
    }
}
