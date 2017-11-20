import { Application } from 'core/application';
import { IExecution, IExecutionStage, IExecutionStageSummary } from 'core/domain';

export interface IStageSummaryWrapperProps {
  application: Application;
  execution: IExecution;
  sourceUrl: string;
  stage: IExecutionStage;
  stageSummary: IExecutionStageSummary;
}
