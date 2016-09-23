'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.certificateReader.service', [
    require('../../core/api/api.service.js'),
    require('../../core/cache/infrastructureCaches.js'),
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
