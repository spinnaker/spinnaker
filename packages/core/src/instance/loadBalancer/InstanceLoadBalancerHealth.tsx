import React from 'react';

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

    let icon = null;
    if (health === 'Up') {
      icon = <span className="glyphicon glyphicon-Up-triangle" />;
    } else if (health === 'OutOfService' || health === 'Down') {
      icon = <span className="glyphicon glyphicon-Down-triangle" />;
    } else if (health === 'Starting') {
      icon = <span className="glyphicon glyphicon-Starting-triangle" />;
    }

    const displayName = <span>{name || loadBalancerName}</span>;

    const downReason =
      healthState !== 'Up' && !!description ? (
        <span style={{ color: 'var(--color-danger)' }}>{description}</span>
      ) : null;

    const healthCheck = healthCheckProtocol?.toLowerCase().startsWith('http') ? (
      <a target="_blank" href={`${healthCheckProtocol}://${ipAddress}${healthCheckPath}`}>
        Health Check
      </a>
    ) : (
      `${healthCheckProtocol}://${ipAddress}${healthCheckPath}`
    );

    return (
      <div className="flex-container-h">
        <div style={{ flex: 'none', width: '16px' }}>{icon}</div>
        <div className="flex-container-v">
          {displayName}
          {downReason}
          {ipAddress && healthCheckPath && healthCheck}
        </div>
      </div>
    );
  }
}
