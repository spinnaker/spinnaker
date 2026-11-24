import React from 'react';

import { screen } from '@testing-library/react';

import { renderHookHarness } from './hookHarness';
import { getFormGroupByLabel, renderWithRouter } from './rtl';

describe('RTL test utilities', () => {
  it('renders children with a UI Router', () => {
    renderWithRouter(<p>routed content</p>);
    expect(screen.getByText('routed content')).toBeInTheDocument();
  });

  it('finds the form group containing a legacy StageConfigField label', () => {
    renderWithRouter(
      <div className="form-group">
        <label>
          <span className="label-text">Account </span>
        </label>
        <input />
      </div>,
    );
    expect(getFormGroupByLabel('Account')).toHaveClass('form-group');
  });

  it('fails clearly when a legacy field label is missing', () => {
    renderWithRouter(<div />);
    expect(() => getFormGroupByLabel('Missing')).toThrow(/Unable to find an element/);
  });

  it('rerenders a hook with new props', () => {
    const hook = ({ value }: { value: string }) => React.useMemo(() => value.toUpperCase(), [value]);
    const rendered = renderHookHarness(hook, { value: 'first' });
    expect(rendered.result.current).toBe('FIRST');
    rendered.rerenderHook({ value: 'second' });
    expect(rendered.result.current).toBe('SECOND');
  });
});
