'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.certificateSelector.component', [
    require('../../../../certificate/certificate.reader.js'),
    require('../../../../../core/cache/cacheInitializer.js'),
    require('../../../../../core/cache/infrastructureCaches.js'),
  ])
  .component('gceCertificateSelector', {
    bindings : {
      certificate: '=ngModel'
    },
    templateUrl: require('./certificateSelector.component.html'),
    controller: function (gceCertificateReader, cacheInitializer, infrastructureCaches) {

      let getCertificates = () => {
        return gceCertificateReader.listCertificates()
          .then(([response]) => {
            this.certificates = response.results;
          });
      };

      this.refreshCertificates = () => {
        this.refreshing = true;
        cacheInitializer.refreshCache('certificates')
          .then(getCertificates)
          .then(() => { this.refreshing = false; });
      };

      this.getCertificateRefreshTime = () => infrastructureCaches.certificates.getStats().ageMax;

      getCertificates();
    }
  });
