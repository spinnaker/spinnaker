/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.halyard.deploy.config.v1.secrets;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import com.netflix.spinnaker.halyard.config.model.v1.node.Secret;
import com.netflix.spinnaker.halyard.config.model.v1.node.SecretFile;
import com.netflix.spinnaker.halyard.core.secrets.v1.SecretSessionManager;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * DecryptingObjectMapper serializes (part of) Halyard configurations, decrypting secrets contained
 * in fields annotated with @Secret.
 *
 * <p>It also decrypts the content of secret files, assign them a random name, and store them in
 * {@link Profile} to be serialized later.
 *
 * <p>decryptedOutputDirectory is the path to the decrypted secret files on the service's host.
 */
public class DecryptingObjectMapper extends ObjectMapper {

  protected Profile profile;
  protected Path decryptedOutputDirectory;
  protected SecretSessionManager secretSessionManager;
  protected boolean decryptAllSecrets;

  public DecryptingObjectMapper(
      SecretSessionManager secretSessionManager,
      Profile profile,
      Path decryptedOutputDirectory,
      boolean decryptAllSecrets) {
    super();
    this.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    this.setSerializationInclusion(JsonInclude.Include.NON_NULL);

    this.secretSessionManager = secretSessionManager;
    this.profile = profile;
    this.decryptedOutputDirectory = decryptedOutputDirectory;
    this.decryptAllSecrets = decryptAllSecrets;

    SimpleModule module = new SimpleModule();
    module.setSerializerModifier(
        new BeanSerializerModifier() {

          @Override
          public List<BeanPropertyWriter> changeProperties(
              SerializationConfig config,
              BeanDescription beanDesc,
              List<BeanPropertyWriter> beanProperties) {
            for (BeanPropertyWriter bpw : beanProperties) {
              Secret secret = bpw.getAnnotation(Secret.class);
              if (secret != null && (decryptAllSecrets || secret.alwaysDecrypt())) {
                bpw.assignSerializer(getSecretSerializer());
              }
              SecretFile secretFile = bpw.getAnnotation(SecretFile.class);
              if (secretFile != null) {
                boolean shouldDecrypt = (decryptAllSecrets || secretFile.alwaysDecrypt());
                bpw.assignSerializer(getSecretFileSerializer(bpw, secretFile, shouldDecrypt));
              }
            }
            return beanProperties;
          }
        });
    this.registerModule(module);
  }

  protected StdScalarSerializer<Object> getSecretSerializer() {
    return new StdScalarSerializer<Object>(String.class, false) {
      @Override
      public void serialize(Object value, JsonGenerator gen, SerializerProvider provider)
          throws IOException {
        if (value != null) {
          String sValue = value.toString();
          if (EncryptedSecret.isEncryptedSecret(sValue)) {
            gen.writeString(secretSessionManager.decrypt(sValue));
          } else {
            gen.writeString(sValue);
          }
        }
      }
    };
  }

  protected StdScalarSerializer<Object> getSecretFileSerializer(
      BeanPropertyWriter beanPropertyWriter, SecretFile annotation, boolean shouldDecrypt) {
    return new StdScalarSerializer<Object>(String.class, false) {
      @Override
      public void serialize(Object value, JsonGenerator gen, SerializerProvider provider)
          throws IOException {
        if (value != null) {
          String sValue = value.toString();
          if (!EncryptedSecret.isEncryptedSecret(sValue) && !isURL(sValue)) {
            // metadataUrl is either a URL or a filepath, so only add prefix if it's a path
            sValue = annotation.prefix() + sValue;
          }
          if (EncryptedSecret.isEncryptedSecret(sValue) && shouldDecrypt) {
            // Decrypt the content of the file and store on the profile under a random
            // generated file name
            String name = newRandomFilePath(beanPropertyWriter.getName());
            byte[] bytes = secretSessionManager.decryptAsBytes(sValue);
            profile.getDecryptedFiles().put(name, bytes);
            sValue = annotation.prefix() + getCompleteFilePath(name);
          }
          gen.writeString(sValue);
        }
      }
    };
  }

  public DecryptingObjectMapper relax() {
    this.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    this.configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, false);
    return this;
  }

  protected String newRandomFilePath(String fieldName) {
    return fieldName + "-" + RandomStringUtils.randomAlphanumeric(5);
  }

  protected String getCompleteFilePath(String filename) {
    return Paths.get(decryptedOutputDirectory.toString(), filename).toString();
  }

  private boolean isURL(String property) {
    try {
      URL url = new URL(property);
      url.toURI();
      return true;
    } catch (Exception exception) {
      return false;
    }
  }
}
