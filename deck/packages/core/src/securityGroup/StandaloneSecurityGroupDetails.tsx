import React from 'react';

import type { Application } from '../application';
import { CloudProviderRegistry } from '../cloudProvider';
import { AngularJSAdapter } from '../reactShims';

export interface IStandaloneResolvedSecurityGroup {
  accountId: string;
  name: string;
  provider: string;
  region: string;
  vpcId?: string | null;
}

export interface IStandaloneSecurityGroupDetailsProps {
  app: Application;
  resolvedSecurityGroup: IStandaloneResolvedSecurityGroup;
}

type ProviderSecurityGroupDetailsComponent = React.ComponentType<IStandaloneSecurityGroupDetailsProps>;

function StandaloneDetailsLayout({ children }: { children: React.ReactNode }): JSX.Element {
  return (
    <div className="standalone">
      <div className="col-md-6 col-md-offset-3">{children}</div>
    </div>
  );
}

export function StandaloneSecurityGroupDetails(props: IStandaloneSecurityGroupDetailsProps): JSX.Element | null {
  const provider = props.resolvedSecurityGroup.provider;
  const DetailsComponent = CloudProviderRegistry.getValue(
    provider,
    'securityGroup.details',
  ) as ProviderSecurityGroupDetailsComponent;

  if (DetailsComponent) {
    return (
      <StandaloneDetailsLayout>
        <DetailsComponent {...props} />
      </StandaloneDetailsLayout>
    );
  }

  const templateUrl = CloudProviderRegistry.getValue(provider, 'securityGroup.detailsTemplateUrl');
  const controller = CloudProviderRegistry.getValue(provider, 'securityGroup.detailsController');

  if (templateUrl && controller) {
    return (
      <StandaloneDetailsLayout>
        <AngularJSAdapter templateUrl={templateUrl} controller={`${controller} as ctrl`} locals={props} />
      </StandaloneDetailsLayout>
    );
  }

  return null;
}
