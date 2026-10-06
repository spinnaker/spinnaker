import { render, waitFor } from '@testing-library/react';
import React from 'react';

import { ApplicationsPickerInput } from './ApplicationsPickerInput';
import { ApplicationReader } from '../application';
import type { IValidator } from '../presentation';

describe('ApplicationsPickerInput', () => {
  function listApplicationsSpy() {
    return vi
      .spyOn(ApplicationReader, 'listApplications')
      .mockResolvedValue([{ name: 'app1' }, { name: 'app2' }] as any);
  }

  it('lists applications on mount', () => {
    const listApplications = listApplicationsSpy();

    render(<ApplicationsPickerInput />);

    expect(listApplications).toHaveBeenCalledTimes(1);
  });

  it('registers a validator that validates the selected application exists', async () => {
    listApplicationsSpy();
    const validation = {
      addValidator: vi.fn(),
      removeValidator: vi.fn(),
      revalidate: vi.fn(),
    };
    render(<ApplicationsPickerInput value="app1" validation={validation} />);

    await waitFor(() => expect(validation.addValidator).toHaveBeenCalled());
    const validator: IValidator = validation.addValidator.mock.lastCall[0];

    expect(validator('app1')).toBeFalsy();
    expect(validator('notexists')).toContain('notexists does not exist');
  });
});
