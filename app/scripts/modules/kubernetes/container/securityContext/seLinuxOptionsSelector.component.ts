import {module} from 'angular';

interface ISeLinuxField {
  label: string;
  model: string;
}

class SeLinuxOptions implements ng.IComponentController {
  public component: any;
  public fields: ISeLinuxField[] = [
    {
      label: 'User',
      model: 'user',
    },
    {
      label: 'Role',
      model: 'role',
    },
    {
      label: 'Type',
      model: 'type',
    },
    {
      label: 'Level',
      model: 'level',
    }
  ];
}

class SeLinuxOptionsComponent implements ng.IComponentOptions {
  public bindings: any = {
    component: '=',
  };
  public templateUrl: string = require('./seLinuxOptionsSelector.component.html');
  public controller: any = SeLinuxOptions;
}

export const KUBERNETES_SE_LINUX_OPTIONS_SELECTOR = 'spinnaker.kubernetes.securityContext.seLinuxOptionsSelector';

module(KUBERNETES_SE_LINUX_OPTIONS_SELECTOR, [])
  .component('kubernetesSeLinuxOptionsSelector', new SeLinuxOptionsComponent());
