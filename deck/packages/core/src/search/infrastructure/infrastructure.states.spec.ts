import { shallow } from 'enzyme';
import React from 'react';

import { SETTINGS } from '../../config/settings';
import { configureRouter } from '../../navigation/router';
import { SpinErrorBoundary } from '../../presentation';
import { SearchV1 } from './SearchV1';
import { SearchV2 } from './SearchV2';

import './infrastructure.states';

describe('infrastructure states', () => {
  const originalSearchVersion = SETTINGS.searchVersion;

  afterEach(() => {
    SETTINGS.searchVersion = originalSearchVersion;
  });

  it('registers direct V1 search and its one-shot route parameter', () => {
    SETTINGS.searchVersion = 1;
    const router = configureRouter();
    const searchState = router.stateRegistry.get('home.search');
    const view = searchState.views['main@'];
    const errorBoundary = shallow(React.createElement(view.component));

    expect(errorBoundary.type()).toBe(SpinErrorBoundary);
    expect(errorBoundary.prop('children').type).toBe(SearchV1);
    expect(view.$type).toBe('react');
    expect(view.controller).toBeUndefined();
    expect(view.template).toBeUndefined();
    expect(view.templateUrl).toBeUndefined();
    expect(searchState.url).toContain('&route');
    expect(searchState.params.route.dynamic).toBe(true);
  });

  it('registers direct V2 search when configured', () => {
    SETTINGS.searchVersion = 2;
    const router = configureRouter();
    const searchState = router.stateRegistry.get('home.search');
    const view = searchState.views['main@'];
    const errorBoundary = shallow(React.createElement(view.component));

    expect(errorBoundary.type()).toBe(SpinErrorBoundary);
    expect(errorBoundary.prop('children').type).toBe(SearchV2);
    expect(view.$type).toBe('react');
    expect(view.controller).toBeUndefined();
    expect(view.template).toBeUndefined();
    expect(view.templateUrl).toBeUndefined();
  });
});
