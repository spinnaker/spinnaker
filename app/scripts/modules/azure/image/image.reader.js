'use strict';

import {API_SERVICE} from 'core/api/api.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.image.reader', [API_SERVICE])
  .factory('azureImageReader', function ($q, API) {

    function findImages(params) {
      return API.one('images/find').get(params)
        .then(function(results) {
          return results;
        },
        function() {
          return [];
        });
    }

    function getImage(amiName, region, credentials) {
      return API
        .one('images')
        .one(credentials)
        .one(region)
        .one(amiName)
        .withParams({provider: 'azure'})
        .get()
        .then(function(results) {
          return results && results.length ? results[0] : null;
        },
        function() {
          return null;
        });
    }

    return {
      findImages: findImages,
      getImage: getImage,
    };
  });
