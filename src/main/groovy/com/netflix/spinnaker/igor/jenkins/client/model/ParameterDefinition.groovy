package com.netflix.spinnaker.igor.jenkins.client.model

import org.simpleframework.xml.Element
import org.simpleframework.xml.Path
import org.simpleframework.xml.Root

/**
 * Represents a parameter for a Jenkins job
 */
@Root(name = "parameterDefinition", strict = false)
class ParameterDefinition {

    @Path("defaultParameterValue[1]")
    @Element(name = "name")
    String defaultName

    @Path("defaultParameterValue[1]")
    @Element(name="value")
    String defaultValue

    @Element
    String name

    @Element(required = false)
    String description

    @Element
    String type
}
