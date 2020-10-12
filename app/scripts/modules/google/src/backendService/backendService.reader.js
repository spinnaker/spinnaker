'use strict';

import { module } from 'angular';

import { API, InfrastructureCaches } from '@spinnaker/core';

export const GOOGLE_BACKENDSERVICE_BACKENDSERVICE_READER = 'spinnaker.deck.gce.backendService.reader.service';
export const name = GOOGLE_BACKENDSERVICE_BACKENDSERVICE_READER; // for backwards compatibility
module(GOOGLE_BACKENDSERVICE_BACKENDSERVICE_READER, []).factory('gceBackendServiceReader', function () {
  function listBackendServices(kind) {
    if (kind) {
      return listBackendServices().then(([services]) => {
        if (services) {
          const results = services.results || [];
          return results.filter((service) => service.kind === kind);
        }
        return [];
      });
    } else {
      return API.all('search')
        .useCache(InfrastructureCaches.get('backendServices'))
        .getList({ q: '', type: 'backendServices', allowShortQuery: 'true' });
    }
  }

  return { listBackendServices };
});
