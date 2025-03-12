'use strict';

import { module } from 'angular';

export const CORE_PIPELINE_CONFIG_STAGES_OPTIONALSTAGE_OPTIONALSTAGE_DIRECTIVE =
  'spinnaker.core.pipeline.stage.optionalStage.directive';
export const name = CORE_PIPELINE_CONFIG_STAGES_OPTIONALSTAGE_OPTIONALSTAGE_DIRECTIVE; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_STAGES_OPTIONALSTAGE_OPTIONALSTAGE_DIRECTIVE, [])
  .directive('optionalStage', function () {
    return {
      restrict: 'E',
      scope: {
        stage: '=',
      },
      templateUrl: require('./optionalStage.directive.html'),
      controller: 'OptionalStageCtrl',
      controllerAs: 'optionalStageCtrl',
    };
  })
  .controller('OptionalStageCtrl', [
    '$scope',
    function ($scope) {
      this.isOptional = function () {
        return $scope.stage && $scope.stage.stageEnabled;
      };

      this.toggleOptional = function () {
        if (this.isOptional()) {
          delete $scope.stage.stageEnabled;
        } else {
          $scope.stage.stageEnabled = {
            type: 'expression',
          };
        }
      };
    },
  ]);
