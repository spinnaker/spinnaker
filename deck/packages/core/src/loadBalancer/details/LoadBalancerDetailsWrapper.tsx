import { $templateCache } from 'ngimport';
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
import { AngularJSAdapter } from '../../reactShims';

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
  detailsTemplateUrl?: string;
  detailsController?: string;
  useDetailsHook?: UseDetailsHook<ILoadBalancer>;
  detailsActions?: FunctionComponent<ILoadBalancerActionsProps>;
  detailsSections?: Array<FunctionComponent<ILoadBalancerDetailsSectionProps>>;
}

const getDetailsTemplate = (provider: string): Promise<IDetailsTemplateState> =>
  Promise.all([
    CloudProviderRegistry.getValue(provider, 'loadBalancer.useDetailsHook'),
    CloudProviderRegistry.getValue(provider, 'loadBalancer.detailsActions'),
    CloudProviderRegistry.getValue(provider, 'loadBalancer.detailsSections'),
    CloudProviderRegistry.getValue(provider, 'loadBalancer.detailsTemplateUrl'),
    CloudProviderRegistry.getValue(provider, 'loadBalancer.detailsController'),
  ]).then(([useDetailsHook, detailsActions, detailsSections, templateUrl, detailsController]) => {
    const detailsTemplateUrl = templateUrl ? $templateCache.get<string>(templateUrl) : undefined;
    return {
      useDetailsHook,
      detailsActions,
      detailsSections,
      detailsTemplateUrl,
      detailsController,
    };
  });

function LoadBalancerDetailsWrapper({ app, loadBalancer }: ILoadBalancerDetailsWrapperProps) {
  const { provider } = loadBalancer;

  const { result: detailsTemplate } = useData<IDetailsTemplateState>(() => getDetailsTemplate(provider), {}, [
    provider,
  ]);

  const {
    useDetailsHook,
    detailsActions: DetailsActions,
    detailsSections,
    detailsTemplateUrl,
    detailsController,
  } = detailsTemplate;

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

  if (detailsTemplateUrl && detailsController) {
    // Angular rendering
    return (
      <AngularJSAdapter
        className="detail-content flex-container-h"
        template={detailsTemplateUrl}
        controller={`${detailsController} as ctrl`}
        locals={{ app, loadBalancer }}
      />
    );
  }

  return null;
}

export const LoadBalancerDetails = overridableComponent(LoadBalancerDetailsWrapper, 'loadBalancer.details');
