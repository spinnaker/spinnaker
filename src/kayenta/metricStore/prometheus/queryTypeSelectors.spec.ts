import { ICanaryMetricConfig } from '../../domain';
import { PrometheusQueryType } from './domain/IPrometheusCanaryMetricSetQueryConfig';
import { appendPromQLPrefix, getPrometheusQueryType, isQueryPromQL, removePromQLPrefix } from './queryTypeSelectors';

describe('Prometheus query type selectors', () => {
  describe('isQueryPromQL', () => {
    it('identifies PromQL queries by prefix', () => {
      expect(isQueryPromQL('PromQL:count(prometheus_target_interval_length_seconds)')).toEqual(true);
      expect(isQueryPromQL('metadata.user_labels."app"="${location}"')).toEqual(false);
    });
  });
  describe('removePromQLPrefix', () => {
    it('removes PromQL prefix from query', () => {
      expect(removePromQLPrefix('PromQL:count(prometheus_target_interval_length_seconds)')).toEqual(
        'count(prometheus_target_interval_length_seconds)',
      );
    });
    it('does not modify queries not prefixed by the PromQL prefix', () => {
      expect(removePromQLPrefix('Not a PromQL template')).toEqual('Not a PromQL template');
    });
  });
  describe('appendPromQLPrefix', () => {
    it('appends the PromQL prefix to the input', () => {
      expect(appendPromQLPrefix('count(prometheus_target_interval_length_seconds)')).toEqual(
        'PromQL:count(prometheus_target_interval_length_seconds)',
      );
    });
  });
  describe('getPrometheusQueryType', () => {
    let editingMetric: ICanaryMetricConfig;
    beforeEach(() => {
      editingMetric = {
        id: '#0',
        name: 'my metric',
        groups: ['group-1'],
        analysisConfigurations: null,
        scopeName: 'default',
        query: {
          customFilterTemplate: 'myDefaultTemplate',
          queryType: PrometheusQueryType.DEFAULT,
        },
      };
    });
    it('reads query type from metric state if set', () => {
      expect(getPrometheusQueryType(editingMetric)).toEqual(PrometheusQueryType.DEFAULT);
      editingMetric.query.queryType = PrometheusQueryType.PROMQL;
      expect(getPrometheusQueryType(editingMetric)).toEqual(PrometheusQueryType.PROMQL);
    });
    it('infers query type from custom inline template if query type not set on state', () => {
      editingMetric.query.queryType = null;
      expect(getPrometheusQueryType(editingMetric)).toEqual(PrometheusQueryType.DEFAULT);
      editingMetric.query.customInlineTemplate = 'PromQL:count(prometheus_target_interval_length_seconds)';
      expect(getPrometheusQueryType(editingMetric)).toEqual(PrometheusQueryType.PROMQL);
    });
    it('defaults to PrometheusQueryType.DEFAULT if query type not set on state and no inline template added', () => {
      editingMetric.query.queryType = null;
      editingMetric.query.customInlineTemplate = null;
      expect(getPrometheusQueryType(editingMetric)).toEqual(PrometheusQueryType.DEFAULT);
    });
  });
});
