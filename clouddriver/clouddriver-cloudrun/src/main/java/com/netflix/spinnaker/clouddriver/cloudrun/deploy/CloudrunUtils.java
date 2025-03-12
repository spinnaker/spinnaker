/*
 * Copyright 2022 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudrun.deploy;

import com.google.api.services.run.v1.CloudRun;
import com.google.api.services.run.v1.model.Revision;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudrunUtils {

  private static Logger logger = LoggerFactory.getLogger(CloudrunUtils.class);

  public static List<Revision> queryAllRevisions(
      String project, CloudrunNamedAccountCredentials credentials, Task task, String phase) {
    task.updateStatus(phase, "Querying all revisions for project $project...");
    List<Revision> serverGroups = getRevisionsList(project, credentials);
    if (serverGroups == null) {
      serverGroups = new ArrayList<>();
    }
    return serverGroups;
  }

  private static Optional<CloudRun.Namespaces.Revisions.List> getRevisionsListRequest(
      String project, CloudrunNamedAccountCredentials credentials) {
    try {
      return Optional.of(
          credentials.getCloudRun().namespaces().revisions().list("namespaces/" + project));
    } catch (IOException e) {
      logger.error(
          "Error in creating request for the method revisions.list !!! {} ", e.getMessage());
      return Optional.empty();
    }
  }

  private static List<Revision> getRevisionsList(
      String project, CloudrunNamedAccountCredentials credentials) {
    Optional<CloudRun.Namespaces.Revisions.List> revisionsListRequest =
        getRevisionsListRequest(project, credentials);
    if (revisionsListRequest.isEmpty()) {
      return new ArrayList<>();
    }
    try {
      return revisionsListRequest.get().execute().getItems();
    } catch (IOException e) {
      logger.error("Error executing revisions.list request. {}", e.getMessage());
      return new ArrayList<>();
    }
  }
}
