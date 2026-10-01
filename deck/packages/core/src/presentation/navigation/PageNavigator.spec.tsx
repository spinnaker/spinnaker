import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import $ from 'jquery';
import React from 'react';

import { PageNavigator } from './PageNavigator';
import { PageSection } from './PageSection';

describe('PageNavigator', () => {
  let router: UIRouterReact;

  beforeEach(() => {
    router = new UIRouterReact();
  });

  afterEach(() => {
    $.fx.off = false;
    router.dispose();
  });

  function renderNavigator() {
    return render(
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
    );
  }

  it('scrolls to the selected section in direct React usage', async () => {
    $.fx.off = true;
    const { container } = renderNavigator();

    const navigationControl = await screen.findByRole('button', { name: 'Two' });
    expect(screen.getByRole('button', { name: 'One' })).toHaveAttribute('aria-current', 'location');
    expect(navigationControl).not.toHaveAttribute('aria-current');
    navigationControl.focus();
    expect(navigationControl).toHaveFocus();
    await userEvent.keyboard('{Enter}');

    expect(container.querySelector('[data-page-id="two"]')).toHaveClass('highlighted');
    expect(screen.getByRole('button', { name: 'One' })).not.toHaveAttribute('aria-current');
    expect(navigationControl).toHaveAttribute('aria-current', 'location');
  });

  it('loads navigation styles for direct React usage', async () => {
    const { container } = renderNavigator();
    const navigation = await screen.findByRole('list');
    const heading = container.querySelector('h4.sticky-header');
    const accentColor = window.getComputedStyle(document.documentElement).getPropertyValue('--color-accent').trim();

    expect(window.getComputedStyle(navigation).listStyleType).toBe('none');
    expect(window.getComputedStyle(navigation).textTransform).toBe('uppercase');
    expect(window.getComputedStyle(heading).paddingTop).toBe('10px');
    expect(window.getComputedStyle(screen.getByRole('button', { name: 'Two' })).color).toBe(accentColor);
  });
});
