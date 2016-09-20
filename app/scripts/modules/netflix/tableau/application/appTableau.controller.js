'use strict';

let angular = require('angular');

require('./../tableau.less');

module.exports = angular
  .module('spinnaker.netflix.tableau.application.controller', [
    require('../../../core/config/settings'),
    require('../../../core/authentication/authentication.service')
  ])
  .controller('AppTableauCtrl', function ($sce, app, settings, authenticationService) {

    this.$onInit = () => {
      let [user] = authenticationService.getAuthenticatedUser().name.split('@');
      let url = settings.tableau.appSourceUrl
        .replace('${app}', app.name)
        .replace('${user}', user);
      this.srcUrl = $sce.trustAsResourceUrl(url);
    };

    this.$onInit();

  });
