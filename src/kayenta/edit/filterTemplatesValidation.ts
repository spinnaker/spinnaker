import { get, isEmpty } from 'lodash';

import { ICanaryMetricConfig } from '../domain';
import { IEditingTemplateState } from '../reducers/editingTemplate';

export interface ICanaryFilterTemplateValidationMessage {
  message: string;
}

type ICanaryFilterTemplateValidationErrorKeys = 'templateName' | 'templateValue';

type ICanaryFilterTemplateValidationWarningKeys = 'template';

export interface ICanaryFilterTemplateValidationMessages {
  errors: { [K in ICanaryFilterTemplateValidationErrorKeys]?: ICanaryFilterTemplateValidationMessage };
  warnings: { [K in ICanaryFilterTemplateValidationWarningKeys]?: ICanaryFilterTemplateValidationMessage };
}

export interface ITemplateValidationInput {
  editingTemplate: IEditingTemplateState;
  configTemplates: { [name: string]: string };
  metricList: ICanaryMetricConfig[];
  editingMetric: ICanaryMetricConfig;
}

export function validateTemplateName(
  validation: ICanaryFilterTemplateValidationMessages,
  validationInput: ITemplateValidationInput,
): ICanaryFilterTemplateValidationMessages {
  const { editingTemplate, configTemplates } = validationInput;

  if (editingTemplate.editedName === '') {
    validation.errors.templateName = {
      message: 'Name is required',
    };
    return validation;
  }

  const isNameEdited = editingTemplate && editingTemplate.editedName !== editingTemplate.name;
  const savedTemplateNames = Object.keys(configTemplates || {});
  const isNameDuplicate = isNameEdited && savedTemplateNames.some(name => editingTemplate.editedName === name);
  if (isNameDuplicate) {
    validation.errors.templateName = {
      message: `Filter template named '${editingTemplate.editedName}' already exists`,
    };
  }

  return validation;
}

export function validateTemplateValue(
  validation: ICanaryFilterTemplateValidationMessages,
  validationInput: ITemplateValidationInput,
): ICanaryFilterTemplateValidationMessages {
  const { editingTemplate } = validationInput;

  if (editingTemplate.editedValue === '') {
    validation.errors.templateValue = {
      message: 'Value is required',
    };
  }

  return validation;
}

export function validateTemplateInUseWarning(
  validation: ICanaryFilterTemplateValidationMessages,
  validationInput: ITemplateValidationInput,
): ICanaryFilterTemplateValidationMessages {
  const { metricList, editingMetric } = validationInput;
  const filterTemplate = get(editingMetric, 'query.customFilterTemplate');

  const otherMetricsUsingTemplate =
    editingMetric != null &&
    filterTemplate != null &&
    metricList.filter(m => m.name !== editingMetric.name && filterTemplate === get(m, 'query.customFilterTemplate'));

  if (!isEmpty(otherMetricsUsingTemplate)) {
    validation.warnings.template = {
      message: `Warning: editing or deleting this template will affect the following metrics: ${otherMetricsUsingTemplate
        .map(m => m.name)
        .join(', ')}`,
    };
  }
  return validation;
}

export function validateTemplate(
  templateValidationInput: ITemplateValidationInput,
): ICanaryFilterTemplateValidationMessages {
  return [validateTemplateName, validateTemplateValue, validateTemplateInUseWarning].reduce(
    (reducedValidation, validator) => validator(reducedValidation, templateValidationInput),
    { errors: {}, warnings: {} },
  );
}
