'use strict';

const angular = require('angular');

import {
  AccountService,
  INSTANCE_TYPE_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  NetworkReader,
  SubnetReader,
} from '@spinnaker/core';

import { GCE_ADDRESS_READER } from 'google/address/address.reader';
import { GCE_HEALTH_CHECK_READER } from '../healthCheck/healthCheck.read.service';

module.exports = angular
  .module('spinnaker.gce.cache.initializer', [
    require('../backendService/backendService.reader').name,
    GCE_ADDRESS_READER,
    GCE_HEALTH_CHECK_READER,
    INSTANCE_TYPE_SERVICE,
    LOAD_BALANCER_READ_SERVICE,
  ])
  .factory('gceCacheConfigurer', [
    'gceAddressReader',
    'gceBackendServiceReader',
    'gceCertificateReader',
    'gceHealthCheckReader',
    'instanceTypeService',
    'loadBalancerReader',
    function(
      gceAddressReader,
      gceBackendServiceReader,
      gceCertificateReader,
      gceHealthCheckReader,
      instanceTypeService,
      loadBalancerReader,
    ) {
      const config = Object.create(null);

      config.addresses = {
        initializers: [() => gceAddressReader.listAddresses()],
      };

      config.backendServices = {
        initializers: [() => gceBackendServiceReader.listBackendServices()],
      };

      config.certificates = {
        initializers: [() => gceCertificateReader.listCertificates()],
      };

      config.credentials = {
        initializers: [() => AccountService.getCredentialsKeyedByAccount('gce')],
      };

      config.healthChecks = {
        initializers: [() => gceHealthCheckReader.listHealthChecks()],
      };

      config.instanceTypes = {
        initializers: [() => instanceTypeService.getAllTypesByRegion('gce')],
      };

      config.loadBalancers = {
        initializers: [() => loadBalancerReader.listLoadBalancers('gce')],
      };

      config.networks = {
        initializers: [() => NetworkReader.listNetworksByProvider('gce')],
      };

      config.subnets = {
        initializers: [() => SubnetReader.listSubnetsByProvider('gce')],
      };

      return config;
    },
  ]);
