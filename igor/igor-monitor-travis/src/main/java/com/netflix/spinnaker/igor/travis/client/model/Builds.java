/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

package com.netflix.spinnaker.igor.travis.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.List;
import lombok.Data;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@XmlRootElement
public class Builds {

  @JacksonXmlElementWrapper(useWrapping = false)
  @XmlElement(name = "builds", required = false)
  private List<Build> builds;

  @JacksonXmlElementWrapper(useWrapping = false)
  @XmlElement(name = "jobs", required = false)
  private List<Job> jobs;

  @JacksonXmlElementWrapper(useWrapping = false)
  @XmlElement(name = "commits", required = false)
  private List<Commit> commits;
}
