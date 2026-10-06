import { fireEvent, render, screen, within } from '@testing-library/react';
import { setupUser } from '../../../../../core/src/utils/testUtils/userEvent';
import type { Mock } from 'vitest';
import React from 'react';

import { getFormGroupByLabel } from '../../../../../core/src/utils/testUtils/rtl';

import type { IDeleteManifestOptionsFormProps } from './DeleteManifestOptionsForm';
import DeleteManifestOptionsForm from './DeleteManifestOptionsForm';

describe('<DeleteManifestOptionsForm />', () => {
  let onChangeSpy: Mock;
  let props: IDeleteManifestOptionsFormProps;
  beforeEach(() => {
    onChangeSpy = vi.fn();
    props = {
      onOptionsChange: onChangeSpy,
      options: {
        cascading: true,
        gracePeriodSeconds: 60,
      },
    };
    render(<DeleteManifestOptionsForm {...props} />);
  });

  describe('view', () => {
    it('renders Cascading and Grace Period options', () => {
      expect(screen.getByText('Cascading', { selector: '.label-text' })).toBeInTheDocument();
      expect(screen.getByText('Grace Period', { selector: '.label-text' })).toBeInTheDocument();
    });
  });
  describe('functionality', () => {
    it('calls `props.onOptionsChange` when cascading is toggled', async () => {
      const user = setupUser();
      const cascading = within(getFormGroupByLabel('Cascading')).getByRole('checkbox');

      await user.click(cascading);
      expect(onChangeSpy).toHaveBeenCalledWith({
        cascading: false,
        gracePeriodSeconds: 60,
      });
      expect(cascading).not.toBeChecked();

      await user.click(cascading);
      expect(onChangeSpy).toHaveBeenCalledWith({
        cascading: true,
        gracePeriodSeconds: 60,
      });
      expect(cascading).toBeChecked();
    });
    it('calls `props.onOptionsChange` when grace period is changed', () => {
      const gracePeriod = within(getFormGroupByLabel('Grace Period')).getByRole('spinbutton');

      fireEvent.change(gracePeriod, { target: { value: '0' } });
      expect(onChangeSpy).toHaveBeenCalledWith({
        cascading: true,
        gracePeriodSeconds: 0,
      });
      fireEvent.change(gracePeriod, { target: { value: '100' } });
      expect(onChangeSpy).toHaveBeenCalledWith({
        cascading: true,
        gracePeriodSeconds: 100,
      });
      fireEvent.change(gracePeriod, { target: { value: '' } });
      expect(onChangeSpy).toHaveBeenCalledWith({
        cascading: true,
        gracePeriodSeconds: null,
      });
    });
  });
});
