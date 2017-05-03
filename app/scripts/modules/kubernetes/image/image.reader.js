'use strict';

import {API_SERVICE} from 'core/api/api.service';
import {RETRY_SERVICE} from 'core/retry/retry.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.image.reader', [API_SERVICE, RETRY_SERVICE])
  .factory('kubernetesImageReader', function ($q, API, retryService) {
    function findImages(params) {
      return retryService
        .buildRetrySequence(() => API.all('images/find').getList(params), results => (results.length > 0), 10, 1000)
        .catch(() => []);
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
