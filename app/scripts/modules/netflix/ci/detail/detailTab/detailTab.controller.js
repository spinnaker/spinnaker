'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.ci.detail.detailTab.controller', [
    require('angular-ui-router'),
    require('../../build.read.service.js'),
  ])
  .controller('CiDetailTabCtrl', function ($stateParams, buildService) {
    this.viewState = { loading: true };
    let assembleText = (content) => {
      this.viewState.loading = false;
      return content.join('\n');
    };

    if ($stateParams.tab === 'output') {
      buildService.getBuildOutput($stateParams.buildId).then((response) => {
        this.content = assembleText(response.data);
      });
    }

    if ($stateParams.tab === 'config') {
      buildService.getBuildConfig($stateParams.buildId).then((response) => {
        this.content = assembleText(response.data);
      });
    }
  });
