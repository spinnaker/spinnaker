import React from 'react';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';

export interface InstanceLoadBalancer {
  healthState?: string; // (usually present but) optional because there's a fallback
  state?: string; // (usually present but) optional because it is the fallback
  name?: string; // optional because there's a fallback (depends on source of data)
  loadBalancerName?: string; // optional because it is the fallback (depends on source of data)
  description?: string; // optional because there usually isn't a useful description when things are healthy
  healthCheckPath?: string;
  healthCheckProtocol?: string;
}

export interface IInstanceLoadBalancerHealthProps {
  loadBalancer: InstanceLoadBalancer;
  ipAddress?: string;
}

export class InstanceLoadBalancerHealth extends React.Component<IInstanceLoadBalancerHealthProps> {
  public render() {
    const {
      loadBalancer: { healthState, state, name, description, loadBalancerName, healthCheckProtocol, healthCheckPath },
      ipAddress,
    } = this.props;

    const health = healthState || (state === 'InService' ? 'Up' : 'OutOfService');
    const displayName = name || loadBalancerName;

    let icon = null;
    if (health === 'Up') {
      icon = <span className="glyphicon glyphicon-Up-triangle" />;
    } else if (health === 'OutOfService' || health === 'Down') {
      icon = <span className="glyphicon glyphicon-Down-triangle" />;
    } else if (health === 'Starting') {
      icon = <span className="glyphicon glyphicon-Starting-triangle" />;
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

    const healthCheckLinkSpan = (
      <span className="pad-left small">
        <a
          ng-if="targetGroup.healthCheckPath"
          target="_blank"
          href={`${healthCheckProtocol}://${ipAddress}${healthCheckPath}`}
        >
          Health Check
        </a>
      </span>
    );

    // Only wrap with tooltip if we have text for a tooltip
    const tooltipText = healthState !== 'Up' ? description : '';
    if (tooltipText) {
      const tooltip = <Tooltip id={name}>{tooltipText}</Tooltip>;
      return (
        <OverlayTrigger placement="left" overlay={tooltip}>
          <>
            {healthDiv}
            {ipAddress && healthCheckPath && healthCheckLinkSpan}
          </>
        </OverlayTrigger>
      );
    }

    return (
      <>
        {healthDiv}
        {ipAddress && healthCheckPath && healthCheckLinkSpan}
      </>
    );
  }
}
