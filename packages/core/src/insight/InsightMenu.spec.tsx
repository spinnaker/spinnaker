import React from 'react';
import { mock } from 'angular';
import { ReactWrapper, mount } from 'enzyme';
import { IInsightMenuProps, IInsightMenuState, InsightMenu } from './InsightMenu';
import { IModalService } from 'angular-ui-bootstrap';
import { OverrideRegistry } from '../overrideRegistry/override.registry';
import { CacheInitializerService } from '../cache/cacheInitializer.service';
import { Button } from 'react-bootstrap';

beforeEach(() => {
  mock.module(($provide: any) => {
    $provide.value('$uibModal', {} as IModalService);
    $provide.value('overrideRegistry', new OverrideRegistry());
    $provide.value('cacheInitializer', {} as CacheInitializerService);
  });
});

describe('<InsightMenu />', () => {
  let component: ReactWrapper<IInsightMenuProps, IInsightMenuState>;

  function getNewMenu(params: object): ReactWrapper<IInsightMenuProps, any> {
    // Set defaults to zero so we only need to pass in the prop we want rendered
    const mergedParams = { ...{ createApp: false, createProject: false, refreshCaches: false }, ...params };
    return mount(
      <InsightMenu
        createApp={mergedParams.createApp}
        createProject={mergedParams.createProject}
        refreshCaches={mergedParams.refreshCaches}
      />,
    );
  }

  it('should only render create application button when initialized', () => {
    component = getNewMenu({ createApp: true });
    const btn = component.find(Button);

    expect(btn.length).toBe(1);
    expect(btn.text()).toEqual('Create Application');
    // Button should always be primary for create application
    // FIXME: when this project moves to v1+ of react-bootstrap this prop will need to change.
    expect(btn.prop('bsStyle')).toEqual('primary');
  });

  it('should only render create project button when initialized', () => {
    component = getNewMenu({ createProject: true });

    const btn = component.find(Button);

    expect(btn.length).toBe(1);
    expect(btn.text()).toEqual('Create Project');
    // If project is the only button rendered, it should be primary.
    // FIXME: when this project moves to v1+ of react-bootstrap this prop will need to change.
    expect(btn.prop('bsStyle')).toEqual('primary');
  });

  it('should only render refresh cache button when initialized', () => {
    // note: this test doesn't validate the state changes that could occur w/
    //       the refresh button in particular.
    component = getNewMenu({ refreshCaches: true });

    const btn = component.find(Button);

    expect(btn.length).toBe(1);
    expect(btn.text()).toMatch('Refresh');
  });

  it('should only render create application as primary when multiple buttons are rendered', () => {
    component = getNewMenu({ createApp: true, createProject: true });

    const btns = component.find(Button);
    const proj = btns.at(0);
    const app = btns.at(1);

    expect(btns.length).toBe(2);
    // Project button should be first
    expect(proj.text()).toEqual('Create Project');
    // FIXME: when this project moves to v1+ of react-bootstrap this prop will need to change.
    expect(proj.prop('bsStyle')).toEqual('default');
    // Application button should be second, so that it renders furthest to the right
    expect(app.text()).toEqual('Create Application');
    // FIXME: when this project moves to v1+ of react-bootstrap this prop will need to change.
    expect(app.prop('bsStyle')).toEqual('primary');
  });
});
