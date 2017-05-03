'use strict';

import _ from 'lodash';
let angular = require('angular');

import {CiFilterModel} from './ciFilter.model';

import './ci.less';

module.exports = angular.module('spinnaker.netflix.ci.controller', [
  require('angular-ui-router').default,
])
  .controller('NetflixCiCtrl', function ($scope, $state, app) {
    const dataSource = app.getDataSource('ci');
    let attr = app.attributes;

    this.filterModel = CiFilterModel;

    this.viewState = { hasAllConfig: [attr.repoType, attr.repoProjectKey, attr.repoSlug].every((attr) => _.trim(attr)) };

    this.refreshBuilds = () => dataSource.refresh();

    this.getBuilds = () => {
      this.builds = dataSource.data;
      if ($state.includes('**.ci') && this.builds.length) {
        $state.go('.detail.detailTab', { buildId: this.builds[0].id, tab: 'output' }, { location: 'replace' });
      }
    };

    dataSource.activate();
    dataSource.ready().then(this.getBuilds);
    dataSource.onRefresh($scope, this.getBuilds);

    $scope.$on('$destroy', () => {
      CiFilterModel.searchFilter = '';
      dataSource.deactivate();
    });
  });

