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

import java.io.File;
import java.io.InputStream;

/**
 * Implementations of this interface will provide helper functions for loading credentials objects from a variety of
 * sources.
 *
 * @author Dan Woods
 */
public interface AccountCredentialsFactory {

    /**
     * This method will load an {@link com.netflix.spinnaker.amos.AccountCredentials} object from a provided input stream.
     * The input stream may be, for example, reading the configuration from the classpath.
     *
     * @param stream
     * @param clazz
     * @param <T>
     * @return typed credentials object
     */
    <T extends AccountCredentials> T load(InputStream stream, Class<T> clazz);

    /**
     * This method will load an {@link com.netflix.spinnaker.amos.AccountCredentials} object from the provided string input.
     * Specific implementations will have to perform the translation of String type to typed credentials object.
     *
     * @param text
     * @param clazz
     * @param <T>
     * @return typed credentials object
     */
    <T extends AccountCredentials> T load(String text, Class<T> clazz);

    /**
     * This method will load an {@link com.netflix.spinnaker.amos.AccountCredentials} object from the file system.
     *
     * @param file
     * @param clazz
     * @param <T>
     * @return typed credentials object
     */
    <T extends AccountCredentials> T load(File file, Class<T> clazz);
}
