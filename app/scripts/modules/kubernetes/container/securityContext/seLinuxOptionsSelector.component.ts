import {module} from 'angular';

interface ISeLinuxField {
  label: string;
  model: string;
}

class SeLinuxOptions implements ng.IComponentController {
  component: any;
  fields: ISeLinuxField[] = [
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
  bindings: any = {
    component: '=',
  };
  templateUrl: string = require('./seLinuxOptionsSelector.component.html');
  controller: any = SeLinuxOptions;
}

export const KUBERNETES_SE_LINUX_OPTIONS_SELECTOR = 'spinnaker.kubernetes.securityContext.seLinuxOptionsSelector';

module(KUBERNETES_SE_LINUX_OPTIONS_SELECTOR, [])
  .component('kubernetesSeLinuxOptionsSelector', new SeLinuxOptionsComponent());
