/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.igor.gcb.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class GoogleCloudStorageObject {
  private static final String PREFIX = "gs://";
  private final String bucket;
  private final String object;
  private final Long version;

  public static GoogleCloudStorageObject fromReference(String reference) {
    String working = reference;
    String bucket;
    String object;
    Long version;

    if (working.startsWith(PREFIX)) {
      working = working.substring(PREFIX.length());
    } else {
      throw new IllegalArgumentException(
          String.format("Google Cloud Storage path must begin with %s: %s", PREFIX, working));
    }

    if (working.contains("/")) {
      int index = working.indexOf("/");
      bucket = working.substring(0, index);
      working = working.substring(index + 1);
    } else {
      throw new IllegalArgumentException("Google Cloud Storage path must begin with %s: %s");
    }

    if (working.contains("#")) {
      int index = working.indexOf("#");
      object = working.substring(0, index);
      working = working.substring(index + 1);
    } else {
      object = working;
      working = "";
    }

    if (working.equals("")) {
      version = null;
    } else {
      version = Long.parseLong(working);
    }

    return new GoogleCloudStorageObject(bucket, object, version);
  }

  public String getVersionString() {
    if (version == null) {
      return null;
    }
    return version.toString();
  }

  public String getReference() {
    String path = getName();
    if (version == null) {
      return path;
    }

    return String.format("%s#%s", path, version);
  }

  public String getName() {
    return String.format("gs://%s/%s", bucket, object);
  }
}
