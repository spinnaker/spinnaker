'use strict';

const angular = require('angular');

import { AccountService, NetworkReader } from '@spinnaker/core';

module.exports = angular.module('spinnaker.oracle.cache.initializer', []).factory('oracleCacheConfigurer', function() {
  let config = Object.create(null);
  let provider = 'oracle';

  config.credentials = {
    initializers: [() => AccountService.listAccounts(provider)],
  };

  config.account = {
    initializers: [() => AccountService.getCredentialsKeyedByAccount(provider)],
  };

  config.networks = {
    initializers: [() => NetworkReader.listNetworksByProvider(provider)],
  };

  return config;
});
