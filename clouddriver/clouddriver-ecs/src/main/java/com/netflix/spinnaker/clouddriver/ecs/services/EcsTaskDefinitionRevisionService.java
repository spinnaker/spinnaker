/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.clouddriver.ecs.services;

import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsTaskDefinitionRevision;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.ListTaskDefinitionsRequest;
import software.amazon.awssdk.services.ecs.model.ListTaskDefinitionsResponse;
import software.amazon.awssdk.services.ecs.model.SortOrder;
import software.amazon.awssdk.services.ecs.model.TaskDefinitionStatus;

/**
 * Lists the {@code ACTIVE} task-definition revisions for an {@code ecs-native} service's family,
 * newest first, to back the Deck rollback picker (roll a durable service back to an earlier
 * revision of the same family).
 *
 * <p>This is a live {@code ListTaskDefinitions} call, not cache-backed: the task-definition cache
 * only ever holds revisions currently referenced by a live service and is authoritatively pruned,
 * so it cannot enumerate a family's history. The result is attached to the {@code ecs-native}
 * server group only on the details path (see {@code EcsServerClusterProvider}), so classic {@code
 * ecs} services and the high-volume list/summary calls never trigger it.
 */
@Component
public class EcsTaskDefinitionRevisionService {

  /**
   * The maximum number of task-definition ARNs to return. ECS returns revisions newest-first, so
   * this caps how far back the picker looks without loading task-definition bodies.
   */
  static final int MAX_REVISIONS = 10;

  /**
   * Page size for {@code ListTaskDefinitions}; 100 is the API maximum. Sibling families sharing the
   * prefix must be paged past, so the largest page keeps that to as few calls as possible.
   */
  static final int LIST_PAGE_SIZE = 100;

  /**
   * Upper bound on {@code ListTaskDefinitions} calls per lookup. This runs on a details read, so a
   * prefix shared with sibling families that have very long histories must not turn one request
   * into an unbounded scan. 10 pages covers 1,000 sibling ARNs; past that the picker shows whatever
   * exact-family revisions were found (possibly none) instead of continuing.
   */
  static final int MAX_PAGES = 10;

  private final AmazonClientProvider amazonClientProvider;

  @Autowired
  public EcsTaskDefinitionRevisionService(AmazonClientProvider amazonClientProvider) {
    this.amazonClientProvider = amazonClientProvider;
  }

  /**
   * Lists the family's ACTIVE revisions (newest first, capped) for the service whose current
   * task-definition ARN is {@code currentTaskDefinitionArn}, flagging that current revision.
   * Returns an empty list when the family can't be derived from the ARN.
   */
  public List<EcsTaskDefinitionRevision> listRevisions(
      NetflixECSCredentials credentials, String region, String currentTaskDefinitionArn) {
    String family = familyFromTaskDefinitionArn(currentTaskDefinitionArn);
    if (family == null) {
      return new ArrayList<>();
    }

    EcsClient ecs = amazonClientProvider.getAmazonEcsV2(credentials, region);

    // ListTaskDefinitions only filters by family *prefix*, so "my-app" also returns "my-app-worker"
    // revisions. Keep only the exact family. With DESC, ECS reverses the order on family name as
    // well as revision, so longer sibling families sort before the requested one: filter while
    // paginating (not after the cap), or the requested family can be pushed out entirely.
    List<String> revisionArns = new ArrayList<>();
    String nextToken = null;
    int pages = 0;
    do {
      pages++;
      ListTaskDefinitionsResponse listed =
          ecs.listTaskDefinitions(
              ListTaskDefinitionsRequest.builder()
                  .familyPrefix(family)
                  .status(TaskDefinitionStatus.ACTIVE)
                  .sort(SortOrder.DESC)
                  .maxResults(LIST_PAGE_SIZE)
                  .nextToken(nextToken)
                  .build());
      for (String arn : listed.taskDefinitionArns()) {
        if (family.equals(familyFromTaskDefinitionArn(arn))) {
          revisionArns.add(arn);
        }
      }
      nextToken = listed.nextToken();
    } while (nextToken != null && revisionArns.size() < MAX_REVISIONS && pages < MAX_PAGES);

    List<EcsTaskDefinitionRevision> revisions = new ArrayList<>();
    for (String arn : revisionArns.subList(0, Math.min(revisionArns.size(), MAX_REVISIONS))) {
      boolean isCurrent = normalizeArn(arn).equals(normalizeArn(currentTaskDefinitionArn));
      revisions.add(toRevision(arn, isCurrent));
    }
    return revisions;
  }

  private static EcsTaskDefinitionRevision toRevision(String taskDefinitionArn, boolean current) {
    return EcsTaskDefinitionRevision.builder()
        .taskDefinitionArn(taskDefinitionArn)
        .family(familyFromTaskDefinitionArn(taskDefinitionArn))
        .revision(revisionFromTaskDefinitionArn(taskDefinitionArn))
        .current(current)
        .build();
  }

  /**
   * Extracts the family from a task-definition ARN or {@code family:revision} string. ECS ARNs are
   * {@code arn:aws:ecs:region:account:task-definition/family:revision}; the family is the segment
   * after {@code task-definition/} and before the trailing {@code :revision}. Returns null when the
   * input is blank or has no recognizable family.
   */
  static String familyFromTaskDefinitionArn(String taskDefinitionArn) {
    if (StringUtils.isBlank(taskDefinitionArn)) {
      return null;
    }
    String afterSlash = StringUtils.substringAfterLast(taskDefinitionArn, "/");
    String familyAndRevision = StringUtils.isBlank(afterSlash) ? taskDefinitionArn : afterSlash;
    String family = StringUtils.substringBeforeLast(familyAndRevision, ":");
    return StringUtils.isBlank(family) ? null : family;
  }

  private static Integer revisionFromTaskDefinitionArn(String taskDefinitionArn) {
    String revision = StringUtils.substringAfterLast(taskDefinitionArn, ":");
    return StringUtils.isNumeric(revision) ? Integer.valueOf(revision) : null;
  }

  /**
   * The cached current task definition may be stored as {@code family:revision} rather than a full
   * ARN, while {@code ListTaskDefinitions} returns full ARNs. To flag the current revision we
   * compare on {@code family:revision}, so reduce a full ARN to that suffix.
   */
  private static String normalizeArn(String taskDefinitionArn) {
    if (StringUtils.isBlank(taskDefinitionArn)) {
      return taskDefinitionArn;
    }
    String afterSlash = StringUtils.substringAfterLast(taskDefinitionArn, "/");
    return StringUtils.isBlank(afterSlash) ? taskDefinitionArn : afterSlash;
  }
}
