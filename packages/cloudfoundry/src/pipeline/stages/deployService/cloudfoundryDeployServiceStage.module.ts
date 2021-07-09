import { safeLoad } from 'js-yaml';
import { get, upperFirst } from 'lodash';

import {
  ExecutionDetailsTasks,
  IPipeline,
  IStage,
  IStageOrTriggerValidator,
  ITrigger,
  IValidatorConfig,
  PipelineConfigValidator,
  Registry,
} from '@spinnaker/core';

import { CloudfoundryDeployServiceStageConfig } from './CloudfoundryDeployServiceStageConfig';
import { ICloudFoundryServiceManifestSource } from './ICloudFoundryServiceManifestSource';
import { CloudfoundryServiceExecutionDetails } from '../../../presentation';

interface IServiceFieldValidatorConfig extends IValidatorConfig {
  manifestSource: string;
}

const sourceType = (manifest: ICloudFoundryServiceManifestSource, userProvided: boolean) => {
  if (manifest && manifest.direct) {
    return userProvided ? 'userProvided' : 'direct';
  } else {
    return 'artifact';
  }
};

const sourceStruct = (manifest: ICloudFoundryServiceManifestSource) => {
  return manifest && manifest.direct ? 'direct' : 'artifact';
};

PipelineConfigValidator.registerValidator(
  'requiredDeployServiceField',
  new (class implements IStageOrTriggerValidator {
    public validate(
      _pipeline: IPipeline,
      stage: IStage | ITrigger,
      validationConfig: IServiceFieldValidatorConfig,
    ): string {
      const serviceInput: ICloudFoundryServiceManifestSource = get(stage, 'manifest');
      if (sourceType(serviceInput, get(stage, 'userProvided')) !== validationConfig.manifestSource) {
        return null;
      }
      const manifestSource: any = get(serviceInput, sourceStruct(serviceInput));
      const content: any = get(manifestSource, validationConfig.fieldName);
      const fieldLabel = validationConfig.fieldLabel || upperFirst(validationConfig.fieldName);
      return content ? null : `<strong>${fieldLabel}</strong> is a required field for the Deploy Service stage.`;
    }
  })(),
);

PipelineConfigValidator.registerValidator(
  'validDeployServiceParameterJsonOrYaml',
  new (class implements IStageOrTriggerValidator {
    private isJson(value: string): boolean {
      try {
        JSON.parse(value);
        return true;
      } catch (e) {
        return false;
      }
    }

    private isYaml(value: string): boolean {
      try {
        safeLoad(value);
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
      const manifestSource: any = get(manifest, sourceStruct(manifest));
      const fieldContent: any = get(manifestSource, validationConfig.fieldName);

      if (fieldContent) {
        if (!this.isJson(fieldContent) && !this.isYaml(fieldContent)) {
          const fieldLabel = upperFirst(validationConfig.fieldLabel || validationConfig.fieldName);
          return validationConfig.message || `<strong>${fieldLabel}</strong> should be a valid JSON or YAML string.`;
        }
      }
      return null;
    }
  })(),
);

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => [stage.context.credentials],
  cloudProvider: 'cloudfoundry',
  component: CloudfoundryDeployServiceStageConfig,
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  supportsCustomTimeout: true,
  description: 'Deploys services using Open Service Broker and deploys user-provided services',
  executionDetailsSections: [CloudfoundryServiceExecutionDetails, ExecutionDetailsTasks],
  key: 'deployService',
  label: 'Deploy Service',
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
      fieldName: 'artifactAccount',
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
      type: 'validDeployServiceParameterJsonOrYaml',
      manifestSource: 'direct',
      fieldName: 'parameters',
      preventSave: true,
    } as IServiceFieldValidatorConfig,
    {
      type: 'validDeployServiceParameterJsonOrYaml',
      manifestSource: 'userProvided',
      fieldName: 'credentials',
      preventSave: true,
    } as IServiceFieldValidatorConfig,
  ],
});
