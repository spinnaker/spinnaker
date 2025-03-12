/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.model;

import com.google.api.services.run.v1.model.ServiceStatus;
import java.util.LinkedHashMap;

public class CloudrunService extends com.google.api.client.json.GenericJson {

  /**
   * The API version for this call such as "serving.knative.dev/v1". The value may be {@code null}.
   */
  @com.google.api.client.util.Key private java.lang.String apiVersion;

  /** The kind of resource, in this case "Service". The value may be {@code null}. */
  @com.google.api.client.util.Key private java.lang.String kind;

  /**
   * Metadata associated with this Service, including name, namespace, labels, and annotations. The
   * value may be {@code null}.
   */
  @com.google.api.client.util.Key private LinkedHashMap<String, Object> metadata;

  /**
   * Spec holds the desired state of the Service (from the client). The value may be {@code null}.
   */
  @com.google.api.client.util.Key private LinkedHashMap<String, Object> spec;

  /**
   * Status communicates the observed state of the Service (from the controller). The value may be
   * {@code null}.
   */
  @com.google.api.client.util.Key private ServiceStatus status;

  /**
   * The API version for this call such as "serving.knative.dev/v1".
   *
   * @return value or {@code null} for none
   */
  public java.lang.String getApiVersion() {
    return apiVersion;
  }

  /**
   * The API version for this call such as "serving.knative.dev/v1".
   *
   * @param apiVersion apiVersion or {@code null} for none
   */
  public CloudrunService setApiVersion(java.lang.String apiVersion) {
    this.apiVersion = apiVersion;
    return this;
  }

  /**
   * The kind of resource, in this case "Service".
   *
   * @return value or {@code null} for none
   */
  public java.lang.String getKind() {
    return kind;
  }

  /**
   * The kind of resource, in this case "Service".
   *
   * @param kind kind or {@code null} for none
   */
  public CloudrunService setKind(java.lang.String kind) {
    this.kind = kind;
    return this;
  }

  /**
   * Metadata associated with this Service, including name, namespace, labels, and annotations.
   *
   * @return value or {@code null} for none
   */
  public LinkedHashMap<String, Object> getMetadata() {
    return metadata;
  }

  /**
   * Metadata associated with this Service, including name, namespace, labels, and annotations.
   *
   * @param metadata metadata or {@code null} for none
   */
  public CloudrunService setMetadata(LinkedHashMap<String, Object> metadata) {
    this.metadata = metadata;
    return this;
  }

  /**
   * Spec holds the desired state of the Service (from the client).
   *
   * @return value or {@code null} for none
   */
  public LinkedHashMap<String, Object> getSpec() {
    return spec;
  }

  /**
   * Spec holds the desired state of the Service (from the client).
   *
   * @param spec spec or {@code null} for none
   */
  public CloudrunService setSpec(LinkedHashMap<String, Object> spec) {
    this.spec = spec;
    return this;
  }

  /**
   * Status communicates the observed state of the Service (from the controller).
   *
   * @return value or {@code null} for none
   */
  public ServiceStatus getStatus() {
    return status;
  }

  /**
   * Status communicates the observed state of the Service (from the controller).
   *
   * @param status status or {@code null} for none
   */
  public CloudrunService setStatus(ServiceStatus status) {
    this.status = status;
    return this;
  }

  @Override
  public CloudrunService set(String fieldName, Object value) {
    return (CloudrunService) super.set(fieldName, value);
  }

  @Override
  public CloudrunService clone() {
    return (CloudrunService) super.clone();
  }
}
