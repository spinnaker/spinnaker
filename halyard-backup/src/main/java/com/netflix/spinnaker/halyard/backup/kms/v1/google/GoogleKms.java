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
 *
 */

package com.netflix.spinnaker.halyard.backup.kms.v1.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudkms.v1.CloudKMS;
import com.google.api.services.cloudkms.v1.CloudKMSScopes;
import com.google.api.services.cloudkms.v1.model.CryptoKey;
import com.google.api.services.cloudkms.v1.model.KeyRing;
import com.netflix.spinnaker.halyard.backup.kms.v1.Kms;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.io.FileInputStream;
import java.io.IOException;

@Slf4j
public class GoogleKms implements Kms {
  private final CloudKMS cloudKms;
  private final String projectId;
  private final String locationId;
  private final String keyRingId;
  private final String cryptoKeyId;
  private final KeyRing keyRing;
  private final CryptoKey cryptoKey;

  private static final String KEY_PURPOSE = "ENCRYPT_DECRYPT";

  public GoogleKms(GoogleKmsProperties properties) {
    cloudKms = buildCredentials(properties);
    projectId = properties.getProject();
    locationId = locationId(projectId, StringUtils.isEmpty(properties.getLocation()) ? "global" : properties.getLocation());
    keyRingId = keyRingId(locationId, StringUtils.isEmpty(properties.getKeyRingName()) ? "halyard" : properties.getKeyRingName());
    cryptoKeyId = cryptoKeyId(keyRingId, StringUtils.isEmpty(properties.getCryptoKeyName()) ? "config" : properties.getCryptoKeyName());

    keyRing = ensureKeyRingExists(cloudKms, locationId, keyRingId);
    cryptoKey = ensureCryptoKeyExists(cloudKms, keyRingId, cryptoKeyId);
  }

  private CloudKMS buildCredentials(GoogleKmsProperties properties) {
    HttpTransport transport = new NetHttpTransport();
    JsonFactory jsonFactory = new JacksonFactory();
    GoogleCredential credential;
    try {
      credential = loadKmsCredential(transport, jsonFactory, properties.getJsonPath());
    } catch (IOException e) {
      throw new RuntimeException("Unable to load KMS credentials: " + e.getMessage(), e);
    }

    return new CloudKMS.Builder(transport, jsonFactory, credential)
        .setApplicationName("halyard")
        .build();
  }

  private static String locationId(String project, String location) {
    return String.format("projects/%s/locations/%s", project, location);
  }

  private static String keyRingId(String locationId, String keyRingId) {
    return String.format("%s/keyRings/%s", locationId, keyRingId);
  }

  private static String cryptoKeyId(String keyRingId, String cryptoKeyId) {
    return String.format("%s/cryptoKeys/%s", keyRingId, cryptoKeyId);
  }

  private static CryptoKey ensureCryptoKeyExists(CloudKMS cloudKms, String keyRingId, String cryptoKeyId) {
    CryptoKey cryptoKey;
    try {
      cryptoKey = cloudKms.projects().locations().keyRings().cryptoKeys().get(cryptoKeyId).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        cryptoKey = null;
      } else {
        throw new HalException(Problem.Severity.FATAL, "Unexpected error retrieving crypto key: " + e.getMessage(), e);
      }
    } catch (IOException e) {
      throw new HalException(Problem.Severity.FATAL, "Unexpected error retrieving crypto key: " + e.getMessage(), e);
    }

    if (cryptoKey == null) {
      String cryptoKeyName = cryptoKeyId.substring(cryptoKeyId.lastIndexOf('/') + 1);
      log.info("Creating a new crypto key " + cryptoKeyName);
      cryptoKey = createCryptoKey(cloudKms, keyRingId, cryptoKeyName);
    }

    return cryptoKey;
  }

  private static CryptoKey createCryptoKey(CloudKMS cloudKms, String keyRingId, String cryptoKeyName) {
    try {
      return cloudKms.projects()
          .locations()
          .keyRings()
          .cryptoKeys()
          .create(keyRingId, new CryptoKey().setPurpose(KEY_PURPOSE))
          .setCryptoKeyId(cryptoKeyName)
          .execute();
    } catch (IOException e) {
      throw new HalException(Problem.Severity.FATAL, "Failed to create a halyard crypto key: " + e.getMessage(), e);
    }
  }

  private static KeyRing ensureKeyRingExists(CloudKMS cloudKms, String locationId, String keyRingId) {
    KeyRing keyRing;
    try {
      keyRing = cloudKms.projects().locations().keyRings().get(keyRingId).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        keyRing = null;
      } else {
        throw new HalException(Problem.Severity.FATAL, "Unexpected error retrieving key ring: " + e.getMessage(), e);
      }
    } catch (IOException e) {
      throw new HalException(Problem.Severity.FATAL, "Unexpected error retrieving key ring: " + e.getMessage(), e);
    }

    if (keyRing == null) {
      String keyRingName = keyRingId.substring(keyRingId.lastIndexOf('/') + 1);
      log.info("Creating a new key ring " + keyRingName);
      keyRing = createKeyRing(cloudKms, locationId, keyRingName);
    }

    return keyRing;
  }

  private static KeyRing createKeyRing(CloudKMS cloudKms, String locationId, String keyRingName) {
    try {
      return cloudKms.projects()
          .locations()
          .keyRings()
          .create(locationId, new KeyRing())
          .setKeyRingId(keyRingName)
          .execute();
    } catch (IOException e) {
      throw new HalException(Problem.Severity.FATAL, "Failed to create a halyard key ring: " + e.getMessage(), e);
    }
  }

  private static GoogleCredential loadKmsCredential(HttpTransport transport, JsonFactory factory, String jsonPath) throws IOException {
    GoogleCredential credential;
    if (!jsonPath.isEmpty()) {
      FileInputStream stream = new FileInputStream(jsonPath);
      credential = GoogleCredential.fromStream(stream, transport, factory);
      log.info("Loaded kms credentials from " + jsonPath);
    } else {
      log.info("Using kms default application credentials.");
      credential = GoogleCredential.getApplicationDefault();
    }

    if (credential.createScopedRequired()) {
      credential = credential.createScoped(CloudKMSScopes.all());
    }

    return credential;
  }
}
