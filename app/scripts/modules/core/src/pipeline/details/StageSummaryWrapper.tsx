import { Application } from '../../application';
import { IExecution, IExecutionStage, IExecutionStageSummary } from '../../domain';

export interface IStageSummaryWrapperProps {
  application: Application;
  execution: IExecution;
  sourceUrl: string;
  stage: IExecutionStage;
  stageSummary: IExecutionStageSummary;
}
