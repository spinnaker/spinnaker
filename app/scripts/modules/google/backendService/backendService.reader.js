'use strict';

import {API_SERVICE} from 'core/api/api.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.backendService.reader.service', [
    API_SERVICE,
    require('core/cache/infrastructureCaches.js'),
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
          .useCache(infrastructureCaches.backendServices)
          .getList({q:'', type: 'backendServices'});
      }
    }

    return { listBackendServices };
  });
