'use strict';

import {CLOUD_PROVIDER_REGISTRY} from 'core/cloudProvider/cloudProvider.registry';

let angular = require('angular');

let templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular.module('spinnaker.oraclebmcs', [
  CLOUD_PROVIDER_REGISTRY,
  // Cache
  require('./cache/cacheConfigurer.service.js'),
  // Images
  require('./image/image.reader.js')
])
  .config(function (cloudProviderRegistryProvider) {
    cloudProviderRegistryProvider.registerProvider('oraclebmcs', {
      name: 'Oracle',
      cache: {
        configurer: 'oraclebmcsCacheConfigurer',
      },
      image: {
        reader: 'oraclebmcsImageReader',
      },
      loadBalancer: {
      },
      serverGroup: {
      },
      instance: {
      },
      securityGroup: {
      }
    });
  });
