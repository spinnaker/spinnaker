/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy;

import com.netflix.frigga.Names;
import java.util.Arrays;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Applies an application and cluster tag on ebs volumes.
 *
 * <p>By default tag names are of the form 'spinnaker:application' and 'spinnaker:cluster'.
 */
@Component
@ConditionalOnProperty(
    name = "aws.defaults.resourceTagging.enabled",
    havingValue = "true",
    matchIfMissing = false)
public class DefaultAmazonResourceTagger implements AmazonResourceTagger {
  private final String clusterTag;
  private final String applicationTag;

  @Autowired
  public DefaultAmazonResourceTagger(
      @Value("${aws.defaults.resourceTagging.applicationTag:spinnaker:application}")
          String applicationTag,
      @Value("${aws.defaults.resourceTagging.clusterTag:spinnaker:cluster}") String clusterTag) {
    this.clusterTag = clusterTag;
    this.applicationTag = applicationTag;
  }

  @NotNull
  @Override
  public Collection<Tag> volumeTags(@NotNull String serverGroupName) {
    Names names = Names.parseName(serverGroupName);

    return Arrays.asList(
        Tag.of(applicationTag, names.getApp()), Tag.of(clusterTag, names.getCluster()));
  }
}
