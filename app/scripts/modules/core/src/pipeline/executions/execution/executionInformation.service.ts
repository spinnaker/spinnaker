import { REST } from 'core/api/ApiService';
import { ExecutionsTransformer } from '../../service/ExecutionsTransformer';
import { IExecution, IPipeline } from 'core/domain';

export class ExecutionInformationService {
  private static instance: ExecutionInformationService;
  private calledExecutions: { [key: string]: IExecution };
  private calledPipelineConfigs: { [key: string]: IPipeline[] };

  constructor() {
    if (!ExecutionInformationService.instance) {
      this.calledExecutions = {};
      this.calledPipelineConfigs = {};

      ExecutionInformationService.instance = this;
    }

    return ExecutionInformationService.instance;
  }

  public getExecution = async (executionId: string): Promise<IExecution> => {
    if (this.calledExecutions.hasOwnProperty(executionId)) {
      return this.calledExecutions[executionId];
    }

    return REST()
      .path('pipelines', executionId)
      .get()
      .then((execution: IExecution) => {
        this.calledExecutions[executionId] = execution;
        // convert the IExecutionStage => IExecutionStageSummary
        ExecutionsTransformer.processStageSummaries(execution);

        return execution;
      });
  };

  // Returns an array of parent executions starting with the one passed in.
  public getAllParentExecutions = (execution: IExecution): IExecution[] => {
    const executions: any[] = [];

    executions.push(execution);

    if (execution.trigger.parentExecution) {
      executions.push(...this.getAllParentExecutions(execution.trigger.parentExecution));
    }

    return executions;
  };

  public getPipelineConfig = async (application: string, pipelineConfigId: string): Promise<IPipeline> => {
    let pipelineConfig;
    if (this.calledPipelineConfigs.hasOwnProperty(application)) {
      pipelineConfig = this.calledPipelineConfigs[application].find(
        (config: IPipeline) => config.id === pipelineConfigId,
      );

      Promise.resolve(pipelineConfig);
    }

    return REST()
      .path('applications', application, 'pipelineConfigs')
      .get()
      .then((pipelineConfigs: IPipeline[]) => {
        // store for later
        this.calledPipelineConfigs[application] = pipelineConfigs;

        return this.calledPipelineConfigs[application].find((config: IPipeline) => config.id === pipelineConfigId);
      });
  };
}
