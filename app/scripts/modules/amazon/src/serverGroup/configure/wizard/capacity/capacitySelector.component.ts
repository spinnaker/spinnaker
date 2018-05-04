import { IComponentController, module } from 'angular';

export class AwsCapacitySelectorController implements IComponentController {
  public minMaxDesiredTemplate = require('./minMaxDesiredFields.template.html');
  public command: any;

  public preferSourceCapacityOptions = [
    { label: 'fail the stage', value: undefined },
    { label: 'use fallback values', value: true },
  ];

  public useSourceCapacityUpdated(): void {
    if (!this.command.useSourceCapacity) {
      delete this.command.preferSourceCapacity;
    }
  }

  public setSimpleCapacity(simpleCapacity: boolean) {
    this.command.viewState.useSimpleCapacity = simpleCapacity;
    this.command.useSourceCapacity = false;
    this.setMinMax(this.command.capacity.desired);
  }

  public setMinMax(newVal: number) {
    if (this.command.viewState.useSimpleCapacity) {
      this.command.capacity.min = newVal;
      this.command.capacity.max = newVal;
      this.command.useSourceCapacity = false;
    }
  }
}

export const CAPACITY_SELECTOR = 'spinnaker.amazon.serverGroup.configure.wizard.capacity.selector';
module(CAPACITY_SELECTOR, []).component('awsServerGroupCapacitySelector', {
  bindings: {
    command: '=',
  },
  templateUrl: require('./capacitySelector.component.html'),
  controller: AwsCapacitySelectorController,
});
