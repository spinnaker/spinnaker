import * as React from 'react';
import { angular2react } from 'angular2react';

import { Application } from 'core/application/application.model';
import { ExecutionDetailsComponent } from './executionDetails.component';
import { IExecution } from 'core/domain/IExecution';

interface IExecutionDetailsProps {
  application: Application;
  execution: IExecution;
  standalone: boolean;
}

export let ExecutionDetails: React.ComponentClass<IExecutionDetailsProps> = undefined;
export const ExecutionDetailsInject = ($injector: any) => {
  ExecutionDetails = angular2react<IExecutionDetailsProps>('executionDetails', new ExecutionDetailsComponent(), $injector);
};
