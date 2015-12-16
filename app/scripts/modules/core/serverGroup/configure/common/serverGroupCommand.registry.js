'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.serverGroup.configure.command.registry', [
  require('../../../utils/lodash.js'),
])
  .provider('serverGroupCommandRegistry', function(_) {

    const providers = Object.create(null);

    function register(cloudProvider, handler) {
      if (!providers[cloudProvider]) {
        providers[cloudProvider] = [];
      }
      providers[cloudProvider].push(handler);
    }

    function getCommandOverrides(provider) {
      return providers[provider] ? _.cloneDeep(providers[provider]) : [];
    }

    this.$get = function() {
      return {
        getCommandOverrides: getCommandOverrides,
        register: register
      };
    };

  });
