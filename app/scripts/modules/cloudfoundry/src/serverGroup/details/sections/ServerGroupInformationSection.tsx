import * as React from 'react';

import { AccountTag, CollapsibleSection, timestamp } from '@spinnaker/core';

import { ICloudFoundryBuildpack } from 'cloudfoundry/domain';
import { ICloudFoundryServerGroupDetailsSectionProps } from './ICloudFoundryServerGroupDetailsSectionProps';

export class ServerGroupInformationSection extends React.Component<ICloudFoundryServerGroupDetailsSectionProps> {
  constructor(props: ICloudFoundryServerGroupDetailsSectionProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    return (
      <CollapsibleSection heading="Server Group Information" defaultExpanded={true}>
        <dl className="dl-horizontal dl-flex">
          <dt>Created</dt>
          <dd>{timestamp(serverGroup.createdTime)}</dd>
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
                serverGroup.droplet.buildpacks.map(function(buildpack: ICloudFoundryBuildpack, index: number) {
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
