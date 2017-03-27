'use strict';

import {AUTHENTICATION_SERVICE} from 'core/authentication/authentication.service';
import {NetflixSettings} from '../../netflix.settings';

let angular = require('angular');

require('./../tableau.less');

module.exports = angular
  .module('spinnaker.netflix.tableau.application.controller', [
    AUTHENTICATION_SERVICE
  ])
  .controller('AppTableauCtrl', function ($sce, app, authenticationService) {

    this.$onInit = () => {
      let [user] = authenticationService.getAuthenticatedUser().name.split('@');
      let url = NetflixSettings.tableau.appSourceUrl
        .replace('${app}', app.name)
        .replace('${user}', user);
      this.srcUrl = $sce.trustAsResourceUrl(url);
    };

    this.$onInit();

  });
