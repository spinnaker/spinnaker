'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.image.reader', [
  require('../../core/api/api.service')
])
  .factory('openstackImageReader', function ($q, API) {

    function findImages(params) {
      return API.all('images/find').getList(params)
        .then(function(results) {
          return results;
        })
        .catch(function() {
          return [];
        });
    }

    function getImage(amiName, region, credentials) {
      return API.all('images').one(credentials).one(region).all(amiName).getList({provider: 'openstack'}).then(function(results) {
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
