'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.ci.detail.detailTab.controller', [
    require('angular-ui-router'),
    require('../../build.read.service.js'),
    require('core/scheduler/scheduler.factory'),
  ])
  .controller('CiDetailTabCtrl', function ($scope, $state, $stateParams, buildService, schedulerFactory) {
    this.viewState = {
      loading: true,
      isRunning: false,
    };

    let assembleText = (content) => {
      this.viewState.loading = false;
      return content.join('\n');
    };

    let getOutput = () => {
      return buildService.getBuildOutput($stateParams.buildId).then((response) => {
        this.content = assembleText(response.data);
      });
    };

    if ($stateParams.tab === 'output') {
      let activeRefresher = schedulerFactory.createScheduler(1000);
      activeRefresher.subscribe(() => {
        if (this.viewState.isRunning) {
          getOutput();
        }
      });
      getOutput();
      $scope.$on('$destroy', () => activeRefresher.unsubscribe());
    }

    if ($stateParams.tab === 'config') {
      buildService.getBuildConfig($stateParams.buildId).then((response) => {
        this.content = assembleText(response.data);
      });
    }
  });
