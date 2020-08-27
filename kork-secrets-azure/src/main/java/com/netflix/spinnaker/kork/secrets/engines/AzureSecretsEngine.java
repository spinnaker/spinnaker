/*
 * Copyright 2020 Project Ronin
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

package com.netflix.spinnaker.kork.secrets.engines;

import com.azure.identity.EnvironmentCredentialBuilder;
import com.azure.storage.blob.*;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.InvalidSecretFormatException;
import com.netflix.spinnaker.kork.secrets.SecretException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AzureSecretsEngine extends AbstractStorageSecretEngine {
  private static final String IDENTIFIER = "az";

  private static final String STORAGE_ACCOUNT = "a";
  private static final String STORAGE_CONTAINER = "c";
  private static final String STORAGE_BLOB = "b";

  @Override
  public String identifier() {
    return IDENTIFIER;
  }

  @Override
  public void validate(EncryptedSecret encryptedSecret) throws InvalidSecretFormatException {
    Set<String> paramNames = encryptedSecret.getParams().keySet();
    if (!paramNames.contains(STORAGE_ACCOUNT)) {
      throw new InvalidSecretFormatException(
          "Storage account parameter is missing (" + STORAGE_ACCOUNT + "=...)");
    }
    if (!paramNames.contains(STORAGE_CONTAINER)) {
      throw new InvalidSecretFormatException(
          "Storage container parameter is missing (" + STORAGE_CONTAINER + "=...)");
    }
    if (!paramNames.contains(STORAGE_BLOB)) {
      throw new InvalidSecretFormatException(
          "Storage blob parameter is missing (" + STORAGE_BLOB + "=...)");
    }
  }

  @Override
  protected InputStream downloadRemoteFile(EncryptedSecret encryptedSecret) {
    String storageAccount = encryptedSecret.getParams().get(STORAGE_ACCOUNT);
    String container = encryptedSecret.getParams().get(STORAGE_CONTAINER);
    String blob = encryptedSecret.getParams().get(STORAGE_BLOB);

    String endpoint =
        String.format(Locale.ROOT, "https://%s.blob.core.windows.net", storageAccount);

    BlobServiceClient storageClient =
        new BlobServiceClientBuilder()
            .endpoint(endpoint)
            .credential(new EnvironmentCredentialBuilder().build())
            .buildClient();

    BlobContainerClient containerClient = storageClient.getBlobContainerClient(container);
    BlobClient blobClient = containerClient.getBlobClient(blob);

    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      blobClient.download(output);
      return new ByteArrayInputStream(output.toByteArray());
    } catch (Exception e) {
      throw new SecretException(
          String.format(
              "Error reading contents of Azure account: %s, container: %s, blob: %s. \nError: %s",
              storageAccount, container, blob, e.toString()));
    }
  }
}
