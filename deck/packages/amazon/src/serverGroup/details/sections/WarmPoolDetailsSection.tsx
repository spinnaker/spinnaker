import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';

import type { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';
import { AWSProviderSettings } from '../../../aws.settings';
import { ModifyWarmPoolModal, WarmPoolService } from '../warmPool';

export class WarmPoolDetailsSection extends React.Component<IAmazonServerGroupDetailsSectionProps> {
  private editWarmPool = (): void => {
    const { app: application, serverGroup } = this.props;
    ModifyWarmPoolModal.show({ application, serverGroup });
  };

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    const configuration = WarmPoolService.getWarmPoolConfiguration(serverGroup);

    return (
      <CollapsibleSection cacheKey="Warm Pool" heading="Warm Pool">
        {configuration ? (
          <ul className="warm-pool">
            <li>Min Size: {configuration.minSize ?? 0}</li>
            <li>
              Max Group Prepared Capacity:{' '}
              {configuration.maxGroupPreparedCapacity != null && configuration.maxGroupPreparedCapacity >= 0
                ? configuration.maxGroupPreparedCapacity
                : 'No limit'}
            </li>
            <li>Instance State: {configuration.poolState ?? 'Stopped'}</li>
            <li>Reuse on Scale In: {configuration.instanceReusePolicy?.reuseOnScaleIn ? 'Yes' : 'No'}</li>
          </ul>
        ) : (
          <div className="text-disabled">No warm pool configured</div>
        )}
        {AWSProviderSettings.adHocInfraWritesEnabled && (
          <a className="clickable" onClick={this.editWarmPool}>
            Edit Warm Pool
          </a>
        )}
      </CollapsibleSection>
    );
  }
}
