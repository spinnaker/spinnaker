'use strict';

import { API_SERVICE } from '@spinnaker/core';

const angular = require('angular');

module.exports = angular.module('spinnaker.dcos.image.reader', [API_SERVICE])
  .factory('dcosImageReader', function ($q, API) {
    function findImages(params) {
      return API.all('images/find').getList(params).then(function(results) {
          return results;
        },
        function() {
          return [];
        });
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
