import { ICanaryMetricConfig } from '../domain';
import {
  ICanaryMetricValidationErrors,
  validateMetric,
  validateMetricName,
  validateMetricQuery,
} from './editMetricValidation';

describe('Canary metric validation', () => {
  let errors: ICanaryMetricValidationErrors;
  let editingMetric: ICanaryMetricConfig;
  let metricList: ICanaryMetricConfig[];

  beforeEach(() => {
    errors = createErrors();
    editingMetric = createEditingMetric('A', 'a');
    metricList = createMetricList();
  });

  describe('validateMetricName', () => {
    it('Does not update errors when metric name is valid', () => {
      editingMetric.name = 'D';
      expect(validateMetricName(errors, editingMetric, metricList).name).toBeNull();
    });
    it('Updates errors appropriately when metric name is empty', () => {
      editingMetric.name = '';
      expect(validateMetricName(errors, editingMetric, metricList).name).toEqual({
        message: 'Name is required',
      });
    });
    it('Updates errors appropriately when metric name is not unique', () => {
      editingMetric.name = 'B';
      expect(validateMetricName(errors, editingMetric, metricList).name).toEqual({
        message: "Metric 'B' already exists",
      });
    });
  });

  describe('validateMetricQuery', () => {
    it('accepts a metric with a template', () => {
      editingMetric.query = { type: 'prometheus', serviceType: 'prometheus', template: 'up' } as any;
      expect(validateMetricQuery(errors, editingMetric).query).toBeNull();
    });
    it('accepts a metric with guided fields', () => {
      editingMetric.query = { type: 'prometheus', serviceType: 'prometheus', metricName: 'cpu' } as any;
      expect(validateMetricQuery(errors, editingMetric).query).toBeNull();
    });
    it('rejects a metric whose query only has the provider type markers', () => {
      editingMetric.query = { type: 'prometheus', serviceType: 'prometheus' } as any;
      expect(validateMetricQuery(errors, editingMetric).query).toEqual({
        message: 'A query template or query fields are required',
      });
    });
    it('rejects a metric whose template was cleared to an empty string', () => {
      editingMetric.query = { type: 'prometheus', serviceType: 'prometheus', template: '' } as any;
      expect(validateMetricQuery(errors, editingMetric).query).not.toBeNull();
    });
    it('rejects a metric with no query at all', () => {
      delete editingMetric.query;
      expect(validateMetricQuery(errors, editingMetric).query).not.toBeNull();
    });
  });

  describe('validateMetric', () => {
    it('Does not update errors when all fields are valid', () => {
      expect(validateMetric(editingMetric, metricList)).toEqual(errors);
    });
    it('Reduces validation of all fields into one object', () => {
      editingMetric.name = '';
      expect(validateMetric(editingMetric, metricList)).toEqual({
        name: { message: 'Name is required' },
        query: null,
      });
    });
  });
});

function createErrors(): ICanaryMetricValidationErrors {
  return {
    name: null,
    query: null,
  };
}

function createEditingMetric(name: string, id: string): ICanaryMetricConfig {
  return {
    name,
    id,
    scopeName: 'default',
    query: { type: 'prometheus', serviceType: 'prometheus', template: 'up' },
  } as ICanaryMetricConfig;
}

function createMetricList(): ICanaryMetricConfig[] {
  return [createEditingMetric('A', 'a'), createEditingMetric('B', 'b'), createEditingMetric('C', 'c')];
}
