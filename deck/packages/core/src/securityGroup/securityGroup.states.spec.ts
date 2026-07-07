import { StandaloneSecurityGroupDetails } from './StandaloneSecurityGroupDetails';
import { getStandaloneFirewallState } from './securityGroup.states';

describe('security group states', () => {
  it('uses the React standalone security group details wrapper for standalone firewall routes', () => {
    const state = getStandaloneFirewallState();

    expect(state.views['main@']).toEqual(
      jasmine.objectContaining({
        component: StandaloneSecurityGroupDetails,
        $type: 'react',
      }),
    );
    expect(state.views['main@'].templateUrl).toBeUndefined();
    expect(state.views['main@'].controllerProvider).toBeUndefined();
  });
});
