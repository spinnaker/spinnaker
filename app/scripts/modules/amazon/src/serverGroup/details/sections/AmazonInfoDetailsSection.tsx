import { get, has } from 'lodash';
import React from 'react';

import {
  AccountTag,
  CollapsibleSection,
  EntitySource,
  IEntityTags,
  IViewChangesConfig,
  SETTINGS,
  timestamp,
  ViewChangesLink,
} from '@spinnaker/core';

import { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';
import { IAmazonServerGroupView } from '../../../domain';
import { VpcTag } from '../../../vpc/VpcTag';

export interface IAmazonInfoDetailsSectionState {
  changeConfig: IViewChangesConfig;
}

export class AmazonInfoDetailsSection extends React.Component<
  IAmazonServerGroupDetailsSectionProps,
  IAmazonInfoDetailsSectionState
> {
  constructor(props: IAmazonServerGroupDetailsSectionProps) {
    super(props);
    this.state = { changeConfig: this.getChangeConfig(props.serverGroup) };
  }

  private getChangeConfig(serverGroup: IAmazonServerGroupView): IViewChangesConfig {
    const changeConfig: IViewChangesConfig = {
      metadata: get(serverGroup.entityTags, 'creationMetadata'),
    };
    if (has(serverGroup, 'buildInfo.jenkins')) {
      changeConfig.buildInfo = {
        ancestor: undefined,
        jenkins: serverGroup.buildInfo.jenkins,
        target: undefined,
      };
    }

    return changeConfig;
  }

  public componentWillReceiveProps(nextProps: IAmazonServerGroupDetailsSectionProps) {
    this.setState({ changeConfig: this.getChangeConfig(nextProps.serverGroup) });
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    const { changeConfig } = this.state;

    const showEntityTags = SETTINGS.feature && SETTINGS.feature.entityTags;
    const entityTags = serverGroup.entityTags || ({} as IEntityTags);

    return (
      <CollapsibleSection heading="Server Group Information" defaultExpanded={true}>
        <dl className="dl-horizontal dl-narrow">
          <dt>Created</dt>
          <dd>{timestamp(serverGroup.createdTime)}</dd>
          {showEntityTags && <EntitySource metadata={entityTags.creationMetadata} />}
          {showEntityTags && (
            <ViewChangesLink
              changeConfig={changeConfig}
              linkText="view changes"
              nameItem={serverGroup}
              viewType="description"
            />
          )}
          <dt>In</dt>
          <dd>
            <AccountTag account={serverGroup.account} />
            {serverGroup.region}
          </dd>
          <dt>VPC</dt>
          <dd>
            <VpcTag vpcId={serverGroup.vpcId} />
          </dd>
          {serverGroup.vpcId && serverGroup.subnetType && <dt>Subnet</dt>}
          {serverGroup.vpcId && serverGroup.subnetType && <dd>{serverGroup.subnetType}</dd>}
          {serverGroup.asg && <dt>Zones</dt>}
          {serverGroup.asg && (
            <dd>
              <ul>
                {serverGroup.asg.availabilityZones.map((zone) => (
                  <li key={zone}>{zone}</li>
                ))}
              </ul>
            </dd>
          )}
        </dl>
      </CollapsibleSection>
    );
  }
}
