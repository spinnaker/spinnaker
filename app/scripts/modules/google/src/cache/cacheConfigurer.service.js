'use strict';

const angular = require('angular');

import {
  ACCOUNT_SERVICE,
  INSTANCE_TYPE_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  NETWORK_READ_SERVICE,
  SUBNET_READ_SERVICE
} from '@spinnaker/core';

import { GCE_ADDRESS_READER } from 'google/address/address.reader';
import { GCE_HEALTH_CHECK_READER } from '../healthCheck/healthCheck.read.service';

module.exports = angular.module('spinnaker.gce.cache.initializer', [
  require('../backendService/backendService.reader.js'),
  ACCOUNT_SERVICE,
  GCE_ADDRESS_READER,
  GCE_HEALTH_CHECK_READER,
  INSTANCE_TYPE_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  NETWORK_READ_SERVICE,
  SUBNET_READ_SERVICE,
])
  .factory('gceCacheConfigurer', function (accountService, gceAddressReader, gceBackendServiceReader,
                                           gceCertificateReader, gceHealthCheckReader,
                                           instanceTypeService, loadBalancerReader, networkReader, subnetReader) {

    let config = Object.create(null);

    config.addresses = {
      initializers: [ () => gceAddressReader.listAddresses() ],
    };

    config.backendServices = {
      initializers: [ () => gceBackendServiceReader.listBackendServices() ],
    };

    config.certificates = {
      initializers: [ () => gceCertificateReader.listCertificates() ],
    };

    config.credentials = {
      initializers: [ () => accountService.getCredentialsKeyedByAccount('gce') ],
    };

    config.healthChecks = {
      initializers: [ () => gceHealthCheckReader.listHealthChecks() ],
    };

    config.instanceTypes = {
      initializers: [ () => instanceTypeService.getAllTypesByRegion('gce') ],
    };

    config.loadBalancers = {
      initializers: [ () => loadBalancerReader.listLoadBalancers('gce') ],
    };

    config.networks = {
      initializers: [ () => networkReader.listNetworksByProvider('gce') ],
    };

    config.subnets = {
      initializers: [ () => subnetReader.listSubnetsByProvider('gce') ],
    };

    return config;
  });
