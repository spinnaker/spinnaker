'use strict';

const angular = require('angular');

import { HELP_FIELD_COMPONENT } from 'core/help/helpField.component';

import './stageConfigField.directive.less';

module.exports = angular
  .module('spinnaker.core.pipeline.config.stages.core.stageField.directive', [HELP_FIELD_COMPONENT])
  .directive('stageConfigField', function() {
    return {
      restrict: 'E',
      transclude: true,
      scope: {},
      controllerAs: 'vm',
      templateUrl: require('./stageConfigField.directive.html'),
      bindToController: {
        label: '@',
        helpKey: '@',
        helpKeyExpand: '@',
        fieldColumns: '@',
      },
      controller: function() {
        this.fieldColumns = this.fieldColumns || 8;
      },
    };
  });
