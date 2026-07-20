export function openLoadBalancerModal(
  config: any,
  modalService: any,
  { application, loadBalancer, isNew, forPipelineConfig }: any,
) {
  if (config.CreateLoadBalancerModal && config.CreateLoadBalancerModal.supportsPipelineConfig) {
    return config.CreateLoadBalancerModal.show({
      app: application,
      application,
      loadBalancer,
      isNew,
      forPipelineConfig,
    });
  }

  return modalService.open({
    templateUrl: config.createLoadBalancerTemplateUrl,
    controller: `${config.createLoadBalancerController} as ctrl`,
    size: 'lg',
    resolve: {
      application: () => application,
      loadBalancer: () => loadBalancer,
      isNew: () => isNew,
      forPipelineConfig: () => forPipelineConfig,
    },
  }).result;
}
