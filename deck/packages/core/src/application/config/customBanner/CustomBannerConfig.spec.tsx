import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import type { ICustomBannerConfig } from './CustomBannerConfig';
import { CustomBannerConfig } from './CustomBannerConfig';
import { noop } from '../../../utils';

describe('<CustomBannerConfig />', () => {
  let bannerConfigs: ICustomBannerConfig[];
  let container: HTMLElement;

  beforeEach(() => {
    bannerConfigs = getTestBannerConfigs();
    ({ container } = render(
      <CustomBannerConfig
        bannerConfigs={bannerConfigs}
        isSaving={false}
        saveError={false}
        updateBannerConfigs={noop}
      />,
    ));
  });

  describe('view', () => {
    it('renders a row for each banner config', () => {
      expect(container.querySelectorAll('.custom-banner-config-row')).toHaveLength(bannerConfigs.length);
    });
    it('renders an "add" button', () => {
      expect(screen.getByRole('button', { name: /add banner/i })).toBeInTheDocument();
    });
  });

  describe('functionality', () => {
    it('update banner config', () => {
      const checkboxes = screen.getAllByRole('checkbox');
      expect(checkboxes[0]).toBeChecked();
      expect(checkboxes[1]).not.toBeChecked();
      fireEvent.click(checkboxes[1]);
      expect(checkboxes[0]).not.toBeChecked();
      expect(checkboxes[1]).toBeChecked();
    });
    it('add banner config', () => {
      fireEvent.click(screen.getByRole('button', { name: /add banner/i }));
      expect(container.querySelectorAll('.custom-banner-config-row')).toHaveLength(3);
      expect(screen.getByDisplayValue('Your custom banner text')).toBeInTheDocument();
    });
    it('remove banner config', () => {
      fireEvent.click(screen.getByRole('button', { name: 'Remove banner 2' }));
      expect(container.querySelectorAll('.custom-banner-config-row')).toHaveLength(1);
      expect(screen.queryByDisplayValue(/production freeze/i)).not.toBeInTheDocument();
    });
  });
});

export function getTestBannerConfigs(): ICustomBannerConfig[] {
  return [
    {
      backgroundColor: 'var(--color-alert)',
      enabled: true,
      text: 'Warning: currently in maintenance mode',
      textColor: 'var(--color-text-on-dark)',
    },
    {
      backgroundColor: 'var(--color-alert)',
      enabled: false,
      text: 'Warning: currently in production freeze',
      textColor: 'var(--color-text-on-dark)',
    },
  ];
}
