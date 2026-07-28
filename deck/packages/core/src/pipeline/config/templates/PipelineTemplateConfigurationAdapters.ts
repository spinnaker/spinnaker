import { dump, load } from 'js-yaml';
import { cloneDeep, has, omit } from 'lodash';

import type { IPipelineTemplate, IPipelineTemplateConfig, IVariableMetadata } from './PipelineTemplateReader';
import type { IPipeline, IPipelineTemplateConfigV2 } from '../../../domain';
import type { IVariable } from './inputs/variableInput.service';
import { PipelineTemplateV2Service } from './v2/pipelineTemplateV2.service';
import { VariableValidatorService } from './validators/variableValidator.service';

export interface ITemplateInheritance {
  inheritTemplateExpectedArtifacts: boolean;
  inheritTemplateNotifications: boolean;
  inheritTemplateParameters: boolean;
  inheritTemplateTriggers: boolean;
}

export interface IVariableMetadataGroup {
  name: string;
  variableMetadata: IVariableMetadata[];
}

export interface ITemplateConfigurationState {
  inheritance: ITemplateInheritance;
  isV2: boolean;
  pipelineName: string;
  source: string;
  variableMetadataGroups: IVariableMetadataGroup[];
  variables: IVariable[];
}

export function validateTemplateVariable(variable: IVariable, isV2: boolean): IVariable['errors'] {
  if (!isV2 || variable.type !== 'object') {
    return VariableValidatorService.validate(variable);
  }
  if (!variable.value) {
    return [{ message: 'Field is required.' }];
  }
  try {
    JSON.parse(variable.value);
    return [];
  } catch (_error) {
    return [{ message: 'Value must be valid JSON.' }];
  }
}

function groupVariableMetadata(variableMetadata: IVariableMetadata[]): IVariableMetadataGroup[] {
  return variableMetadata.reduce((groups, metadata) => {
    const groupName = metadata.group || 'Ungrouped';
    const existing = groups.find((group) => group.name === groupName);
    if (existing) {
      existing.variableMetadata.push(metadata);
    } else {
      groups.push({ name: groupName, variableMetadata: [metadata] });
    }
    return groups;
  }, [] as IVariableMetadataGroup[]);
}

function initialVariableValue(metadata: IVariableMetadata, values: Record<string, any>, isV2: boolean): any {
  const hasConfiguredValue = has(values, metadata.name);
  const value = hasConfiguredValue ? values[metadata.name] : metadata.defaultValue;
  if (metadata.type === 'list' && !hasConfiguredValue && !has(metadata, 'defaultValue')) {
    return [''];
  }
  if (metadata.type === 'object' && value !== undefined) {
    return isV2 ? JSON.stringify(value) : dump(value);
  }
  return cloneDeep(value);
}

export function initializeTemplateConfiguration(
  template: IPipelineTemplate,
  pipelineTemplateConfig: IPipelineTemplateConfig | IPipelineTemplateConfigV2,
): ITemplateConfigurationState {
  const isV2 = PipelineTemplateV2Service.isV2PipelineConfig(pipelineTemplateConfig);
  const v1Config = pipelineTemplateConfig as IPipelineTemplateConfig;
  const v2Config = pipelineTemplateConfig as IPipelineTemplateConfigV2;
  const metadata = template.variables || [];
  const values = isV2 ? v2Config.variables || {} : v1Config.config?.pipeline.variables || {};
  const variables = metadata.map((variableMetadata) => {
    const variable: IVariable = {
      name: variableMetadata.name,
      type: variableMetadata.type || 'string',
      value: initialVariableValue(variableMetadata, values, isV2),
      errors: [],
      hideErrors: true,
    };
    variable.errors = validateTemplateVariable(variable, isV2);
    return variable;
  });
  const v1Inherit = v1Config.config?.configuration?.inherit || ['expectedArtifacts', 'parameters', 'triggers'];
  const v2Exclude = v2Config.exclude || [];

  return {
    isV2,
    pipelineName: isV2 ? v2Config.name : v1Config.config?.pipeline.name,
    source: isV2 ? v2Config.template.reference : v1Config.config?.pipeline.template.source,
    variableMetadataGroups: groupVariableMetadata(metadata),
    variables,
    inheritance: {
      inheritTemplateExpectedArtifacts: isV2 || v1Inherit.includes('expectedArtifacts'),
      inheritTemplateNotifications: isV2 && !v2Exclude.includes('notifications'),
      inheritTemplateParameters: isV2 ? !v2Exclude.includes('parameters') : v1Inherit.includes('parameters'),
      inheritTemplateTriggers: isV2 ? !v2Exclude.includes('triggers') : v1Inherit.includes('triggers'),
    },
  };
}

