import * as React from 'react';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';

export interface InstanceLoadBalancer {
  healthState?: string; // (usually present but) optional because there's a fallback
  state?: string; // (usually present but) optional because it is the fallback
  name?: string; // optional because there's a fallback (depends on source of data)
  loadBalancerName?: string; // optional because it is the fallback (depends on source of data)
  description?: string; // optional because there usually isn't a useful description when things are healthy
}

export interface IInstanceLoadBalancerHealthProps {
  loadBalancer: InstanceLoadBalancer;
}

export class InstanceLoadBalancerHealth extends React.Component<IInstanceLoadBalancerHealthProps> {
  public render() {
    const {
      loadBalancer: { healthState, state, name, description, loadBalancerName },
    } = this.props;

    const health = healthState || (state === 'InService' ? 'Up' : 'OutOfService');
    const displayName = name || loadBalancerName;

    let icon = null;
    if (health === 'Up') {
      icon = <span className="glyphicon glyphicon-Up-triangle" />;
    } else if (health === 'OutOfService' || health === 'Down') {
      icon = <span className="glyphicon glyphicon-Down-triangle" />;
    }

    // We need to continue injecting spaces so that angular and react components align
    // until everything in a particular view is react and we can move them all to margins
    const artificialSpaceBetweenSpans = icon ? ' ' : null;

    const healthDiv = (
      <div style={{ display: 'inline-block' }}>
        {icon}
        {artificialSpaceBetweenSpans}
        <span>{displayName}</span>
      </div>
    );

    // Only wrap with tooltip if we have text for a tooltip
    const tooltipText = healthState !== 'Up' ? description : '';
    if (tooltipText) {
      const tooltip = <Tooltip id={name}>{tooltipText}</Tooltip>;
      return (
        <OverlayTrigger placement="left" overlay={tooltip}>
          {healthDiv}
        </OverlayTrigger>
      );
    }

    return healthDiv;
  }
}
