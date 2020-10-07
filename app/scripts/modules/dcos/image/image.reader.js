'use strict';

import { API } from '@spinnaker/core';

import { module } from 'angular';

export const DCOS_IMAGE_IMAGE_READER = 'spinnaker.dcos.image.reader';
export const name = DCOS_IMAGE_IMAGE_READER; // for backwards compatibility
module(DCOS_IMAGE_IMAGE_READER, []).factory('dcosImageReader', function () {
  function findImages(params) {
    return API.all('images', 'find')
      .getList(params)
      .then(
        function (results) {
          return results;
        },
        function () {
          return [];
        },
      );
  }

  function getImage(/*amiName, region, account*/) {
    // dcos images are not regional so we don't need to retrieve ids scoped to regions.
    return null;
  }

  return {
    findImages: findImages,
    getImage: getImage,
  };
});
