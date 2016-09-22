'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.serverGroup.configure.command.registry', [])
  .provider('serverGroupCommandRegistry', function () {

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
