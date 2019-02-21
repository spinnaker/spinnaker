import { IController, IComponentOptions, module } from 'angular';
import { set, has } from 'lodash';

interface ICapabilitiesSelectorField {
  label: string;
  buttonLabel: string;
  model: string;
}

class CapabilitiesSelector implements IController {
  public component: any;
  public fields: ICapabilitiesSelectorField[] = [
    {
      label: 'Add',
      buttonLabel: 'Add Linux Capability',
      model: 'add',
    },
    {
      label: 'Drop',
      buttonLabel: 'Drop Linux Capability',
      model: 'drop',
    },
  ];

  public add(fieldModel: string): void {
    const path = ['securityContext', 'capabilities', fieldModel];
    if (!has(this.component, path)) {
      set(this.component, path, []);
    }
    this.component.securityContext.capabilities[fieldModel].push('');
  }

  public remove(fieldModel: string, index: number): void {
    this.component.securityContext.capabilities[fieldModel].splice(index, 1);
  }
}

const capabilitiesSelectorComponent: IComponentOptions = {
  bindings: {
    component: '=',
  },
  templateUrl: require('./capabilitiesSelector.component.html'),
  controller: CapabilitiesSelector
};

export const KUBERNETES_CAPABILITIES_SELECTOR = 'spinnaker.kubernetes.securityContext.capabilitiesSelector.component';

module(KUBERNETES_CAPABILITIES_SELECTOR, []).component(
  'kubernetesCapabilitiesSelector',
  capabilitiesSelectorComponent,
);
