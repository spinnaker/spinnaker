import React from 'react';

export interface IScopeClusterSelectorProps {
  clusters: string[];
  required?: boolean;
  onChange: ({ clusterName }: { clusterName: string }) => void;
  model: string;
}

export interface IScopeClusterSelectorState {
  freeFormClusterField: boolean;
}

export class ScopeClusterSelector extends React.Component<IScopeClusterSelectorProps, IScopeClusterSelectorState> {
  constructor(props: IScopeClusterSelectorProps) {
    super(props);
    const { clusters, model } = this.props;
    const selectedNotInClusterList = !(Array.isArray(clusters) && clusters.some((cluster) => cluster === model));

    const modelIsSet = model != null || (model || '').trim() !== '';

    this.state = {
      freeFormClusterField: modelIsSet ? selectedNotInClusterList : false,
    };
  }

  private handleClusterChange = (event: React.FormEvent<HTMLInputElement | HTMLSelectElement>) => {
    this.props.onChange && this.props.onChange({ clusterName: event.currentTarget.value });
  };

  private toggleFreeFormClusterField = () => this.setState({ freeFormClusterField: !this.state.freeFormClusterField });

  public render() {
    return (
      <div>
        <div>
          {!this.state.freeFormClusterField && (
            <select
              className="form-control input-sm"
              onChange={this.handleClusterChange}
              required={this.props.required}
              value={this.props.model || ''}
            >
              <option value={''}>-- select cluster --</option>
              {(this.props.clusters || []).map((cluster) => (
                <option key={cluster} value={cluster}>
                  {cluster}
                </option>
              ))}
            </select>
          )}
          {this.state.freeFormClusterField && (
            <input
              type="text"
              className="form-control input-sm"
              required={this.props.required}
              value={this.props.model || ''}
              onChange={this.handleClusterChange}
              onBlur={this.handleClusterChange}
            />
          )}
        </div>
        <div className="pull-right">
          {!this.state.freeFormClusterField && (
            <a className="clickable" onClick={this.toggleFreeFormClusterField}>
              Toggle for text input
            </a>
          )}
          {this.state.freeFormClusterField && (
            <a className="clickable" onClick={this.toggleFreeFormClusterField}>
              Toggle for list of existing clusters
            </a>
          )}
        </div>
      </div>
    );
  }
}
