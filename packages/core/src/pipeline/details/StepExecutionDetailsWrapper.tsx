import { Application } from '../../application';
import { IExecution, IExecutionStage } from '../../domain';

export interface IStepExecutionDetailsWrapperProps {
  application: Application;
  configSections: string[];
  execution: IExecution;
  stage: IExecutionStage;
  sourceUrl: string;
}
