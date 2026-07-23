import { ClusterMaster } from '../cluster/ClusterMaster';
import { ClusterFilters } from '../cluster/filter/ClusterFilters';
import { MultipleServerGroupsDetails } from './details/MultipleServerGroupsDetails';
import { ServerGroupDetailsWrapper } from './details/ServerGroupDetailsWrapper';
import { getClustersState, getMultipleServerGroupsState, getServerGroupDetailsState } from './serverGroup.states';

describe('server group states', () => {
  it('uses React for the clusters insight route', () => {
    const state = getClustersState({ paramsToQuery: () => '', buildDynamicParams: () => ({}) } as any);

    expect(state.views.nav).toEqual(jasmine.objectContaining({ component: ClusterFilters, $type: 'react' }));
    expect(state.views.master).toEqual(jasmine.objectContaining({ component: ClusterMaster, $type: 'react' }));
  });

  it('uses React for single server group details', () => {
    const state = getServerGroupDetailsState();
    const view = state.views['detail@../insight'];

    expect(view).toEqual(jasmine.objectContaining({ component: ServerGroupDetailsWrapper, $type: 'react' }));
    expect(view.templateUrl).toBeUndefined();
    expect(view.controller).toBeUndefined();
    expect(view.controllerAs).toBeUndefined();
  });

  it('uses React for the multiple server groups detail route', () => {
    const state = getMultipleServerGroupsState();
    const view = state.views['detail@../insight'];

    expect(view).toEqual(jasmine.objectContaining({ component: MultipleServerGroupsDetails, $type: 'react' }));
    expect(view.templateUrl).toBeUndefined();
    expect(view.controller).toBeUndefined();
    expect(view.controllerAs).toBeUndefined();
  });
});
