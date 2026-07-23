import { shallow } from 'enzyme';
import React from 'react';

import type { Application } from '../../application';
import { ServerGroupDetails } from './ServerGroupDetails';
import { ServerGroupDetailsWrapper } from './ServerGroupDetailsWrapper';

describe('ServerGroupDetailsWrapper', () => {
  const app = { serverGroups: {} } as Application;
  const serverGroup = {
    accountId: 'test',
    name: 'deck-v001',
    region: 'us-east-1',
  };

  it('renders React server group details when provider React config is available', () => {
    const Actions = () => <button />;
    const Section = () => <div />;
    const detailsGetter = jasmine.createSpy('detailsGetter');
    const component = shallow(<ServerGroupDetailsWrapper app={app} serverGroup={serverGroup} />, {
      disableLifecycleMethods: true,
    });

    component.setState({ Actions, detailsGetter, sections: [Section] });

    expect(component.find(ServerGroupDetails).prop('app')).toBe(app);
    expect(component.find(ServerGroupDetails).prop('serverGroup')).toBe(serverGroup);
    expect(component.find(ServerGroupDetails).prop('Actions')).toBe(Actions);
    expect(component.find(ServerGroupDetails).prop('detailsGetter')).toBe(detailsGetter);
    expect(component.find(ServerGroupDetails).prop('sections')).toEqual([Section]);
  });

  it('renders a migration-required message for legacy template/controller-only server group details', () => {
    const component = shallow(<ServerGroupDetailsWrapper app={app} serverGroup={serverGroup} />, {
      disableLifecycleMethods: true,
    });

    component.setState({ Actions: undefined, detailsGetter: undefined, legacyDetailsConfigured: true, sections: [] });

    expect(component.text()).toContain('Server group details must be migrated to React.');
  });

  it('renders nothing when provider server group details config is missing', () => {
    const component = shallow(<ServerGroupDetailsWrapper app={app} serverGroup={serverGroup} />, {
      disableLifecycleMethods: true,
    });

    component.setState({ Actions: undefined, detailsGetter: undefined, legacyDetailsConfigured: false, sections: [] });

    expect(component.isEmptyRender()).toBe(true);
  });
});
