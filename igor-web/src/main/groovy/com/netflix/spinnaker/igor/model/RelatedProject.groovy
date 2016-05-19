package com.netflix.spinnaker.igor.jenkins.client.model

import org.simpleframework.xml.Element
import org.simpleframework.xml.Root

/**
 * Represents a upstream/downstream project for a Jenkins job
 */
@Root(strict=false)
class RelatedProject {
    @Element
    String name

    @Element
    String url

    @Element
    String color
}
