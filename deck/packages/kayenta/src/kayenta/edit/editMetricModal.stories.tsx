import * as React from 'react';
import { Provider } from 'react-redux';
import { createStore } from 'redux';

import { KayentaAccountType } from '../domain';
import '../metricStore';
import EditMetricModal from './editMetricModal';

export default { component: EditMetricModal, title: 'Kayenta/Edit Metric Modal' };

const baseState = {
  app: { disableConfigEdit: false },
  data: {
    kayentaAccounts: {
      data: [
        {
          name: 'metrics-account',
          type: 'prometheus',
          supportedTypes: [KayentaAccountType.MetricsStore],
          metricsStoreType: 'prometheus',
        },
      ],
    },
    metricsServiceMetadata: { data: [] },
  },
  selectedConfig: {
    editingMetric: null as any,
    editingTemplate: {},
    config: { templates: {} as { [key: string]: string } },
    group: { list: ['Group 1', 'Group 2'] },
    metricList: [] as any[],
  },
};

function buildStore(overrides: any) {
  const state = {
    ...baseState,
    ...overrides,
    selectedConfig: { ...baseState.selectedConfig, ...(overrides.selectedConfig || {}) },
  };
  const store = createStore(() => state);
  store.dispatch = (() => undefined) as any;
  return store;
}

const commonMetricFields = {
  id: '1',
  name: 'Request Latency',
  groups: ['Group 1'],
  analysisConfigurations: {
    canary: { direction: 'increase', nanStrategy: 'default', critical: false },
  },
};

export const PrometheusNewMetricDefaultsToTemplate = () => {
  const store = buildStore({
    selectedConfig: {
      editingMetric: {
        ...commonMetricFields,
        query: { type: 'prometheus', serviceType: 'prometheus' },
      },
    },
  });
  return (
    <Provider store={store}>
      <EditMetricModal />
    </Provider>
  );
};

export const PrometheusExistingGuidedConfig = () => {
  const store = buildStore({
    selectedConfig: {
      editingMetric: {
        ...commonMetricFields,
        query: {
          type: 'prometheus',
          serviceType: 'prometheus',
          metricName: 'http_requests_total',
          resourceType: 'gce_instance',
          labelBindings: ['service=checkout'],
          groupByFields: ['instance'],
        },
      },
    },
  });
  return (
    <Provider store={store}>
      <EditMetricModal />
    </Provider>
  );
};

export const StackdriverTemplateWithSavedTemplates = () => {
  const store = buildStore({
    selectedConfig: {
      editingMetric: {
        ...commonMetricFields,
        name: 'CPU Utilization',
        query: {
          type: 'stackdriver',
          serviceType: 'stackdriver',
          customInlineTemplate:
            'metric.type="compute.googleapis.com/instance/cpu/utilization" AND resource.type="${resourceType}" AND resource.label.project_id="${project}"',
        },
      },
      config: {
        templates: {
          'standard-scope-filter': 'resource.label.project_id="${project}" AND resource.type="${resourceType}"',
        },
      },
    },
  });
  return (
    <Provider store={store}>
      <EditMetricModal />
    </Provider>
  );
};

export const InfluxDbTemplateOnlyProvider = () => {
  const store = buildStore({
    selectedConfig: {
      editingMetric: {
        ...commonMetricFields,
        name: 'Disk Read IOPS',
        query: {
          type: 'influxdb',
          serviceType: 'influxdb',
          customInlineTemplate: 'SELECT mean("value") FROM "disk_reads" WHERE ${timeFilter} AND "host" = \'${scope}\' GROUP BY time(${step})',
        },
      },
    },
  });
  return (
    <Provider store={store}>
      <EditMetricModal />
    </Provider>
  );
};

export const WavefrontNewMetric = () => {
  const store = buildStore({
    selectedConfig: {
      editingMetric: {
        ...commonMetricFields,
        name: 'Request Count',
        query: { type: 'wavefront', serviceType: 'wavefront' },
      },
    },
  });
  return (
    <Provider store={store}>
      <EditMetricModal />
    </Provider>
  );
};
