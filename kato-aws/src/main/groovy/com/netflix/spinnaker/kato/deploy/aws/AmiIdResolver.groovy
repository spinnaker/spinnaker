package com.netflix.spinnaker.kato.deploy.aws

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.Filter

import java.util.regex.Pattern

class AmiIdResolver {
  private static final Pattern amiIdPattern = Pattern.compile('^ami-[0-9a-f]+$')

  public static String resolveAmiId(AmazonEC2 amazonEC2, String nameOrId) {
    if (amiIdPattern.matcher(nameOrId).matches()) {
      nameOrId
    } else {
      def images = amazonEC2.describeImages(new DescribeImagesRequest().withFilters(new Filter('name').withValues(nameOrId)))
      images?.images?.first()?.imageId
    }

  }
}
