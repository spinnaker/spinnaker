'use strict';

import {API_SERVICE} from 'core/api/api.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.certificateReader.service', [
    API_SERVICE,
    require('core/cache/infrastructureCaches.js'),
  ])
  .factory('gceCertificateReader', function (API, infrastructureCaches) {

    function listCertificates () {
      return API
        .all('search')
        .useCache(infrastructureCaches.certificates)
        .getList({ q: '', type: 'sslCertificates' });
    }

    return { listCertificates };
  });
