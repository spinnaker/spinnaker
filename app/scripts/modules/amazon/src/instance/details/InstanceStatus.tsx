import React from 'react';

import { CollapsibleSection, InstanceLoadBalancerHealth, robotToHuman, Tooltip } from '@spinnaker/core';
import { IAmazonHealth } from '../../domain';

export type MetricTypes = 'LoadBalancer' | 'TargetGroup';

export interface CustomHealthLink {
  type: string;
  href: string;
  text: string;
}

export interface IInstanceStatusProps {
  healthMetrics: IAmazonHealth[];
  healthState: string;
  metricTypes: MetricTypes[];
  customHealthUrl?: CustomHealthLink;
  privateIpAddress?: string;
}

export const InstanceStatus = ({
  healthMetrics,
  healthState,
  metricTypes,
  customHealthUrl,
  privateIpAddress,
}: IInstanceStatusProps) => {
  const hasLoadBalancer = metricTypes.includes('LoadBalancer');
  const hasTargetGroup = metricTypes.includes('TargetGroup');

  return (
    <CollapsibleSection heading="Status" defaultExpanded={true}>
      {!healthMetrics.length && (
        <p>{healthState === 'Starting' ? 'Starting' : 'No health metrics found for this instance'}</p>
      )}
      <dl className="horizontal-when-filters-collapsed">
        {healthMetrics
          .sort((a: IAmazonHealth, b: IAmazonHealth) => (a.type < b.type ? -1 : a.type > b.type ? 1 : 0))
          .map((metric: IAmazonHealth) => (
            <React.Fragment key={`${metric.type}-${metric.description}`}>
              <dt>{robotToHuman(metric.type)}</dt>
              <dd>
                {!metricTypes.includes(metric.type as MetricTypes) && (
                  <div>
                    <Tooltip value={metric.state.toLowerCase() === 'down' ? metric.description : ''} placement="left">
                      <span>
                        <span className={`glyphicon glyphicon-${metric.state}-triangle`} />
                        <span>{robotToHuman(metric.state)}</span>
                      </span>
                    </Tooltip>
                    <span className="pad-left small">
                      {metric.healthCheckUrl && (
                        <a target="_blank" href={metric.healthCheckUrl}>
                          Health Check
                        </a>
                      )}
                      {metric.healthCheckUrl && metric.statusPageUrl && <span> | </span>}
                      {metric.statusPageUrl && (
                        <a target="_blank" href={metric.statusPageUrl}>
                          Status
                        </a>
                      )}
                      {customHealthUrl && metric.type === customHealthUrl.type && (
                        <span>
                          {' '}
                          |{' '}
                          <a target="_blank" href={customHealthUrl.href}>
                            {customHealthUrl.text}
                          </a>
                        </span>
                      )}
                    </span>
                  </div>
                )}
                {hasLoadBalancer &&
                  metric.type === 'LoadBalancer' &&
                  (metric.loadBalancers || []).map((lb) => (
                    <InstanceLoadBalancerHealth key={`lb-${lb.name}`} loadBalancer={lb} />
                  ))}
                {hasTargetGroup &&
                  metric.type === 'TargetGroup' &&
                  (metric.targetGroups || []).map((tg) => (
                    <InstanceLoadBalancerHealth key={`tg-${tg.name}`} loadBalancer={tg} ipAddress={privateIpAddress} />
                  ))}
              </dd>
            </React.Fragment>
          ))}
      </dl>
    </CollapsibleSection>
  );
};
