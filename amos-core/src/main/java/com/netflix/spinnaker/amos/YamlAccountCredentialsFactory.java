/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.amos;

import org.yaml.snakeyaml.Yaml;

import java.io.*;

/**
 * This implementation will load {@link com.netflix.spinnaker.amos.AccountCredentials} objects from YAML configuration.
 * According to the contract of the interface, {@link com.netflix.spinnaker.amos.AccountCredentials} objects can be derived from
 * an input stream of YAML, a String of YAML, or some YAML configuration file on the file system.
 *
 * @author Dan Woods
 */
public class YamlAccountCredentialsFactory implements AccountCredentialsFactory {
    private Yaml yaml;

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends AccountCredentials> T load(InputStream stream, Class<T> clazz) {
        return getYaml().loadAs(stream, clazz);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends AccountCredentials> T load(String text, Class<T> clazz) {
        return getYaml().loadAs(text, clazz);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends AccountCredentials> T load(File file, Class<T> clazz) {
        try(FileInputStream fis = new FileInputStream(file);) {
            return load(fis, clazz);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Sets a specific implementation of the {@link org.yaml.snakeyaml.Yaml} processor. If this is not specified, then
     * the library's default will be chosen lazily.
     *
     * @param yaml
     */
    public void setYaml(Yaml yaml) {
        this.yaml = yaml;
    }

    private Yaml getYaml() {
        if (this.yaml == null) {
            this.yaml = new Yaml();
        }
        return this.yaml;
    }
}
