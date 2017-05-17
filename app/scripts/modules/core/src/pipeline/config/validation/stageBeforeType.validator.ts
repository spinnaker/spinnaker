import { module } from 'angular';

import { IPipeline, IStage, IStageOrTriggerTypeConfig } from 'core/domain';

import { PIPELINE_CONFIG_SERVICE, PipelineConfigService } from '../services/pipelineConfig.service';
import {
  IStageOrTriggerValidator,
  IValidatorConfig,
  PIPELINE_CONFIG_VALIDATOR,
  PipelineConfigValidator
} from './pipelineConfig.validator';

export interface IStageBeforeTypeValidationConfig extends IValidatorConfig {
  stageTypes?: string[];
  stageType?: string;
  message: string;
}

export class StageBeforeTypeValidator implements IStageOrTriggerValidator {

  constructor(private pipelineConfigService: PipelineConfigService) { 'ngInject'; }

  public validate(pipeline: IPipeline,
                  stage: IStage,
                  validator: IStageBeforeTypeValidationConfig,
                  _config: IStageOrTriggerTypeConfig): string {

    if (pipeline.strategy === true && stage.type === 'deploy') {
      return null;
    }

    const stageTypes: string[] = validator.stageTypes || [validator.stageType];
    const stagesToTest = this.pipelineConfigService.getAllUpstreamDependencies(pipeline, stage);
    if (stagesToTest.every((test) => !stageTypes.includes(test.type))) {
      return validator.message;
    }
    return null;
  }
}

export const STAGE_BEFORE_TYPE_VALIDATOR = 'spinnaker.core.pipeline.validation.config.stageBeforeType';
module(STAGE_BEFORE_TYPE_VALIDATOR, [
  PIPELINE_CONFIG_SERVICE,
  PIPELINE_CONFIG_VALIDATOR,
])
  .service('stageBeforeTypeValidator', StageBeforeTypeValidator)
  .run((pipelineConfigValidator: PipelineConfigValidator, stageBeforeTypeValidator: StageBeforeTypeValidator) => {
    pipelineConfigValidator.registerValidator('stageBeforeType', stageBeforeTypeValidator);
});
