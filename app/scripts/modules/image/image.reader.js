'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.image.service', [
  require('../core/cloudProvider/serviceDelegate.service.js'),
])
  .factory('imageReader', function (serviceDelegate) {

    function getDelegate(provider) {
      return serviceDelegate.getDelegate(provider, 'image.reader');
    }

    function findImages(params) {
      return getDelegate(params.provider).findImages(params);
    }

    function getImage(selectedProvider, amiName, region, credentials) {
      return getDelegate(selectedProvider).getImage(amiName, region, credentials);
    }

    return {
      findImages: findImages,
      getImage: getImage,
    };
  }).name;
