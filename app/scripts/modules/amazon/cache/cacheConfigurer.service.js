'use strict';

const angular = require('angular');

import {
  ACCOUNT_SERVICE,
  INSTANCE_TYPE_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  SUBNET_READ_SERVICE
} from '@spinnaker/core';

import { VPC_READ_SERVICE } from '../vpc/vpc.read.service';

module.exports = angular.module('spinnaker.aws.cache.initializer', [
  ACCOUNT_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  INSTANCE_TYPE_SERVICE,
  SUBNET_READ_SERVICE,
  VPC_READ_SERVICE,
])
  .factory('awsCacheConfigurer', function ($q,
                                         accountService, instanceTypeService,
                                         subnetReader, vpcReader, loadBalancerReader) {

    let config = Object.create(null);

    config.credentials = {
      initializers: [ () => accountService.listAccounts('aws') ],
    };

    config.instanceTypes = {
      initializers: [ () => instanceTypeService.getAllTypesByRegion('aws') ],
    };

    config.loadBalancers = {
      initializers: [ () => loadBalancerReader.listLoadBalancers('aws') ],
    };

    config.subnets = {
      version: 2,
      initializers: [() => subnetReader.listSubnets() ],
    };

    config.networks = {
      version: 2,
      initializers: [() => vpcReader.listVpcs() ],
      onReset: [() => vpcReader.resetCache() ],
    };

    return config;
  });
