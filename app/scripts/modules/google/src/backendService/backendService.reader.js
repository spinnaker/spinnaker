'use strict';

const angular = require('angular');

import { API, InfrastructureCaches } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.deck.gce.backendService.reader.service', [])
  .factory('gceBackendServiceReader', function() {
    function listBackendServices(kind) {
      if (kind) {
        return listBackendServices().then(([services]) => {
          if (services) {
            const results = services.results || [];
            return results.filter(service => service.kind === kind);
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
