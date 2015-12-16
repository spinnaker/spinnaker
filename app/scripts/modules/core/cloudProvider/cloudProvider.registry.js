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

    function overrideValue(provider, key, val) {
      if (!providers[provider]) {
        console.warn('Cannot override "' + key + '" for provider "' + provider + '" (provider not registered)');
      }
      let config = providers[provider],
          parentKeys = key.split('.'),
          lastKey = parentKeys.pop(),
          current = config;

      parentKeys.forEach((parentKey) => {
        if (!current[parentKey]) {
          current[parentKey] = {};
        }
        current = current[parentKey];
      });

      current[lastKey] = val;
    }

    function hasValue(provider, key) {
      return !!getProvider(provider) && getValue(provider, key) !== null;
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
        hasValue: hasValue,
        overrideValue: overrideValue,
        listRegisteredProviders: listRegisteredProviders,
      };
    };

  });
