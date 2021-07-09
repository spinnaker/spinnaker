import React from 'react';

import { AccountTag, CollapsibleSection, timestamp } from '@spinnaker/core';

import { ICloudFoundryServerGroupDetailsSectionProps } from './ICloudFoundryServerGroupDetailsSectionProps';
import { ICloudFoundryBuildpack } from '../../../domain';

export class ServerGroupInformationSection extends React.Component<ICloudFoundryServerGroupDetailsSectionProps> {
  constructor(props: ICloudFoundryServerGroupDetailsSectionProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    return (
      <CollapsibleSection heading="Server Group Information" defaultExpanded={true}>
        <dl className="dl-horizontal dl-narrow">
          <dt>Created</dt>
          {serverGroup.pipelineId ? (
            <dd>
              <a target="_blank" href={'/#/applications/' + serverGroup.app + '/executions/' + serverGroup.pipelineId}>
                {timestamp(serverGroup.createdTime)}
              </a>
            </dd>
          ) : (
            <dd>{timestamp(serverGroup.createdTime)}</dd>
          )}
          <dt>Account</dt>
          <dd>
            <AccountTag account={serverGroup.account} />
          </dd>
          <dt>Organization</dt>
          <dd>{serverGroup.space.organization.name}</dd>
          <dt>Space</dt>
          <dd>{serverGroup.space.name}</dd>
          {serverGroup.droplet && (
            <div>
              <dt>Rootfs</dt>
              <dd>{serverGroup.droplet.stack}</dd>
              <dt>Buildpack</dt>
              {serverGroup.droplet.buildpacks ? (
                serverGroup.droplet.buildpacks.map(function (buildpack: ICloudFoundryBuildpack, index: number) {
                  return (
                    <dd key={index}>
                      {buildpack.name} {buildpack.version}
                    </dd>
                  );
                })
              ) : (
                <dd>n/a</dd>
              )}
            </div>
          )}
        </dl>
      </CollapsibleSection>
    );
  }
}
