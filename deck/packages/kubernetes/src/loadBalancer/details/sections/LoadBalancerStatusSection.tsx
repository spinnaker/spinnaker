import { UISref } from '@uirouter/react';
import { isEmpty, orderBy } from 'lodash';
import React from 'react';

import type { IServerGroup } from '@spinnaker/core';
import { CollapsibleSection, CopyToClipboard, HealthCounts, robotToHuman } from '@spinnaker/core';

import type { IKubernetesLoadBalancerDetailsSectionProps } from './IKubernetesLoadBalancerDetailsSectionProps';

export function LoadBalancerStatusSection({ loadBalancer }: IKubernetesLoadBalancerDetailsSectionProps) {
  const { manifest } = loadBalancer;
  return (
    <CollapsibleSection heading="Status" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        {isEmpty(loadBalancer.serverGroups) && (
          <div>No workloads associated with this {robotToHuman(loadBalancer.kind)}.</div>
        )}
        {!isEmpty(loadBalancer.serverGroups) && (
          <>
            <dt>Workloads</dt>
            <dd>
              <ul>
                {orderBy(loadBalancer.serverGroups, ['isDisabled', 'name'], ['asc', 'desc']).map(
                  (serverGroup: IServerGroup) => (
                    <li key={serverGroup.name}>
                      <UISref
                        to="^.serverGroup"
                        params={{
                          region: serverGroup.region,
                          accountId: serverGroup.account,
                          serverGroup: serverGroup.name,
                          provider: 'kubernetes',
                        }}
                      >
                        <a>{robotToHuman(serverGroup.name)}</a>
                      </UISref>
                    </li>
                  ),
                )}
              </ul>
            </dd>
            <div>
              <dt>Pod status</dt>
              <dd>
                <HealthCounts className="pull-left" container={loadBalancer.instanceCounts} />
              </dd>
            </div>
          </>
        )}
        {manifest.manifest.spec.clusterIP && (
          <div>
            <dt>Cluster IP</dt>
            <dd>
              <a target="_blank" href={`//${manifest.manifest.spec.clusterIP}`}>
                {manifest.manifest.spec.clusterIP}
              </a>
              <CopyToClipboard
                className="sp-margin-s-left copy-to-clipboard copy-to-clipboard-sm"
                text={manifest.manifest.spec.clusterIP}
                toolTip="Copy Cluster IP to clipboard"
              />
            </dd>
          </div>
        )}
        {manifest.manifest.spec.loadBalancerIP && (
          <div>
            <dt>Load Balancer IP</dt>
            <dd>
              <a target="_blank" href={`//${manifest.manifest.spec.loadBalancerIP}`}>
                {manifest.manifest.spec.loadBalancerIP}
              </a>
              <CopyToClipboard
                className="sp-margin-s-left copy-to-clipboard copy-to-clipboard-sm"
                text={manifest.manifest.spec.loadBalancerIP}
                toolTip="Copy Load Balancer IP to clipboard"
              />
            </dd>
          </div>
        )}
        {!isEmpty(manifest.manifest.spec.rules) && (
          <div>
            <dt>Host Rules</dt>
            {manifest.manifest.spec.rules
              .filter((ingressRule: { host: string }) => Boolean(ingressRule.host?.trim()))
              .map((ingressRule: { host: string }) => (
                <dd>
                  <a target="_blank" href={`//${ingressRule.host}`}>
                    {' '}
                    {ingressRule.host}{' '}
                  </a>
                  <CopyToClipboard
                    className="sp-margin-s-left copy-to-clipboard copy-to-clipboard-sm"
                    text={ingressRule.host}
                    toolTip="Copy ingress rule host to clipboard"
                  />
                </dd>
              ))}
          </div>
        )}
        {!isEmpty(manifest.manifest.status.loadBalancer.ingress) && (
          <div>
            <dt>Ingress</dt>
            {manifest.manifest.status.loadBalancer.ingress.map((ingress: { hostname: string; ip: string }) => (
              <dd>
                {ingress.hostname && (
                  <>
                    <a target="_blank" href={`//${ingress.hostname}`}>
                      {' '}
                      {ingress.hostname}{' '}
                    </a>
                    <CopyToClipboard
                      className="sp-margin-s-left copy-to-clipboard copy-to-clipboard-sm"
                      text={ingress.hostname}
                      toolTip="Copy Ingress hostname to clipboard"
                    />
                  </>
                )}
                {ingress.ip && (
                  <>
                    <a target="_blank" href={`//${ingress.ip}`}>
                      {' '}
                      {ingress.ip}{' '}
                    </a>
                    <CopyToClipboard
                      className="sp-margin-s-left copy-to-clipboard copy-to-clipboard-sm"
                      text={ingress.ip}
                      toolTip="Copy Ingress IP to clipboard"
                    />
                  </>
                )}
              </dd>
            ))}
          </div>
        )}
      </dl>
    </CollapsibleSection>
  );
}
