import type { FunctionComponent } from 'react';
import React from 'react';

import { LoadBalancerDetailsContent } from './LoadBalancerDetails';
import type { Application } from '../../application';
import { CloudProviderRegistry } from '../../cloudProvider';
import type { ILoadBalancer } from '../../domain';
import type { ILoadBalancerStateParams } from '../loadBalancer.states';
import type { IOverridableProps } from '../../overrideRegistry';
import { overridableComponent } from '../../overrideRegistry';
import { useData } from '../../presentation';

export interface ILoadBalancerDetailsWrapperProps extends IOverridableProps {
  app: Application;
  loadBalancer: ILoadBalancerStateParams;
}

export interface UseDetailsResult<T> {
  data: T | undefined;
  loading: boolean;
  error: string | null;
  refetch: () => Promise<void>;
}

export interface IUseDetailsHookProps {
  app: Application;
  loadBalancerParams: ILoadBalancerStateParams;
  autoClose: () => void;
}

export type UseDetailsHook<T> = (props: IUseDetailsHookProps) => UseDetailsResult<T>;

export interface ILoadBalancerDetailsSectionProps {
  app: Application;
  loadBalancer: ILoadBalancer;
}

export interface ILoadBalancerActionsProps {
  app: Application;
  loadBalancer: ILoadBalancer;
}

export interface ILoadBalancerDetailsProps extends ILoadBalancerDetailsWrapperProps {
  useDetails: UseDetailsHook<ILoadBalancer>;
  Actions: FunctionComponent<ILoadBalancerActionsProps>;
  sections: Array<FunctionComponent<ILoadBalancerDetailsSectionProps>>;
}

interface IDetailsTemplateState {
  useDetailsHook?: UseDetailsHook<ILoadBalancer>;
  detailsActions?: FunctionComponent<ILoadBalancerActionsProps>;
  detailsSections?: Array<FunctionComponent<ILoadBalancerDetailsSectionProps>>;
  legacyDetailsConfigured?: boolean;
}

const getDetailsTemplate = (provider: string): Promise<IDetailsTemplateState> =>
  Promise.all([
    CloudProviderRegistry.getValue(provider, 'loadBalancer.useDetailsHook'),
    CloudProviderRegistry.getValue(provider, 'loadBalancer.detailsActions'),
    CloudProviderRegistry.getValue(provider, 'loadBalancer.detailsSections'),
    CloudProviderRegistry.getValue(provider, 'loadBalancer.detailsTemplateUrl'),
    CloudProviderRegistry.getValue(provider, 'loadBalancer.detailsController'),
  ]).then(([useDetailsHook, detailsActions, detailsSections, templateUrl, detailsController]) => {
    return {
      useDetailsHook,
      detailsActions,
      detailsSections,
      legacyDetailsConfigured: !!(templateUrl && detailsController),
    };
  });

export function LoadBalancerDetailsWrapper({ app, loadBalancer }: ILoadBalancerDetailsWrapperProps) {
  const { provider } = loadBalancer;

  const { result: detailsTemplate } = useData<IDetailsTemplateState>(() => getDetailsTemplate(provider), {}, [
    provider,
  ]);

  const { useDetailsHook, detailsActions: DetailsActions, detailsSections, legacyDetailsConfigured } = detailsTemplate;

  if (useDetailsHook && DetailsActions && detailsSections) {
    // React rendering
    return (
      <LoadBalancerDetailsContent
        app={app}
        loadBalancer={loadBalancer}
        useDetails={useDetailsHook}
        Actions={DetailsActions}
        sections={detailsSections}
      />
    );
  }

  if (legacyDetailsConfigured) {
    return (
      <div className="alert alert-warning">
        Load balancer details for {provider} must be migrated to React. AngularJS templates/controllers are no longer
        supported.
      </div>
    );
  }

  return null;
}

export const LoadBalancerDetails = overridableComponent(LoadBalancerDetailsWrapper, 'loadBalancer.details');
