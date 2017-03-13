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

package com.netflix.spinnaker.halyard.cli.command.v1.config;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStorage;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.UUID;

@Parameters()
public class EditPersistentStorageCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  private String description = "Configure Spinnaker's persistent storage options.";

  @Parameter(
      names = "--account-name",
      description = "The Spinnaker account that has access to either a GCS or S3 bucket. This account "
          + "does _not_ have to be separate from the accounts used to manage/deploy infrastructure, but "
          + "it can be."
  )
  private String accountName;

  @Parameter(
      names = "--bucket",
      description = "The name of a storage bucket that your specified account has access to. If not "
          + "specified, a random name will be chosen. If you specify a globally unique bucket name "
          + "that doesn't exist yet, Halyard will create that bucket for you."
  )
  private String bucket;

  @Parameter(
      names = "--root-folder",
      description = "The root folder in the chosen bucket to place all of Spinnaker's persistent data in."
  )
  private String rootFolder = "spinnaker";

  @Parameter(
      names = "--location",
      description = "This is only required if the bucket you specify doesn't exist yet. In that case, the "
          + "bucket will be created in that location."
  )
  private String location;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    PersistentStorage persistentStorage = new OperationHandler<PersistentStorage>()
        .setOperation(Daemon.getPersistentStorage(currentDeployment, !noValidate))
        .setFailureMesssage("Failed to load persistent storage.")
        .get();

    persistentStorage.setAccountName(isSet(accountName) ? accountName : persistentStorage.getAccountName());
    persistentStorage.setBucket(isSet(bucket) ? bucket : persistentStorage.getBucket());
    persistentStorage.setRootFolder(isSet(rootFolder) ? rootFolder : persistentStorage.getRootFolder());
    persistentStorage.setLocation(isSet(location) ? location : persistentStorage.getLocation());

    if (persistentStorage.getBucket() == null) {
      String bucketName = "spin-" + UUID.randomUUID().toString();
      AnsiUi.raw("Generated bucket name: " + bucketName);
      persistentStorage.setBucket(bucketName);
    }

    new OperationHandler<Void>()
        .setOperation(Daemon.setPersistentStorage(currentDeployment, !noValidate, persistentStorage))
        .setFailureMesssage("Failed to edit persistent storage.")
        .setFailureMesssage("Successfully updated persistent storage.")
        .get();
  }
}
