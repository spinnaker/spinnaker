import { module } from 'angular';

import { SETTINGS } from 'core/config/settings';
import { IPipeline, IStage, IStageOrTriggerTypeConfig } from 'core/domain';
import { ServiceAccountService } from 'core/serviceAccount/serviceAccount.service';
import { PIPELINE_CONFIG_SERVICE } from '../services/pipelineConfig.service';

import {
  IStageOrTriggerValidator,
  IValidatorConfig,
  PIPELINE_CONFIG_VALIDATOR,
  PipelineConfigValidator
} from './pipelineConfig.validator';

export interface ITriggerWithServiceAccount extends IStage {
  runAsUser: string;
}

export interface IServiceAccountAccessValidationConfig extends IValidatorConfig {
  message: string;
}

export class ServiceAccountAccessValidator implements IStageOrTriggerValidator {

  constructor(private serviceAccountService: ServiceAccountService) { 'ngInject'; }

  public validate(_pipeline: IPipeline,
                  stage: ITriggerWithServiceAccount,
                  validator: IServiceAccountAccessValidationConfig,
                  _config: IStageOrTriggerTypeConfig): ng.IPromise<string> {

    if (SETTINGS.feature.fiatEnabled) {
      return this.serviceAccountService.getServiceAccounts()
        .then((serviceAccounts: string[]) => {
          if (stage.runAsUser && !serviceAccounts.includes(stage.runAsUser)) {
            return validator.message;
          } else {
            return null;
          }
        });
    } else {
      return null;
    }
  }
}

export const SERVICE_ACCOUNT_ACCESS_VALIDATOR = 'spinnaker.core.pipeline.validation.config.serviceAccountAccess';
module(SERVICE_ACCOUNT_ACCESS_VALIDATOR, [
  PIPELINE_CONFIG_SERVICE,
  PIPELINE_CONFIG_VALIDATOR,
]).service('serviceAccountAccessValidator', ServiceAccountAccessValidator)
  .run((pipelineConfigValidator: PipelineConfigValidator, serviceAccountAccessValidator: ServiceAccountAccessValidator) => {
    pipelineConfigValidator.registerValidator('serviceAccountAccess', serviceAccountAccessValidator);
  });
