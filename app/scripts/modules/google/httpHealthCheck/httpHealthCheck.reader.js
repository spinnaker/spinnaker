'use strict';

let angular = require('angular');

import {INFRASTRUCTURE_CACHE_SERVICE} from 'core/cache/infrastructureCaches.service';
import {API_SERVICE} from 'core/api/api.service';

module.exports = angular.module('spinnaker.gce.httpHealthCheck.reader', [
  API_SERVICE,
  INFRASTRUCTURE_CACHE_SERVICE
])
  .factory('gceHttpHealthCheckReader', function ($q, API, infrastructureCaches) {

    function listHttpHealthChecks() {
      return API
        .all('search')
        .useCache(infrastructureCaches.get('httpHealthChecks'))
        .getList({q: '', type: 'httpHealthChecks'});
    }

    return {
      listHttpHealthChecks: listHttpHealthChecks,
    };
  });
