import { $templateCache } from 'ngimport';
import type { ComponentType } from 'react';
import React from 'react';

import type { Application } from '../../application';
import { CloudProviderRegistry } from '../../cloudProvider';
import { AngularJSAdapter } from '../../reactShims';

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
  const template = templateUrl ? $templateCache.get<string>(templateUrl) : undefined;

  if (template && controller) {
    return <AngularJSAdapter template={template} controller={`${controller} as ctrl`} locals={props} />;
  }

  return null;
}
