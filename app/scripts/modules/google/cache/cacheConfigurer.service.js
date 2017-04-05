'use strict';

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {GCE_ADDRESS_READER} from 'google/address/address.reader';
import {GCE_HEALTH_CHECK_READER} from '../healthCheck/healthCheck.read.service';
import {INSTANCE_TYPE_SERVICE} from 'core/instance/instanceType.service';
import {LOAD_BALANCER_READ_SERVICE} from 'core/loadBalancer/loadBalancer.read.service';
import {NETWORK_READ_SERVICE} from 'core/network/network.read.service';
import {SUBNET_READ_SERVICE} from 'core/subnet/subnet.read.service';

let angular = require('angular');

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
