import * as React from 'react';
import { IArtifact, IExpectedArtifact } from 'core/domain';

import './artifactList.less';

export interface IArtifactListProps {
  artifacts: IArtifact[];
  resolvedExpectedArtifacts?: IExpectedArtifact[];
}

export interface IArtifactListState {}

export class ArtifactList extends React.Component<IArtifactListProps, IArtifactListState> {
  constructor(props: IArtifactListProps) {
    super(props);
  }

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
    const { artifacts, resolvedExpectedArtifacts = [] } = this.props;
    const defaultArtifactRefs = new Set();
    resolvedExpectedArtifacts.forEach(rea => {
      if (rea && rea.defaultArtifact && rea.defaultArtifact.reference) {
        defaultArtifactRefs.add(rea.defaultArtifact.reference);
      }
    });
    if (!artifacts || artifacts.length === 0) {
      return null;
    }
    return (
      <ul className="trigger-details artifacts">
        {artifacts.map((artifact: IArtifact, i: number) => {
          const { name, version, type, reference } = artifact;
          const isDefault = defaultArtifactRefs.has(reference);
          return (
            <li key={`${i}-${name}`} className="break-word" title={this.tooltip(artifact, isDefault)}>
              <dl>
                <div>
                  <dt>Type</dt>
                  <dd>{type}</dd>
                </div>
                <div>
                  <dt>Artifact{isDefault && '*'}</dt>
                  <dd>{name}</dd>
                </div>
                {version && (
                  <div>
                    <dt>Version</dt>
                    <dd>{version}</dd>
                  </div>
                )}
              </dl>
            </li>
          );
        })}
      </ul>
    );
  }
}
