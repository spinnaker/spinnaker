import { Application } from 'core/application/application.model';
import { IExecution } from 'core/domain';

export interface IExecutionDetailsProps {
  application: Application;
  execution: IExecution;
  standalone: boolean;
}
