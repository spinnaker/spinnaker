'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws.image.reader', [
  require('../../core/api/api.service')
])
  .factory('awsImageReader', function ($q, API) {

    function findImages(params) {
      if (!params.q || params.q.length < 3) {
        return $q.when([{message: 'Please enter at least 3 characters...'}]);
      }
      return API.one('images/find').withParams(params).get()
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
        .withParams({provider: 'aws'})
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
