import { getApplicationStateRegistrationsForTests } from '../application/applicationState.registration';
import type { INestedState } from '../navigation/state.provider';

describe('loadBalancer.states', () => {
  it('registers loadBalancerType on load balancer detail routes', () => {
    const stateConfigProvider = {
      buildDynamicParams: () => ({}),
      paramsToQuery: () => '',
    };
    let loadBalancerDetails: INestedState | undefined;

    for (const registration of getApplicationStateRegistrationsForTests()) {
      const detailStates: INestedState[] = [];
      const provider = {
        addChildState: () => undefined,
        addInsightState: () => undefined,
        addInsightDetailState: (state: INestedState) => detailStates.push(state),
        addParentState: () => undefined,
      };
      try {
        registration(provider as any, stateConfigProvider as any);
      } catch {
        continue;
      }
      loadBalancerDetails = detailStates.find((state) => state.name === 'loadBalancerDetails');
      if (loadBalancerDetails) {
        break;
      }
    }

    expect(loadBalancerDetails?.params?.loadBalancerType).toEqual({
      squash: true,
      value: null,
    });
    expect(loadBalancerDetails?.resolve?.loadBalancer?.[1].toString()).toContain('loadBalancerType');
  });
});
