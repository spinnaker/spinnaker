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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.ui;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.security.ApacheSsl;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Parameters(separators = "=")
public class ApacheSslEditCommand extends AbstractConfigCommand {
  private String commandName = "edit";

  private String shortDescription = "Edit SSL settings for your UI server.";

  private String longDescription =
      "Configure SSL termination to handled by the UI server's Apache server.";

  @Parameter(
      names = "--ssl-certificate-file",
      converter = LocalFileConverter.class,
      description = "Path to your .crt file.")
  String sslCertificateFile;

  @Parameter(
      names = "--ssl-certificate-key-file",
      converter = LocalFileConverter.class,
      description = "Path to your .key file.")
  String sslCertificateKeyFile;

  @Parameter(
      names = "--ssl-certificate-passphrase",
      password = true,
      description =
          "The passphrase needed to unlock your SSL certificate. This will be provided to Apache on startup.")
  String sslCertificatePassphrase;

  @Parameter(
      names = "--ssl-certificate-ca-file",
      description =
          "Path to the .crt file for the CA that issued your SSL certificate. This is only needed for localgit"
              + "deployments that serve the UI using webpack dev server.")
  String sslCACertificateFile;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();

    ApacheSsl apacheSsl =
        new OperationHandler<ApacheSsl>()
            .setOperation(Daemon.getApacheSsl(currentDeployment, false))
            .setFailureMesssage("Failed to load SSL settings.")
            .get();

    int originalHash = apacheSsl.hashCode();

    apacheSsl.setSslCertificateFile(
        isSet(sslCertificateFile) ? sslCertificateFile : apacheSsl.getSslCertificateFile());
    apacheSsl.setSslCertificateKeyFile(
        isSet(sslCertificateKeyFile)
            ? sslCertificateKeyFile
            : apacheSsl.getSslCertificateKeyFile());
    apacheSsl.setSslCertificatePassphrase(
        isSet(sslCertificatePassphrase)
            ? sslCertificatePassphrase
            : apacheSsl.getSslCertificatePassphrase());
    apacheSsl.setSslCACertificateFile(
        isSet(sslCACertificateFile) ? sslCACertificateFile : apacheSsl.getSslCACertificateFile());

    if (originalHash == apacheSsl.hashCode()) {
      AnsiUi.failure("No changes supplied.");
      return;
    }

    new OperationHandler<Void>()
        .setOperation(Daemon.setApacheSsl(currentDeployment, !noValidate, apacheSsl))
        .setFailureMesssage("Failed to edit SSL settings.")
        .setSuccessMessage("Successfully updated SSL settings.")
        .get();
  }
}
