'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.image.reader', [
  require('exports?"restangular"!imports?_=lodash!restangular')
])
  .factory('openstackImageReader', function ($q, Restangular) {

    function findImages(params) {
      return Restangular.all('images/find').getList(params, {})
        .then(function(results) {
          return results;
        })
        .catch(function() {
          return [];
        });
    }

    function getImage(amiName, region, credentials) {
      return Restangular.all('images').one(credentials).one(region).all(amiName).getList({provider: 'openstack'}).then(function(results) {
          return results && results.length ? results[0] : null;
        })
        .catch(function() {
          return [];
        });
    }

    return {
      findImages: findImages,
      getImage: getImage,
    };
  });
