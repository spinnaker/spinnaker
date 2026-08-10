import React from 'react';

import { AccountTag } from '../../../../account/AccountTag';
import type { IExecutionDetailsSectionProps } from '../common';
import { ExecutionDetailsSection } from '../common';
import { StageFailureMessage } from '../../../details/StageFailureMessage';
import type { IExecutionStage } from '../../../../domain';
import { CopyToClipboard } from '../../../../utils';

export interface ICreatedLoadBalancer {
  account: string;
  application: string;
  dnsName: string;
  name: string;
  provider: string;
  region: string;
  type: string;
}

export function getCreatedLoadBalancers(stage: IExecutionStage): ICreatedLoadBalancer[] {
  const context = stage.context || {};
  const katoTasks = context['kato.tasks'];
  const resultObjects = katoTasks && katoTasks.length ? katoTasks[0].resultObjects : null;

  if (!resultObjects || !resultObjects.length) {
    return [];
  }

  return resultObjects.reduce((results: ICreatedLoadBalancer[], resultObject: any) => {
    Object.keys(resultObject.loadBalancers || {}).forEach((region) => {
      const valueObj = resultObject.loadBalancers[region];
      results.push({
        type: 'loadBalancers',
        application: context.application,
        name: valueObj.name,
        region,
        account: context.account,
        dnsName: valueObj.dnsName,
        provider: context.providerType || context.cloudProvider || 'aws',
      });
    });
    return results;
  }, []);
}

export function hasLoadBalancerSubnetDeployments(stage: IExecutionStage): boolean {
  return ((stage.context && stage.context.loadBalancers) || []).some((loadBalancer: any) => !!loadBalancer.subnetType);
}

export class CreateLoadBalancerExecutionDetails extends React.Component<IExecutionDetailsSectionProps> {
  public static title = 'loadBalancerConfig';

  public render(): React.ReactNode {
    const { current, name, stage } = this.props;
    const context = stage.context || {};
    const loadBalancers = context.loadBalancers || [];
    const createdLoadBalancers = getCreatedLoadBalancers(stage);
    const showSubnetColumn = hasLoadBalancerSubnetDeployments(stage);

    return (
      <ExecutionDetailsSection name={name} current={current}>
        <div className="row">
          <div className="col-md-12">
            <table className="table table-condensed">
              <thead>
                <tr>
                  <th>Account</th>
                  <th>Name</th>
                  <th>Region</th>
                  {showSubnetColumn && <th>Subnet</th>}
                </tr>
              </thead>
              <tbody>
                {loadBalancers.map((loadBalancer: any, index: number) => (
                  <tr key={`${loadBalancer.credentials}-${loadBalancer.region}-${loadBalancer.name}-${index}`}>
                    <td>
                      <AccountTag account={loadBalancer.credentials} />
                    </td>
                    <td>{loadBalancer.name}</td>
                    <td>{loadBalancer.region}</td>
                    {showSubnetColumn && <td>{loadBalancer.subnetType || '[none]'}</td>}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
        <StageFailureMessage stage={stage} message={stage.failureMessage} />
        {!!createdLoadBalancers.length && (
          <div className="row">
            <div className="col-md-12">
              <div className="well alert alert-info">
                <strong>Created:</strong>
                {createdLoadBalancers.map((loadBalancer) => (
                  <div key={`${loadBalancer.region}-${loadBalancer.name}-${loadBalancer.dnsName}`}>
                    <a target="_blank" rel="noopener noreferrer" href={`http://${loadBalancer.dnsName}`}>
                      {' '}
                      {loadBalancer.dnsName}{' '}
                    </a>
                    <CopyToClipboard
                      className="copy-to-clipboard copy-to-clipboard-sm"
                      text={loadBalancer.dnsName}
                      toolTip="Copy DNS Name to clipboard"
                    />
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </ExecutionDetailsSection>
    );
  }
}
