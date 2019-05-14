/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.lifecycle;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.Activity;
import com.amazonaws.services.autoscaling.model.DescribeScalingActivitiesRequest;
import com.amazonaws.services.autoscaling.model.DescribeScalingActivitiesResult;
import com.amazonaws.services.autoscaling.model.ScalingActivityStatusCode;
import com.netflix.spinnaker.cats.agent.RunnableAgent;
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent;
import com.netflix.spinnaker.clouddriver.model.EntityTags;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.tags.EntityTagger;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LaunchFailureNotificationCleanupAgent implements RunnableAgent, CustomScheduledAgent {
  private static final Logger log = LoggerFactory.getLogger(LaunchFailureNotificationAgent.class);

  private static final String TAG_NAME = "spinnaker_ui_alert:autoscaling:ec2_instance_launch_error";
  private static final int MAX_RESULTS = 10000;

  private final AmazonClientProvider amazonClientProvider;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final EntityTagger serverGroupTagger;

  LaunchFailureNotificationCleanupAgent(
      AmazonClientProvider amazonClientProvider,
      AccountCredentialsProvider accountCredentialsProvider,
      EntityTagger serverGroupTagger) {
    this.amazonClientProvider = amazonClientProvider;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.serverGroupTagger = serverGroupTagger;
  }

  @Override
  public String getAgentType() {
    return LaunchFailureNotificationCleanupAgent.class.getSimpleName();
  }

  @Override
  public String getProviderName() {
    return AwsProvider.PROVIDER_NAME;
  }

  @Override
  public long getPollIntervalMillis() {
    return TimeUnit.MINUTES.toMillis(5);
  }

  @Override
  public long getTimeoutMillis() {
    return -1;
  }

  @Override
  public void run() {
    Collection<EntityTags> taggedEntities =
        serverGroupTagger.taggedEntities(
            AmazonCloudProvider.ID,
            null, // all accounts
            EntityTagger.ENTITY_TYPE_SERVER_GROUP,
            TAG_NAME,
            MAX_RESULTS);

    taggedEntities.forEach(
        entityTags -> {
          EntityTags.EntityRef entityRef = entityTags.getEntityRef();
          Optional<NetflixAmazonCredentials> credentials =
              Optional.ofNullable(accountCredentialsProvider.getCredentials(entityRef.getAccount()))
                  .filter((c) -> c instanceof NetflixAmazonCredentials)
                  .map(NetflixAmazonCredentials.class::cast);

          if (!credentials.isPresent()) {
            log.warn(
                "No account configuration for {}. Unable to determine if '{}' has launch failures",
                entityRef.getAccount(),
                entityTags.getId());
            return;
          }

          AmazonAutoScaling amazonAutoScaling =
              amazonClientProvider.getAutoScaling(credentials.get(), entityRef.getRegion());

          try {
            if (hasLaunchFailures(amazonAutoScaling, entityTags)) {
              return;
            }

            serverGroupTagger.delete(
                AmazonCloudProvider.ID,
                entityRef.getAccountId(),
                entityRef.getRegion(),
                EntityTagger.ENTITY_TYPE_SERVER_GROUP,
                entityRef.getEntityId(),
                TAG_NAME);
          } catch (Exception e) {
            log.error("Unable to determine if '{}' has launch failures", entityTags.getId(), e);
          }
        });
  }

  /**
   * Fetch scaling activities and determine if the most recent activity was successful.
   *
   * <p>A successful scaling activity is sufficient to indicate that a server group is no longer
   * having launch failures.
   */
  protected boolean hasLaunchFailures(AmazonAutoScaling amazonAutoScaling, EntityTags entityTags) {
    EntityTags.EntityRef entityRef = entityTags.getEntityRef();

    try {
      DescribeScalingActivitiesResult describeScalingActivitiesResult =
          amazonAutoScaling.describeScalingActivities(
              new DescribeScalingActivitiesRequest()
                  .withAutoScalingGroupName(entityRef.getEntityId()));

      List<Activity> activities = describeScalingActivitiesResult.getActivities();
      return !activities.isEmpty()
          && !activities
              .get(0)
              .getStatusCode()
              .equals(ScalingActivityStatusCode.Successful.toString());
    } catch (Exception e) {
      AmazonServiceException amazonServiceException = amazonServiceException(e);
      if (amazonServiceException != null) {
        if (amazonServiceException.getErrorMessage().toLowerCase().contains("name not found")) {
          return false;
        }
      }

      throw e;
    }
  }

  private static AmazonServiceException amazonServiceException(Exception e) {
    if (e instanceof AmazonServiceException) {
      return (AmazonServiceException) e;
    }

    if (!(e instanceof UndeclaredThrowableException)) {
      return null;
    }

    UndeclaredThrowableException ute = (UndeclaredThrowableException) e;

    if (!(ute.getUndeclaredThrowable() instanceof InvocationTargetException)) {
      return null;
    }

    InvocationTargetException ite = (InvocationTargetException) ute.getUndeclaredThrowable();
    if (!(ite.getTargetException() instanceof AmazonServiceException)) {
      return null;
    }

    return (AmazonServiceException) ite.getTargetException();
  }
}
