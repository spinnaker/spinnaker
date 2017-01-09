'use strict';

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {INSTANCE_TYPE_SERVICE} from 'core/instance/instanceType.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.cf.cache.initializer', [
  ACCOUNT_SERVICE,
  require('core/loadBalancer/loadBalancer.read.service.js'),
  INSTANCE_TYPE_SERVICE,
  require('core/securityGroup/securityGroup.read.service.js'),
])
  .factory('cfCacheConfigurer', function ($q, accountService, instanceTypeService, loadBalancerReader) {

    let config = Object.create(null);

    config.credentials = {
      initializers: [ () => accountService.getCredentialsKeyedByAccount('cf') ],
    };

    config.instanceTypes = {
      initializers: [ () => instanceTypeService.getAllTypesByRegion('cf') ],
    };

    config.loadBalancers = {
      initializers: [ () => loadBalancerReader.listLoadBalancers('cf') ],
    };

    return config;
  });
