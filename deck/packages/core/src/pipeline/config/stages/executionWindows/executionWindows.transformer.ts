import type { ITransformer } from '../../PipelineRegistry';
import type { Application } from '../../../../application';
import { applySuspendedStatuses } from '../common';
import type { IExecution } from '../../../../domain';

export class ExecutionWindowsTransformer implements ITransformer {
  public transform(_application: Application, execution: IExecution): void {
    applySuspendedStatuses(execution, 'restrictExecutionDuringTimeWindow');
  }
}
