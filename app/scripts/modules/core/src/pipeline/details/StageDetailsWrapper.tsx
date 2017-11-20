import { Application } from 'core/application';
import { IExecution, IExecutionStage } from 'core/domain';

export interface IStageDetailsWrapperProps {
  application: Application;
  configSections: string[];
  execution: IExecution;
  stage: IExecutionStage;
  sourceUrl: string;
}
