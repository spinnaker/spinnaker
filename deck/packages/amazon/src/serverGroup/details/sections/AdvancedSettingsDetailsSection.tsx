import React from 'react';

import { CollapsibleSection, DeckRuntimeContext } from '@spinnaker/core';
import { HelpField } from '@spinnaker/core';

import type { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';
import { EditAsgAdvancedSettingsModal } from '../advancedSettings';
import { AWSProviderSettings } from '../../../aws.settings';

export class AdvancedSettingsDetailsSection extends React.Component<IAmazonServerGroupDetailsSectionProps> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  private editAdvancedSettings = (): void => {
    const { app: application, serverGroup } = this.props;
    EditAsgAdvancedSettingsModal.show({ application, serverGroup }, this.context.services);
  };

  public render(): JSX.Element {
    const { serverGroup } = this.props;

    const asg = serverGroup.asg;

    return (
      <CollapsibleSection heading="Advanced Settings">
        <dl className="horizontal-when-filters-collapsed">
          <dt>Cooldown</dt>
          <dd>{asg.defaultCooldown} seconds</dd>
          {asg.enabledMetrics.length > 0 && [
            <dt key={'t-metrics'}>Enabled Metrics</dt>,
            <dd key={'d-metrics'}>{asg.enabledMetrics.map((m) => m.metric).join(', ')}</dd>,
          ]}
          <dt>Health Check Type</dt>
          <dd>{asg.healthCheckType}</dd>
          <dt>Grace Period</dt>
          <dd>{asg.healthCheckGracePeriod} seconds</dd>
          <dt>Termination Policies</dt>
          <dd>{asg.terminationPolicies.join(', ')}</dd>
          {asg.capacityRebalance && [
            <dt>
              Capacity Rebalance <HelpField id="aws.serverGroup.capacityRebalance" />
            </dt>,
            <dd>{`${asg.capacityRebalance}`}</dd>,
          ]}
        </dl>
        {AWSProviderSettings.adHocInfraWritesEnabled && (
          <a className="clickable" onClick={this.editAdvancedSettings}>
            Edit Advanced Settings
          </a>
        )}
      </CollapsibleSection>
    );
  }
}
