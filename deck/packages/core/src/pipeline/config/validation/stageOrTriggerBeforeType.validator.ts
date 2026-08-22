import type { IStageOrTriggerValidator, IValidatorConfig } from './PipelineConfigValidator';
import { PipelineConfigValidator } from './PipelineConfigValidator';
import type { IPipeline, IPipelineTrigger, IStage, IStageOrTriggerTypeConfig, ITrigger } from '../../../domain';
import { PipelineConfigService } from '../services/PipelineConfigService';

export interface IStageOrTriggerBeforeTypeValidationConfig extends IValidatorConfig {
  getStageTypes?: Function;
  stageTypes?: string[];
  stageType?: string;
  checkParentTriggers?: boolean;
  getMessage?: Function;
  message?: string;
}

export class StageOrTriggerBeforeTypeValidator implements IStageOrTriggerValidator {
  // Stores application pipeline configs so we don't needlessly fetch them every time we validate the pipeline
  private pipelineCache: Map<string, IPipeline[]> = new Map();

  public validate(
    pipeline: IPipeline,
    stage: IStage,
    validator: IStageOrTriggerBeforeTypeValidationConfig,
    _config: IStageOrTriggerTypeConfig,
  ): PromiseLike<string> {
    const stageTypes = validator.getStageTypes
      ? validator.getStageTypes()
      : validator.stageTypes || [validator.stageType];
    const stagesToTest: Array<IStage | ITrigger> = PipelineConfigService.getAllUpstreamDependencies(pipeline, stage);
    stagesToTest.push(...pipeline.triggers);

    const parentTriggersToCheck = validator.checkParentTriggers ? this.addPipelineTriggers(pipeline, stagesToTest) : [];
    return Promise.all(parentTriggersToCheck).then(() => {
      if (stagesToTest.every((test) => !stageTypes.includes(test.type))) {
        return validator.getMessage ? validator.getMessage() : validator.message;
      }
      return null;
    });
  }

  private addTriggers(pipelines: IPipeline[], pipelineIdToFind: string, stagesToTest: Array<IStage | ITrigger>): void {
    const match = pipelines.find((p) => p.id === pipelineIdToFind);
    if (match) {
      stagesToTest.push(...match.triggers);
    }
  }

  private addExternalTriggers(trigger: IPipelineTrigger, stagesToTest: Array<IStage | ITrigger>): Promise<void> {
    return Promise.resolve()
      .then(() => PipelineConfigService.getPipelinesForApplication(trigger.application))
      .then((pipelines) => {
        this.pipelineCache.set(trigger.application, pipelines);
        this.addTriggers(pipelines, trigger.pipeline, stagesToTest);
      });
  }

  private addPipelineTriggers(pipeline: IPipeline, stagesToTest: Array<IStage | ITrigger>) {
    const pipelineTriggers: IPipelineTrigger[] = pipeline.triggers.filter(
      (t) => t.type === 'pipeline',
    ) as IPipelineTrigger[];
    const parentTriggersToCheck: Array<PromiseLike<void>> = [];
    pipelineTriggers.forEach((trigger) => {
      if (this.pipelineCache.has(trigger.application)) {
        this.addTriggers(this.pipelineCache.get(trigger.application), trigger.pipeline, stagesToTest);
      } else {
        parentTriggersToCheck.push(this.addExternalTriggers(trigger, stagesToTest));
      }
    });
    return parentTriggersToCheck;
  }
}

PipelineConfigValidator.registerValidator('stageOrTriggerBeforeType', new StageOrTriggerBeforeTypeValidator());
