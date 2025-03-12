import React from 'react';

import type { ICapacity } from '@spinnaker/core';
import { CapacityDetailsSection, CollapsibleSection, Overridable, ViewScalingActivitiesLink } from '@spinnaker/core';

import type { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';
import { AWSProviderSettings } from '../../../aws.settings';
import { AmazonResizeServerGroupModal } from '../resize/AmazonResizeServerGroupModal';

@Overridable('amazon.serverGroup.CapacityDetailsSection')
export class AmazonCapacityDetailsSection extends React.Component<IAmazonServerGroupDetailsSectionProps> {
  public render(): JSX.Element {
    const { serverGroup, app: application } = this.props;

    const capacity: ICapacity = {
      min: serverGroup.asg.minSize,
      max: serverGroup.asg.maxSize,
      desired: serverGroup.asg.desiredCapacity,
    };

    return (
      <CollapsibleSection heading="Capacity" defaultExpanded={true}>
        <CapacityDetailsSection current={serverGroup.instances.length} capacity={capacity} />

        <div>
          {AWSProviderSettings.adHocInfraWritesEnabled && (
            <a className="clickable" onClick={() => AmazonResizeServerGroupModal.show({ application, serverGroup })}>
              Resize Server Group
            </a>
          )}
        </div>

        <div>
          <ViewScalingActivitiesLink serverGroup={serverGroup} />
        </div>
      </CollapsibleSection>
    );
  }
}
