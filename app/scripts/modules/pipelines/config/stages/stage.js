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

    function getConfig(type) {
      var matches = stageTypes.filter(function(config) {
        return config.key === type;
      });
      return matches.length ? matches[0] : null;
    }

    this.selectStage = function(newVal, oldVal) {
      var type = $scope.stage.type,
          stageScope = $scope.$new();

      // clear existing contents
      $element.find('.stage-details').html('');
      $scope.description = '';

      if (type) {
        var config = getConfig(type);

        if (config) {
          $scope.description = config.description;
          updateStageName(config, oldVal);
          applyConfigController(config, stageScope);

          var template = $templateCache.get(config.templateUrl);
          var templateBody = $compile(template)(stageScope);
          $element.find('.stage-details').html(templateBody);
        }
      }
    };

    function applyConfigController(config, stageScope) {
      if (config.controller) {
        var ctrl = config.controller.split(' as ');
        var controller = $controller(ctrl[0], {$scope: stageScope, stage: $scope.stage, viewState: $scope.viewState});
        if (ctrl.length === 2) {
          stageScope[ctrl[1]] = controller;
        }
        if (config.controllerAs) {
          stageScope[config.controllerAs] = controller;
        }
      }
    }

    function updateStageName(config, oldVal) {
      // apply a default name if the type changes and the user has not specified a name
      if (oldVal) {
        var oldConfig = getConfig(oldVal);
        if (oldConfig && $scope.stage.name === oldConfig.label) {
          $scope.stage.name = config.label;
        }
      }
      if (!$scope.stage.name) {
        $scope.stage.name = config.label;
      }
    }

    $scope.$watch('stage.type', this.selectStage);
    $scope.$watch('viewState.stageIndex', this.selectStage);
  });
