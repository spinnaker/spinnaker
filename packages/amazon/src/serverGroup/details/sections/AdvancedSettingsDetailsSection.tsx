import React from 'react';

import { CollapsibleSection, confirmNotManaged, ModalInjector } from '@spinnaker/core';
import { HelpField } from '@spinnaker/core';

import { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';

export class AdvancedSettingsDetailsSection extends React.Component<IAmazonServerGroupDetailsSectionProps> {
  private editAdvancedSettings = (): void => {
    const { app, serverGroup } = this.props;
    confirmNotManaged(serverGroup, app).then(
      (notManaged) =>
        notManaged &&
        ModalInjector.modalService.open({
          templateUrl: require('../advancedSettings/editAsgAdvancedSettings.modal.html'),
          controller: 'EditAsgAdvancedSettingsCtrl as ctrl',
          resolve: {
            application: () => app,
            serverGroup: () => serverGroup,
          },
        }),
    );
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
        <a className="clickable" onClick={this.editAdvancedSettings}>
          Edit Advanced Settings
        </a>
      </CollapsibleSection>
    );
  }
}
