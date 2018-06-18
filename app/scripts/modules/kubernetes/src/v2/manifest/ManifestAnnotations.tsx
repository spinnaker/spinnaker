import * as React from 'react';
import { get, keys } from 'lodash';

export interface IManifestAnnotationsMap {
  [key: string]: string;
}

export interface IManifestAnnotationsProps {
  manifest?: {
    metadata?: {
      annotations?: IManifestAnnotationsMap;
    };
  };
}

export class ManifestAnnotations extends React.Component<IManifestAnnotationsProps> {
  private ignoreAnnotations = ['kubectl.kubernetes.io/last-applied-configuration'];

  public render() {
    const annotations: IManifestAnnotationsMap = get(this.props, ['manifest', 'metadata', 'annotations'], {});
    const annotationKeys = keys(annotations).filter(k => !this.ignoreAnnotations.includes(k));
    return (
      <div className="vertical left">
        {annotationKeys.map(key => (
          <div className="info" key={key}>
            <code>
              {key}: {annotations[key]}
            </code>
          </div>
        ))}
      </div>
    );
  }
}
