'use strict';

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {GCE_HEALTH_CHECK_READER} from '../healthCheck/healthCheck.read.service';
import {INSTANCE_TYPE_SERVICE} from 'core/instance/instanceType.service';
import {LOAD_BALANCER_READ_SERVICE} from 'core/loadBalancer/loadBalancer.read.service';
import {NETWORK_READ_SERVICE} from 'core/network/network.read.service';
import {SUBNET_READ_SERVICE} from 'core/subnet/subnet.read.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce.cache.initializer', [
  require('../backendService/backendService.reader.js'),
  ACCOUNT_SERVICE,
  INSTANCE_TYPE_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  NETWORK_READ_SERVICE,
  SUBNET_READ_SERVICE,
  GCE_HEALTH_CHECK_READER,
  require('../httpHealthCheck/httpHealthCheck.reader.js'),
])
  .factory('gceCacheConfigurer', function (accountService, gceBackendServiceReader,
                                           gceCertificateReader, gceHealthCheckReader,
                                           gceHttpHealthCheckReader, instanceTypeService,
                                           loadBalancerReader, networkReader, subnetReader) {

    let config = Object.create(null);

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

    config.httpHealthChecks = {
      initializers: [ () => gceHttpHealthCheckReader.listHttpHealthChecks() ],
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
