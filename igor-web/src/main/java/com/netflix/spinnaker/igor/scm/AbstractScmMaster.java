/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.igor.scm;

import java.util.List;

public class AbstractScmMaster implements ScmMaster {
  public List<String> listDirectory(
      String projectKey, String repositorySlug, String path, String at) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public String getTextFileContents(
      String projectKey, String repositorySlug, String path, String at) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public String getTextFileContents(String projectKey, String repositorySlug, String path) {
    return getTextFileContents(projectKey, repositorySlug, path, ScmMaster.DEFAULT_GIT_REF);
  }
}
