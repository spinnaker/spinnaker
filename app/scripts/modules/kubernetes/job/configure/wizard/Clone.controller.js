'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.job.configure.kubernetes.clone', [
  require('angular-ui-router'),
  require('../../../../core/application/modal/platformHealthOverride.directive.js'),
  require('../../../../core/job/job.write.service.js'),
  require('../../../../core/modal/wizard/v2modalWizard.service.js'),
  require('../../../../core/task/monitor/taskMonitorService.js'),
  require('../../../../core/modal/wizard/wizardSubFormValidation.service.js'),
])
  .controller('kubernetesCloneJobController', function($scope, $uibModalInstance, _, jobWriter,
                                                       v2modalWizardService, taskMonitorService,
                                                       kubernetesServerGroupConfigurationService,
                                                       jobCommand, application, title,
                                                       wizardSubFormValidation, $timeout) {
    $scope.pages = {
      templateSelection: require('./templateSelection.html'),
      basicSettings: require('../../../serverGroup/configure/wizard/basicSettings.html'),
      loadBalancers: require('../../../serverGroup/configure/wizard/loadBalancers.html'),
      completion: require('./completion.html'),
      volumes: require('../../../serverGroup/configure/wizard/volumes.html'),
    };

    $scope.title = title;

    $scope.applicationName = application.name;
    $scope.application = application;

    $scope.command = jobCommand;
    $scope.contextImages = jobCommand.viewState.contextImages;

    $scope.state = {
      loaded: false,
      requiresTemplateSelection: !!jobCommand.viewState.requiresTemplateSelection,
    };

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: 'Creating your job',
      forceRefreshMessage: 'Getting your new job from Kubernetes...',
      modalInstance: $uibModalInstance,
      forceRefreshEnabled: true
    });

    function configureCommand() {
      jobCommand.viewState.contextImages = $scope.contextImages;
      $scope.contextImages = null;
      kubernetesServerGroupConfigurationService.configureCommand(application, jobCommand).then(function () {
        $scope.state.loaded = true; // allows wizard directive to run (after digest).
        $timeout(initializeWizardState); // wait for digest.
        initializeWatches();
      });
    }

    function initializeWatches() {
      $scope.$watch('command.account', $scope.command.accountChanged);
      $scope.$watch('command.namespace', $scope.command.namespaceChanged);
    }

    function initializeWizardState() {
      var mode = jobCommand.viewState.mode;
      if (mode === 'clone' || mode === 'editPipeline') {
        v2modalWizardService.markComplete('location');
        v2modalWizardService.markComplete('load-balancers');
        v2modalWizardService.markComplete('completion');
        v2modalWizardService.markComplete('volumes');
      }

      wizardSubFormValidation
        .config({ scope: $scope, form: 'form' })
        .register({ page: 'location', subForm: 'basicSettings'});
    }

    this.isValid = function () {
      return $scope.command && $scope.command.containers.length > 0 &&
        $scope.command.account !== null &&
        v2modalWizardService.isComplete();
    };

    this.showSubmitButton = function () {
      return v2modalWizardService.allPagesVisited();
    };

    this.clone = function () {
      if ($scope.command.viewState.mode === 'editPipeline' || $scope.command.viewState.mode == 'createPipeline') {
        return $uibModalInstance.close($scope.command);
      }
      $scope.taskMonitor.submit(
        function() {
          return jobWriter.cloneJob(angular.copy($scope.command), application);
        }
      );
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };

    if (!$scope.state.requiresTemplateSelection) {
      configureCommand();
    } else {
      $scope.state.loaded = true;
    }

    $scope.$on('template-selected', function() {
      $scope.state.requiresTemplateSelection = false;
      configureCommand();
    });
  });
