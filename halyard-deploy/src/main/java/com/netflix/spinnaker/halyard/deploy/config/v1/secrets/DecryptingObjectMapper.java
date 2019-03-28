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
import com.netflix.spinnaker.config.secrets.EncryptedSecret;
import com.netflix.spinnaker.halyard.config.model.v1.node.Secret;
import com.netflix.spinnaker.halyard.config.model.v1.node.SecretFile;
import com.netflix.spinnaker.halyard.core.secrets.v1.SecretSessionManager;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import org.apache.commons.lang.RandomStringUtils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * DecryptingObjectMapper serializes (part of) Halyard configurations, decrypting secrets contained in fields
 * annotated with @Secret.
 *
 * It also decrypts the content of secret files, assign them a random name, and store them
 * in {@link Profile} to be serialized later.
 *
 * decryptedOutputDirectory is the path to the decrypted secret files on the service's host.
 */
public class DecryptingObjectMapper extends ObjectMapper {

    protected Profile profile;
    protected Path decryptedOutputDirectory;
    protected SecretSessionManager secretSessionManager;

    public DecryptingObjectMapper(SecretSessionManager secretSessionManager, Profile profile, Path decryptedOutputDirectory) {
        super();
        this.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        this.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        this.secretSessionManager = secretSessionManager;
        this.profile = profile;
        this.decryptedOutputDirectory = decryptedOutputDirectory;

        SimpleModule module = new SimpleModule();
        module.setSerializerModifier(new BeanSerializerModifier() {

            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties) {
                Class _class = beanDesc.getBeanClass();

                for (BeanPropertyWriter bpw : beanProperties) {
                    Annotation annotation = getSecretFieldAnnotationType(_class, bpw.getName());
                    if (annotation != null) {
                        if (annotation.annotationType() == Secret.class) {
                            // Decrypt the field secret before sending
                            bpw.assignSerializer(getSecretSerializer());
                        } else if (annotation.annotationType() == SecretFile.class) {
                            bpw.assignSerializer(getSecretFileSerializer(bpw, (SecretFile) annotation));
                        }
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
            public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                if (value != null) {
                    String sValue = value.toString();
                    if (!EncryptedSecret.isEncryptedSecret(sValue)) {
                        gen.writeString(sValue);
                    } else {
                        gen.writeString(secretSessionManager.decrypt(sValue));
                    }
                }
            }
        };
    }

    protected StdScalarSerializer<Object> getSecretFileSerializer(BeanPropertyWriter beanPropertyWriter, SecretFile annotation) {
        return new StdScalarSerializer<Object>(String.class, false) {
            @Override
            public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                if (value != null) {
                    String sValue = value.toString();
                    if (EncryptedSecret.isEncryptedSecret(sValue)) {
                        // Decrypt the content of the file and store on the profile under a random
                        // generated file name
                        String decrypted = secretSessionManager.decrypt(sValue);
                        String name = newRandomFilePath(beanPropertyWriter.getName());
                        profile.getDecryptedFiles().put(name, decrypted);
                        sValue = getCompleteFilePath(name);
                    }
                    if (annotation != null) {
                        sValue = annotation.prefix() + sValue;
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

    protected Annotation getSecretFieldAnnotationType(Class _class, String fieldName) {
        for (Field f : _class.getDeclaredFields()) {
            if (f.getName().equals(fieldName)) {
                if (f.isAnnotationPresent(Secret.class)) {
                    return f.getAnnotation(Secret.class);
                }
                if (f.isAnnotationPresent(SecretFile.class)) {
                    return f.getAnnotation(SecretFile.class);
                }
                return null;
            }
        }
        if (_class.getSuperclass() != null) {
            return getSecretFieldAnnotationType(_class.getSuperclass(), fieldName);
        }
        return null;
    }

    protected SecretFile getFieldSecretTypeAnnotation(Class _class, String fieldName) {
        for (Field f : _class.getDeclaredFields()) {
            if (f.getName().equals(fieldName)) {
                SecretFile sf = f.getAnnotation(SecretFile.class);
                if (sf != null) {
                    return sf;
                }
                break;
            }
        }
        if (_class.getSuperclass() != null) {
            return getFieldSecretTypeAnnotation(_class.getSuperclass(), fieldName);
        }
        return null;
    }

    protected String newRandomFilePath(String fieldName) {
        return fieldName + "-" + RandomStringUtils.randomAlphanumeric(5);
    }

    protected String getCompleteFilePath(String filename) {
        return Paths.get(decryptedOutputDirectory.toString(), filename).toString();
    }
}