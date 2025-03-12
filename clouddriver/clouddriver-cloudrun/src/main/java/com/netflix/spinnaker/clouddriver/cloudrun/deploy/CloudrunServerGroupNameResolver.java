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

import com.google.api.services.run.v1.model.Revision;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunModelUtil;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CloudrunServerGroupNameResolver extends AbstractServerGroupNameResolver {
  private static final String PHASE = "DEPLOY";

  private final String project;
  private String region;
  private final CloudrunNamedAccountCredentials credentials;

  public CloudrunServerGroupNameResolver(
      String project, String region, CloudrunNamedAccountCredentials credentials) {
    this.project = project;
    this.region = region;
    this.credentials = credentials;
  }

  @Override
  public String getPhase() {
    return PHASE;
  }

  @Override
  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  @Override
  public List<TakenSlot> getTakenSlots(String clusterName) {
    List<Revision> versions =
        CloudrunUtils.queryAllRevisions(project, credentials, getTask(), getPhase());
    return findMatchingVersions(versions, clusterName);
  }

  public static List<AbstractServerGroupNameResolver.TakenSlot> findMatchingVersions(
      List<Revision> revisions, String clusterName) {

    List<AbstractServerGroupNameResolver.TakenSlot> slot = new ArrayList<>();
    revisions.forEach(
        revision -> {
          String versionName = revision.getMetadata().getName();
          Names friggaNames = Names.parseName(versionName);
          if (clusterName.equals(friggaNames.getCluster())) {
            Long timestamp =
                CloudrunModelUtil.translateTime(revision.getMetadata().getCreationTimestamp());
            AbstractServerGroupNameResolver.TakenSlot temp =
                new AbstractServerGroupNameResolver.TakenSlot(
                    versionName, friggaNames.getSequence(), new Date(timestamp));
            slot.add(temp);
          }
        });
    return slot;
  }
}
