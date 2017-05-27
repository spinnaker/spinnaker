'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.serverGroup.details.scalingPolicy.upsert.actions.simplePolicy', [
  ])
  .component('awsSimplePolicyAction', {
    bindings: {
      command: '<',
      viewState: '=',
    },
    templateUrl: require('./simplePolicyAction.component.html'),
    controller: function () {
      this.operatorChanged = () => {
        this.adjustmentTypeOptions = this.viewState.operator === 'Set to' ?
          ['instances'] :
          ['instances', 'percent of group'];
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
    }
  });
