'use strict';

let angular = require('angular');
import {API_SERVICE} from 'core/api/api.service';
import {INFRASTRUCTURE_CACHE_SERVICE} from 'core/cache/infrastructureCaches.service';

module.exports = angular.module('spinnaker.deck.gce.backendService.reader.service', [
  API_SERVICE,
  INFRASTRUCTURE_CACHE_SERVICE
])
  .factory('gceBackendServiceReader', function (API, infrastructureCaches) {

    function listBackendServices (kind) {
      if (kind) {
        return listBackendServices()
          .then(([services]) => {
            return services.results.filter((service) => service.kind === kind);
          });
      } else {
        return API
          .all('search')
          .useCache(infrastructureCaches.get('backendServices'))
          .getList({q:'', type: 'backendServices'});
      }
    }

    return { listBackendServices };
  });
