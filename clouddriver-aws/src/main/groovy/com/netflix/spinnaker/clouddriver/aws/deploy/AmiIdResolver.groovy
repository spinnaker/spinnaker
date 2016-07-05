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

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.Image
import java.util.regex.Pattern

class AmiIdResolver {
  private static final Pattern amiIdPattern = Pattern.compile('^ami-[0-9a-f]+$')

  private static ResolvedAmiResult resolveAmiId(AmazonEC2 amazonEC2, String region, String nameOrId, String owner = null, String launcher = null) {
    def req = new DescribeImagesRequest()
    if (amiIdPattern.matcher(nameOrId).matches()) {
      req.withImageIds(nameOrId)
    } else {
      req.withFilters(new Filter('name').withValues(nameOrId))
    }

    if (owner) {
      req.withOwners(owner)
    }
    if (launcher) {
      req.withExecutableUsers(launcher)
    }
    Image resolvedImage = amazonEC2.describeImages(req)?.images?.getAt(0)
    if (resolvedImage) {
      return new ResolvedAmiResult(nameOrId, region, resolvedImage.imageId, resolvedImage.virtualizationType, resolvedImage.blockDeviceMappings)
    }

    return null
  }

  public static ResolvedAmiResult resolveAmiIdFromAllSources(AmazonEC2 amazonEC2, String region, String nameOrId, String accountId) {
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
