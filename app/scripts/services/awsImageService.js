'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws.image.service', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../modules/caches/deckCacheFactory.js'),
  require('../modules/caches/scheduledCache.js'),
])
  .factory('awsImageService', function ($q, Restangular) {

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

    function getAmi(amiName, region, credentials) {
      return Restangular.all('images').one(credentials).one(region).all(amiName).getList({provider: 'aws'}).then(function(results) {
          return results && results.length ? results[0] : null;
        },
        function() {
          return null;
        });
    }

    return {
      findImages: findImages,
      getAmi: getAmi,
    };
  }).name;
