/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spectator.stackdriver;

import com.google.api.services.monitoring.v3.model.MonitoredResource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a MonitoredResource instance to attach to TimeSeries data.
 *
 * <p>This will build different resource types depending on where the runtime is deployed.
 */
class MonitoredResourceBuilder {
  private String stackdriverProjectId = "";

  /** Constructor. */
  MonitoredResourceBuilder() {
    // empty.
  }

  /**
   * The GCP project that the data should be stored under.
   *
   * <p>This is not always used. It depends on the what the managed resource will be. Setting it
   * makes it available later if it is needed.
   */
  MonitoredResourceBuilder setStackdriverProject(String name) {
    stackdriverProjectId = name;
    return this;
  }

  /** This is the project that we will store data under. */
  public String determineProjectName(String defaultProjectName) {
    if (defaultProjectName != null && !defaultProjectName.isEmpty()) {
      return defaultProjectName;
    }
    try {
      return getGoogleMetadataValue("project/project-id");
    } catch (IOException ioex) {
      return defaultProjectName;
    }
  }

  /** Helper function to read the result from a HTTP GET. */
  private String getConnectionValue(HttpURLConnection con) throws IOException {
    int responseCode = con.getResponseCode();
    if (responseCode < 200 || responseCode > 299) {
      throw new IOException("Unexpected responseCode " + responseCode);
    }
    BufferedReader input =
        new BufferedReader(new InputStreamReader(con.getInputStream(), "US-ASCII"));
    StringBuffer value = new StringBuffer();
    for (String line = input.readLine(); line != null; line = input.readLine()) {
      value.append(line);
    }
    input.close();
    return value.toString();
  }

  /**
   * Helper function to read a value from the GCP Metadata Service.
   *
   * <p>This will throw an IOException if not on GCP.
   */
  String getGoogleMetadataValue(String key) throws IOException {
    URL url = new URL(String.format("http://169.254.169.254/computeMetadata/v1/%s", key));
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setConnectTimeout(1000);
    con.setInstanceFollowRedirects(true);
    con.setRequestMethod("GET");
    con.setRequestProperty("Metadata-Flavor", "Google");
    return getConnectionValue(con);
  }

  /**
   * Collect the GCP labels to use for a gce_instance resource.
   *
   * <p>This will return false if not on a GCE instance.
   */
  boolean maybeCollectGceInstanceLabels(Map<String, String> labels) {
    try {
      String id = getGoogleMetadataValue("instance/id");
      String zone = getGoogleMetadataValue("instance/zone");
      zone = zone.substring(zone.lastIndexOf('/') + 1);
      labels.put("instance_id", id);
      labels.put("zone", zone);
      // project_id is only intended for reading since it is already a URL parameter.

      return true;
    } catch (IOException ioex) {
      return false;
    }
  }

  /**
   * Collect the GKE labels to use for a gke_container resource.
   *
   * <p>This will return false if not on a GKE instance.
   */
  boolean maybeCollectGkeInstanceLabels(Map<String, String> labels) {
    // Not sure how to get the info I need
    /*
     * project_id: The identifier of the GCP project associated with this.
     * cluster_name: An immutable name for the cluster the container is in.
     * namespace_id: Immutable ID of the cluster namespace the container is in.
     * instance_id: Immutable ID of the GCE instance the container is in.
     * pod_id: Immutable ID of the pod the container is in.
     * container_name: Immutable name of the container.
     * zone: The GCE zone in which the instance is running.
     */
    return false;
  }

  /**
   * Return the attribute value associated with the key.
   *
   * <p>Uses regexp matching, returning "" if the key isnt found.
   */
  String matchAttribute(String text, String key) {
    String regex = String.format("\"%s\" : \"(.+?)\"", key);
    Pattern p = Pattern.compile(regex);
    Matcher m = p.matcher(text);
    return m.find() ? m.group(1) : "";
  }

  /**
   * Return the AWS identify document from the AWS Metadata Service.
   *
   * <p>Throws an IOException if not running on an AWS instance.
   */
  String getAwsIdentityDocument() throws IOException {
    URL url = new URL("http://169.254.169.254/latest/dynamic/instance-identity/document");
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setConnectTimeout(1000);
    con.setRequestMethod("GET");
    return getConnectionValue(con);
  }

  /**
   * Collect the EC2 labels to use for a aws_ec2_instance resource.
   *
   * <p>Returns false if not an AWS ec2 instance.
   */
  boolean maybeCollectEc2InstanceLabels(Map<String, String> labels) {
    String doc;
    try {
      doc = getAwsIdentityDocument();
    } catch (IOException ioex) {
      return false;
    }
    String id = matchAttribute(doc, "instanceId");
    String account = matchAttribute(doc, "accountId");
    String region = matchAttribute(doc, "region");

    if (id.isEmpty() || account.isEmpty() || region.isEmpty()) {
      return false;
    } else if (stackdriverProjectId.isEmpty()) {
      throw new IllegalStateException("stackdriverProjectId was not set.");
    }

    labels.put("instance_id", id);
    labels.put("region", region);
    labels.put("aws_account", account);
    labels.put("project_id", stackdriverProjectId);
    return true;
  }

  /**
   * Create a MonitoredResource that describes this deployment.
   *
   * <p>This will throw an IOException if the resource could not be created. In practice this
   * exception is not currently thrown.
   *
   * <p>However the expectation is that custom types will be added in the future and there may be
   * transient IO errors interacting with Stackdriver to create the type.
   */
  public MonitoredResource build() throws IOException {
    HashMap<String, String> labels = new HashMap<String, String>();
    MonitoredResource resource = new MonitoredResource();

    if (maybeCollectGceInstanceLabels(labels)) {
      resource.setType("gce_instance");
    } else if (maybeCollectGkeInstanceLabels(labels)) {
      resource.setType("gke_container");
    } else if (maybeCollectEc2InstanceLabels(labels)) {
      resource.setType("aws_ec2_instance");
    } else if (stackdriverProjectId.isEmpty()) {
      throw new IllegalStateException("stackdriverProjectId was not set.");
    } else {
      labels.put("project_id", stackdriverProjectId);
      resource.setType("global");
    }

    resource.setLabels(labels);
    return resource;
  }
}
