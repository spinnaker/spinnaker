'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.application.create.modal.controller', [
    require('angular-ui-router'),
    require('../../core/application/service/applications.write.service.js'),
    require('../../core/application/service/applications.read.service.js'),
    require('../../core/utils/lodash.js'),
    require('../../core/account/account.service.js'),
    require('../../core/config/settings.js'),
  ])
  .controller('netflixCreateApplicationModalCtrl', function($controller, $scope, $q, $log, $state, $uibModalInstance,
                                                            settings,
                                                            accountService, applicationWriter, applicationReader, _) {

    angular.extend(this, $controller('CreateApplicationModalCtrl', {
      $scope: $scope,
      $q: $q,
      $log: $log,
      $state: $state,
      $uibModalInstance: $uibModalInstance,
      accountService: accountService,
      applicationWriter: applicationWriter,
      applicationReader: applicationReader,
      _ : _,
    }));

    this.chaosEnabled = settings.feature.chaosMonkey;

    if (this.chaosEnabled) {
      this.application.chaosMonkey = {
        enabled: true,
        meanTimeBetweenKillsInWorkDays: 2,
        minTimeBetweenKillsInWorkDays: 1,
        grouping: 'cluster',
        regionsAreIndependent: true,
        exceptions: [],
      };
    }

    this.application.legacyUdf = false;

  });
