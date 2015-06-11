package com.netflix.spinnaker.kato.aws.deploy

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.Filter

import java.util.regex.Pattern

class AmiIdResolver {
  private static final Pattern amiIdPattern = Pattern.compile('^ami-[0-9a-f]+$')

  public static ResolvedAmiResult resolveAmiId(AmazonEC2 amazonEC2, String region, String nameOrId, String owner = null, String launcher = null) {
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
    String imageId = amazonEC2.describeImages(req)?.images?.getAt(0)?.imageId
    if (imageId) {
      return new ResolvedAmiResult(nameOrId, region, imageId)
    }

    return null
  }
}
