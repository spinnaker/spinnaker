'use strict';

require('../migrator.less');

let angular = require('angular');

module.exports = angular
  .module('spinnaker.migrator.pipeline.directive', [
    require('angular-ui-bootstrap'),
    require('../../../amazon/vpc/vpc.read.service.js'),
    require('../../../core/config/settings.js'),
    require('../migrator.service.js'),
    require('../../../core/presentation/autoScroll/autoScroll.directive.js'),
    require('../../../core/pipeline/config/services/pipelineConfigService.js'),
    require('../../../core/utils/scrollTo/scrollTo.service.js'),
    require('../../../core/cache/cacheInitializer.js'),
    require('../../../core/task/task.read.service.js'),
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

    var actionableDeployStages = [];

    function testCluster(stage) {
      return function(cluster) {
        if (!cluster.subnetType && (!cluster.provider || cluster.provider === 'aws')) {
          $scope.showAction = true;
          if (cluster.strategy !== '') {
            actionableDeployStages.push({strategy: cluster.strategy, name: stage.name});
          }
        }
      };
    }

    if (settings.feature.vpcMigrator) {
      let [migrated] = $scope.application.pipelineConfigs.data.filter(test => test.name === $scope.pipeline.name + ' - vpc0');
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
          actionableDeployStages: function() {
            return actionableDeployStages;
          },
        }
      });
    };
  })
  .controller('PipelineMigratorCtrl', function ($scope, pipeline, application, actionableDeployStages,
                                                $uibModalInstance, taskReader, $timeout, $state,
                                                migratorService, pipelineConfigService, scrollToService,
                                                cacheInitializer) {

    this.submittingTemplateUrl = require('../migrator.modal.submitting.html');

    this.actionableDeployStages = actionableDeployStages;

    this.viewState = {
      computing: true,
      executing: false,
      error: false,
      migrationComplete: false,
      targetName: pipeline.name + ' - vpc0',
    };

    // shared component used by "submitting" overlay to indicate what's being operated against
    this.component = {
      name: pipeline.name
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

    var source = { pipelineId: pipeline.id },
        target = { vpcName: 'vpc0', };

    this.migrationOptions = {
      allowIngressFromClassic: true,
      subnetType: 'internal',
    };

    var migrationConfig = {
      application: application,
      type: 'deepCopyPipeline',
      name: pipeline.name,
      source: source,
      target: target,
      allowIngressFromClassic: true,
      dryRun: true
    };

    // Generate preview
    let executor = migratorService.executeMigration(migrationConfig);

    executor.then(dryRunStarted, errorMode);

    this.close = () => {
      var [newPipeline] = application.pipelineConfigs.data.filter(test => {
        return test.name.indexOf(this.viewState.targetName) === 0;
      });
      $state.go('^.pipelineConfig', {pipelineId: newPipeline.id});
    };

    this.cancel = () => {
      $timeout.cancel(this.task.poller);
      $uibModalInstance.dismiss();
    };

    this.submit = () => {
      this.viewState.executing = true;
      migrationConfig.dryRun = false;
      migrationConfig.allowIngressFromClassic = this.migrationOptions.allowIngressFromClassic;
      migrationConfig.target.subnetType = this.migrationOptions.subnetType;
      let executor = migratorService.executeMigration(migrationConfig);
      executor.then(migrationStarted, errorMode);
    };
  });
