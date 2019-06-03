/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.persistentStorage.gcs;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.persistentStorage.AbstractPersistentStoreEditCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStore;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.GcsPersistentStore;
import java.util.UUID;

@Parameters(separators = "=")
public class GcsEditCommand extends AbstractPersistentStoreEditCommand<GcsPersistentStore> {
  protected String getPersistentStoreType() {
    return PersistentStore.PersistentStoreType.GCS.getId();
  }

  @Parameter(
      names = "--project",
      description =
          "The Google Cloud Platform project you are using to host the GCS bucket as a backing store.")
  private String project;

  @Parameter(
      names = "--json-path",
      converter = LocalFileConverter.class,
      description =
          "A path to a JSON service account with permission to read and write to the bucket to be used as a backing store.")
  private String jsonPath;

  @Parameter(
      names = "--bucket",
      description =
          "The name of a storage bucket that your specified account has access to. If not "
              + "specified, a random name will be chosen. If you specify a globally unique bucket name "
              + "that doesn't exist yet, Halyard will create that bucket for you.")
  private String bucket;

  @Parameter(
      names = "--root-folder",
      description =
          "The root folder in the chosen bucket to place all of Spinnaker's persistent data in.")
  private String rootFolder;

  @Parameter(
      names = "--bucket-location",
      description =
          "This is only required if the bucket you specify doesn't exist yet. In that case, the "
              + "bucket will be created in that location. See https://cloud.google.com/storage/docs/managing-buckets#manage-class-location.")
  private String bucketLocation;

  @Override
  protected GcsPersistentStore editPersistentStore(GcsPersistentStore persistentStore) {
    persistentStore.setProject(isSet(project) ? project : persistentStore.getProject());
    persistentStore.setJsonPath(isSet(jsonPath) ? jsonPath : persistentStore.getJsonPath());
    persistentStore.setBucket(isSet(bucket) ? bucket : persistentStore.getBucket());
    persistentStore.setRootFolder(isSet(rootFolder) ? rootFolder : persistentStore.getRootFolder());
    persistentStore.setBucketLocation(
        isSet(bucketLocation) ? bucketLocation : persistentStore.getBucketLocation());

    if (persistentStore.getBucket() == null) {
      String bucketName = "spin-" + UUID.randomUUID().toString();
      AnsiUi.raw("Generated bucket name: " + bucketName);
      persistentStore.setBucket(bucketName);
    }

    return persistentStore;
  }
}
