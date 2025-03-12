/*
 * Copyright 2018 Datadog, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.s3;

import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactProvider;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("artifacts.s3")
final class S3ArtifactProviderProperties implements ArtifactProvider<S3ArtifactAccount> {
  private boolean enabled;
  private List<S3ArtifactAccount> accounts = new ArrayList<>();

  /**
   * See
   * https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/ClientConfiguration.html#setClientExecutionTimeout-int-
   */
  private Integer clientExecutionTimeout;

  /**
   * See
   * https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/ClientConfiguration.html#setConnectionMaxIdleMillis-long-
   */
  private Long connectionMaxIdleMillis;

  /**
   * See
   * https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/ClientConfiguration.html#setConnectionTimeout-int-
   */
  private Integer connectionTimeout;

  /**
   * See
   * https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/ClientConfiguration.html#setConnectionTTL-long-
   * The units are milliseconds.
   */
  private Long connectionTTL;

  /**
   * See
   * https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/ClientConfiguration.html#setMaxConnections-int-
   */
  private Integer maxConnections;

  /**
   * See
   * https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/ClientConfiguration.html#setRequestTimeout-int-
   * The units are milliseconds.
   */
  private Integer requestTimeout;

  /**
   * See
   * https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/ClientConfiguration.html#setSocketTimeout-int-
   * The units are milliseconds.
   */
  private Integer socketTimeout;

  /**
   * See
   * https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/ClientConfiguration.html#setValidateAfterInactivityMillis-int-
   */
  private Integer validateAfterInactivityMillis;
}
