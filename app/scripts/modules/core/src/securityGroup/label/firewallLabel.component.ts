import { IComponentController, module } from 'angular';
import { FirewallLabels } from './FirewallLabels';

class LabelCtrl implements IComponentController {
  public value: string;
  private label: string;

  public $onInit() {
    this.value = FirewallLabels.get(this.label);
  }
}

export const FIREWALL_LABEL_COMPONENT = 'spinnaker.core.firewall.label.component';
module(FIREWALL_LABEL_COMPONENT, []).component('firewallLabel', {
  bindings: {
    label: '@',
  },
  controller: LabelCtrl,
  template: `<span>{{$ctrl.value}}</span>`,
});
