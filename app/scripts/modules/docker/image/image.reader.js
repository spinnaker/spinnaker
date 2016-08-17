'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.docker.image.reader', [
    require('../../core/api/api.service'),
    require('../../core/retry/retry.service.js')
  ])
  .factory('dockerImageReader', function ($q, API, retryService) {
    function findImages (params) {
      return retryService.buildRetrySequence(
          () => API.all('images/find').getList(params),
          results => results.length > 0,
          10,
          1000)
        .then(function (results) {
          return results;
        },
        function () {
          return [];
        });
    }

    return {
      findImages: findImages,
    };
  });
