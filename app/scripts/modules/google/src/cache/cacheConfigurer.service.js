'use strict';

const angular = require('angular');

import { GCE_ADDRESS_READER } from 'google/address/address.reader';
import { GCE_HEALTH_CHECK_READER } from '../healthCheck/healthCheck.read.service';

export const GOOGLE_CACHE_CACHECONFIGURER_SERVICE = 'spinnaker.gce.cache.initializer';
export const name = GOOGLE_CACHE_CACHECONFIGURER_SERVICE; // for backwards compatibility
angular
  .module(GOOGLE_CACHE_CACHECONFIGURER_SERVICE, [
    require('../backendService/backendService.reader').name,
    GCE_ADDRESS_READER,
    GCE_HEALTH_CHECK_READER,
  ])
  .factory('gceCacheConfigurer', [
    'gceAddressReader',
    'gceBackendServiceReader',
    'gceCertificateReader',
    'gceHealthCheckReader',
    function(gceAddressReader, gceBackendServiceReader, gceCertificateReader, gceHealthCheckReader) {
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

      config.healthChecks = {
        initializers: [() => gceHealthCheckReader.listHealthChecks()],
      };

      return config;
    },
  ]);
