'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.create.controller', [
  require('../../../../utils/lodash.js'),
  require('../../services/pipelineConfigService.js'),
  require('../../../../utils/scrollTo/scrollTo.service.js'),
])
  .controller('CreatePipelineModalCtrl', function($scope, application, target, reinitialize,
                                                  _, pipelineConfigService, $modalInstance, $log) {

    var noTemplate = {name: 'None', stages: [], triggers: [], application: application.name};

    $scope.viewState = {};

    application.pipelines = application.pipelines || [];

    $scope.templates = [noTemplate].concat(application.pipelines);
    $scope.existingNames = _.pluck($scope.templates, 'name');

    $scope.command = {
      template: noTemplate,
      parallel: true,
    };

    this.cancel = $modalInstance.dismiss;

    this.createPipeline = function() {
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
      if (target === 'top') {
        template.index = application.pipelines.length ? application.pipelines[0].index - 1 : 0;
      } else {
        template.index = application.pipelines.length;
      }
      delete template.id;
      return pipelineConfigService.savePipeline(template).then(
        function() {
          template.isNew = true;
          if (reinitialize) {
            reinitialize();
          }
          $modalInstance.close();
        },
        function(response) {
          $log.warn(response);
          $scope.viewState.saveError = true;
          $scope.viewState.errorMessage = response.message || 'No message provided';
        }
      );
    };

  });
