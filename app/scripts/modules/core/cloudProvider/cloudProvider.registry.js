'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.cloudProvider.registry', [
  require('../utils/lodash.js'),
  require('../config/settings.js'),
])
  .provider('cloudProviderRegistry', function(_, settings) {

    const providers = Object.create(null);

    this.registerProvider = (cloudProvider, config) => {
      if (settings.providers && settings.providers[cloudProvider]) {
        providers[cloudProvider] = config;
      }
    };

    function getProvider(provider) {
      return _.cloneDeep(providers[provider]);
    }

    function listRegisteredProviders() {
      return Object.keys(providers);
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
        console.debug(`No provider found matching '${provider}' for key '${key}'`);
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
        console.debug(`No value configured for '${key}' in provider '${provider}'`);
        return null;
      }
      return current;
    }

    this.$get = function() {
      return {
        getProvider: getProvider,
        getValue: getValue,
        listRegisteredProviders: listRegisteredProviders,
      };
    };

  }).name;
