import * as React from 'react';
import { angular2react } from 'angular2react';

import { ExecutionStatusComponent } from './executionStatus.component';
import {IExecution} from 'core/domain/IExecution';
import { ReactInjector } from 'core/react.module';

interface IExecutionStatusProps {
  execution: IExecution;
  toggleDetails: (stageIndex?: number) => void;
  showingDetails: boolean;
  standalone: boolean;
}

export let ExecutionStatus: React.ComponentClass<IExecutionStatusProps> = undefined;
ReactInjector.give(($injector: any) => ExecutionStatus = angular2react('executionStatus', new ExecutionStatusComponent(), $injector) as any);
