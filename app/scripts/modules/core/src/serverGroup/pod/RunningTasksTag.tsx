import { Application } from 'core/application';
import { IExecution, ITask } from 'core/domain';

export interface IRunningTasksTagProps {
  application: Application;
  tasks: ITask[];
  executions: IExecution[];
}

export const runningTasksTagBindings: Record<keyof IRunningTasksTagProps, string> = {
  application: '=',
  tasks: '=',
  executions: '='
};
