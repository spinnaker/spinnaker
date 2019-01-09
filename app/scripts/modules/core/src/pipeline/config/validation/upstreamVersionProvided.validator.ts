import { IPromise } from 'angular';

import { Registry } from 'core/registry';
import { IPipeline, IStage, IStageOrTriggerTypeConfig } from 'core/domain';

import { IStageOrTriggerValidator, IValidatorConfig, PipelineConfigValidator } from './PipelineConfigValidator';
import {
  StageOrTriggerBeforeTypeValidator,
  IStageOrTriggerBeforeTypeValidationConfig,
} from './stageOrTriggerBeforeType.validator';
import { uniq, map } from 'lodash';

export interface IUpstreamVersionProvidedValidationConfig extends IValidatorConfig {
  getVersionProviders?: Function;
  checkParentTriggers?: boolean;
  getMessage: Function;
}

export class UpstreamVersionProvidedValidator implements IStageOrTriggerValidator {
  private defaultGetVersionProviders() {
    const versionProvidingStages = Registry.pipeline.getStageTypes().filter(x => x.providesVersionForBake);
    const versionProvidingTriggers = Registry.pipeline.getTriggerTypes().filter(x => x.providesVersionForBake);
    return versionProvidingStages.concat(versionProvidingTriggers);
  }

  public validate(
    pipeline: IPipeline,
    stage: IStage,
    validator: IUpstreamVersionProvidedValidationConfig,
    _config: IStageOrTriggerTypeConfig,
  ): IPromise<string> {
    const genericUpstreamValidator = new StageOrTriggerBeforeTypeValidator();
    const versionProviders = validator.getVersionProviders
      ? validator.getVersionProviders()
      : this.defaultGetVersionProviders();
    const stageTypes: string[] = uniq(map(versionProviders, 'key'));
    const labels = uniq(map(versionProviders, 'label'));
    const message = validator.getMessage(labels);
    const downstreamValidatorConfig: IStageOrTriggerBeforeTypeValidationConfig = {
      type: 'stageOrTriggerBeforeType',
      stageTypes,
      message,
    };
    return genericUpstreamValidator.validate(pipeline, stage, downstreamValidatorConfig, _config);
  }
}

PipelineConfigValidator.registerValidator('upstreamVersionProvided', new UpstreamVersionProvidedValidator());
