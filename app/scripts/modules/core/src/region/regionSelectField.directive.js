import { module } from 'angular';
import { react2angular } from 'react2angular';

import { RegionSelectField } from './RegionSelectField';
import { withErrorBoundary } from '../presentation/SpinErrorBoundary';

('use strict');

export const CORE_REGION_REGIONSELECTFIELD_DIRECTIVE = 'spinnaker.core.region.regionSelectField.directive';
export const name = CORE_REGION_REGIONSELECTFIELD_DIRECTIVE; // for backwards compatibility
module(CORE_REGION_REGIONSELECTFIELD_DIRECTIVE, [])
  .component(
    'regionSelectFieldWrapper',
    react2angular(withErrorBoundary(RegionSelectField, 'regionSelectFieldWrapper'), [
      'regions',
      'component',
      'field',
      'account',
      'provider',
      'onChange',
      'labelColumns',
      'fieldColumns',
      'readOnly',
    ]),
  )
  .component('regionSelectField', {
    controllerAs: 'vm',
    template: `
      <region-select-field-wrapper regions="vm.regions"
                                   component="vm.component"
                                   field="vm.field"
                                   account="vm.account"
                                   provider="vm.provider"
                                   on-change="vm.propagate"
                                   label-columns="vm.labelColumns"
                                   field-columns="vm.fieldColumns"
                                   read-only="vm.readOnly"></react-region-select-field>
    `,
    bindings: {
      regions: '=',
      component: '=',
      field: '@',
      account: '=',
      provider: '=',
      onChange: '&',
      labelColumns: '<',
      fieldColumns: '<',
      readOnly: '=',
    },
    controller: function () {
      const vm = this;
      vm.propagate = function (data) {
        vm.component[vm.field] = data;
        vm.onChange();
      };
    },
  });
