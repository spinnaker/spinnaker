import { IDeferred } from 'angular';
import { $q } from 'ngimport';

import { IStageOrTriggerValidator, IValidatorConfig, PipelineConfigValidator } from './PipelineConfigValidator';
import { IPipeline, IPipelineTrigger, IStage, IStageOrTriggerTypeConfig, ITrigger } from '../../../domain';
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
    return $q.all(parentTriggersToCheck).then(() => {
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

  private addExternalTriggers(
    trigger: IPipelineTrigger,
    stagesToTest: Array<IStage | ITrigger>,
    deferred: IDeferred<any>,
  ): void {
    PipelineConfigService.getPipelinesForApplication(trigger.application).then((pipelines) => {
      this.pipelineCache.set(trigger.application, pipelines);
      this.addTriggers(pipelines, trigger.pipeline, stagesToTest);
      deferred.resolve();
    });
  }

  private addPipelineTriggers(pipeline: IPipeline, stagesToTest: Array<IStage | ITrigger>) {
    const pipelineTriggers: IPipelineTrigger[] = pipeline.triggers.filter(
      (t) => t.type === 'pipeline',
    ) as IPipelineTrigger[];
    const parentTriggersToCheck: Array<PromiseLike<any>> = [];
    pipelineTriggers.forEach((trigger) => {
      const deferred: IDeferred<any> = $q.defer();
      if (this.pipelineCache.has(trigger.application)) {
        this.addTriggers(this.pipelineCache.get(trigger.application), trigger.pipeline, stagesToTest);
      } else {
        this.addExternalTriggers(trigger, stagesToTest, deferred);
        parentTriggersToCheck.push(deferred.promise);
      }
    });
    return parentTriggersToCheck;
  }
}

PipelineConfigValidator.registerValidator('stageOrTriggerBeforeType', new StageOrTriggerBeforeTypeValidator());
