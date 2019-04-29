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

package com.netflix.spinnaker.igor.travis.client.model.v3;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.simpleframework.xml.Default;

@Default
@JsonIgnoreProperties(ignoreUnknown = true)
public class Request {
  private V3Repository repository;
  private List<V3Build> builds;
  private int id;

  public V3Repository getRepository() {
    return repository;
  }

  public void setRepository(V3Repository repository) {
    this.repository = repository;
  }

  public List<V3Build> getBuilds() {
    return builds;
  }

  public void setBuilds(List<V3Build> builds) {
    this.builds = builds;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }
}
