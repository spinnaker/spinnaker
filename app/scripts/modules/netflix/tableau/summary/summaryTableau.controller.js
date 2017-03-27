'use strict';

import {AUTHENTICATION_SERVICE} from 'core/authentication/authentication.service';
import {NetflixSettings} from '../../netflix.settings';

let angular = require('angular');

require('./../tableau.less');

module.exports = angular
  .module('spinnaker.netflix.tableau.summary.controller', [
    AUTHENTICATION_SERVICE
  ])
  .controller('SummaryTableauCtrl', function ($sce, authenticationService) {
    this.$onInit = () => {
      let [user] = authenticationService.getAuthenticatedUser().name.split('@');
      let url = NetflixSettings.tableau.summarySourceUrl
        .replace('${user}', user);
      this.srcUrl = $sce.trustAsResourceUrl(url);
    };

    this.$onInit();
  });
