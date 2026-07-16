'use strict';

import { InfrastructureCaches, REST, SearchService } from '@spinnaker/core';

import { GceCertificateReader } from '../certificate/certificate.reader';
import { GceHealthCheckReader } from '../healthCheck/healthCheck.read.service';

export const GOOGLE_CACHE_CACHECONFIGURER_SERVICE = 'spinnaker.gce.cache.initializer';
export const name = GOOGLE_CACHE_CACHECONFIGURER_SERVICE; // for backwards compatibility
export class GceCacheConfigurer {
  constructor() {
    const gceCertificateReader = new GceCertificateReader();
    const gceHealthCheckReader = new GceHealthCheckReader();
    const listAddresses = () =>
      SearchService.search({ q: '', type: 'addresses', allowShortQuery: 'true' }, InfrastructureCaches.get('addresses'))
        .then((searchResults) => {
          if (searchResults && searchResults.results) {
            return searchResults.results
              .filter((result) => result.provider === 'gce')
              .map((result) => ({ ...JSON.parse(result.address), account: result.account, region: result.region }));
          }
          return [];
        })
        .catch(() => []);
    const listBackendServices = () =>
      REST('/search')
        .useCache(InfrastructureCaches.get('backendServices'))
        .query({ q: '', type: 'backendServices', allowShortQuery: 'true' })
        .get();

    const config = Object.create(null);

    config.addresses = {
      initializers: [listAddresses],
    };

    config.backendServices = {
      initializers: [listBackendServices],
    };

    config.certificates = {
      initializers: [() => gceCertificateReader.listCertificates()],
    };

    config.healthChecks = {
      initializers: [() => gceHealthCheckReader.listHealthChecks()],
    };

    return config;
  }
}
