'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce.image.reader', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
])
  .factory('gceImageReader', function ($q, Restangular) {

    function findImages(params) {
      return Restangular.all('images/find').getList(params, {}).then(function(results) {
          return results;
        },
        function() {
          return [];
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
