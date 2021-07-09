import { IStageOrTriggerValidator, IValidatorConfig, PipelineConfigValidator } from './PipelineConfigValidator';
import { SETTINGS } from '../../../config/settings';
import { IPipeline, IStage, IStageOrTriggerTypeConfig } from '../../../domain';
import { ServiceAccountReader } from '../../../serviceAccount/ServiceAccountReader';

export interface ITriggerWithServiceAccount extends IStage {
  runAsUser: string;
}

export interface IServiceAccountAccessValidationConfig extends IValidatorConfig {
  message: string;
}

export class ServiceAccountAccessValidator implements IStageOrTriggerValidator {
  public validate(
    _pipeline: IPipeline,
    stage: ITriggerWithServiceAccount,
    validator: IServiceAccountAccessValidationConfig,
    _config: IStageOrTriggerTypeConfig,
  ): PromiseLike<string> {
    if (SETTINGS.feature.fiatEnabled) {
      return ServiceAccountReader.getServiceAccounts().then((serviceAccounts: string[]) => {
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

PipelineConfigValidator.registerValidator('serviceAccountAccess', new ServiceAccountAccessValidator());
