import React from 'react';

import { Artifact } from './Artifact';
import { IArtifact, IExpectedArtifact } from '../../domain';

import './artifactList.less';

export interface IResolvedArtifactListProps {
  artifacts: IArtifact[];
  resolvedExpectedArtifacts?: IExpectedArtifact[];
  showingExpandedArtifacts: boolean;
}

export class ResolvedArtifactList extends React.Component<IResolvedArtifactListProps> {
  constructor(props: IResolvedArtifactListProps) {
    super(props);
  }

  public render() {
    let { artifacts, resolvedExpectedArtifacts } = this.props;
    const { showingExpandedArtifacts } = this.props;

    artifacts = artifacts || [];
    resolvedExpectedArtifacts = resolvedExpectedArtifacts || [];

    const defaultArtifactRefs: Set<string> = resolvedExpectedArtifacts.reduce((set, rea) => {
      if (rea && rea.defaultArtifact && rea.defaultArtifact.reference) {
        set.add(rea.defaultArtifact.reference);
      }
      return set;
    }, new Set<string>());

    const decoratedArtifacts = artifacts.filter(({ name, reference, type }) => (name || reference) && type);
    const decoratedExpectedArtifacts = resolvedExpectedArtifacts
      .map((rea) => rea.boundArtifact)
      .filter(({ name, reference, type }) => (name || reference) && type);

    // if there's none, don't show it
    if (!showingExpandedArtifacts || (decoratedArtifacts.length === 0 && decoratedExpectedArtifacts.length === 0)) {
      return null;
    }

    // if we're exceeding the limit, don't show it
    if (!showingExpandedArtifacts) {
      return null;
    }

    const halfIndex = Math.ceil(decoratedArtifacts.length / 2);
    const columns = [decoratedArtifacts.slice(0, halfIndex), decoratedArtifacts.slice(halfIndex)];

    return (
      <div className="resolved-artifacts">
        <h6 className="artifacts-title">Artifacts</h6>
        <div className="resolved-artifact-list">
          {columns.map((artifactSubset: IArtifact[], colNum: number) => {
            return (
              <div key={`artifact-list-column-${colNum}`} className="artifact-list-column">
                {artifactSubset.map((artifact: IArtifact, i: number) => {
                  const { reference } = artifact;
                  const isDefault = defaultArtifactRefs.has(reference);
                  return (
                    <div key={`${i}-${name}`} className="break-word">
                      <Artifact artifact={artifact} isDefault={isDefault} />
                    </div>
                  );
                })}
              </div>
            );
          })}
        </div>
        {decoratedArtifacts.length > decoratedExpectedArtifacts.length && (
          <div className="extraneous-artifacts">
            {decoratedArtifacts.length - decoratedExpectedArtifacts.length} received artifacts were not consumed
          </div>
        )}
      </div>
    );
  }
}
