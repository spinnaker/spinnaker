'use strict';

import {SCHEDULER_FACTORY} from 'core/scheduler/scheduler.factory';
import {CI_BUILD_READ_SERVICE} from '../services/ciBuild.read.service';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.ci.detail.controller', [
    require('angular-ui-router').default,
    CI_BUILD_READ_SERVICE,
    SCHEDULER_FACTORY,
  ])
  .controller('CiDetailCtrl', function ($scope, $state, $stateParams, ciBuildReader, schedulerFactory, app) {
    const dataSource = app.getDataSource('ci');
    this.viewState = {
      isDownloadable: () => $state.params.tab === 'output',
      isRunning: false
    };

    let getDetails = () => {
      ciBuildReader.getBuildDetails($stateParams.buildId).then((response) => {
        if ($state.includes('**.ci.detail')) {
          $state.go('.detailTab', {buildId: $stateParams.buildId, tab: 'output'}, {location: 'replace'});
        }
        this.build = response;
        this.viewState.isRunning = response.isRunning;
        let existingIndex = dataSource.data.findIndex(b => b.id === $stateParams.buildId);
        if (existingIndex > -1) {
          dataSource.data[existingIndex] = response;
          dataSource.dataUpdated();
        }
      });
    };

    let activeRefresher = schedulerFactory.createScheduler(1000);
    activeRefresher.subscribe(() => {
      if (this.viewState.isRunning) {
        getDetails();
      }
    });

    this.downloadLink = ciBuildReader.getBuildRawLogLink($stateParams.buildId);
    getDetails();

    $scope.$on('$destroy', () => activeRefresher.unsubscribe());
  });
