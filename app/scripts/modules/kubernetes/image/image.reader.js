'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.image.reader', [])
  .factory('kubernetesImageReader', function ($q) {
    function findImages(/* params */) {
      return $q.when([{ imageName: 'nginx', account: 'my-kubernetes-account' }, { imageName: 'redis', account: 'my-kubernetes-account' }]);
    }

    function getImage(/*amiName, region, credentials*/) {
      // kubernetes images are not regional so we don't need to retrieve ids scoped to regions.
      return null;
    }

    return {
      findImages: findImages,
      getImage: getImage,
    };
  });
