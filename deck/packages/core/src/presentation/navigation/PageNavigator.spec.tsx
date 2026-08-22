import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import { mount } from 'enzyme';
import $ from 'jquery';
import React from 'react';

import { PageNavigator } from './PageNavigator';
import { PageSection } from './PageSection';

describe('PageNavigator', () => {
  let host: HTMLElement;
  let router: UIRouterReact;

  beforeEach(() => {
    router = new UIRouterReact();
    host = document.createElement('div');
    document.body.appendChild(host);
  });

  afterEach(() => {
    $.fx.off = false;
    router.dispose();
    host.remove();
  });

  it('scrolls to the selected section in direct React usage', () => {
    $.fx.off = true;
    const wrapper = mount(
      <UIRouterContext.Provider value={router}>
        <div className="container" style={{ height: 60, overflowY: 'scroll' }}>
          <PageNavigator scrollableContainer=".container">
            <PageSection pageKey="one" label="One">
              <div style={{ height: 100 }} />
            </PageSection>
            <PageSection pageKey="two" label="Two">
              <div style={{ height: 100 }} />
            </PageSection>
          </PageNavigator>
        </div>
      </UIRouterContext.Provider>,
      { attachTo: host },
    );
    wrapper.update();

    const secondNavigationLink = wrapper.find('.page-navigation a').at(1);
    expect(secondNavigationLink.exists()).toBe(true);
    expect(() => secondNavigationLink.simulate('click')).not.toThrow();
    expect(host.querySelector('[data-page-id="two"]').classList.contains('highlighted')).toBe(true);

    wrapper.unmount();
  });

  it('loads navigation styles for direct React usage', () => {
    const wrapper = mount(
      <UIRouterContext.Provider value={router}>
        <div className="container" style={{ height: 60, overflowY: 'scroll' }}>
          <PageNavigator scrollableContainer=".container">
            <PageSection pageKey="one" label="One">
              <div style={{ height: 100 }} />
            </PageSection>
          </PageNavigator>
        </div>
      </UIRouterContext.Provider>,
      { attachTo: host },
    );
    wrapper.update();

    const navigation = wrapper.find('.page-navigation').getDOMNode<HTMLElement>();
    const heading = wrapper.find('h4.sticky-header').getDOMNode<HTMLElement>();

    expect(window.getComputedStyle(navigation).listStyleType).toBe('none');
    expect(window.getComputedStyle(navigation).textTransform).toBe('uppercase');
    expect(window.getComputedStyle(heading).paddingTop).toBe('10px');

    wrapper.unmount();
  });
});
