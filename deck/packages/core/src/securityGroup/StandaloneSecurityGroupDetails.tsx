import React from 'react';

import type { Application } from '../application';
import { CloudProviderRegistry } from '../cloudProvider';

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
        <div className="alert alert-warning">
          Security group details for {provider} must be migrated to React. AngularJS templates/controllers are no longer
          supported.
        </div>
      </StandaloneDetailsLayout>
    );
  }

  return null;
}
