import {IComponentOptions, module} from 'angular';

import './executionDetails.less';

export class ExecutionDetailsComponent implements IComponentOptions {
  public bindings: any = {
    execution: '<',
    application: '<',
    standalone: '<'
  };
  public controller = 'executionDetails as ctrl';
  public templateUrl: string = require('./executionDetails.html');

}

export const EXECUTION_DETAILS_COMPONENT = 'spinnaker.core.delivery.executionDetails.component';
module(EXECUTION_DETAILS_COMPONENT, [])
.component('executionDetails', new ExecutionDetailsComponent());
