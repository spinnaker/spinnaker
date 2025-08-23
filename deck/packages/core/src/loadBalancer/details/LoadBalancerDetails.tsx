import { useRouter } from '@uirouter/react';
import React, { useEffect, useState } from 'react';

import type { ILoadBalancerDetailsProps } from './LoadBalancerDetailsWrapper';
import { CloudProviderLogo } from '../../cloudProvider';
import { EntityNotifications } from '../../entityTag/notifications/EntityNotifications';
import { Details } from '../../presentation';
import { IfFeatureEnabled } from '../../utils';

export function LoadBalancerDetailsContent({
  app,
  loadBalancer: params,
  useDetails,
  Actions,
  sections,
}: ILoadBalancerDetailsProps) {
  const [initialized, setInitialized] = useState(false);
  const {
    stateService: { go },
  } = useRouter();
  const autoClose = () => {
    go('^', { allowModalToStayOpen: true }, { location: 'replace' });
  };
  const { data: loadBalancer, loading } = useDetails({ app, loadBalancerParams: params, autoClose });

  useEffect(() => {
    if (loadBalancer) {
      setInitialized(true);
    }
  }, [loading]);

  if (!initialized) return <Details loading={loading} />;

  return (
    <Details loading={loading}>
      <Details.Header
        icon={<CloudProviderLogo provider={params.provider} height="36px" width="36px" />}
        name={loadBalancer.displayName ? loadBalancer.displayName : loadBalancer.name}
        notifications={
          <IfFeatureEnabled
            feature="entityTags"
            render={
              <EntityNotifications
                entity={loadBalancer}
                application={app}
                placement="bottom"
                hOffsetPercent="90%"
                entityType="loadBalancer"
                pageLocation="details"
                onUpdate={() => app.loadBalancers.refresh()}
              />
            }
          />
        }
        actions={<Actions key="actions" app={app} loadBalancer={loadBalancer} />}
      />
      <Details.Content loading={loading}>
        {sections.map((Section, index) => (
          <Section key={index} app={app} loadBalancer={loadBalancer} />
        ))}
      </Details.Content>
    </Details>
  );
}
