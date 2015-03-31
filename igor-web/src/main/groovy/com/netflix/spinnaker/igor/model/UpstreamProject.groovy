package com.netflix.spinnaker.igor.jenkins.client.model

import org.simpleframework.xml.Root

/**
 * Represents a Jenkins job upstream project
 */
@Root(name = 'upstreamProject')
class UpstreamProject extends RelatedProject {
}
