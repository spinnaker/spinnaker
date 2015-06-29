'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines')
  .directive('pipelineConfigView', function() {
    return {
      restrict: 'E',
      require: '^pipelineConfigurer',
      scope: {
        pipeline: '=',
        application: '=',
        viewState: '='
      },
      templateUrl: require('./pipelineConfigView.html'),
      link: function(scope, elem, attrs, pipelineConfigurerCtrl) {
        scope.pipelineConfigurerCtrl = pipelineConfigurerCtrl;
      }
    };
  });
