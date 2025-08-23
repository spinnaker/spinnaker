import React from 'react';
import type { IKubernetesLoadBalancerDetailsSectionProps } from './IKubernetesLoadBalancerDetailsSectionProps';
import {KubernetesReactInjector} from "../../../reactShims";

export function LoadBalancerAnnotationCustomSection({loadBalancer}: IKubernetesLoadBalancerDetailsSectionProps) {
  const { manifest } = loadBalancer;
  const { KubernetesAnnotationCustomSections } = KubernetesReactInjector;
  return <KubernetesAnnotationCustomSections manifest={manifest.manifest} resource={loadBalancer} />;
}
