'use strict';

const angular = require('angular');

import { AccountService, NETWORK_READ_SERVICE } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.oraclebmcs.cache.initializer', [NETWORK_READ_SERVICE])
  .factory('oraclebmcsCacheConfigurer', function(networkReader) {
    let config = Object.create(null);
    let provider = 'oraclebmcs';

    config.credentials = {
      initializers: [() => AccountService.listAccounts(provider)],
    };

    config.account = {
      initializers: [() => AccountService.getCredentialsKeyedByAccount(provider)],
    };

    config.networks = {
      initializers: [() => networkReader.listNetworksByProvider(provider)],
    };

    return config;
  });
