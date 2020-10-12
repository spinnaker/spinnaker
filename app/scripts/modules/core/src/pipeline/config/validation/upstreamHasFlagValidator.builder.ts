import { Registry } from 'core/registry';
import { IPipeline, IStage, IStageOrTriggerTypeConfig } from 'core/domain';

import { IValidatorConfig, PipelineConfigValidator } from './PipelineConfigValidator';
import {
  StageOrTriggerBeforeTypeValidator,
  IStageOrTriggerBeforeTypeValidationConfig,
} from './stageOrTriggerBeforeType.validator';
import { uniq, map } from 'lodash';

export interface IUpstreamFlagProvidedValidationConfig extends IValidatorConfig {
  getProviders?: Function;
  checkParentTriggers?: boolean;
  getMessage: Function;
}

export const buildUpstreamHasFlagValidator = (flag: keyof IStageOrTriggerTypeConfig, name: string) => {
  PipelineConfigValidator.registerValidator(name, {
    validate: (
      pipeline: IPipeline,
      stage: IStage,
      validator: IUpstreamFlagProvidedValidationConfig,
      _config: IStageOrTriggerTypeConfig,
    ) => {
      const providingStages = Registry.pipeline.getStageTypes().filter((x) => x[flag]);
      const providingTriggers = Registry.pipeline.getTriggerTypes().filter((x) => x[flag]);
      const defaultProviders = providingStages.concat(providingTriggers);

      const genericUpstreamValidator = new StageOrTriggerBeforeTypeValidator();
      const repositoryProviders = validator.getProviders ? validator.getProviders() : defaultProviders;
      const stageTypes: string[] = uniq(map(repositoryProviders, 'key'));
      const labels = uniq(map(repositoryProviders, 'label'));
      const message = validator.getMessage(labels);
      const downstreamValidatorConfig: IStageOrTriggerBeforeTypeValidationConfig = {
        type: 'stageOrTriggerBeforeType',
        checkParentTriggers: validator.checkParentTriggers,
        stageTypes,
        message,
      };
      return genericUpstreamValidator.validate(pipeline, stage, downstreamValidatorConfig, _config);
    },
  });
};
