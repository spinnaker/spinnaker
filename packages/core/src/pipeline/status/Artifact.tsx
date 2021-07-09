import React from 'react';

import { ArtifactIconService } from '../../artifact';
import { IArtifact } from '../../domain';

import './artifact.less';

export interface IArtifactProps {
  artifact: IArtifact;
  isDefault?: boolean;
  sequence?: number;
}

export class Artifact extends React.Component<IArtifactProps> {
  private tooltip(artifact: IArtifact, isDefault: boolean): string {
    const tooltipEntries = [];
    if (isDefault) {
      tooltipEntries.push('Default Artifact');
    }
    if (artifact.name) {
      tooltipEntries.push(`Name: ${artifact.name}`);
    }
    if (artifact.type) {
      tooltipEntries.push(`Type: ${artifact.type}`);
    }
    if (artifact.version) {
      tooltipEntries.push(`Version: ${artifact.version}`);
    }
    if (artifact.reference) {
      tooltipEntries.push(`Reference: ${artifact.reference}`);
    }
    return tooltipEntries.join('\n');
  }

  public render() {
    const { artifact, isDefault } = this.props;
    const { name, reference, version, type } = artifact;

    return (
      <div className="artifact-details">
        <dl title={this.tooltip(artifact, isDefault)}>
          <div className="artifact-detail">
            <dt>
              {ArtifactIconService.getPath(type) ? (
                <img className="artifact-icon" src={ArtifactIconService.getPath(type)} width="18" height="18" />
              ) : (
                <span>[{type}] </span>
              )}
            </dt>
            <dd>
              <div className="artifact-name">{name || reference}</div>
              {version && <div className="artifact-version"> - {version}</div>}
            </dd>
          </div>
        </dl>
      </div>
    );
  }
}
