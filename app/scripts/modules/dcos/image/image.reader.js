'use strict';

import { API } from '@spinnaker/core';

const angular = require('angular');

module.exports = angular.module('spinnaker.dcos.image.reader', []).factory('dcosImageReader', function() {
  function findImages(params) {
    return API.all('images/find')
      .getList(params)
      .then(
        function(results) {
          return results;
        },
        function() {
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
