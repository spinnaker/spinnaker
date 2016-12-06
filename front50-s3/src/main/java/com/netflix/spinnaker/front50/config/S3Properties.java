/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.front50.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties("spinnaker.s3")
public class S3Properties extends S3BucketProperties {
  String rootFolder;

  @NestedConfigurationProperty
  S3FailoverProperties failover = new S3FailoverProperties();

  public String getRootFolder() {
    return rootFolder;
  }

  public void setRootFolder(String rootFolder) {
    this.rootFolder = rootFolder;
  }

  public S3FailoverProperties getFailover() {
    return failover;
  }

  public void setFailover(S3FailoverProperties failover) {
    this.failover = failover;
  }

  public boolean isFailoverEnabled() {
    return failover != null && failover.enabled;
  }

  @Override
  public String getBucket() {
    if (isFailoverEnabled()) {
      return failover.getBucket();
    }
    return super.getBucket();
  }

  @Override
  public String getRegion() {
    if (isFailoverEnabled()) {
      return failover.getRegion();
    }
    return super.getRegion();
  }

  @Override
  public String getProxyHost() {
    if (isFailoverEnabled()) {
      return failover.getProxyHost();
    }
    return super.getProxyHost();
  }

  @Override
  public String getProxyPort() {
    if (isFailoverEnabled()) {
      return failover.getProxyPort();
    }
    return super.getProxyPort();
  }

  @Override
  public String getProxyProtocol() {
    if (isFailoverEnabled()) {
      return failover.getProxyProtocol();
    }
    return super.getProxyProtocol();
  }
}
