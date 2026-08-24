import { PrometheusQueryType } from '../metricStore/prometheus/domain/IPrometheusCanaryMetricSetQueryConfig';
import type { ICanaryState } from '../reducers';
import { useInlineTemplateEditorSelector } from './filterTemplatesSelectors';

function stateWithEditingMetric(query: any): ICanaryState {
  return {
    selectedConfig: {
      editingMetric: {
        id: '#0',
        name: 'my metric',
        groups: ['group-1'],
        analysisConfigurations: null,
        scopeName: 'default',
        query,
      },
    },
  } as any;
}

describe('useInlineTemplateEditorSelector', () => {
  it('is true for Clickhouse metrics, which always edit an inline SQL template', () => {
    const state = stateWithEditingMetric({ serviceType: 'clickhouse', customInlineTemplate: 'SELECT 1' });
    expect(useInlineTemplateEditorSelector(state)).toEqual(true);
  });

  it('is true for Prometheus metrics using the PromQL query type', () => {
    const state = stateWithEditingMetric({ serviceType: 'prometheus', queryType: PrometheusQueryType.PROMQL });
    expect(useInlineTemplateEditorSelector(state)).toEqual(true);
  });

  it('is false for Prometheus metrics using the default structured query type', () => {
    const state = stateWithEditingMetric({ serviceType: 'prometheus', queryType: PrometheusQueryType.DEFAULT });
    expect(useInlineTemplateEditorSelector(state)).toEqual(false);
  });

  it('is false for providers with no inline-template-only query type', () => {
    const state = stateWithEditingMetric({ serviceType: 'newrelic' });
    expect(useInlineTemplateEditorSelector(state)).toEqual(false);
  });
});
