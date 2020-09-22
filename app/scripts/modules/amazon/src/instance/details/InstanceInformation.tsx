import React from 'react';
import { UISref } from '@uirouter/react';
import { UIRouterContextComponent } from '@uirouter/react-hybrid';

import { AccountTag, LabeledValue, timestamp } from '@spinnaker/core';

export interface IInstanceInformationProps {
  account: string;
  availabilityZone: string;
  customInfo?: React.Component;
  instanceType: string;
  launchTime: number;
  provider: string;
  region: string;
  serverGroup: string;
}

export const InstanceInformation = ({
  account,
  availabilityZone,
  instanceType,
  launchTime,
  provider,
  region,
  serverGroup,
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
    <LabeledValue label="Type" value={instanceType || 'Unknown'} />
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
