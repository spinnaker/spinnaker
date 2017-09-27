import * as React from 'react';
import { BindAll } from 'lodash-decorators';
import { IAmazonServerGroupView } from 'amazon/domain';

export interface IServerGroupAdvancedSettingsViewProps {
  serverGroup: IAmazonServerGroupView;
}

@BindAll()
export class ServerGroupAdvancedSettingsView extends React.PureComponent<IServerGroupAdvancedSettingsViewProps> {

  public render(): JSX.Element {
    const { asg } = this.props.serverGroup;
    return (
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
    )
  }
}
