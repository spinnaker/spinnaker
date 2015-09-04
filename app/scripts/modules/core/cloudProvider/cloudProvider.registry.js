'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.cloudProvider.registry', [
  require('../../utils/lodash.js'),
])
  .provider('cloudProviderRegistry', function(_) {

    const providers = {};

    this.registerProvider = (cloudProvider, config) => {
      providers[cloudProvider] = config;
    };

    function getProvider(provider) {
      return _.cloneDeep(providers[provider]);
    }

    function getValue(provider, key) {
      if (!key) {
        return null;
      }
      let config = getProvider(provider),
          keyParts = key.split('.'),
          current = config,
          notFound = false;

      if (!config) {
        console.warn(`No provider found matching '${provider}' for key '${key}'`);
        return null;
      }

      keyParts.forEach((keyPart) => {
        if (!notFound && current.hasOwnProperty(keyPart)) {
          current = current[keyPart];
        } else {
          notFound = true;
        }
      });

      if (notFound) {
        console.warn(`No value configured for '${key}' in provider '${provider}'`);
        return null;
      }
      return current;
    }

    this.$get = function() {
      return {
        getProvider: getProvider,
        getValue: getValue,
      };
    };

  }).name;
