import * as React from 'react';

import { IArtifact, IExpectedArtifact } from 'core/domain';
import { ArtifactIconService } from 'core/artifact';

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
    let { artifacts, resolvedExpectedArtifacts } = this.props;

    artifacts = artifacts || [];
    resolvedExpectedArtifacts = resolvedExpectedArtifacts || [];

    const defaultArtifactRefs: Set<string> = resolvedExpectedArtifacts.reduce((set, rea) => {
      if (rea && rea.defaultArtifact && rea.defaultArtifact.reference) {
        set.add(rea.defaultArtifact.reference);
      }
      return set;
    }, new Set());

    const decoratedArtifacts = artifacts.filter(({ name, type }) => name && type);
    const decoratedExpectedArtifacts = resolvedExpectedArtifacts
      .map(rea => rea.boundArtifact)
      .filter(({ name, type }) => name && type);

    if (decoratedArtifacts.length === 0 && decoratedExpectedArtifacts.length === 0) {
      return null;
    }

    return (
      <ul className="trigger-details artifacts">
        {decoratedExpectedArtifacts.map((artifact: IArtifact, i: number) => {
          const { name, version, type, reference } = artifact;
          const isDefault = defaultArtifactRefs.has(reference);
          return (
            <li key={`${i}-${name}`} className="break-word" title={this.tooltip(artifact, isDefault)}>
              <dl>
                <div>
                  <dt>
                    {ArtifactIconService.getPath(type) ? (
                      <img className="artifact-icon" src={ArtifactIconService.getPath(type)} width="18" height="18" />
                    ) : (
                      <span>{type}</span>
                    )}
                  </dt>
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
        {decoratedArtifacts.length > decoratedExpectedArtifacts.length && (
          <li key="extraneous-artifacts">
            {decoratedArtifacts.length - decoratedExpectedArtifacts.length} received artifacts were not consumed
          </li>
        )}
      </ul>
    );
  }
}
