'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.provider.image.service', [])
  .provider('providerImageService', function() {

    var providerImages = {};

    function registerImage(config) {
      var provider = config.provider,
          key = config.key,
          path = config.path;
      if (!providerImages[provider]) {
        providerImages[provider] = {};
      }
      providerImages[provider][key] = path;
    }

    function getImage(provider, key) {
      if (providerImages[provider] && providerImages[provider][key]) {
        return providerImages[provider][key];
      }
      return null;
    }

    this.registerImage = registerImage;

    this.$get = function() {
      return {
        getImage: getImage,
      };
    };
  })
  .name;
