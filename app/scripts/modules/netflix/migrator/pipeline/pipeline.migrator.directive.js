'use strict';

require('../migrator.less');

import _ from 'lodash';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.migrator.pipeline.directive', [
    require('angular-ui-bootstrap'),
    require('../../../amazon/vpc/vpc.read.service.js'),
    require('../../../core/config/settings.js'),
    require('../../../core/subnet/subnet.read.service'),
    require('../migrator.service.js'),
    require('../../../core/presentation/autoScroll/autoScroll.directive.js'),
    require('../../../core/pipeline/config/services/pipelineConfigService.js'),
    require('../../../core/utils/scrollTo/scrollTo.service.js'),
    require('../../../core/cache/cacheInitializer.js'),
    require('../../../core/task/task.read.service.js'),
    require('../../../amazon/keyPairs/keyPairs.read.service'),
    require('../../../amazon/vpc/vpc.read.service'),
    require('../migrationWarnings.component'),
    require('../migratedSecurityGroups.component'),
    require('../migratedLoadBalancers.component'),
  ])
  .directive('pipelineMigrator', function () {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        application: '=',
        pipeline: '=',
      },
      templateUrl: require('./pipeline.migrator.directive.html'),
      controller: 'PipelineMigratorActionCtrl',
      controllerAs: 'migratorActionCtrl',
    };
  })
  .controller('PipelineMigratorActionCtrl', function ($scope, $uibModal, $state, vpcReader, settings) {

    $scope.showAction = false;

    $scope.submittingTemplateUrl = require('../migrator.modal.submitting.html');

    var migratableClusters = [];

    function testCluster(stage) {
      return function(cluster) {
        if (!cluster.subnetType && (!cluster.provider || cluster.provider === 'aws')) {
          $scope.showAction = true;
          migratableClusters.push({stage: stage.name, cluster: cluster});
        }
      };
    }

    if (settings.feature.vpcMigrator) {
      let migrated = $scope.application.pipelineConfigs.data.find(test => test.name === $scope.pipeline.name + ' - vpc0');
      if (migrated) {
        $scope.migrated = migrated;
      }
      var stages = $scope.pipeline.stages || [];
      stages.forEach((stage) => {
        if (stage.type === 'deploy') {
          var clusters = stage.clusters || [];
          clusters.forEach(testCluster(stage));
        }
        if (stage.type === 'canary') {
          var clusterPairs = stage.clusterPairs || [];
          clusterPairs.forEach((clusterPair) => {
            testCluster(stage)(clusterPair.baseline);
            testCluster(stage)(clusterPair.canary);
          });
        }
      });
    }

    this.previewMigration = function () {
      if ($scope.migrated) {
        $state.go('^.pipelineConfig', {pipelineId: $scope.migrated.id});
        return;
      }
      $uibModal.open({
        templateUrl: require('./pipeline.migrator.modal.html'),
        controller: 'PipelineMigratorCtrl as vm',
        resolve: {
          pipeline: function () {
            return $scope.pipeline;
          },
          application: function () {
            return $scope.application;
          },
          migratableClusters: function() {
            return migratableClusters;
          },
        }
      });
    };
  })
  .controller('PipelineMigratorCtrl', function ($scope, pipeline, application, migratableClusters,
                                                $uibModalInstance, taskReader, $timeout, $state,
                                                migratorService, pipelineConfigService, scrollToService,
                                                cacheInitializer, $q, keyPairsReader, vpcReader, subnetReader) {

    this.submittingTemplateUrl = require('../migrator.modal.submitting.html');

    this.clustersWithStrategies = migratableClusters.filter(c => c.cluster.strategy !== '');

    // states: initialize, migrate, configure, error, preview, dryRun, complete

    this.state = 'initialize';

    this.viewState = {
      error: false,
      targetName: pipeline.name + ' - vpc0',
    };

    // shared component used by "submitting" overlay to indicate what's being operated against
    this.component = {
      name: pipeline.name
    };

    this.source = { pipelineId: pipeline.id };
    this.accountMapping = [];

    migratableClusters.forEach(stage => {
      let cluster = stage.cluster;
      if (!this.accountMapping.some(a => a.source === cluster.account)) {
        this.accountMapping.push({
          source: cluster.account,
          target: cluster.account,
          sourceKeyName: cluster.keyPair,
          keyName: cluster.keyPair,
        });
      }
    });

    this.migrationOptions = {
      allowIngressFromClassic: true,
    };

    let keyPairLoader = keyPairsReader.listKeyPairs(),
        vpcLoader = vpcReader.listVpcs(),
        subnetLoader = subnetReader.listSubnetsByProvider('aws');

    $q.all({keyPairs: keyPairLoader, vpcs: vpcLoader, subnets: subnetLoader}).then(data => {
      this.vpcs = data.vpcs.filter(vpc => vpc.name === 'vpc0');
      this.accounts = _.uniq(this.vpcs.map(vpc => vpc.account));
      this.subnetsByAccount = {};
      this.vpcs.forEach(vpc => {
        vpc.subnets = _.uniq(data.subnets.filter(s => s.vpcId === vpc.id && s.purpose).map(s => s.purpose)).sort();
        if (!this.subnetsByAccount[vpc.account]) {
          this.subnetsByAccount[vpc.account] = [];
        }
        this.subnetsByAccount[vpc.account] = _.uniq(this.subnetsByAccount[vpc.account].concat(vpc.subnets));
      });
      let filteredKeyPairs = data.keyPairs
        .filter(kp => migratableClusters
          .some(c => (c.cluster.availabilityZones || {})[kp.region]));
      this.keyPairs = _.groupBy(filteredKeyPairs || [], 'account');
      this.accountChanged();
      this.state = 'configure';
    });

    this.accountChanged = () => {
      let subnets = this.accountMapping.map(m => m.target).map(a => this.subnetsByAccount[a]);
      this.subnets = _.intersection.apply(null, subnets);
      if (this.subnets.indexOf(this.targetSubnet) < 0) {
        this.targetSubnet = this.subnets[0];
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
      application.pipelineConfigs.refresh().then(() => {
        this.viewState.executing = false;
        this.viewState.migrationComplete = true;

        if (this.preview.securityGroups && this.preview.securityGroups.length) {
          cacheInitializer.refreshCache('securityGroups');
        }
        if (this.preview.loadBalancers && this.preview.loadBalancers.length) {
          cacheInitializer.refreshCache('loadBalancers');
        }
      });
    };

    let migrationStarted = (task) => {
      this.task = task;
      taskReader.waitUntilTaskCompletes(application.name, task).then(migrationComplete, errorMode);
    };

    this.targetSubnet = 'internal (vpc0)';

    let buildCommand = (dryRun = true) => {
      let accountMapping = {},
          keyPairMapping = {};
      this.accountMapping.forEach(a => {
        accountMapping[a.source] = a.target;
        keyPairMapping[a.sourceKeyName] = a.keyName;
      });
      return {
        application: application.name,
        type: 'migratePipeline',
        name: pipeline.name,
        pipelineConfigId: pipeline.id,
        subnetTypeMapping: { 'EC2-CLASSIC': this.targetSubnet },
        elbSubnetTypeMapping: { 'EC2-CLASSIC': 'external (vpc0)' },
        accountMapping: accountMapping,
        keyPairMapping: keyPairMapping,
        newPipelineName: this.viewState.targetName,
        allowIngressFromClassic: this.migrationOptions.allowIngressFromClassic,
        dryRun: dryRun
      };
    };

    // Generate preview
    this.calculateDryRun = () => {
      migratorService.executeMigration(application, buildCommand(true)).then(dryRunStarted, errorMode);
    };

    this.close = () => {
      let newPipeline = application.pipelineConfigs.data.find(test => {
        return test.name.indexOf(this.viewState.targetName) === 0;
      });
      $state.go('^.pipelineConfig', {pipelineId: newPipeline.id});
    };

    this.cancel = () => {
      if (this.task) {
        $timeout.cancel(this.task.poller);
      }
      $uibModalInstance.dismiss();
    };

    this.submit = () => {
      this.state = 'migrate';
      this.task = null;
      migratorService.executeMigration(application, buildCommand(false)).then(migrationStarted, errorMode);
    };
  });
