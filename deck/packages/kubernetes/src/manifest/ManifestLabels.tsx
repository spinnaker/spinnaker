import { get, keys } from 'lodash';
import React from 'react';

import './manifestLabels.less';

export interface IManifestLabelsMap {
  [key: string]: string;
}

export interface IManifestLabelsProps {
  manifest?: {
    metadata?: {
      labels?: IManifestLabelsMap;
    };
  };
}

export class ManifestLabels extends React.Component<IManifestLabelsProps> {
  public render() {
    const labels: IManifestLabelsMap = get(this.props, ['manifest', 'metadata', 'labels'], {});
    return (
      <div className="horizontal wrap">
        {keys(labels).map((key) => (
          <div key={key} className="manifest-label sp-badge info">
            {key}: {labels[key]}
          </div>
        ))}
      </div>
    );
  }
}
