'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.cf.image.reader', [
  require('../../core/api/api.service')
])
  .factory('cfImageReader', function ($q, API) {

    function findImages(params) {
      return API.all('images/find').getList(params).then(function(results) {
          return results;
        },
        function() {
          return ['ubuntu-1404-trusty-v20141031a', 'debian-7-wheezy-v20141108', 'centos-7-v20141108'];
        });
    }

    function getImage(/*amiName, region, credentials*/) {
      // cf images are not regional so we don't need to retrieve ids scoped to regions.
      return null;
    }

    return {
      findImages: findImages,
      getImage: getImage,
    };
  });
