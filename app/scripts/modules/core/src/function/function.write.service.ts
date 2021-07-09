import { Application } from '../application/application.model';
import { ITask } from '../domain';
import { IJob, TaskExecutor } from '../task/taskExecutor';

export interface IFunctionUpsertCommand extends IJob {
  functionName: string;
  cloudProvider: string;
  credentials: string;
  description?: string;
  region: string;
  operation: string;
}

export interface IFunctionDeleteCommand extends IJob {
  cloudProvider: string;
  functionName: string;
  credentials: string;
  region: string;
  vpcId?: string;
  operation?: string;
}

export class FunctionWriter {
  public static deleteFunction(command: IFunctionDeleteCommand, application: Application): PromiseLike<ITask> {
    command.type = 'lambdaFunction';
    command.operation = 'deleteLambdaFunction';
    return TaskExecutor.executeTask({
      job: [command],
      application,
      description: `Delete Function: ${command.functionName}`,
    });
  }

  public static upsertFunction(
    command: IFunctionUpsertCommand,
    application: Application,
    descriptor: string,
    params: any = {},
  ): PromiseLike<ITask> {
    Object.assign(command, params);
    return TaskExecutor.executeTask({
      job: [command],
      application,
      description: `${descriptor} Function: ${command.functionName || ''}`,
    });
  }
}
