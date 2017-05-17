'use strict';

const angular = require('angular');

import { API_SERVICE } from '@spinnaker/core';

module.exports = angular.module('spinnaker.gce.image.reader', [API_SERVICE])
  .factory('gceImageReader', function ($q, API) {

    function findImages(params) {
      return API.all('images/find').getList(params).then(function(results) {
          return results;
        },
        function() {
          return [];
        });
    }

    function getImage(/*amiName, region, credentials*/) {
      // GCE images are not regional so we don't need to retrieve ids scoped to regions.
      return null;
    }

    return {
      findImages: findImages,
      getImage: getImage,
    };
  });
