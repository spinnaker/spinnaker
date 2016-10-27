'use strict';

import _ from 'lodash';
import {CI_FILTER_MODEL} from './ci.filter.model';

let angular = require('angular');

require('./ci.less');

module.exports = angular.module('spinnaker.netflix.ci.controller', [
  CI_FILTER_MODEL,
  require('angular-ui-router'),
])
  .controller('NetflixCiCtrl', function ($scope, $state, app, CiFilterModel) {
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

    dataSource.ready().then(this.getBuilds);
    dataSource.onRefresh($scope, this.getBuilds);

    $scope.$on('$destroy', () => CiFilterModel.searchFilter = '');
  });

