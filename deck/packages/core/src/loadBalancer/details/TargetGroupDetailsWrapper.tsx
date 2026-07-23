import type { ComponentType } from 'react';
import React from 'react';

import type { Application } from '../../application';
import { CloudProviderRegistry } from '../../cloudProvider';

export interface ITargetGroupStateParams {
  accountId: string;
  loadBalancerName: string;
  name: string;
  provider: string;
  region: string;
  vpcId?: string;
}

export interface ITargetGroupDetailsProps {
  accountId: string;
  app: Application;
  name: string;
  provider: string;
  targetGroup: ITargetGroupStateParams;
}

type ProviderTargetGroupDetailsComponent = ComponentType<ITargetGroupDetailsProps>;

export function TargetGroupDetails(props: ITargetGroupDetailsProps): JSX.Element | null {
  const DetailsComponent = CloudProviderRegistry.getValue(
    props.provider,
    'loadBalancer.targetGroupDetails',
  ) as ProviderTargetGroupDetailsComponent;

  if (DetailsComponent) {
    return <DetailsComponent {...props} />;
  }

  const templateUrl = CloudProviderRegistry.getValue(props.provider, 'loadBalancer.targetGroupDetailsTemplateUrl');
  const controller = CloudProviderRegistry.getValue(props.provider, 'loadBalancer.targetGroupDetailsController');

  if (templateUrl && controller) {
    return (
      <div className="alert alert-warning">
        Target group details for {props.provider} must be migrated to React. AngularJS templates/controllers are no
        longer supported.
      </div>
    );
  }

  return null;
}
