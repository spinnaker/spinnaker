import { IComponentOptions, module } from 'angular';

import './executions.less';

const executionsComponent: IComponentOptions = {
  bindings: {
    application: '<',
  },
  templateUrl: require('./executions.html'),
  controller: 'ExecutionsCtrl',
};

export const EXECUTIONS_COMPONENT = 'spinnaker.core.executions.component';
module(EXECUTIONS_COMPONENT, [
  require('./executions.controller'),
]).component('executions', executionsComponent);