function transformVariables(state: ITemplateConfigurationState): Record<string, any> {
  return state.variables.reduce((values, variable) => {
    let value = cloneDeep(variable.value);
    switch (variable.type) {
      case 'boolean':
        value = !!value;
        break;
      case 'object':
        value = state.isV2 ? JSON.parse(value) : load(value);
        break;
      case 'int':
        value = parseInt(value, 10);
        break;
      case 'float':
        value = parseFloat(value);
        break;
    }
    values[variable.name] = value;
    return values;
  }, {} as Record<string, any>);
}

export function buildTemplateConfig(
  applicationName: string,
  pipelineId: string,
  pipelineTemplateConfig: IPipelineTemplateConfig | IPipelineTemplateConfigV2,
  state: ITemplateConfigurationState,
): IPipelineTemplateConfig | IPipelineTemplateConfigV2 {
  const variables = transformVariables(state);
  const { inheritance } = state;

  if (!state.isV2) {
    const inherit = [
      ...(inheritance.inheritTemplateParameters ? ['parameters'] : []),
      ...(inheritance.inheritTemplateExpectedArtifacts ? ['expectedArtifacts'] : []),
      ...(inheritance.inheritTemplateTriggers ? ['triggers'] : []),
    ];
    return {
      ...omit(pipelineTemplateConfig, ['triggers', 'parameterConfig', 'parameters', 'expectedArtifacts']),
      type: 'templatedPipeline',
      name: state.pipelineName,
      application: applicationName,
      config: {
        schema: '1',
        pipeline: {
          name: state.pipelineName,
          application: applicationName,
          pipelineConfigId: pipelineId,
          template: { source: state.source },
          variables,
        },
        configuration: { inherit },
      },
    } as IPipelineTemplateConfig;
  }

  const exclude = [
    ...(inheritance.inheritTemplateParameters ? [] : ['parameters']),
    ...(inheritance.inheritTemplateNotifications ? [] : ['notifications']),
    ...(inheritance.inheritTemplateTriggers ? [] : ['triggers']),
  ];
  return {
    ...PipelineTemplateV2Service.filterInheritedConfig(cloneDeep(pipelineTemplateConfig)),
    type: 'templatedPipeline',
    name: state.pipelineName,
    application: applicationName,
    variables,
    exclude,
    ...PipelineTemplateV2Service.getPipelineTemplateConfigV2(state.source),
  } as IPipelineTemplateConfigV2;
}

export function mergeTemplatePlan(
  config: IPipelineTemplateConfig | IPipelineTemplateConfigV2,
  plan: IPipeline,
  state: ITemplateConfigurationState,
): IPipeline {
  const { inheritance } = state;
  return {
    ...config,
    ...(inheritance.inheritTemplateParameters && plan.parameterConfig ? { parameterConfig: plan.parameterConfig } : {}),
    ...(state.isV2 && inheritance.inheritTemplateNotifications && plan.notifications
      ? { notifications: plan.notifications }
      : {}),
    ...(!state.isV2 && inheritance.inheritTemplateExpectedArtifacts && plan.expectedArtifacts
      ? { expectedArtifacts: plan.expectedArtifacts }
      : {}),
    ...(state.isV2 && plan.expectedArtifacts ? { expectedArtifacts: plan.expectedArtifacts } : {}),
    ...(inheritance.inheritTemplateTriggers && plan.triggers ? { triggers: plan.triggers } : {}),
  } as IPipeline;
}
