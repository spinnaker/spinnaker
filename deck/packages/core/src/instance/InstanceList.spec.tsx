import { Subject } from 'rxjs';

import { InstanceListComponent } from './InstanceList';
import { InstanceListBodyComponent } from './InstanceListBody';
import { setDirectRouter } from '../navigation/directRouter';
import { initialize } from '../state';

describe('InstanceList router context', () => {
  const serverGroup = {
    account: 'account',
    instances: [],
    name: 'server-group',
    region: 'region',
    stringVal: 'server-group',
    type: 'kubernetes',
  } as any;

  beforeEach(() => {
    initialize();
    const params = { instanceSort: 'launchTime', multiselect: false };
    setDirectRouter({
      globals: { params, params$: new Subject() },
      stateService: { params },
    } as any);
  });

  afterEach(() => setDirectRouter(null));

  it('initializes the list from injected route params', () => {
    const component = new InstanceListComponent({
      serverGroup,
      stateParams: { instanceSort: 'name', multiselect: true },
    } as any);

    expect(component.state.multiselect).toBe(true);
    expect(component.state.instanceSort).toBe('name');
  });

  it('initializes the list body from injected route params', () => {
    const component = new InstanceListBodyComponent({
      instances: [],
      serverGroup,
      stateParams: { instanceId: 'instance-id', instanceSort: 'name', multiselect: true },
    } as any);

    expect(component.state.activeInstanceId).toBe('instance-id');
    expect(component.state.multiselect).toBe(true);
    expect(component.state.instanceSort).toBe('name');
  });
});
