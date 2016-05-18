/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.front50.model;
import com.netflix.spinnaker.front50.exception.NotFoundException;

import java.util.Collection;
import java.util.Map;


public interface StorageService {
    /**
     * Check to see if the bucket exists, creating it if it is not there.
     */
    public void ensureBucketExists();

    /**
     * Returns true if the storage service supports versioning.
     */
    public boolean supportsVersioning();

    public <T extends Timestamped> T
           loadCurrentObject(String objectKey, String daoTypeName, Class<T> clas)
           throws NotFoundException;

    // There base mechanism and Timestamped need to work together better in order
    // to have the concept of a version. The store method should return the version
    // and the Timestamped object should know what its version is
    // (or VersionTimestamped). Otherwise it isnt practical to specify or implement
    // an interface for versioning.
    public <T extends Timestamped> T
           loadObjectVersion(String objectKey, String daoTypeName, Class<T> clas,
                             String versionId) throws NotFoundException;

    public void deleteObject(String objectKey, String daoTypeName);
    public <T extends Timestamped> void
           storeObject(String objectKey, String daoTypeName, T item);

    public Map<String, Long> listObjectKeys(String daoTypeName);

    public <T extends Timestamped> Collection<T>
           listObjectVersions(String objectKey, String daoTypeName, Class<T> clas,
                              int maxResults) throws NotFoundException;

    public long getLastModified(String daoTypeName);
}
