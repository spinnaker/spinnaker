'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.create.controller', [
  require('../../utils/lodash.js'),
  require('../../pipeline/config/services/pipelineConfigService.js'),
])
  .controller('CreatePipelineModalCtrl', function($scope, application,
                                                  _, pipelineConfigService, $modalInstance, $log) {

    var noTemplate = {
      name: 'None',
      stages: [],
      triggers: [],
      application: application.name,
      limitConcurrent: true
    };

    $scope.viewState = {};

    application.pipelineConfigs = application.pipelineConfigs || [];

    $scope.templates = [noTemplate].concat(application.pipelineConfigs);
    $scope.existingNames = _.pluck($scope.templates, 'name');

    $scope.command = {
      template: noTemplate,
      parallel: true,
    };

    this.cancel = $modalInstance.dismiss;

    this.createPipeline = () => {
      var template = $scope.command.template;
      if (template.fromServer) {
        template = angular.copy(template.plain());
      } else {
        template = angular.copy(template);
      }
      if ($scope.command.template === noTemplate && $scope.command.parallel) {
        pipelineConfigService.enableParallelExecution(template);
      }

      template.name = $scope.command.name;
      template.index = application.pipelineConfigs.length;
      delete template.id;
      return pipelineConfigService.savePipeline(template).then(
        function() {
          template.isNew = true;
          application.reloadPipelineConfigs().then(() => {
            let newPipeline = _.find(application.pipelineConfigs, {name: template.name});
            if (!newPipeline) {
              $log.warn('Could not find new pipeline after save succeeded.');
              $scope.viewState.saveError = true;
              $scope.viewState.errorMessage = 'Sorry, there was an error retrieving your new pipeline. Please refresh the browser.';
            } else {
              $modalInstance.close(newPipeline.id);
            }
          });
        },
        function(response) {
          $log.warn(response);
          $scope.viewState.saveError = true;
          var message = response && response.data && response.data.message ? response.data.message : 'No message provided';
          $scope.viewState.errorMessage = message;
        }
      );
    };

  }).name;
