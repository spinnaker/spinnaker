'use strict';

const angular = require('angular');

require('./stepPolicyAction.component.less');

module.exports = angular
  .module('spinnaker.aws.serverGroup.details.scalingPolicy.upsert.actions.stePolicy', [
  ])
  .component('awsStepPolicyAction', {
    bindings: {
      command: '<',
      viewState: '=',
      boundsChanged: '&',
    },
    templateUrl: require('./stepPolicyAction.component.html'),
    controller: function () {
      this.operatorChanged = () => {
        this.command.adjustmentType = this.viewState.operator === 'Set to' ? 'ExactCapacity' : 'ChangeInCapacity';
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

      this.addStep = () => {
        this.command.step.stepAdjustments.push({ scalingAdjustment: 1 });
      };

      this.removeStep = (index) => {
        this.command.step.stepAdjustments.splice(index, 1);
        this.boundsChanged();
      };

      this.$onInit = () => {
        this.operatorChanged();
      };
    }
  });
