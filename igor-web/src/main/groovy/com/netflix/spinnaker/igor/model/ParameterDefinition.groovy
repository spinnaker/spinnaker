/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.jenkins.client.model

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
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
    @Element(name="value", required = false)
    String defaultValue

    @Element
    String name

    @Element(required = false)
    String description

    @Element
    String type

    @ElementList(entry = "choice", required = false, inline = true)
    @JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
    List<String> choices
}
