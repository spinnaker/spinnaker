package com.netflix.spinnaker.igor.jenkins.client.model

import org.simpleframework.xml.Element

/**
 * Represents a upstream/downstream project for a Jenkins job
 */
class RelatedProject {
    @Element
    String name

    @Element
    String url

    @Element
    String color
}
