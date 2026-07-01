import { GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_LOADBALANCERSELECTOR_DIRECTIVE } from './loadBalancerSelector.directive';

describe('gceServerGroupLoadBalancerSelectorCtrl', () => {
  let $controller;

  beforeEach(window.module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_LOADBALANCERSELECTOR_DIRECTIVE));

  beforeEach(() => {
    window.inject((_$controller_) => {
      $controller = _$controller_;
    });
  });

  function buildController(loadBalancerType) {
    const ctrl = $controller('gceServerGroupLoadBalancerSelectorCtrl');
    ctrl.command = {
      loadBalancers: ['selected-lb'],
      backingData: {
        filtered: {
          loadBalancerIndex: {
            'selected-lb': {
              provider: 'gce',
              loadBalancerType,
            },
          },
        },
      },
    };
    return ctrl;
  }

  it('hides the load balancing policy selector for regional external network load balancers', () => {
    const ctrl = buildController('REGIONAL_EXTERNAL_NETWORK');

    expect(ctrl.showLoadBalancingPolicy()).toBe(false);
  });
});
