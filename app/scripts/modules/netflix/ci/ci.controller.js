'use strict';

import _ from 'lodash';
import {AUTHENTICATION_SERVICE} from 'core/authentication/authentication.service';

let angular = require('angular');

require('./ci.less');

module.exports = angular.module('spinnaker.netflix.ci.controller', [
  AUTHENTICATION_SERVICE,
  require('core/application/service/applications.read.service'),
  require('./build.read.service.js'),
  require('angular-ui-router'),
])
  .controller('NetflixCiCtrl', function ($scope, authenticationService, app, buildService, $stateParams, $state) {
    let attr = app.attributes;

    this.viewState = { searchFilter: '', hasAllConfig: [attr.repoType, attr.repoProjectKey, attr.repoSlug].every((attr) => _.trim(attr)) };

    this.getBuilds = () => {
      if (!this.viewState.hasAllConfig) { return; }
      buildService.getBuilds(attr.repoType, attr.repoProjectKey, attr.repoSlug, this.viewState.searchFilter).then((response) => {
        if ($state.includes('**.ci')) {
          $state.go('.detail.detailTab', { buildId: response[0].id, tab: 'output' }, { location: 'replace' });
        }
        this.builds = response;
      });
    };

    this.getBuilds();
    app.onRefresh($scope, this.getBuilds);
  });

