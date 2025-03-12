import type { Dictionary } from 'lodash';
import * as React from 'react';

import type { ICloudMetricDescriptor, IMetricAlarmDimension } from '@spinnaker/core';
import { CloudMetricsReader, HelpField, ReactSelectInput, useData } from '@spinnaker/core';

import { DimensionsEditor } from './DimensionsEditor';
import { AWSProviderSettings } from '../../../../../aws.settings';
import type { IAmazonServerGroup, IScalingPolicyAlarmView } from '../../../../../domain';
import { NAMESPACES } from './namespaces';

import './MetricSelector.less';

export interface IMetricOption extends ICloudMetricDescriptor {
  label: string;
  dimensionValues: string;
  value: ICloudMetricDescriptor;
}

export interface IMetricSelectorProps {
  alarm: IScalingPolicyAlarmView;
  serverGroup: IAmazonServerGroup;
  updateAlarm: (alarm: IScalingPolicyAlarmView) => void;
}

export const MetricSelector = ({ alarm, updateAlarm, serverGroup }: IMetricSelectorProps) => {
  const namespaces = (AWSProviderSettings?.metrics?.customNamespaces || []).concat(NAMESPACES);
  const [advancedMode, setAdvancedMode] = React.useState<boolean>(false);

  const dimensionsObject = (alarm?.dimensions || []).reduce(
    (acc: Dictionary<string>, dimension: IMetricAlarmDimension) => {
      acc[dimension.name] = dimension.value;
      return acc;
    },
    {} as Dictionary<string>,
  );

  const buildDimensionValues = (dimensions: IMetricAlarmDimension[]) =>
    dimensions
      .sort((a: IMetricAlarmDimension, b: IMetricAlarmDimension) => a.name?.localeCompare(b.name))
      .map((d: IMetricAlarmDimension) => d.value)
      .join(', ');

  const fetchCloudMetrics = () => {
    const account = serverGroup.cloudProvider === 'aws' ? serverGroup.account : serverGroup?.awsAccount;
    const queryParams = advancedMode ? { namespace: alarm?.namespace } : { AutoScalingGroupName: serverGroup.name };
    return CloudMetricsReader.listMetrics('aws', account, serverGroup.region, queryParams)
      .then((results: ICloudMetricDescriptor[]) => {
        const sortedMetrics: IMetricOption[] = (results || [])
          .map((m) => ({
            label: `(${m.namespace}) ${m.name}`,
            dimensions: [],
            dimensionValues: buildDimensionValues(m.dimensions || []),
            value: m,
            ...m,
          }))
          .sort((a, b) => a.label?.localeCompare(b.label));
        return sortedMetrics;
      })
      .catch(() => {
        return [];
      });
  };

  const { result: metrics } = useData(fetchCloudMetrics, [], [serverGroup, alarm?.namespace]);
  const metricsByNamespace = (metrics || []).filter((m) => m.namespace === alarm?.namespace);
  const selectedMetric =
    metricsByNamespace.find((m: IMetricOption) => m.name === alarm?.metricName && m.namespace === alarm?.namespace) ||
    metricsByNamespace.find((m) => m.name.match('CPUUtilization')) ||
    metricsByNamespace[0];
  const { name: metricName, namespace } = selectedMetric || {};

  const toggleMode = () => {
    const newMode = !advancedMode;
    if (newMode) {
      dimensionsObject.namespace = alarm?.namespace;
    } else {
      const newAlarm = {
        ...alarm,
        dimension: [{ name: 'AutoScalingGroupName', value: serverGroup.name }],
      };
      updateAlarm(newAlarm);
    }
    setAdvancedMode(newMode);
  };

  const onMetricChange = (metric: IMetricOption) => {
    const newAlarm = {
      ...alarm,
      metricName: metric.name,
      namespace: metric.namespace,
      dimensions: metric.dimensions,
    };
    updateAlarm(newAlarm);
  };

  const onAdvancedChange = (newNamespace: string, newName: string) => {
    const newAlarm = {
      ...alarm,
      metricName: newName,
      namespace: newNamespace,
    };
    updateAlarm(newAlarm);
  };

  const updateDimensions = (dimensions: IMetricAlarmDimension[]) => {
    const newAlarm = {
      ...alarm,
      dimensions: dimensions,
    };
    updateAlarm(newAlarm);
  };

  React.useEffect(() => {
    if (selectedMetric) {
      onMetricChange(selectedMetric);
    }
  }, [selectedMetric]);

  if (!advancedMode) {
    return (
      <div className="MetricSelector horizontal middle">
        <ReactSelectInput
          value={selectedMetric}
          onChange={(e) => onMetricChange(e.target.value)}
          options={metrics}
          clearable={false}
          inputClassName="sp-margin-s-right simple-input"
        />
        <a className="clickable" onClick={toggleMode}>
          <span className="sp-margin-xs-right">Search all metrics</span>
          <HelpField id="aws.scalingPolicy.search.all" />
        </a>
      </div>
    );
  }

  return (
    <div className="MetricSelector">
      <div className="horizontal middle">
        <ReactSelectInput
          value={namespace}
          onChange={(e) => onAdvancedChange(e.target.value, alarm?.metricName)}
          stringOptions={namespaces}
          inputClassName="advanced-input-namespace sp-margin-m-right"
        />
        <ReactSelectInput
          value={metricName}
          onChange={(e) => onAdvancedChange(alarm?.namespace, e.target.value)}
          stringOptions={metrics.map((m) => m.name)}
          placeholder="name"
          searchable={true}
          inputClassName="advanced-input-metric"
        />
      </div>
      <div className="vertical">
        {!Boolean(metrics.length) && (
          <span className="input-label">
            <b>Note:</b> no metrics found for selected namespace + dimensions
          </span>
        )}
        <a className="clickable" onClick={toggleMode}>
          <span className="sp-margin-s-yaxis sp-margin-xs-right">Only show metrics for this auto scaling group</span>
          <HelpField id="aws.scalingPolicy.search.restricted" />
        </a>
      </div>
      <DimensionsEditor alarm={alarm} serverGroup={serverGroup} updateAvailableMetrics={updateDimensions} />
    </div>
  );
};
