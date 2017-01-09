'use strict';

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {INSTANCE_TYPE_SERVICE} from 'core/instance/instanceType.service';
import {SUBNET_READ_SERVICE} from 'core/subnet/subnet.read.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws.cache.initializer', [
  ACCOUNT_SERVICE,
  require('core/loadBalancer/loadBalancer.read.service.js'),
  INSTANCE_TYPE_SERVICE,
  require('core/securityGroup/securityGroup.read.service.js'),
  SUBNET_READ_SERVICE,
  require('../vpc/vpc.read.service.js'),
])
  .factory('awsCacheConfigurer', function ($q,
                                         accountService, instanceTypeService, securityGroupReader,
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
      initializers: [vpcReader.listVpcs],
      onReset: [vpcReader.resetCache],
    };

    return config;
  });
