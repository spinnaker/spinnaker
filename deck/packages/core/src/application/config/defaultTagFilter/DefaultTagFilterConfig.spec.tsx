import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import type { IDefaultTagFilterConfig } from './DefaultTagFilterConfig';
import { DefaultTagFilterConfig } from './DefaultTagFilterConfig';
import { noop } from '../../../utils';

describe('<DefaultTagFilterConfig />', () => {
  let tagConfigs: IDefaultTagFilterConfig[];
  let container: HTMLElement;

  beforeEach(() => {
    tagConfigs = getTestDefaultFilterTagConfigs();
    ({ container } = render(
      <DefaultTagFilterConfig
        defaultTagFilterConfigs={tagConfigs}
        isSaving={false}
        saveError={false}
        updateDefaultTagFilterConfigs={noop}
      />,
    ));
  });

  describe('view', () => {
    it('renders a row for each banner config', () => {
      expect(container.querySelectorAll('.default-filter-config-row')).toHaveLength(tagConfigs.length);
    });
    it('renders an "add" button', () => {
      expect(screen.getByRole('button', { name: /add default filter/i })).toBeInTheDocument();
    });
  });

  describe('functionality', () => {
    it('update default tag filter config', () => {
      const textareas = screen.getAllByRole('textbox');
      fireEvent.change(textareas[1], { target: { value: 'hello' } });
      expect(textareas[1]).toHaveValue('hello');
      expect(textareas[3]).toHaveValue(tagConfigs[1].tagValue);
    });
    it('add default filter tag config', () => {
      fireEvent.click(screen.getByRole('button', { name: /add default filter/i }));
      expect(container.querySelectorAll('.default-filter-config-row')).toHaveLength(3);
      expect(screen.getByDisplayValue(/name of the tag/i)).toBeInTheDocument();
    });
    it('remove default filter tag config', () => {
      fireEvent.click(screen.getByRole('button', { name: 'Remove default filter 2' }));
      expect(container.querySelectorAll('.default-filter-config-row')).toHaveLength(1);
      expect(screen.queryByDisplayValue('Repair Pipelines')).not.toBeInTheDocument();
    });
  });
});

export function getTestDefaultFilterTagConfigs(): IDefaultTagFilterConfig[] {
  return [
    {
      tagName: 'Pipeline Type',
      tagValue: 'Deployment Pipelines',
    },
    {
      tagName: 'Pipeline Type',
      tagValue: 'Repair Pipelines',
    },
  ];
}
