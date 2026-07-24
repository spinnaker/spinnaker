import type { DeckRuntimeServices } from '../../../../bootstrap/DeckRuntimeServices';
import type { ICloudProviderConfig } from '../../../../cloudProvider';

export const hasPipelineLoadBalancerModal = (provider: ICloudProviderConfig): boolean => {
  return Boolean(provider?.loadBalancer?.CreateLoadBalancerModal?.supportsPipelineConfig);
};

export function openLoadBalancerModal(
  config: any,
  { application, loadBalancer, isNew, forPipelineConfig }: any,
  runtimeServices: DeckRuntimeServices,
) {
  if (config.CreateLoadBalancerModal && config.CreateLoadBalancerModal.supportsPipelineConfig) {
    return config.CreateLoadBalancerModal.show(
      {
        app: application,
        application,
        loadBalancer,
        isNew,
        forPipelineConfig,
      },
      runtimeServices,
    );
  }

  return Promise.reject(new Error('No React create load balancer modal is registered with pipeline support.'));
}
