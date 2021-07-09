'use strict';

import { module } from 'angular';

import { HELP_FIELD_COMPONENT } from '../../../../../help/helpField.component';

import './stageConfigField.directive.less';

export const CORE_PIPELINE_CONFIG_STAGES_COMMON_STAGECONFIGFIELD_STAGECONFIGFIELD_DIRECTIVE =
  'spinnaker.core.pipeline.config.stages.common.stageField.directive';
export const name = CORE_PIPELINE_CONFIG_STAGES_COMMON_STAGECONFIGFIELD_STAGECONFIGFIELD_DIRECTIVE; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_STAGES_COMMON_STAGECONFIGFIELD_STAGECONFIGFIELD_DIRECTIVE, [
  HELP_FIELD_COMPONENT,
]).directive('stageConfigField', function () {
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
    controller: function () {
      this.fieldColumns = this.fieldColumns || 8;
    },
  };
});
