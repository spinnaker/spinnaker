package com.netflix.spinnaker.kato.aws.deploy

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.Filter

import java.util.regex.Pattern

class AmiIdResolver {
  private static final Pattern amiIdPattern = Pattern.compile('^ami-[0-9a-f]+$')

  public static String resolveAmiId(AmazonEC2 amazonEC2, String nameOrId, String owner = null, String launcher = null) {
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
    def images = amazonEC2.describeImages(req)
    images?.images?.getAt(0)?.imageId
  }
}
