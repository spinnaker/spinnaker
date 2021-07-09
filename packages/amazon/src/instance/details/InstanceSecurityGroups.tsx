import { UISref } from '@uirouter/react';
import { UIRouterContextComponent } from '@uirouter/react-hybrid';
import { orderBy } from 'lodash';
import React from 'react';

import { CollapsibleSection, FirewallLabels } from '@spinnaker/core';

import { IAmazonInstance } from '../../domain';

export interface IInstanceSecurityGroupsProps {
  instance: IAmazonInstance;
}

export const InstanceSecurityGroups = ({ instance }: IInstanceSecurityGroupsProps) => {
  const { account, region, provider, securityGroups, vpcId } = instance;
  const sortedSecurityGroups = orderBy(securityGroups, ['groupName'], ['asc']);
  const securityGroupLabel = FirewallLabels.get('Firewalls');

  return (
    <CollapsibleSection heading={securityGroupLabel} defaultExpanded={true}>
      <UIRouterContextComponent>
        <ul>
          {(sortedSecurityGroups || []).map((sg) => (
            <li key={sg.groupId}>
              <UISref
                to="^.firewallDetails"
                params={{
                  name: sg.groupName,
                  accountId: account,
                  region,
                  vpcId,
                  provider,
                }}
              >
                <a>
                  {sg.groupName} ({sg.groupId})
                </a>
              </UISref>
            </li>
          ))}
        </ul>
      </UIRouterContextComponent>
    </CollapsibleSection>
  );
};
