import { get, upperFirst } from 'lodash';

import { IPipeline, IStage, IStageOrTriggerValidator, ITrigger, PipelineConfigValidator } from '@spinnaker/core';
import { IManifestFieldValidatorConfig } from 'cloudfoundry/pipeline/config/validation/ManifestConfigValidator';

export class ServiceParameterJsonFieldValidator implements IStageOrTriggerValidator {
  private static validationMessage(validationConfig: IManifestFieldValidatorConfig): string {
    const fieldLabel: string = ServiceParameterJsonFieldValidator.printableFieldLabel(validationConfig);
    return validationConfig.message || `<strong>${fieldLabel}</strong> should be a valid JSON string.`;
  }

  private static printableFieldLabel(config: IManifestFieldValidatorConfig): string {
    const fieldLabel: string = config.fieldLabel || config.fieldName;
    return upperFirst(fieldLabel);
  }

  private static fieldIsValid(stage: IStage | ITrigger, config: IManifestFieldValidatorConfig): boolean {
    const manifest: any = get(stage, 'manifest');
    const content: any = get(manifest, config.fieldName);

    if (!content) {
      return true;
    }

    try {
      JSON.parse(content);
      return true;
    } catch (e) {
      return false;
    }
  }

  public validate(
    _pipeline: IPipeline,
    stage: IStage | ITrigger,
    validationConfig: IManifestFieldValidatorConfig,
  ): string {
    const manifest: any = get(stage, 'manifest');

    if (manifest.type !== validationConfig.manifestType) {
      return null;
    }

    if (!ServiceParameterJsonFieldValidator.fieldIsValid(stage, validationConfig)) {
      return ServiceParameterJsonFieldValidator.validationMessage(validationConfig);
    }
    return null;
  }
}

PipelineConfigValidator.registerValidator('validServiceParameterJson', new ServiceParameterJsonFieldValidator());
