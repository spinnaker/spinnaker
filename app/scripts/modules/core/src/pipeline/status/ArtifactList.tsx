import * as React from 'react';
import { IArtifact } from 'core/domain';

import './artifactList.less';

export interface IArtifactListProps {
  artifacts: IArtifact[],
};

export interface IArtifactListState {}

export class ArtifactList extends React.Component<IArtifactListProps, IArtifactListState> {
  constructor(props: IArtifactListProps) {
    super(props);
  }

  private tooltip(artifact: IArtifact): string {
    const tooltipEntries = [];
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
    const { artifacts } = this.props;
    if (!artifacts || artifacts.length === 0) {
      return null;
    }
    return (
      <ul className="trigger-details artifacts">
        {artifacts.map((artifact: IArtifact, i: number) => {
          const { name, version, type } = artifact;
          return (
            <li key={`${i}-${name}`} className="break-word" title={this.tooltip(artifact)}>
              <dl>
                <div>
                  <dt>
                    Type
                  </dt>
                  <dd>
                    {type}
                  </dd>
                </div>
                <div>
                  <dt>
                    Artifact
                  </dt>
                  <dd>
                    {name}
                  </dd>
                </div>
                {version &&
                  <div>
                    <dt>
                      Version
                    </dt>
                    <dd>
                      {version}
                    </dd>
                  </div>
                }
              </dl>
            </li>
          );
        })}
      </ul>
    );
  }
}
