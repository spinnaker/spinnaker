import * as React from 'react';

import { isEmpty, map } from 'lodash';

import { AccountTag, CollapsibleSection, IServerGroupDetailsSectionProps, timestamp } from '@spinnaker/core';

import {
  ICloudFoundryBuildpack,
  ICloudFoundryServerGroup,
  ICloudFoundryEnvVar,
  ICloudFoundryServiceInstance,
} from 'cloudfoundry/domain';

export interface ICloudFoundryServerGroupDetailsSectionProps extends IServerGroupDetailsSectionProps {
  serverGroup: ICloudFoundryServerGroup;
}

export class CloudFoundryInfoDetailsSection extends React.Component<ICloudFoundryServerGroupDetailsSectionProps> {
  constructor(props: ICloudFoundryServerGroupDetailsSectionProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    return (
      <div>
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
        {serverGroup.appsManagerUri && (
          <CollapsibleSection heading="Apps Manager" defaultExpanded={true}>
            <div>
              <a href={serverGroup.appsManagerUri} target="_blank">
                {serverGroup.appsManagerUri}
              </a>
            </div>
          </CollapsibleSection>
        )}
        <CollapsibleSection heading="Server Group Sizing" defaultExpanded={true}>
          <dl className="dl-horizontal dl-flex">
            <dt>Instances</dt>
            <dd>{serverGroup.instances.length}</dd>
            <dt>Disk Mb</dt>
            <dd>{serverGroup.diskQuota}</dd>
            <dt>Memory Mb</dt>
            <dd>{serverGroup.memory}</dd>
          </dl>
        </CollapsibleSection>
        <CollapsibleSection heading="Package" defaultExpanded={true}>
          <dl className="dl-horizontal dl-flex">
            <dt>Checksum</dt>
            <dd>{serverGroup.droplet.packageChecksum}</dd>
          </dl>
        </CollapsibleSection>
        {!isEmpty(serverGroup.serviceInstances) && (
          <CollapsibleSection heading="Bound Services" defaultExpanded={true}>
            <dl className="dl-horizontal dl-flex">
              {serverGroup.serviceInstances.map(function(service: ICloudFoundryServiceInstance, index: number) {
                return (
                  <div key={index}>
                    <dt>Name</dt>
                    <dd>{service.name}</dd>
                    <dt>Plan</dt>
                    <dd>{service.plan}</dd>
                  </div>
                );
              })}
            </dl>
          </CollapsibleSection>
        )}
        {!isEmpty(serverGroup.env) && (
          <CollapsibleSection heading="Environment Variables" defaultExpanded={true}>
            <dl className="dl-horizontal dl-flex">
              {map(serverGroup.env, (obj: ICloudFoundryEnvVar, index: number) => {
                return (
                  <div key={index}>
                    <dt>{obj.key}</dt>
                    <dd>{obj.value}</dd>
                  </div>
                );
              })}
            </dl>
          </CollapsibleSection>
        )}
      </div>
    );
  }
}
