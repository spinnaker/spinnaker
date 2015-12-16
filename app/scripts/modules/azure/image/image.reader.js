'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.image.reader', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
])
  .factory('azureImageReader', function ($q, Restangular) {

    function findImages(params) {
      if (!params.q || params.q.length < 3) {
        return $q.when([{message: 'Please enter at least 3 characters...'}]);
      }
      return Restangular.all('images/find').getList(params, {})
        .then(function(results) {
          return results;
        },
        function() {
          return [];
        });
    }

    function getImage(amiName, region, credentials) {
      return Restangular.all('images').one(credentials).one(region).all(amiName).getList({provider: 'azure'}).then(function(results) {
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
