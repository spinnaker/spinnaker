'use strict';

require('../migrator.less');

import _ from 'lodash';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.migrator.directive', [
    require('angular-ui-bootstrap'),
    require('amazon/vpc/vpc.read.service.js'),
    require('core/config/settings.js'),
    require('core/subnet/subnet.read.service'),
    require('../migrator.service.js'),
    require('core/presentation/autoScroll/autoScroll.directive.js'),
    require('amazon/keyPairs/keyPairs.read.service'),
    require('amazon/vpc/vpc.read.service'),
    require('../migrationWarnings.component'),
    require('../migratedSecurityGroups.component'),
    require('../migratedLoadBalancers.component'),
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
                                        $uibModalInstance,
                                        migratorService, taskReader, keyPairsReader, vpcReader, subnetReader,
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
      subnetType: 'internal (vpc0)',
      elbSubnetType: 'external (vpc0)',
    };

    this.source = {
      region: serverGroup.region,
      credentials: serverGroup.account,
      name: serverGroup.name,
    };

    this.target = {
      region: serverGroup.region,
      credentials: serverGroup.account,
      vpcName: 'vpc0',
      keyName: serverGroup.launchConfig.keyName,
      availabilityZones: serverGroup.zones,
    };

    let keyPairLoader = keyPairsReader.listKeyPairs(),
        vpcLoader = vpcReader.listVpcs(),
        subnetLoader = subnetReader.listSubnetsByProvider('aws');

    $q.all({keyPairs: keyPairLoader, vpcs: vpcLoader, subnets: subnetLoader}).then(data => {
      this.vpcs = data.vpcs.filter(vpc => vpc.name === 'vpc0');
      this.accounts = _.uniq(this.vpcs.map(vpc => vpc.account));
      this.vpcs.forEach(vpc => {
        vpc.subnets = _.uniq(data.subnets.filter(s => s.vpcId === vpc.id && s.purpose).map(s => s.purpose)).sort();
      });

      this.keyPairs = (data.keyPairs || []).filter(kp => kp.region === serverGroup.region).map(kp => {
        return {account: kp.account, region: kp.region, name: kp.keyName};
      });
      this.accountChanged();
      this.state = 'configure';
    });

    this.accountChanged = () => {
      let vpc = this.vpcs.filter(v => v.account === this.target.credentials && v.region === this.target.region)[0];
      this.target.vpcId = vpc.id;
      this.subnets = vpc.subnets;
      this.filterKeyPairs();
    };

    this.filterKeyPairs = () => {
      this.filteredKeyPairs = (this.keyPairs || []).filter(kp => kp.account === this.target.credentials).map(kp => kp.name);
      if (!this.filteredKeyPairs.includes(this.target.keyName)) {
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

    let buildMigrationConfig = () => {
      return {
        application: application.name,
        type: 'migrateServerGroup',
        name: serverGroup.name,
        source: this.source,
        target: this.target,
        subnetType: this.migrationOptions.subnetType,
        elbSubnetType: this.migrationOptions.elbSubnetType,
        allowIngressFromClassic: this.migrationOptions.allowIngressFromClassic,
        dryRun: true
      };
    };

    // Generate preview
    this.calculateDryRun = () => {
      migratorService.executeMigration(application, buildMigrationConfig()).then(dryRunStarted, errorMode);
    };

    // Button handlers
    this.submit = () => {
      this.task = null;
      this.state = 'migrate';
      let config = buildMigrationConfig();
      config.dryRun = false;
      let executor = migratorService.executeMigration(application, config);
      executor.then(migrationStarted, errorMode);
    };

    this.cancel = () => {
      if (this.task && this.task.poller) {
        $timeout.cancel(this.task.poller);
      }
      $uibModalInstance.dismiss();
    };

  });
