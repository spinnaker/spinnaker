'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.ci.detail.controller', [
    require('angular-ui-router'),
    require('../build.read.service.js')
  ])
  .controller('CiDetailCtrl', function ($state, $stateParams, buildService) {
    this.viewState = { isDownloadable: () => $state.params.tab === 'output' };

    buildService.getBuildDetails($stateParams.buildId).then((response) => {
      if ($state.includes('**.ci.detail')) {
        $state.go('.detailTab', { buildId: $stateParams.buildId, tab: 'output' }, { location: 'replace' });
      }
      this.build = response;
    });

    this.downloadLink = buildService.getBuildRawLogLink($stateParams.buildId);
  });
