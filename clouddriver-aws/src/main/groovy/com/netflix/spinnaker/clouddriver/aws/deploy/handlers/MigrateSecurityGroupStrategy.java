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

package com.netflix.spinnaker.clouddriver.aws.deploy.handlers;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Vpc;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AbstractAmazonCredentialsDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupReference;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupResult;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupIngressConverter;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupUpdater;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupMigrator.SecurityGroupLocation;
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonVpcProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface MigrateSecurityGroupStrategy {

  /**
   * Generates a result set, describing the actions required to migrate the source security group to the target.
   *
   * @param source                the source security group
   * @param target                the target location
   * @param sourceLookup          a lookup cache for the source region
   * @param targetLookup          a lookup cache for the target region (may be the same as the sourceLookup)
   * @param createIfSourceMissing indicates whether the operation should proceed if the source does not already exist;
   *                              if false and the source group cannot be found, an IllegalStateException will be thrown
   * @return the result set
   */
  default MigrateSecurityGroupResult generateResults(
    SecurityGroupLocation source,
    SecurityGroupLocation target,
    SecurityGroupLookup sourceLookup,
    SecurityGroupLookup targetLookup,
    boolean createIfSourceMissing,
    boolean dryRun
  ) {
    final MigrateSecurityGroupResult result = new MigrateSecurityGroupResult();
    final SecurityGroupUpdater sourceUpdater = sourceLookup.getSecurityGroupByName(
      source.getCredentialAccount(),
      source.getName(),
      source.getVpcId()
    );
    boolean sourceExists = sourceUpdater != null && sourceUpdater.getSecurityGroup() != null;
    if (!createIfSourceMissing && !sourceExists) {
      throw new IllegalStateException("Security group does not exist: " + source.toString());
    }

    Set<MigrateSecurityGroupReference> targetReferences = new HashSet<>();
    if (sourceExists) {
      targetReferences = getTargetReferences(sourceUpdater, sourceLookup);

      // convert names before determining if rules are important
      targetReferences.stream().forEach(reference -> reference.setTargetName(getTargetName(reference.getSourceName())));

      result.setErrors(shouldError(sourceLookup, targetLookup, target, targetReferences));
      if (!result.getErrors().isEmpty()) {
        return result;
      }

      result.setSkipped(shouldSkipWithoutWarning(sourceLookup, targetLookup, target, targetReferences));
      Set<MigrateSecurityGroupReference> toCheck = new HashSet<>(targetReferences);
      toCheck.removeAll(result.getSkipped());

      result.setWarnings(shouldWarn(sourceLookup, targetLookup, target, toCheck));
      toCheck.removeAll(result.getWarnings());
    }
    result.setTarget(createTargetSecurityGroupReference(source, target, sourceUpdater));

    Set<MigrateSecurityGroupReference> toVerify = new HashSet<>(targetReferences);
    toVerify.add(result.getTarget());
    toVerify.removeAll(result.getWarnings());
    toVerify.removeAll(result.getSkipped());

    result.setCreated(shouldCreate(sourceLookup, targetLookup, target, toVerify));

    List<MigrateSecurityGroupReference> reused = new ArrayList<>(toVerify);
    reused.removeAll(result.getCreated());
    result.setReused(reused);

    if (!dryRun) {
      performMigration(result, sourceLookup, targetLookup, source, target);
    }

    return result;
  }

  default void performMigration(MigrateSecurityGroupResult results, SecurityGroupLookup sourceLookup,
                                SecurityGroupLookup targetLookup, SecurityGroupLocation source,
                                SecurityGroupLocation target) {
    final SecurityGroupUpdater sourceGroupUpdater = sourceLookup.getSecurityGroupByName(
      source.getCredentialAccount(),
      source.getName(),
      source.getVpcId());

    final SecurityGroup securityGroup = sourceGroupUpdater != null ? sourceGroupUpdater.getSecurityGroup() : null;

    Set<MigrateSecurityGroupReference> targetGroups = new HashSet<>(results.getCreated());
    targetGroups.addAll(results.getReused());
    if (!results.targetExists()) {
      targetGroups.add(results.getTarget());
    }
    results.getCreated().stream()
      .forEach(r -> r.setTargetId(
        createDependentSecurityGroup(r, sourceLookup, targetLookup, source, target).getSecurityGroup().getGroupId()));

    SecurityGroupUpdater targetGroup = targetLookup.getSecurityGroupByName(
      target.getCredentialAccount(),
      results.getTarget().getTargetName(),
      target.getVpcId()
    );

    if (securityGroup != null && shouldCreateTargetPermissions(securityGroup)) {
      createTargetPermissions(securityGroup, targetGroup, targetGroups, results);
    }
    results.getTarget().setTargetId(targetGroup.getSecurityGroup().getGroupId());
  }

  // Creates a security group in the target location with no ingress rules
  default SecurityGroupUpdater createDependentSecurityGroup(MigrateSecurityGroupReference reference,
                                                            SecurityGroupLookup sourceLookup,
                                                            SecurityGroupLookup targetLookup,
                                                            SecurityGroupLocation source,
                                                            SecurityGroupLocation target) {
    String sourceAccount = sourceLookup.getAccountNameForId(reference.getAccountId());
    NetflixAmazonCredentials targetCredentials = sourceAccount.equals(source.getCredentialAccount()) ?
      target.getCredentials() :
      sourceLookup.getCredentialsForName(sourceAccount);
    SecurityGroupUpdater sourceGroup = sourceLookup.getSecurityGroupByName(sourceAccount, reference.getTargetName(), reference.getVpcId());
    String description = "Security group " + reference.getTargetName();
    if (sourceGroup != null && sourceGroup.getSecurityGroup() != null) {
      description = sourceGroup.getSecurityGroup().getDescription();
    }
    UpsertSecurityGroupDescription upsertDescription = new UpsertSecurityGroupDescription();
    upsertDescription.setName(reference.getTargetName());
    upsertDescription.setCredentials(targetCredentials);
    upsertDescription.setDescription(description);
    upsertDescription.setVpcId(target.getVpcId());

    return targetLookup.createSecurityGroup(upsertDescription);
  }

  default void createTargetPermissions(SecurityGroup sourceGroup,
                                       SecurityGroupUpdater targetGroup,
                                       Set<MigrateSecurityGroupReference> targetGroups,
                                       MigrateSecurityGroupResult results) {

    List<IpPermission> targetPermissions = SecurityGroupIngressConverter
      .flattenPermissions(sourceGroup.getIpPermissions())
      .stream()
      .filter(p -> p.getUserIdGroupPairs().isEmpty() ||
        p.getUserIdGroupPairs().stream().allMatch(pair -> targetGroups.stream()
          .anyMatch(g -> pair.getGroupId().equals(g.getSourceId()))))
      .collect(Collectors.toList());

    targetPermissions.forEach(permission ->
      permission.getUserIdGroupPairs().forEach(pair -> {
        MigrateSecurityGroupReference targetReference = targetGroups.stream().filter(group ->
          group.getSourceId().equals(pair.getGroupId())
        ).findFirst().get();
        pair.setGroupId(targetReference.getTargetId());
        pair.setGroupName(null);
        pair.setVpcId(targetReference.getVpcId());
      })
    );

    filterOutExistingRules(targetPermissions, targetGroup.getSecurityGroup());
    results.setIngressUpdates(targetPermissions.stream().filter(p -> !p.getUserIdGroupPairs().isEmpty() || !p.getIpRanges().isEmpty()).collect(Collectors.toList()));

    if (!results.getIngressUpdates().isEmpty()) {
      targetGroup.addIngress(targetPermissions);
    }
  }

  default void filterOutExistingRules(List<IpPermission> permissionsToApply, SecurityGroup targetGroup) {
    permissionsToApply.forEach(permission -> {
      permission.getUserIdGroupPairs().removeIf(pair ->
        targetGroup.getIpPermissions().stream().anyMatch(targetPermission ->
          targetPermission.getFromPort().equals(permission.getFromPort())
            && targetPermission.getToPort().equals(permission.getToPort())
            && targetPermission.getUserIdGroupPairs().stream().anyMatch(t -> t.getGroupId().equals(pair.getGroupId()))
        )
      );
      permission.getIpRanges().removeIf(range ->
        targetGroup.getIpPermissions().stream().anyMatch(targetPermission ->
          targetPermission.getFromPort().equals(permission.getFromPort())
          && targetPermission.getToPort().equals(permission.getToPort())
          && targetPermission.getIpRanges().contains(range)
        )
      );
    });
  }

  AmazonClientProvider getAmazonClientProvider();

  /**
   * Returns references to all security groups that should be created for the target
   *
   * @param sourceLookup the lookup cache for the source region
   * @param targetLookup the lookup cache for the target region (can be the same as the sourceLookup)
   * @param target       the target security group
   * @param references   the collection of potential security groups to select from; implementations can choose to provide
   *                     additional security groups that are *not* members of this set
   * @return a list of security groups that need to be created in order to migrate the target security group
   */
  default Set<MigrateSecurityGroupReference> shouldCreate(SecurityGroupLookup sourceLookup,
                                                          SecurityGroupLookup targetLookup,
                                                          SecurityGroupLocation target,
                                                          Set<MigrateSecurityGroupReference> references) {

    List<NetflixAmazonCredentials> credentials = references.stream()
      .map(AbstractAmazonCredentialsDescription::getCredentials).distinct().collect(Collectors.toList());
    Map<String, String> vpcMappings = getVpcMappings(target, credentials);

    return references.stream().distinct().filter(reference -> {
      String targetVpc = vpcMappings.get(reference.getAccountId());
      reference.setVpcId(targetVpc);

      SecurityGroupUpdater targetMatch =
        targetLookup.getSecurityGroupByName(reference.getCredentialAccount(), reference.getTargetName(), targetVpc);
      if (targetMatch != null) {
        reference.setTargetId(targetMatch.getSecurityGroup().getGroupId());
        return false;
      }
      return true;
    }).collect(Collectors.toSet());
  }

  /**
   * Returns references to all security groups that should halt the migration
   *
   * @param sourceLookup the lookup cache for the source region
   * @param targetLookup the lookup cache for the target region (can be the same as the sourceLookup)
   * @param target       the target security group
   * @param references   the collection of potential security groups to select from; implementations can choose to provide
   *                     additional security groups that are *not* members of this set
   * @return a list of security groups that will fail the migration; if this call returns anything, additional checks
   * will not run
   */
  default Set<MigrateSecurityGroupReference> shouldError(SecurityGroupLookup sourceLookup,
                                                         SecurityGroupLookup targetLookup,
                                                         SecurityGroupLocation target,
                                                         Set<MigrateSecurityGroupReference> references) {
    return new HashSet<>();
  }

  /**
   * Returns references to all security groups that should be created for the target
   *
   * @param sourceLookup the lookup cache for the source region
   * @param targetLookup the lookup cache for the target region (can be the same as the sourceLookup)
   * @param target       the target security group
   * @param references   the collection of potential security groups to select from; implementations can choose to provide
   *                     additional security groups that are *not* members of this set
   * @return a list of security groups that need to be created in order to migrate the target security group
   */
  default Set<MigrateSecurityGroupReference> shouldWarn(SecurityGroupLookup sourceLookup,
                                                        SecurityGroupLookup targetLookup,
                                                        SecurityGroupLocation target,
                                                        Set<MigrateSecurityGroupReference> references) {
    return references.stream().filter(reference -> {
      if (!targetLookup.accountIdExists(reference.getAccountId())) {
        reference.setExplanation("Spinnaker does not manage the account " + reference.getAccountId());
        return true;
      }
      return false;
    }).collect(Collectors.toSet());
  }

  /**
   * Returns references to all security groups that will be skipped - but not visually reported to the user - when
   * migrating the target. This includes security groups
   *
   * @param sourceLookup the lookup cache for the source region
   * @param targetLookup the lookup cache for the target region (can be the same as the sourceLookup)
   * @param target       the target security group
   * @param references   the collection of potential security groups to select from; implementations can choose to provide
   *                     additional security groups that are *not* members of this set
   * @return a list of security groups that will be skipped when migrating the target security group
   */
  default Set<MigrateSecurityGroupReference> shouldSkipWithoutWarning(SecurityGroupLookup sourceLookup,
                                                                      SecurityGroupLookup targetLookup,
                                                                      SecurityGroupLocation target,
                                                                      Set<MigrateSecurityGroupReference> references) {

    return references.stream().filter(reference -> {
      if (reference.getAccountId().equals("amazon-elb")) {
        reference.setExplanation("amazon-elb groups are not required in VPC environments");
        return true;
      }
      return false;
    }).collect(Collectors.toSet());
  }

  /**
   * Determines whether ingress rules should be updated when migrating the security group - for example, you may
   * not want to touch security groups that are managed by a different team, or security groups in a specific service
   *
   * @param securityGroup the security group
   * @return true if ingress rules should be updated, false otherwise
   */
  default boolean shouldCreateTargetPermissions(SecurityGroup securityGroup) {
    return true;
  }

  default Set<MigrateSecurityGroupReference> getTargetReferences(SecurityGroupUpdater source,
                                                                 SecurityGroupLookup sourceLookup) {
    SecurityGroup group = source.getSecurityGroup();
    return group.getIpPermissions()
      .stream()
      .map(IpPermission::getUserIdGroupPairs)
      .flatMap(List::stream)
      .filter(pair -> !pair.getGroupId().equals(group.getGroupId()) || !pair.getUserId().equals(group.getOwnerId()))
      .map(pair -> new MigrateSecurityGroupReference(pair, sourceLookup.getCredentialsForId(pair.getUserId())))
      .collect(Collectors.toSet());
  }

  /**
   * Returns the name of the target security group based on the name of the source security group. Useful (at least at
   * Netflix) when migrating to remove legacy naming conventions
   *
   * @param sourceName the source name
   * @return the target name
   */
  default String getTargetName(String sourceName) {
    return sourceName;
  }

  /**
   * Builds a reference from an existing source
   *
   * @param source        the source location
   * @param target        the target location
   * @param sourceUpdater the source updater
   * @return a reference with source, account, vpcId, credentials, and target name populated; targetId will not be
   * populated
   */
  default MigrateSecurityGroupReference createTargetSecurityGroupReference(SecurityGroupLocation source,
                                                                           SecurityGroupLocation target,
                                                                           SecurityGroupUpdater sourceUpdater) {
    MigrateSecurityGroupReference ref = new MigrateSecurityGroupReference();
    ref.setSourceName(source.getName());
    ref.setAccountId(target.getCredentials().getAccountId());
    ref.setVpcId(target.getVpcId());
    if (sourceUpdater != null) {
      ref.setSourceId(sourceUpdater.getSecurityGroup().getGroupId());
    }
    ref.setCredentials(target.getCredentials());
    ref.setTargetName(getTargetName(source.getName()));
    return ref;
  }

  /**
   * Returns a mapping of accountId:vpcId for the list of accounts, based on the name of the vpcId of the target.
   * This should be a private method, but this is an interface.
   *
   * @param target   the target location
   * @param accounts the list of accounts to match
   * @return a map of accountId:vpcId, where each vpcId maps to a VPC with the same name as the target vpcId
   */
  default Map<String, String> getVpcMappings(SecurityGroupLocation target, List<NetflixAmazonCredentials> accounts) {
    Map<String, String> mappings = new HashMap<>();
    if (target.getVpcId() == null) {
      return mappings;
    }
    AmazonEC2 baseTarget = getAmazonClientProvider().getAmazonEC2(target.getCredentials(), target.getRegion());
    Vpc targetVpc = baseTarget.describeVpcs().getVpcs().stream()
      .filter(vpc -> vpc.getVpcId().equals(target.getVpcId()))
      .findFirst().orElse(null);

    String targetName = AmazonVpcProvider.getVpcName(targetVpc);

    accounts.stream().forEach(account -> {
      List<Vpc> vpcs = getAmazonClientProvider().getAmazonEC2(account, target.getRegion()).describeVpcs().getVpcs();
      Vpc match = vpcs.stream()
        .filter(vpc -> AmazonVpcProvider.getVpcName(vpc).equals(targetName))
        .findFirst().orElse(null);
      mappings.put(account.getAccountId(), match.getVpcId());
    });
    return mappings;
  }

}
