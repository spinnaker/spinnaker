package com.netflix.spinnaker.halyard.config.config.v1;

import static net.logstash.logback.argument.StructuredArguments.value;

import com.google.api.services.storage.StorageScopes;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.netflix.spinnaker.front50.config.GcsConfig;
import com.netflix.spinnaker.front50.config.GcsProperties;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;

public class GCSConfig {

  private static final Logger log = LoggerFactory.getLogger(GcsConfig.class);

  public static Credentials getGcsCredentials(GcsProperties gcsProperties) throws IOException {

    String jsonPath = gcsProperties.getJsonPath();

    GoogleCredentials credentials;
    if (!jsonPath.isEmpty()) {
      try (FileInputStream fis = new FileInputStream(jsonPath)) {
        credentials = GoogleCredentials.fromStream(fis);
      }
      log.info("Loaded GCS credentials from {}", value("jsonPath", jsonPath));
    } else {
      log.info(
          "spinnaker.gcs.enabled without spinnaker.gcs.jsonPath. "
              + "Using default application credentials. Using default credentials.");
      credentials = GoogleCredentials.getApplicationDefault();
    }

    return credentials.createScopedRequired()
        ? credentials.createScoped(Collections.singleton(StorageScopes.DEVSTORAGE_FULL_CONTROL))
        : credentials;
  }

  public static Storage getGoogleCloudStorage(
      @Qualifier("gcsCredentials") Credentials credentials, GcsProperties properties) {
    return StorageOptions.newBuilder()
        .setCredentials(credentials)
        .setProjectId(properties.getProject())
        .build()
        .getService();
  }
}
