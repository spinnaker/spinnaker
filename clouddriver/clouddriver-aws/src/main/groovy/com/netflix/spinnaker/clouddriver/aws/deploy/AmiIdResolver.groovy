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

package com.netflix.spinnaker.clouddriver.aws.deploy

import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.DescribeImagesRequest
import software.amazon.awssdk.services.ec2.model.Filter
import software.amazon.awssdk.services.ec2.model.Image
import java.util.regex.Pattern

class AmiIdResolver {
  private static final Pattern amiIdPattern = Pattern.compile('^ami-[0-9a-f]+$')

  private static ResolvedAmiResult resolveAmiId(Ec2Client amazonEC2, String region, String nameOrId, String owner = null, String launcher = null) {
    def reqBuilder = DescribeImagesRequest.builder()
    if (amiIdPattern.matcher(nameOrId).matches()) {
      reqBuilder.imageIds(nameOrId)
    } else {
      reqBuilder.filters(Filter.builder().name('name').values(nameOrId).build())
    }

    if (owner) {
      reqBuilder.owners(owner)
    }
    if (launcher) {
      reqBuilder.executableUsers(launcher)
    }
    Image resolvedImage = amazonEC2.describeImages(reqBuilder.build())?.images()?.getAt(0)
    if (resolvedImage) {
      return new ResolvedAmiResult(
        nameOrId,
        region,
        resolvedImage.imageId,
        resolvedImage.virtualizationTypeAsString(),
        resolvedImage.ownerId,
        resolvedImage.blockDeviceMappings(),
        resolvedImage.publicLaunchPermissions(),
        resolvedImage.architectureAsString())
    }

    return null
  }

  public static ResolvedAmiResult resolveAmiIdFromAllSources(Ec2Client amazonEC2, String region, String nameOrId, String accountId) {
    /* Find am AMI by searching in order:
       1) Explicitly granted launch permission
       2) Owner of the AMI
       3) Global search of all AMIs
    */
    return resolveAmiId(amazonEC2, region, nameOrId, null, accountId) ?:
      resolveAmiId(amazonEC2, region, nameOrId, accountId, null) ?:
        resolveAmiId(amazonEC2, region, nameOrId, null, null)
  }
}
