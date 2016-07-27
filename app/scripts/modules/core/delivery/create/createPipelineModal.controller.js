'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.create.controller', [
  require('../../utils/lodash.js'),
  require('../../pipeline/config/services/pipelineConfigService.js'),
])
  .controller('CreatePipelineModalCtrl', function($scope, application,
                                                  _, pipelineConfigService, $uibModalInstance, $log) {

    var noTemplate = {
      name: 'None',
      stages: [],
      triggers: [],
      application: application.name,
      limitConcurrent: true,
      keepWaitingPipelines: false
    };

    $scope.viewState = {};

    $scope.templates = [noTemplate].concat(application.pipelineConfigs.data);
    $scope.strategyTemplates = [noTemplate].concat(application.strategyConfigs.data);

    $scope.existingNames = _.pluck($scope.templates, 'name');
    $scope.existingStrategyNames = _.pluck($scope.strategyTemplates, 'name');

    $scope.command = {
      template: noTemplate,
      parallel: true,
      strategy: false,
    };

    this.cancel = $uibModalInstance.dismiss;

    this.createPipeline = () => {
      var template = $scope.command.template;
      template = angular.copy(template);
      if ($scope.command.template === noTemplate && $scope.command.parallel) {
        pipelineConfigService.enableParallelExecution(template);
      }

      template.name = $scope.command.name;
      template.index = application.pipelineConfigs.data.length;
      delete template.id;

      if($scope.command.strategy === true) {
          template.strategy = true;
          template.limitConcurrent = false;
      }

      $scope.viewState.submitting = true;
      return pipelineConfigService.savePipeline(template).then(
        function() {
          template.isNew = true;
          application.pipelineConfigs.refresh().then(() => {
            let newPipeline = _.find(template.strategy === true ? application.strategyConfigs.data : application.pipelineConfigs.data, {name: template.name});
            if (!newPipeline) {
              $log.warn('Could not find new pipeline after save succeeded.');
              $scope.viewState.saveError = true;
              $scope.viewState.errorMessage = 'Sorry, there was an error retrieving your new pipeline. Please refresh the browser.';
              $scope.viewState.submitting = false;
            } else {
              $uibModalInstance.close(newPipeline.id);
            }
          });
        },
        function(response) {
          $log.warn(response);
          $scope.viewState.submitting = false;
          $scope.viewState.saveError = true;
          var message = response && response.data && response.data.message ? response.data.message : 'No message provided';
          $scope.viewState.errorMessage = message;
        }
      );
    };

  });
