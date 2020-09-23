import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';

import { ICloudFoundryServerGroupDetailsSectionProps } from './ICloudFoundryServerGroupDetailsSectionProps';

export class BuildSection extends React.Component<ICloudFoundryServerGroupDetailsSectionProps> {
  constructor(props: ICloudFoundryServerGroupDetailsSectionProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    return (
      <CollapsibleSection heading="Build" defaultExpanded={true}>
        <dl className="dl-horizontal dl-narrow">
          {serverGroup.ciBuild && serverGroup.ciBuild.jobName && (
            <div>
              <dt>Job Name</dt>
              <dd>{serverGroup.ciBuild.jobName}</dd>
            </div>
          )}
          {serverGroup.ciBuild && serverGroup.ciBuild.jobNumber && (
            <div>
              <dt>Job Number</dt>
              {serverGroup.ciBuild.jobUrl ? (
                <dd>
                  <a target="_blank" href={serverGroup.ciBuild.jobUrl}>
                    {serverGroup.ciBuild.jobNumber}
                  </a>
                </dd>
              ) : (
                <dd>{serverGroup.ciBuild.jobNumber}</dd>
              )}
            </div>
          )}
          {serverGroup.appArtifact && serverGroup.appArtifact.name && (
            <div>
              <dt>Artifact Name</dt>
              <dd>{serverGroup.appArtifact.name}</dd>
            </div>
          )}
          {serverGroup.appArtifact && serverGroup.appArtifact.version && (
            <div>
              <dt>Version</dt>
              {serverGroup.appArtifact.url ? (
                <dd>
                  <a target="_blank" href={serverGroup.appArtifact.url}>
                    {serverGroup.appArtifact.version}
                  </a>
                </dd>
              ) : (
                <dd>{serverGroup.appArtifact.version}</dd>
              )}
            </div>
          )}
        </dl>
      </CollapsibleSection>
    );
  }
}
