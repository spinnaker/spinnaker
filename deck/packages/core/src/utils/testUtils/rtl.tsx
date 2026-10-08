import { render, within } from '@testing-library/react';
import type { RenderOptions, RenderResult } from '@testing-library/react';
import { pushStateLocationPlugin, UIRouter } from '@uirouter/react';
import React from 'react';
import type { ReactElement, ReactNode } from 'react';

const RouterWrapper = ({ children }: { children?: ReactNode }) => (
  <UIRouter plugins={[pushStateLocationPlugin]}>{children}</UIRouter>
);

export function renderWithRouter(ui: ReactElement, options: Omit<RenderOptions, 'wrapper'> = {}): RenderResult {
  return render(ui, { ...options, wrapper: RouterWrapper });
}

export function getFormGroupByLabel(label: string, root: HTMLElement = document.body): HTMLElement {
  const labelNode = within(root).getByText(label, { selector: '.label-text' });
  const formGroup = labelNode.closest('.form-group');
  if (!(formGroup instanceof HTMLElement)) {
    throw new Error(`No form group found for label "${label}"`);
  }
  return formGroup;
}
