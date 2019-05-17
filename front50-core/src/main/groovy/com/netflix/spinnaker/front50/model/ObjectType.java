/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.front50.model;

import com.netflix.spinnaker.front50.model.application.Application;
import com.netflix.spinnaker.front50.model.delivery.Delivery;
import com.netflix.spinnaker.front50.model.notification.Notification;
import com.netflix.spinnaker.front50.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplate;
import com.netflix.spinnaker.front50.model.project.Project;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount;
import com.netflix.spinnaker.front50.model.snapshot.Snapshot;
import com.netflix.spinnaker.front50.model.tag.EntityTags;

public enum ObjectType {
  PROJECT(Project.class, "projects", "project-metadata.json"),
  PIPELINE(Pipeline.class, "pipelines", "pipeline-metadata.json"),
  STRATEGY(Pipeline.class, "pipeline-strategies", "pipeline-strategy-metadata.json"),
  PIPELINE_TEMPLATE(
      PipelineTemplate.class, "pipeline-templates", "pipeline-template-metadata.json"),
  NOTIFICATION(Notification.class, "notifications", "notification-metadata.json"),
  SERVICE_ACCOUNT(ServiceAccount.class, "serviceAccounts", "serviceAccount-metadata.json"),
  APPLICATION(Application.class, "applications", "application-metadata.json"),
  // TODO(ewiseblatt) Add migration logic to allow GCS to use application-permission.json like the
  // other providers.
  APPLICATION_PERMISSION(
      Application.Permission.class, "applications", "application-permission.json"),
  SNAPSHOT(Snapshot.class, "snapshots", "snapshot.json"),
  ENTITY_TAGS(EntityTags.class, "tags", "entity-tags-metadata.json"),
  DELIVERY(Delivery.class, "delivery", "delivery-metadata.json");

  public final Class<? extends Timestamped> clazz;
  public final String group;
  public final String defaultMetadataFilename;

  ObjectType(Class<? extends Timestamped> clazz, String group, String defaultMetadataFilename) {
    this.clazz = clazz;
    this.group = group;
    this.defaultMetadataFilename = defaultMetadataFilename;
  }
}
