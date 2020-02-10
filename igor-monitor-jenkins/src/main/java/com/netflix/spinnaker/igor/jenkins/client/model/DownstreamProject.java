package com.netflix.spinnaker.igor.jenkins.client.model;

import org.simpleframework.xml.Root;

/** Represents a Jenkins job downstream project */
@Root(name = "downstreamProject", strict = false)
public class DownstreamProject extends RelatedProject {}
