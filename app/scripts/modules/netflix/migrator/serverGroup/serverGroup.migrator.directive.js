'use strict';

require('../migrator.less');

let angular = require('angular');

module.exports = angular
  .module('spinnaker.migrator.directive', [
    require('angular-ui-bootstrap'),
    require('../../../amazon/vpc/vpc.read.service.js'),
    require('../../../core/config/settings.js'),
    require('../../../core/utils/lodash'),
    require('../migrator.service.js'),
    require('../../../core/presentation/autoScroll/autoScroll.directive.js'),
    require('../../../amazon/keyPairs/keyPairs.read.service'),
    require('../../../amazon/vpc/vpc.read.service'),
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
  .controller('MigratorCtrl', function ($scope, $timeout, $q,
                                        $uibModalInstance, _,
                                        migratorService, taskReader, keyPairsReader, vpcReader,
                                        serverGroup, application) {

    this.submittingTemplateUrl = require('../migrator.modal.submitting.html');

    // states: initialize, migrate, configure, error, preview, dryRun, complete

    this.state = 'initialize';

    this.viewState = {
      error: false,
    };

    // shared component used by "submitting" overlay to indicate what's being operated against
    this.component = {
      name: serverGroup.name
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

    this.target = {
      region: serverGroup.region,
      account: serverGroup.account,
      vpcName: 'vpc0',
      keyName: serverGroup.launchConfig.keyName,
    };

    let keyPairLoader = keyPairsReader.listKeyPairs(),
        vpcLoader = vpcReader.listVpcs();

    $q.all({keyPairs: keyPairLoader, vpcs: vpcLoader}).then(data => {
      this.accounts = _.uniq(data.vpcs.filter(vpc => vpc.name === 'vpc0').map(vpc => vpc.account));
      this.keyPairs = (data.keyPairs || []).filter(kp => kp.region === serverGroup.region).map(kp => {
        return {account: kp.account, region: kp.region, name: kp.keyName};
      });
      this.filterKeyPairs();
      this.state = 'configure';
    });

    this.filterKeyPairs = () => {
      this.filteredKeyPairs = (this.keyPairs || []).filter(kp => kp.account === this.target.account).map(kp => kp.name);
      if (this.filteredKeyPairs.indexOf(this.target.keyName) < 0) {
        this.target.keyName = null;
      }
    };

    // Async handlers

    let errorMode = (error) => {
      this.state = 'error';
      if (this.task && this.task.failureMessage) {
        this.viewState.error = this.task.failureMessage;
      } else {
        this.viewState.error = error || 'An unknown error occurred. Please try again later.';
      }
    };

    let dryRunComplete = () => {
      this.state = 'preview';
      this.preview = this.task.getPreview();
    };

    let dryRunStarted = (task) => {
      this.task = task;
      this.state = 'dryRun';
      taskReader.waitUntilTaskCompletes(application.name, task).then(dryRunComplete, errorMode);
    };

    let migrationComplete = (task) => {
      this.task = task;
      this.preview = this.task.getPreview();
      this.state = 'complete';
    };

    let migrationStarted = (task) => {
      this.task = task;
      taskReader.waitUntilTaskCompletes(application.name, task).then(migrationComplete, errorMode);
    };

    // Shared config for dry run and migration
    let migrationConfig = {
      application: application,
      type: 'deepCopyServerGroup',
      name: serverGroup.name,
      source: this.source,
      target: this.target,
      allowIngressFromClassic: true,
      dryRun: true
    };

    // Generate preview
    this.calculateDryRun = () => {
      migratorService.executeMigration(migrationConfig).then(dryRunStarted, errorMode);
    };

    // Button handlers
    this.submit = () => {
      this.state = 'migrate';
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
