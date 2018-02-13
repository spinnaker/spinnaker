import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { CollapsibleSection, ReactInjector } from '@spinnaker/core';

import { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';

@BindAll()
export class AdvancedSettingsDetailsSection extends React.Component<IAmazonServerGroupDetailsSectionProps> {
  private editAdvancedSettings(): void {
    ReactInjector.modalService.open({
      templateUrl: require('../advancedSettings/editAsgAdvancedSettings.modal.html'),
      controller: 'EditAsgAdvancedSettingsCtrl as ctrl',
      resolve: {
        application: () => this.props.app,
        serverGroup: () => this.props.serverGroup,
      }
    });
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;

    const asg = serverGroup.asg;

    if (serverGroup.buildInfo && serverGroup.buildInfo.jenkins) {
      return (
        <CollapsibleSection heading="Advanced Settings">
          <dl className="horizontal-when-filters-collapsed">
            <dt>Cooldown</dt>
            <dd>{asg.defaultCooldown} seconds</dd>
            { asg.enabledMetrics.length > 0 && ([
              <dt key={'t-metrics'}>Enabled Metrics</dt>,
              <dd key={'d-metrics'}>{asg.enabledMetrics.map(m => m.metric).join(', ')}</dd>
            ]) }
            <dt>Health Check Type</dt>
            <dd>{asg.healthCheckType}</dd>
            <dt>Grace Period</dt>
            <dd>{asg.healthCheckGracePeriod} seconds</dd>
            <dt>Termination Policies</dt>
            <dd>{asg.terminationPolicies.join(', ')}</dd>
          </dl>
          <a className="clickable" onClick={this.editAdvancedSettings}>Edit Advanced Settings</a>
        </CollapsibleSection>
      );
    }

    return null;
  }
}
