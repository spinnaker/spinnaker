import React from 'react';
import type { IKubernetesLoadBalancerDetailsSectionProps } from './IKubernetesLoadBalancerDetailsSectionProps';
import { AnnotationCustomSections } from '../../../manifest/AnnotationCustomSections';

export function LoadBalancerAnnotationCustomSection({ loadBalancer }: IKubernetesLoadBalancerDetailsSectionProps) {
  const { manifest } = loadBalancer;
  return <AnnotationCustomSections manifest={manifest.manifest} resource={loadBalancer} />;
}
