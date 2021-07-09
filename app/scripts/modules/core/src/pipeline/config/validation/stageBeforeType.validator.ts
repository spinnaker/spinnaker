import { IStageOrTriggerValidator, IValidatorConfig, PipelineConfigValidator } from './PipelineConfigValidator';
import { IPipeline, IStage, IStageOrTriggerTypeConfig } from '../../../domain';
import { PipelineConfigService } from '../services/PipelineConfigService';

export interface IStageBeforeTypeValidationConfig extends IValidatorConfig {
  stageTypes?: string[];
  stageType?: string;
  message: string;
}

export class StageBeforeTypeValidator implements IStageOrTriggerValidator {
  public validate(
    pipeline: IPipeline,
    stage: IStage,
    validator: IStageBeforeTypeValidationConfig,
    _config: IStageOrTriggerTypeConfig,
  ): string {
    if (pipeline.strategy === true && stage.type === 'deploy') {
      return null;
    }

    const stageTypes: string[] = validator.stageTypes || [validator.stageType];
    const stagesToTest = PipelineConfigService.getAllUpstreamDependencies(pipeline, stage);
    if (stagesToTest.every((test) => !stageTypes.includes(test.type))) {
      return validator.message;
    }
    return null;
  }
}

PipelineConfigValidator.registerValidator('stageBeforeType', new StageBeforeTypeValidator());
