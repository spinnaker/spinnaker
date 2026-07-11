import React from 'react';

import type { Application } from '../../application';
import { CloudProviderRegistry } from '../../cloudProvider';
import type { IMoniker } from '../../naming';
import { AngularJSAdapter } from '../../reactShims';

export interface IStandaloneInstance {
  account: string;
  instanceId: string;
  noApplication: boolean;
  provider: string;
  region: string;
}

export interface IStandaloneInstanceDetailsProps {
  app: Application;
  environment?: string;
  instance: IStandaloneInstance;
  moniker?: IMoniker;
  overrides?: any;
}

type ProviderInstanceDetailsComponent = React.ComponentType<IStandaloneInstanceDetailsProps>;

function StandaloneDetailsLayout({ children }: { children: React.ReactNode }): JSX.Element {
  return (
    <div className="standalone">
      <div className="col-md-6 col-md-offset-3">{children}</div>
    </div>
  );
}

export function StandaloneInstanceDetails(props: IStandaloneInstanceDetailsProps): JSX.Element | null {
  const provider = props.instance.provider;
  const DetailsComponent = CloudProviderRegistry.getValue(
    provider,
    'instance.details',
  ) as ProviderInstanceDetailsComponent;

  if (DetailsComponent) {
    return (
      <StandaloneDetailsLayout>
        <DetailsComponent {...props} />
      </StandaloneDetailsLayout>
    );
  }

  const templateUrl = CloudProviderRegistry.getValue(provider, 'instance.detailsTemplateUrl');
  const controller = CloudProviderRegistry.getValue(provider, 'instance.detailsController');

  if (templateUrl && controller) {
    return (
      <StandaloneDetailsLayout>
        <AngularJSAdapter templateUrl={templateUrl} controller={`${controller} as ctrl`} locals={props} />
      </StandaloneDetailsLayout>
    );
  }

  return null;
}
