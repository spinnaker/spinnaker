'use strict';

angular.module('deckApp.pipelines')
  .directive('pipelineConfigStage', function() {
    return {
      restrict: 'E',
      require: '^pipelineConfigurer',
      scope: {
        stage: '=',
        viewState: '=',
        application: '='
      },
      controller: 'StageConfigCtrl as stageConfigCtrl',
      templateUrl: 'scripts/modules/pipelines/config/stages/stage.html',
      link: function(scope, elem, attrs, pipelineConfigurerCtrl) {
        scope.pipelineConfigurerCtrl = pipelineConfigurerCtrl;
      }
    };
  })
  .controller('StageConfigCtrl', function($scope, $element, pipelineConfig, $compile, $controller, $templateCache) {
    var stageTypes = pipelineConfig.getStageTypes();
    $scope.options = stageTypes;

    this.selectStage = function() {
      var type = $scope.stage.type,
          stageScope = $scope.$new();

      // clear existing contents
      $element.find('.stage-details').html('');
      $scope.description = '';

      if (type) {
        var stageConfig = stageTypes.filter(function(config) {
          return config.key === type;
        });
        if (stageConfig.length) {
          var config = stageConfig[0];
          $scope.description = config.description;

          var ctrl = config.controller.split(' as ');
          var controller = $controller(ctrl[0], {$scope: stageScope, stage: $scope.stage, viewState: $scope.viewState});
          if (ctrl.length === 2) {
            stageScope[ctrl[1]] = controller;
          }
          if (config.controllerAs) {
            stageScope[config.controllerAs] = controller;
          }

          var template = $templateCache.get(config.templateUrl);
          var templateBody = $compile(template)(stageScope);
          $element.find('.stage-details').html(templateBody);
        }
      }
    };

    $scope.$watch('stage', this.selectStage);
  });
