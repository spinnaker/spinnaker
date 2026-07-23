import { MultipleInstancesDetails } from './details/MultipleInstancesDetails';
import { StandaloneInstanceDetails } from './details/StandaloneInstanceDetails';
import { getMultipleInstancesState, getStandaloneInstanceState } from './instance.states';

describe('instance states', () => {
  it('uses the React standalone instance details wrapper for standalone instance routes', () => {
    const state = getStandaloneInstanceState();

    expect(state.views['main@']).toEqual(
      jasmine.objectContaining({
        component: StandaloneInstanceDetails,
        $type: 'react',
      }),
    );
    expect(state.views['main@'].templateUrl).toBeUndefined();
    expect(state.views['main@'].controllerProvider).toBeUndefined();
  });

  it('includes the provider in standalone instance route params', () => {
    const state = getStandaloneInstanceState();
    const resolve = state.resolve.instance as any[];
    const resolveInstance = resolve[resolve.length - 1];

    expect(
      resolveInstance({
        account: 'prod',
        instanceId: 'i-abc',
        provider: 'aws',
        region: 'us-west-2',
      }),
    ).toEqual({
      account: 'prod',
      instanceId: 'i-abc',
      noApplication: true,
      provider: 'aws',
      region: 'us-west-2',
    });
  });

  it('uses React for the multiple instances detail route', () => {
    const state = getMultipleInstancesState();
    const view = state.views['detail@../insight'];

    expect(view).toEqual(jasmine.objectContaining({ component: MultipleInstancesDetails, $type: 'react' }));
    expect(view.templateUrl).toBeUndefined();
    expect(view.controller).toBeUndefined();
    expect(view.controllerAs).toBeUndefined();
  });
});
