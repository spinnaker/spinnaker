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

import com.amazonaws.services.ec2.model.SecurityGroup;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupReference;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupUpdater;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupMigrator.SecurityGroupLocation;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultMigrateSecurityGroupStrategy implements MigrateSecurityGroupStrategy {

  private AmazonClientProvider amazonClientProvider;

  public AmazonClientProvider getAmazonClientProvider() {
    return amazonClientProvider;
  }

  public DefaultMigrateSecurityGroupStrategy(AmazonClientProvider amazonClientProvider) {
    this.amazonClientProvider = amazonClientProvider;
  }


  // TODO: The rest of this class does not belong here - move it into -nflx
  @Override
  public Set<MigrateSecurityGroupReference> getTargetReferences(SecurityGroupUpdater source,
                                                                SecurityGroupLookup sourceLookup) {
    if (source.getSecurityGroup().getGroupName().startsWith("nf-")) {
      return new HashSet<>();
    }
    return MigrateSecurityGroupStrategy.super.getTargetReferences(source, sourceLookup);
  }

  @Override
  public Set<MigrateSecurityGroupReference> shouldSkipWithoutWarning(SecurityGroupLookup sourceLookup,
                                                                     SecurityGroupLookup targetLookup,
                                                                     SecurityGroupLocation target,
                                                                     Set<MigrateSecurityGroupReference> references) {

    Set<MigrateSecurityGroupReference> baseSkipped =
      MigrateSecurityGroupStrategy.super.shouldSkipWithoutWarning(sourceLookup, targetLookup, target, references);

    Set<MigrateSecurityGroupReference> base = new HashSet<>(references);

    return base.stream().filter(reference -> {
      if (baseSkipped.contains(reference)) {
        return true;
      }
      String targetName = reference.getTargetName();
      String account = targetLookup.getAccountNameForId(reference.getAccountId());
      if (targetName.startsWith("nf-") &&
        targetLookup.getSecurityGroupByName(account, targetName, reference.getVpcId()) == null) {

        reference.setExplanation(targetName + " does not exist in destination");
        return true;
      }
      return false;
    }).collect(Collectors.toSet());
  }

  @Override
  public String getTargetName(String sourceName) {
    String targetName = sourceName;
    if (targetName.endsWith("-vpc")) {
      targetName = targetName.substring(0, targetName.length() - 4);
    }
    return targetName;
  }

  @Override
  public boolean shouldCreateTargetPermissions(SecurityGroup securityGroup) {
    return !securityGroup.getGroupName().startsWith("nf-");
  }

}
