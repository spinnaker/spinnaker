import { map, uniq } from 'lodash';

import { IValidatorConfig, PipelineConfigValidator } from './PipelineConfigValidator';
import { IPipeline, IStage, IStageOrTriggerTypeConfig } from '../../../domain';
import { Registry } from '../../../registry';
import {
  IStageOrTriggerBeforeTypeValidationConfig,
  StageOrTriggerBeforeTypeValidator,
} from './stageOrTriggerBeforeType.validator';

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
