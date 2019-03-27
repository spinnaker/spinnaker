import * as React from 'react';

import { IClusterConfigExpression } from './configBin.reader';

export interface ICustomMetricProps {
  metric: IClusterConfigExpression;
  metricUpdated: (oldMetric: IClusterConfigExpression, newMetric: IClusterConfigExpression) => void;
  metricRemoved: (metric: IClusterConfigExpression) => void;
}

export class CustomMetric extends React.Component<ICustomMetricProps> {
  constructor(props: ICustomMetricProps) {
    super(props);
  }

  private metricNameUpdated = (e: React.ChangeEvent<HTMLInputElement>): void => {
    this.setState({ name: e.target.value });
    this.props.metricUpdated(this.props.metric, { ...this.props.metric, metricName: e.target.value });
  };

  private metricUriUpdated = (e: React.ChangeEvent<HTMLTextAreaElement>): void => {
    this.setState({ uri: e.target.value });
    this.props.metricUpdated(this.props.metric, { ...this.props.metric, atlasUri: e.target.value });
  };

  private removeMetric = (): void => {
    this.props.metricRemoved(this.props.metric);
  };

  public render() {
    const { metric } = this.props;
    return (
      <div style={{ paddingRight: '40px' }}>
        <dl className="dl-horizontal dl-narrow">
          <dt>Name</dt>
          <dd style={{ marginBottom: '10px' }}>
            <input className="form-control input-sm" value={metric.metricName} onChange={this.metricNameUpdated} />
          </dd>
          <dt>Atlas URI</dt>
          <dd>
            <textarea
              rows={3}
              className="form-control input-sm"
              value={metric.atlasUri}
              onChange={this.metricUriUpdated}
            />
          </dd>
          <dt />
          <dd className="text-right">
            <a className="clickable" onClick={this.removeMetric}>
              <i className="fa fa-trash" /> Remove
            </a>
          </dd>
        </dl>
      </div>
    );
  }
}
