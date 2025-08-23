import type { ILoadBalancerDetailsSectionProps } from '@spinnaker/core';
import type { IKubernetesLoadBalancerView } from '../../../interfaces';

export interface IKubernetesLoadBalancerDetailsSectionProps extends ILoadBalancerDetailsSectionProps {
  loadBalancer: IKubernetesLoadBalancerView;
}
