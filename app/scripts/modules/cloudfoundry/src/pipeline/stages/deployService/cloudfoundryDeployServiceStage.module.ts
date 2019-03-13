import { get, upperFirst } from 'lodash';

import {
  ExecutionDetailsTasks,
  PipelineConfigValidator,
  Registry,
  IPipeline,
  IStage,
  IStageOrTriggerValidator,
  ITrigger,
  IValidatorConfig,
} from '@spinnaker/core';

import { CloudfoundryServiceExecutionDetails } from 'cloudfoundry/presentation';
import { CloudfoundryDeployServiceStageConfig } from './CloudfoundryDeployServiceStageConfig';
import { ICloudFoundryServiceManifestSource } from './ICloudFoundryServiceManifestSource';

interface IServiceFieldValidatorConfig extends IValidatorConfig {
  manifestSource: string;
}

const sourceType = (manifest: ICloudFoundryServiceManifestSource, userProvided: boolean) => {
  if (manifest.direct) {
    return userProvided ? 'userProvided' : 'direct';
  } else {
    return 'artifact';
  }
};

PipelineConfigValidator.registerValidator(
  'requiredDeployServiceField',
  new class implements IStageOrTriggerValidator {
    public validate(
      _pipeline: IPipeline,
      stage: IStage | ITrigger,
      validationConfig: IServiceFieldValidatorConfig,
    ): string {
      const serviceInput: ICloudFoundryServiceManifestSource = get(stage, 'manifest');
      if (sourceType(serviceInput, get(stage, 'userProvided')) !== validationConfig.manifestSource) {
        return null;
      }
      const manifestSource: any = get(serviceInput, validationConfig.manifestSource);
      const content: any = get(manifestSource, validationConfig.fieldName);
      const fieldLabel = validationConfig.fieldLabel || upperFirst(validationConfig.fieldName);
      return content ? null : `<strong>${fieldLabel}</strong> is a required field for the Deploy Service stage.`;
    }
  }(),
);

PipelineConfigValidator.registerValidator(
  'validDeployServiceParameterJson',
  new class implements IStageOrTriggerValidator {
    private validationMessage(validationConfig: IServiceFieldValidatorConfig): string {
      return (
        validationConfig.message ||
        `<strong>${this.printableFieldLabel(validationConfig)}</strong> should be a valid JSON string.`
      );
    }

    private printableFieldLabel(config: IServiceFieldValidatorConfig): string {
      return upperFirst(config.fieldLabel || config.fieldName);
    }

    private fieldIsValid(stage: IStage | ITrigger, config: IServiceFieldValidatorConfig): boolean {
      const serviceInput = get(stage, 'manifest');
      const content: any = get(serviceInput, config.fieldName);

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
      validationConfig: IServiceFieldValidatorConfig,
    ): string {
      const manifest: any = get(stage, 'manifest');
      if (sourceType(manifest, get(stage, 'userProvided')) !== validationConfig.manifestSource) {
        return null;
      }

      if (!this.fieldIsValid(stage, validationConfig)) {
        return this.validationMessage(validationConfig);
      }
      return null;
    }
  }(),
);

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => stage.context.credentials,
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  label: 'Deploy Service',
  description: 'Deploys services using Open Service Broker and deploys user-provided services',
  key: 'deployService',
  cloudProvider: 'cloudfoundry',
  component: CloudfoundryDeployServiceStageConfig,
  executionDetailsSections: [CloudfoundryServiceExecutionDetails, ExecutionDetailsTasks],
  defaultTimeoutMs: 30 * 60 * 1000,
  validators: [
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    { type: 'requiredField', fieldName: 'region' },
    {
      type: 'requiredDeployServiceField',
      manifestSource: 'direct',
      fieldName: 'serviceInstanceName',
      preventSave: true,
    } as IServiceFieldValidatorConfig,
    {
      type: 'requiredDeployServiceField',
      manifestSource: 'direct',
      fieldName: 'service',
      preventSave: true,
    } as IServiceFieldValidatorConfig,
    {
      type: 'requiredDeployServiceField',
      manifestSource: 'direct',
      fieldName: 'servicePlan',
      preventSave: true,
    } as IServiceFieldValidatorConfig,
    {
      type: 'requiredDeployServiceField',
      manifestSource: 'artifact',
      fieldName: 'account',
      preventSave: true,
    } as IServiceFieldValidatorConfig,
    {
      type: 'requiredDeployServiceField',
      manifestSource: 'artifact',
      fieldName: 'reference',
      preventSave: true,
    } as IServiceFieldValidatorConfig,
    {
      type: 'requiredDeployServiceField',
      manifestSource: 'userProvided',
      fieldName: 'serviceInstanceName',
      preventSave: true,
    } as IServiceFieldValidatorConfig,
    {
      type: 'validDeployServiceParameterJson',
      manifestSource: 'direct',
      fieldName: 'parameters',
      preventSave: true,
    } as IServiceFieldValidatorConfig,
    {
      type: 'validDeployServiceParameterJson',
      manifestSource: 'userProvided',
      fieldName: 'credentials',
      preventSave: true,
    } as IServiceFieldValidatorConfig,
  ],
});
