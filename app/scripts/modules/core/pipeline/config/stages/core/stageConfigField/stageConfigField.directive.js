'use strict';

const angular = require('angular');

require('./stageConfigField.directive.less');

module.exports = angular
  .module('spinnaker.core.pipeline.config.stages.core.stageField.directive', [
    require('../../../../../help/helpField.directive.js'),
  ])
  .directive('stageConfigField', function () {
    return {
      restrict: 'E',
      transclude: true,
      scope: {},
      controllerAs: 'vm',
      templateUrl: require('./stageConfigField.directive.html'),
      bindToController: {
        label: '@',
        helpKey: '@',
        fieldColumns: '@',
      },
      controller: function() {
        this.fieldColumns = this.fieldColumns || 6;
      }
    };
  });
