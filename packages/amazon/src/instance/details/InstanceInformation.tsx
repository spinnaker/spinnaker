import { UISref } from '@uirouter/react';
import { UIRouterContextComponent } from '@uirouter/react-hybrid';
import React from 'react';

import { AccountTag, LabeledValue, timestamp } from '@spinnaker/core';

export interface IInstanceInformationProps {
  account: string;
  availabilityZone: string;
  instanceType: string;
  capacityType?: string;
  launchTime: number;
  provider: string;
  region: string;
  serverGroup: string;
  showInstanceType?: boolean;
}

export const InstanceInformation = ({
  account,
  availabilityZone,
  instanceType,
  capacityType,
  launchTime,
  provider,
  region,
  serverGroup,
  showInstanceType,
}: IInstanceInformationProps) => (
  <>
    <LabeledValue label="Launched" value={launchTime ? timestamp(launchTime) : 'Unknown'} />
    <LabeledValue
      label="In"
      value={
        <div>
          <AccountTag account={account} />
          {availabilityZone || 'Unknown'}
        </div>
      }
    />
    {showInstanceType && <LabeledValue label="Type" value={instanceType || 'Unknown'} />}
    {showInstanceType && capacityType && <LabeledValue label="Capacity Type" value={capacityType || 'Unknown'} />}
    {serverGroup && (
      <LabeledValue
        label="Server Group"
        value={
          <div>
            <UIRouterContextComponent>
              <UISref
                to="^.serverGroup"
                params={{
                  region,
                  accountId: account,
                  serverGroup,
                  provider,
                }}
              >
                <a>{serverGroup}</a>
              </UISref>
            </UIRouterContextComponent>
          </div>
        }
      />
    )}
  </>
);
