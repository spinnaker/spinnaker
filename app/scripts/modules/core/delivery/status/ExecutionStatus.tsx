import * as React from 'react';
import { angular2react } from 'angular2react';

import { ExecutionStatusComponent } from './executionStatus.component';
import {IExecution} from 'core/domain/IExecution';

interface IExecutionStatusProps {
  execution: IExecution;
  toggleDetails: (node: {executionId: string, index: number}) => void;
  showingDetails: boolean;
  standalone: boolean;
}

export let ExecutionStatus: React.ComponentClass<IExecutionStatusProps> = undefined;
export const ExecutionStatusInject = ($injector: any) => {
  ExecutionStatus = angular2react<IExecutionStatusProps>('executionStatus', new ExecutionStatusComponent(), $injector);
};
