package com.netflix.spinnaker.igor.jenkins.client.model

import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Path
import org.simpleframework.xml.Root

/**
 * Represents the basic Jenkins job configuration information
 */
@Root(strict=false)
class JobConfig {
    @Element(required = false)
    String description

    @Element
    String displayName

    @Element
    String name

    @Element
    boolean buildable

    @Element
    String color

    @Element
    String url

    @Path("property[1]")
    @ElementList(required = false, inline = true)
    List<ParameterDefinition> parameterDefinitionList

    @ElementList(name = "upstreamProject", required = false, inline = true)
    List<UpstreamProject> upstreamProjectList

    @ElementList(name = "downstreamProject", required = false, inline = true)
    List<DownstreamProject> downstreamProjectList

    @Element
    boolean concurrentBuild
}
