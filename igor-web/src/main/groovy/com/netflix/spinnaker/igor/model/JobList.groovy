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

import groovy.transform.CompileStatic
import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

/**
 * Represents a list of projects
 */
@Root(name = 'hudson', strict=false)
@CompileStatic
class JobList {

    @ElementList(inline = true, name = "job")
    List<Job> list

}

@Root(strict=false)
class Job {
    @ElementList(inline = true, name = "job", required=false)
    List<Job> list

    @Element(required = false)
    String name
}
