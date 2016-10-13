'use strict';

import {API_SERVICE} from 'core/api/api.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce.httpHealthCheck.reader', [
  API_SERVICE,
  require('core/cache/infrastructureCaches.js'),
])
  .factory('gceHttpHealthCheckReader', function ($q, API, infrastructureCaches) {

    function listHttpHealthChecks() {
      return API
        .all('search')
        .useCache(infrastructureCaches.httpHealthChecks)
        .getList({q: '', type: 'httpHealthChecks'});
    }

    return {
      listHttpHealthChecks: listHttpHealthChecks,
    };
  });
