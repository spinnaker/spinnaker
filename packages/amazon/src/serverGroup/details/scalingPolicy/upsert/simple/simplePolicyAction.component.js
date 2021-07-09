'use strict';

import { module } from 'angular';

export const AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_SIMPLE_SIMPLEPOLICYACTION_COMPONENT =
  'spinnaker.amazon.serverGroup.details.scalingPolicy.upsert.actions.simplePolicy';
export const name = AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_SIMPLE_SIMPLEPOLICYACTION_COMPONENT; // for backwards compatibility
module(AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_UPSERT_SIMPLE_SIMPLEPOLICYACTION_COMPONENT, []).component(
  'awsSimplePolicyAction',
  {
    bindings: {
      command: '<',
      viewState: '=',
    },
    templateUrl: require('./simplePolicyAction.component.html'),
    controller: function () {
      this.operatorChanged = () => {
        this.adjustmentTypeOptions =
          this.viewState.operator === 'Set to' ? ['instances'] : ['instances', 'percent of group'];
      };

      this.availableActions = ['Add', 'Remove', 'Set to'];

      this.adjustmentTypeChanged = () => {
        if (this.viewState.adjustmentType === 'instances') {
          this.command.adjustmentType = this.viewState.operator === 'Set to' ? 'ExactCapacity' : 'ChangeInCapacity';
        } else {
          this.command.adjustmentType = 'PercentChangeInCapacity';
        }
      };

      this.$onInit = () => {
        this.operatorChanged();
        this.adjustmentTypeChanged();
      };
    },
  },
);
