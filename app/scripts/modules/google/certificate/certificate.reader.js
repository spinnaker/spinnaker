'use strict';

import {INFRASTRUCTURE_CACHE_SERVICE} from 'core/cache/infrastructureCaches.service';
import {API_SERVICE} from 'core/api/api.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.certificateReader.service', [API_SERVICE, INFRASTRUCTURE_CACHE_SERVICE])
  .factory('gceCertificateReader', function (API, infrastructureCaches) {

    function listCertificates () {
      return API
        .all('search')
        .useCache(infrastructureCaches.get('certificates'))
        .getList({ q: '', type: 'sslCertificates' });
    }

    return { listCertificates };
  });
