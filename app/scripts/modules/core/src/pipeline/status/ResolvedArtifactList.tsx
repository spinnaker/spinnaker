import * as React from 'react';

import { IArtifact, IExpectedArtifact } from 'core/domain';
import { Artifact } from 'core/pipeline/status/Artifact';

import './artifactList.less';

export interface IResolvedArtifactListProps {
  artifacts: IArtifact[];
  resolvedExpectedArtifacts?: IExpectedArtifact[];
}

export class ResolvedArtifactList extends React.Component<IResolvedArtifactListProps> {
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
      <div className="artifact-list">
        <ul>
          {decoratedExpectedArtifacts.map((artifact: IArtifact, i: number) => {
            const { reference } = artifact;
            const isDefault = defaultArtifactRefs.has(reference);
            return (
              <li key={`${i}-${name}`} className="break-word">
                <Artifact artifact={artifact} isDefault={isDefault} />
              </li>
            );
          })}
          {decoratedArtifacts.length > decoratedExpectedArtifacts.length && (
            <li key="extraneous-artifacts">
              {decoratedArtifacts.length - decoratedExpectedArtifacts.length} received artifacts were not consumed
            </li>
          )}
        </ul>
      </div>
    );
  }
}
