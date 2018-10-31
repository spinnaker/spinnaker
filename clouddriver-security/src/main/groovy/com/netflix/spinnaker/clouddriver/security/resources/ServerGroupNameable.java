/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.security.resources;

import com.netflix.frigga.Names;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Convenience trait for parsing application names out of a description with one or more server group names.
 */
public interface ServerGroupNameable extends ApplicationNameable {
  Collection<String> getServerGroupNames();

  @Override
  default Collection<String> getApplications() {
    return getServerGroupNames().stream().map(n -> Names.parseName(n).getApp()).collect(Collectors.toList());
  }
}
