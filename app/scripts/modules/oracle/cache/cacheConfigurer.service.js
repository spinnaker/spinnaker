'use strict';

let angular = require('angular');

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {NETWORK_READ_SERVICE} from 'core/network/network.read.service';

module.exports = angular.module('spinnaker.oraclebmcs.cache.initializer', [
  ACCOUNT_SERVICE,
  NETWORK_READ_SERVICE
])
  .factory('oraclebmcsCacheConfigurer', function (accountService, networkReader) {

    let config = Object.create(null);
    let provider = 'oraclebmcs';

    config.credentials = {
      initializers: [ () => accountService.listAccounts(provider) ],
    };

    config.account = {
      initializers: [ () => accountService.getCredentialsKeyedByAccount(provider) ],
    };

    config.networks = {
      initializers: [ () => networkReader.listNetworksByProvider(provider) ],
    };

    return config;
  });
