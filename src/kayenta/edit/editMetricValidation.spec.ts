import { ICanaryMetricValidationErrors, validateMetric, validateMetricName } from './editMetricValidation';
import { ICanaryMetricConfig } from '../domain';

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

  describe('validateMetric', () => {
    it('Does not update errors when all fields are valid', () => {
      expect(validateMetric(editingMetric, metricList)).toEqual(errors);
    });
    it('Reduces validation of all fields into one object', () => {
      editingMetric.name = '';
      expect(validateMetric(editingMetric, metricList)).toEqual({
        name: { message: 'Name is required' },
      });
    });
  });
});

function createErrors(): ICanaryMetricValidationErrors {
  return {
    name: null,
  };
}

function createEditingMetric(name: string, id: string): ICanaryMetricConfig {
  return {
    name,
    id,
    scopeName: 'default',
  } as ICanaryMetricConfig;
}

function createMetricList(): ICanaryMetricConfig[] {
  return [createEditingMetric('A', 'a'), createEditingMetric('B', 'b'), createEditingMetric('C', 'c')];
}
