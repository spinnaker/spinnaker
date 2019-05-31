'use strict';

const angular = require('angular');

import { API } from '@spinnaker/core';

module.exports = angular.module('spinnaker.azure.image.reader', []).factory('azureImageReader', function() {
  function findImages(params) {
    return API.one('images/find')
      .get(params)
      .then(
        function(results) {
          return results;
        },
        function() {
          return [];
        },
      );
  }

  function getImage(amiName, region, credentials) {
    return API.one('images')
      .one(credentials)
      .one(region)
      .one(amiName)
      .withParams({ provider: 'azure' })
      .get()
      .then(
        function(results) {
          return results && results.length ? results[0] : null;
        },
        function() {
          return null;
        },
      );
  }

  return {
    findImages: findImages,
    getImage: getImage,
  };
});
