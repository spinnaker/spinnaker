import { IController, IComponentOptions, module } from 'angular';

import './securityContextSelector.component.less';
import { KUBERNETES_SE_LINUX_OPTIONS_SELECTOR } from './seLinuxOptionsSelector.component';
import { KUBERNETES_CAPABILITIES_SELECTOR } from './capabilitiesSelector.component';

interface ISecurityContextField {
  label: string;
  model: string;
  type: string;
  inputClasses?: string;
  columns?: number;
}

class SecurityContextSelector implements IController {
  public component: any;
  public fields: ISecurityContextField[] = [
    {
      label: 'Run As User',
      model: 'runAsUser',
      type: 'number',
      inputClasses: 'form-control input-sm',
      columns: 4,
    },
    {
      label: 'Run As Non-Root',
      model: 'runAsNonRoot',
      type: 'checkbox',
    },
    {
      label: 'Read Only Root Filesystem',
      model: 'readOnlyRootFilesystem',
      type: 'checkbox',
    },
    {
      label: 'Privileged',
      model: 'privileged',
      type: 'checkbox',
    },
  ];
}

const securityContextSelectorComponent: IComponentOptions = {
  bindings: {
    component: '=',
  },
  templateUrl: require('./securityContextSelector.component.html'),
  controller: SecurityContextSelector,
};

export const KUBERNETES_SECURITY_CONTEXT_SELECTOR = 'spinnaker.kubernetes.securityContextSelector.component';

module(KUBERNETES_SECURITY_CONTEXT_SELECTOR, [
  KUBERNETES_SE_LINUX_OPTIONS_SELECTOR,
  KUBERNETES_CAPABILITIES_SELECTOR,
]).component('kubernetesSecurityContextSelector', securityContextSelectorComponent);
