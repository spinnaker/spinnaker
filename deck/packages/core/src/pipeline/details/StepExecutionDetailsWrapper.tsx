import type { Application } from '../../application';
import type { IExecution, IExecutionStage } from '../../domain';

export interface IStepExecutionDetailsWrapperProps {
  application: Application;
  configSections: string[];
  execution: IExecution;
  stage: IExecutionStage;
  sourceUrl: string;
}
