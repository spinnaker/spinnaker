import {
  ITemplateValidationInput,
  validateTemplateInUseWarning,
  validateTemplateName,
  validateTemplateValue,
} from './filterTemplatesValidation';
import { PrometheusQueryType } from '../metricStore/prometheus/domain/IPrometheusCanaryMetricSetQueryConfig';

describe('Filter template validation', () => {
  let validationInput: ITemplateValidationInput;
  beforeEach(() => {
    validationInput = getTestValidationInput();
  });
  describe('validateTemplateName', () => {
    it('handles valid name', () => {
      expect(validateTemplateName({ errors: {}, warnings: {} }, validationInput)).toEqual({ errors: {}, warnings: {} });
    });
    it('handles missing template name', () => {
      validationInput.editingTemplate.editedName = '';
      expect(validateTemplateName({ errors: {}, warnings: {} }, validationInput).errors.templateName.message).toEqual(
        'Name is required',
      );
    });
    it('handles duplicate template name', () => {
      validationInput.editingTemplate.editedName = 'my-other-filter-template';
      expect(validateTemplateName({ errors: {}, warnings: {} }, validationInput).errors.templateName.message).toEqual(
        "Filter template named 'my-other-filter-template' already exists",
      );
    });
  });
  describe('validateTemplateValue', () => {
    it('handles valid template value', () => {
      expect(validateTemplateValue({ errors: {}, warnings: {} }, validationInput)).toEqual({
        errors: {},
        warnings: {},
      });
    });
    it('handles missing template value', () => {
      validationInput.editingTemplate.editedValue = '';
      expect(validateTemplateValue({ errors: {}, warnings: {} }, validationInput).errors.templateValue.message).toEqual(
        'Value is required',
      );
    });
  });
  describe('validateTemplateInUseWarning', () => {
    it('handles template not in use by other metrics', () => {
      expect(validateTemplateInUseWarning({ errors: {}, warnings: {} }, validationInput)).toEqual({
        errors: {},
        warnings: {},
      });
    });
    it('warns if other metrics are using selected template', () => {
      validationInput.metricList.push({
        id: '#1',
        name: 'my other metric',
        groups: ['group-1'],
        analysisConfigurations: null,
        scopeName: 'default',
        query: {
          customFilterTemplate: 'my-filter-template',
          queryType: PrometheusQueryType.DEFAULT,
        },
      });
      expect(
        validateTemplateInUseWarning({ errors: {}, warnings: {} }, validationInput).warnings.template.message,
      ).toEqual('Warning: editing or deleting this template will affect the following metrics: my other metric');
    });
  });
});

function getTestValidationInput(): ITemplateValidationInput {
  return {
    editingTemplate: {
      name: 'my-filter-template',
      editedName: 'my-filter-template-edited',
      editedValue: 'metadata.user_labels."app"="${scope}"',
      isNew: false,
    },
    configTemplates: {
      'my-filter-template': 'metadata.user_labels."app"="${scope}"',
      'my-other-filter-template': 'metadata.user_labels."app"="${location}"',
    },
    metricList: [
      {
        id: '#0',
        name: 'my metric',
        groups: ['group-1'],
        analysisConfigurations: null,
        scopeName: 'default',
        query: {
          customFilterTemplate: 'my-filter-template',
          queryType: PrometheusQueryType.DEFAULT,
        },
      },
    ],
    editingMetric: {
      id: '#0',
      name: 'my metric',
      groups: ['group-1'],
      analysisConfigurations: null,
      scopeName: 'default',
      query: {
        customFilterTemplate: 'my-filter-template',
        queryType: PrometheusQueryType.DEFAULT,
      },
    },
  };
}
