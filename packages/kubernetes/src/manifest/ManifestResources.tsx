import { get } from 'lodash';
import React from 'react';

export interface IManifestResources {
  cpu?: string;
  memory?: string;
}

// TODO(dpeach) https://github.com/spinnaker/spinnaker/issues/3239
export interface IManifestResourceProps {
  manifest?: {
    spec?: {
      containers?: [
        {
          name?: string;
          resources?: {
            limits?: IManifestResources;
            requests?: IManifestResources;
          };
        },
      ];
    };
    status?: {
      qosClass?: string;
    };
  };
  metrics?: [
    {
      containerName?: string;
      metrics?: IPodMetricsMap;
    },
  ];
}

export interface IPodMetricsMap {
  [key: string]: string;
}

export class ManifestResources extends React.Component<IManifestResourceProps> {
  public render() {
    const containers = get(this.props, ['manifest', 'spec', 'containers'], []);
    const metrics = get(this.props, ['metrics'], []);

    if (!metrics) {
      return <div>No metrics reported.</div>;
    }
    const containerToMetricMap = metrics.reduce((acc, val) => {
      acc[val.containerName] = { metrics: val.metrics };
      return acc;
    }, {});

    return (
      <div>
        {containers.map((c, i) => (
          <div key={c.name}>
            Resource usage for <b>{c.name}</b>
            <dl className="dl-horizontal dl-narrow">
              <dt>CPU</dt>
              <dd>{get(containerToMetricMap, [c.name, 'metrics', 'CPU(cores)'], 'Unknown')}</dd>
              <dt>MEMORY</dt>
              <dd>{get(containerToMetricMap, [c.name, 'metrics', 'MEMORY(bytes)'], 'Unknown')}</dd>
            </dl>
            {i < containers.length - 1 ? <hr /> : ''}
          </div>
        ))}
      </div>
    );
  }
}
