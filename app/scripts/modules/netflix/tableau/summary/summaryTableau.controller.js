'use strict';

import {AUTHENTICATION_SERVICE} from 'core/authentication/authentication.service';

let angular = require('angular');

require('./../tableau.less');

module.exports = angular
  .module('spinnaker.netflix.tableau.summary.controller', [
    require('core/config/settings'),
    AUTHENTICATION_SERVICE
  ])
  .controller('SummaryTableauCtrl', function ($sce, settings, authenticationService) {
    this.$onInit = () => {
      let [user] = authenticationService.getAuthenticatedUser().name.split('@');
      let url = settings.tableau.summarySourceUrl
        .replace('${user}', user);
      this.srcUrl = $sce.trustAsResourceUrl(url);
    };

    this.$onInit();
  });
