'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.docker.image.reader', [
    require('../../core/api/api.service')
  ])
  .factory('dockerImageReader', function ($q, API) {
    function findImages (params) {
      return API.all('images/find').getList(params).then(function (results) {
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
