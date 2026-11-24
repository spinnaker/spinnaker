import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import { ThemeMenu } from './ThemeMenu';
import { THEME_ATTRIBUTE, THEME_STORAGE_KEY } from '../presentation/theme';

describe('ThemeMenu', () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.removeAttribute(THEME_ATTRIBUTE);
  });

  it('switches and persists the theme from the menu', () => {
    render(<ThemeMenu />);

    fireEvent.click(screen.getByRole('button', { name: 'Theme' }));
    fireEvent.click(screen.getByText('Dark'));

    expect(document.documentElement.getAttribute(THEME_ATTRIBUTE)).toBe('dark');
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
  });
});
