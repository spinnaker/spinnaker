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

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudkms.v1.CloudKMS;
import com.google.api.services.cloudkms.v1.CloudKMSScopes;
import com.google.api.services.cloudkms.v1.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
class GoogleKms {
  private final CloudKMS cloudKms;
  private final String projectId;
  private final String locationId;
  private final String keyRingId;
  private final String cryptoKeyId;
  private final KeyRing keyRing;
  private final CryptoKey cryptoKey;

  private GoogleCredentials credentials;

  private static final String KEY_PURPOSE = "ENCRYPT_DECRYPT";

  GoogleKms(GoogleSecureStorageProperties properties) {
    cloudKms = buildCredentials(properties);
    projectId = properties.getProject();
    locationId =
        locationId(
            projectId,
            StringUtils.isEmpty(properties.getKeyRingLocation())
                ? "global"
                : properties.getKeyRingLocation());
    keyRingId =
        keyRingId(
            locationId,
            StringUtils.isEmpty(properties.getKeyRingName())
                ? "halyard"
                : properties.getKeyRingName());
    cryptoKeyId =
        cryptoKeyId(
            keyRingId,
            StringUtils.isEmpty(properties.getCryptoKeyName())
                ? "config"
                : properties.getCryptoKeyName());

    keyRing = ensureKeyRingExists(cloudKms, locationId, keyRingId);
    cryptoKey = ensureCryptoKeyExists(cloudKms, credentials, keyRingId, cryptoKeyId);
  }

  byte[] encryptContents(String plaintext) {
    plaintext = Base64.getEncoder().encodeToString(plaintext.getBytes());
    EncryptRequest encryptRequest = new EncryptRequest().encodePlaintext(plaintext.getBytes());
    EncryptResponse response;
    try {
      response =
          cloudKms
              .projects()
              .locations()
              .keyRings()
              .cryptoKeys()
              .encrypt(cryptoKey.getName(), encryptRequest)
              .execute();
    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.FATAL, "Failed to encrypt user data: " + e.getMessage(), e);
    }

    return response.decodeCiphertext();
  }

  private CloudKMS buildCredentials(GoogleSecureStorageProperties properties) {
    HttpTransport transport = new NetHttpTransport();
    JsonFactory jsonFactory = new JacksonFactory();
    try {
      credentials = loadKmsCredential(properties.getJsonPath());
    } catch (IOException e) {
      throw new RuntimeException("Unable to load KMS credentials: " + e.getMessage(), e);
    }

    return new CloudKMS.Builder(transport, jsonFactory, new HttpCredentialsAdapter(credentials))
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

  private static CryptoKey ensureCryptoKeyExists(
      CloudKMS cloudKms, GoogleCredentials credentials, String keyRingId, String cryptoKeyId) {
    CryptoKey cryptoKey;
    try {
      cryptoKey =
          cloudKms.projects().locations().keyRings().cryptoKeys().get(cryptoKeyId).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        cryptoKey = null;
      } else {
        throw new HalException(
            Problem.Severity.FATAL, "Unexpected error retrieving crypto key: " + e.getMessage(), e);
      }
    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.FATAL, "Unexpected error retrieving crypto key: " + e.getMessage(), e);
    }

    if (cryptoKey == null) {
      String cryptoKeyName = cryptoKeyId.substring(cryptoKeyId.lastIndexOf('/') + 1);
      log.info("Creating a new crypto key " + cryptoKeyName);
      if (credentials instanceof ServiceAccountCredentials) {
        String user =
            "serviceAccount:" + ((ServiceAccountCredentials) credentials).getClientEmail();
        cryptoKey = createCryptoKey(cloudKms, keyRingId, cryptoKeyName, user);
      } else {
        throw new HalException(
            Problem.Severity.FATAL,
            "Credentials are not an instance of ServiceAccountCredentials: " + credentials);
      }
    }

    return cryptoKey;
  }

  private static CryptoKey createCryptoKey(
      CloudKMS cloudKms, String keyRingId, String cryptoKeyName, String user) {
    CryptoKey cryptoKey;
    try {
      cryptoKey =
          cloudKms
              .projects()
              .locations()
              .keyRings()
              .cryptoKeys()
              .create(keyRingId, new CryptoKey().setPurpose(KEY_PURPOSE))
              .setCryptoKeyId(cryptoKeyName)
              .execute();

    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.FATAL, "Failed to create a halyard crypto key: " + e.getMessage(), e);
    }

    Policy policy = getCryptoKeyPolicy(cloudKms, cryptoKey.getName());
    policy.setBindings(
        Collections.singletonList(
            new Binding()
                .setRole("roles/cloudkms.cryptoKeyEncrypterDecrypter")
                .setMembers(Collections.singletonList(user))));

    log.info("Updating iam policy for " + cryptoKey.getName());
    setCryptoKeyPolicy(cloudKms, cryptoKey.getName(), policy);

    return cryptoKey;
  }

  private static void setCryptoKeyPolicy(CloudKMS cloudKms, String cryptoKeyId, Policy policy) {
    try {
      SetIamPolicyRequest iamPolicyRequest = new SetIamPolicyRequest().setPolicy(policy);
      cloudKms
          .projects()
          .locations()
          .keyRings()
          .cryptoKeys()
          .setIamPolicy(cryptoKeyId, iamPolicyRequest)
          .execute();
    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.FATAL, "Failed to set crypo key policy: " + e.getMessage(), e);
    }
  }

  private static Policy getCryptoKeyPolicy(CloudKMS cloudKms, String cryptoKeyId) {
    try {
      return cloudKms
          .projects()
          .locations()
          .keyRings()
          .cryptoKeys()
          .getIamPolicy(cryptoKeyId)
          .execute();
    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.FATAL, "Failed to load crypo key policy: " + e.getMessage(), e);
    }
  }

  private static KeyRing ensureKeyRingExists(
      CloudKMS cloudKms, String locationId, String keyRingId) {
    KeyRing keyRing;
    try {
      keyRing = cloudKms.projects().locations().keyRings().get(keyRingId).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        keyRing = null;
      } else {
        throw new HalException(
            Problem.Severity.FATAL, "Unexpected error retrieving key ring: " + e.getMessage(), e);
      }
    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.FATAL, "Unexpected error retrieving key ring: " + e.getMessage(), e);
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
      return cloudKms
          .projects()
          .locations()
          .keyRings()
          .create(locationId, new KeyRing())
          .setKeyRingId(keyRingName)
          .execute();
    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.FATAL, "Failed to create a halyard key ring: " + e.getMessage(), e);
    }
  }

  private static GoogleCredentials loadKmsCredential(String jsonPath) throws IOException {
    GoogleCredentials credentials;
    if (!jsonPath.isEmpty()) {
      FileInputStream stream = new FileInputStream(jsonPath);
      credentials = GoogleCredentials.fromStream(stream);
      log.info("Loaded kms credentials from " + jsonPath);
    } else {
      log.info("Using kms default application credentials.");
      credentials = GoogleCredentials.getApplicationDefault();
    }

    if (credentials.createScopedRequired()) {
      credentials = credentials.createScoped(CloudKMSScopes.all());
    }

    return credentials;
  }
}
