import { ITransformer } from '../../PipelineRegistry';
import { Application } from 'core/application';
import { IExecution } from 'core/domain';

import { applySuspendedStatuses } from '../common';

export class ExecutionWindowsTransformer implements ITransformer {
  public transform(_application: Application, execution: IExecution): void {
    applySuspendedStatuses(execution, 'restrictExecutionDuringTimeWindow');
  }
}
