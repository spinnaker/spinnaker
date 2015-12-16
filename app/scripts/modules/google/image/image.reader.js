'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce.image.reader', [
  require('exports?"restangular"!imports?_=lodash!restangular')
])
  .factory('gceImageReader', function ($q, Restangular) {

    function findImages(params) {
      return Restangular.all('images/find').getList(params, {}).then(function(results) {
          return results;
        },
        function() {
          return ['ubuntu-1404-trusty-v20141031a', 'debian-7-wheezy-v20141108', 'centos-7-v20141108'];
        });
    }

    function getImage(/*amiName, region, credentials*/) {
      // GCE images are not regional so we don't need to retrieve ids scoped to regions.
      return null;
    }

    return {
      findImages: findImages,
      getImage: getImage,
    };
  });
