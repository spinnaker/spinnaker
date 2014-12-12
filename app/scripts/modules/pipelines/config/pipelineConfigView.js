'use strict';

angular.module('deckApp.pipelines')
  .directive('pipelineConfigView', function() {
    return {
      restrict: 'E',
      require: '^pipelineConfigurer',
      scope: {
        pipeline: '=',
        application: '=',
        viewState: '='
      },
      templateUrl: 'scripts/modules/pipelines/config/pipelineConfigView.html',
      link: function(scope, elem, attrs, pipelineConfigurerCtrl) {
        scope.pipelineConfigurerCtrl = pipelineConfigurerCtrl;
      }
    };
  });
