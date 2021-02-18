import React from 'react';

import { InstanceInformation } from '@spinnaker/amazon';
import { CollapsibleSection, LabeledValue, LinkWithClipboard } from '@spinnaker/core';

import { ITitusInstance } from '../../domain';

export interface ITitusInstanceInformationProps {
  instance: ITitusInstance;
  titusUiEndpoint: string;
}

export const TitusInstanceInformation = ({ instance, titusUiEndpoint }: ITitusInstanceInformationProps) => {
  const { id, jobId } = instance;
  return (
    <CollapsibleSection heading="Instance Information" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <InstanceInformation
          account={instance.account}
          availabilityZone={instance.availabilityZone}
          instanceType={instance.instanceType}
          launchTime={instance.launchTime}
          provider={instance.provider}
          region={instance.region}
          serverGroup={instance.serverGroup}
        />
        <LabeledValue
          label="Job Id"
          value={
            <a href={`${titusUiEndpoint}jobs/${jobId}`} target="_blank">
              {jobId}
            </a>
          }
        />
        <LabeledValue
          label="Instance Id"
          value={<LinkWithClipboard text={id} url={`${titusUiEndpoint}jobs/${jobId}/tasks/${id}`} />}
        />
      </dl>
    </CollapsibleSection>
  );
};
