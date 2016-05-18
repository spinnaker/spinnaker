'use strict';

require('../migrator.less');

let angular = require('angular');

module.exports = angular
  .module('spinnaker.migrator.directive', [
    require('angular-ui-bootstrap'),
    require('../../../amazon/vpc/vpc.read.service.js'),
    require('../../../core/config/settings.js'),
    require('../migrator.service.js'),
    require('../../../core/presentation/autoScroll/autoScroll.directive.js'),
  ])
  .directive('migrator', function () {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        application: '=',
        serverGroup: '=',
      },
      templateUrl: require('./serverGroup.migrator.directive.html'),
      controller: 'MigratorActionCtrl',
      controllerAs: 'migratorActionCtrl',
    };
  })
  .controller('MigratorActionCtrl', function ($scope, $uibModal, vpcReader, settings) {

    vpcReader.getVpcName($scope.serverGroup.vpcId).then(function (name) {
      $scope.showAction = !name && settings.feature.vpcMigrator;
    });

    this.previewMigration = function () {
      $uibModal.open({
        templateUrl: require('./serverGroup.migrator.modal.html'),
        controller: 'MigratorCtrl as vm',
        resolve: {
          serverGroup: function () {
            return $scope.serverGroup;
          },
          application: function () {
            return $scope.application;
          },
        }
      });
    };
  })
  .controller('MigratorCtrl', function ($scope, $timeout,
                                        $uibModalInstance,
                                        migratorService, taskReader,
                                        serverGroup, application) {

    this.submittingTemplateUrl = require('../migrator.modal.submitting.html');

    this.viewState = {
      computing: true,
      executing: false,
      error: false,
      migrationComplete: false,
    };

    // shared component used by "submitting" overlay to indicate what's being operated against
    this.component = {
      name: serverGroup.name
    };

    // Async handlers

    let errorMode = (error) => {
      this.viewState.computing = false;
      this.viewState.executing = false;
      if (this.task && this.task.failureMessage) {
        this.viewState.error = this.task.failureMessage;
      } else {
        this.viewState.error = error || 'An unknown error occurred. Please try again later.';
      }
    };

    let dryRunComplete = () => {
      this.viewState.computing = false;
      this.preview = this.task.getPreview();
    };

    let dryRunStarted = (task) => {
      this.task = task;
      taskReader.waitUntilTaskCompletes(application.name, task).then(dryRunComplete, errorMode);
    };

    let migrationComplete = () => {
      this.viewState.executing = false;
      this.viewState.migrationComplete = true;
    };

    let migrationStarted = (task) => {
      this.task = task;
      taskReader.waitUntilTaskCompletes(application.name, task).then(migrationComplete, errorMode);
    };

    this.migrationOptions = {
      allowIngressFromClassic: true,
      subnetType: 'internal',
    };

    this.source = {
      region: serverGroup.region,
      account: serverGroup.account,
      asgName: serverGroup.name,
    };

    // this will probably become configurable at some point
    let target = {
      region: serverGroup.region,
      account: serverGroup.account,
      vpcName: 'vpc0'
    };

    // Shared config for dry run and migration
    let migrationConfig = {
      application: application,
      type: 'deepCopyServerGroup',
      name: serverGroup.name,
      source: this.source,
      target: target,
      allowIngressFromClassic: true,
      dryRun: true
    };

    // Generate preview
    let executor = migratorService.executeMigration(migrationConfig);

    executor.then(dryRunStarted, errorMode);

    // Button handlers
    this.submit = () => {
      this.viewState.executing = true;
      migrationConfig.dryRun = false;
      migrationConfig.allowIngressFromClassic = this.migrationOptions.allowIngressFromClassic;
      migrationConfig.target.subnetType = this.migrationOptions.subnetType;
      let executor = migratorService.executeMigration(migrationConfig);
      executor.then(migrationStarted, errorMode);
    };

    this.cancel = () => {
      if (this.task && this.task.poller) {
        $timeout.cancel(this.task.poller);
      }
      $uibModalInstance.dismiss();
    };

  });
