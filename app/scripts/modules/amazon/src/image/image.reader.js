'use strict';

const angular = require('angular');

import { API } from '@spinnaker/core';

module.exports = angular.module('spinnaker.amazon.image.reader', []).factory('awsImageReader', function($q) {
  function findImages(params) {
    if (!params.q || params.q.length < 3) {
      return $q.when([{ message: 'Please enter at least 3 characters...', disabled: true }]);
    }
    return API.one('images/find')
      .withParams(params)
      .get()
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
      .withParams({ provider: 'aws' })
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
