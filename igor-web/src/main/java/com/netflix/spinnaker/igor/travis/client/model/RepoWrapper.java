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
import org.simpleframework.xml.Default;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

/**
 * Repos are represented in many ways in the travis api, this is a helper to get one of the
 * representations.
 */
@Default
@JsonInclude(JsonInclude.Include.NON_NULL)
@Root(strict = false)
public class RepoWrapper {
  @Element(required = false, name = "repo")
  private Repo repo;

  public Repo getRepo() {
    return repo;
  }

  public void setRepo(Repo repo) {
    this.repo = repo;
  }
}
