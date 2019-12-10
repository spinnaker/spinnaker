'use strict';

import { module } from 'angular';

import { API, RetryService } from '@spinnaker/core';

export const KUBERNETES_V1_IMAGE_IMAGE_READER = 'spinnaker.kubernetes.image.reader';
export const name = KUBERNETES_V1_IMAGE_IMAGE_READER; // for backwards compatibility
module(KUBERNETES_V1_IMAGE_IMAGE_READER, []).factory('kubernetesImageReader', function() {
  function findImages(params) {
    return RetryService.buildRetrySequence(
      () => API.all('images/find').getList(params),
      results => results.length > 0,
      10,
      1000,
    ).catch(() => []);
  }

  function getImage(/*amiName, region, account*/) {
    // kubernetes images are not regional so we don't need to retrieve ids scoped to regions.
    return null;
  }

  return {
    findImages: findImages,
    getImage: getImage,
  };
});
