'use strict';

import _ from 'lodash';
import {AUTHENTICATION_SERVICE} from 'core/authentication/authentication.service';
import {CI_FILTER_MODEL} from './ci.filter.model';

let angular = require('angular');

require('./ci.less');

module.exports = angular.module('spinnaker.netflix.ci.controller', [
  AUTHENTICATION_SERVICE,
  CI_FILTER_MODEL,
  require('core/application/service/applications.read.service'),
  require('./build.read.service.js'),
  require('angular-ui-router'),
])
  .controller('NetflixCiCtrl', function ($scope, authenticationService, app, buildService, CiFilterModel) {
    const dataSource = app.getDataSource('ci');
    let attr = app.attributes;

    this.filterModel = CiFilterModel;

    this.viewState = { hasAllConfig: [attr.repoType, attr.repoProjectKey, attr.repoSlug].every((attr) => _.trim(attr)) };

    this.refreshBuilds = () => dataSource.refresh();

    this.getBuilds = () => this.builds = dataSource.data;

    dataSource.ready().then(this.getBuilds);
    dataSource.onRefresh($scope, this.getBuilds);

    $scope.$on('$destroy', () => CiFilterModel.searchFilter = '');
  });

