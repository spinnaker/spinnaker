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
      // In Angular 1.7 Directive bindings were removed in the constructor, default values now must be instantiated within $onInit
      // See https://docs.angularjs.org/guide/migration#-compile- and https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$compile
      this.$onInit = () => {
        this.fieldColumns = this.fieldColumns || 8;
      };
    },
  };
});
