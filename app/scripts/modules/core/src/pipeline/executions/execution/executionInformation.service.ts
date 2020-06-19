import { API } from 'core/api/ApiService';
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

    return API.one('pipelines', executionId)
      .get()
      .then((execution: IExecution) => {
        this.calledExecutions[executionId] = execution;
        // convert the IExecutionStage => IExecutionStageSummary
        ExecutionsTransformer.processStageSummaries(execution);

        return execution;
      });
  };

  public getPipelineConfig = async (application: string, pipelineConfigId: string): Promise<IPipeline> => {
    let pipelineConfig;
    if (this.calledPipelineConfigs.hasOwnProperty(application)) {
      pipelineConfig = this.calledPipelineConfigs[application].find(
        (config: IPipeline) => config.id === pipelineConfigId,
      );

      Promise.resolve(pipelineConfig);
    }

    return API.one('applications', application, 'pipelineConfigs')
      .get()
      .then((pipelineConfigs: IPipeline[]) => {
        // store for later
        this.calledPipelineConfigs[application] = pipelineConfigs;

        return this.calledPipelineConfigs[application].find((config: IPipeline) => config.id === pipelineConfigId);
      });
  };
}
