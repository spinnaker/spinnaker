import { ITransformer } from '../../PipelineRegistry';
import { Application } from '../../../../application';
import { applySuspendedStatuses } from '../common';
import { IExecution } from '../../../../domain';

export class ExecutionWindowsTransformer implements ITransformer {
  public transform(_application: Application, execution: IExecution): void {
    applySuspendedStatuses(execution, 'restrictExecutionDuringTimeWindow');
  }
}
