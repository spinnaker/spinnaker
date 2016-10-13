'use strict';

import {API_SERVICE} from 'core/api/api.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.backendService.reader.service', [
    API_SERVICE,
    require('core/cache/infrastructureCaches.js'),
  ])
  .factory('gceBackendServiceReader', function (API, infrastructureCaches) {

    function listBackendServices () {
      return API
        .all('search')
        .useCache(infrastructureCaches.backendServices)
        .getList({q:'', type: 'backendServices'});
    }

    return { listBackendServices };
  });
