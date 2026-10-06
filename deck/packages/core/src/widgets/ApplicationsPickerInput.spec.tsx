import { mount } from 'enzyme';
import React from 'react';

import { ApplicationsPickerInput } from './ApplicationsPickerInput';
import { ApplicationReader } from '../application';
import type { IValidator } from '../presentation';

describe('ApplicationsPickerInput', () => {
  function listApplicationsSpy() {
    return vi.spyOn(ApplicationReader, 'listApplications').mockImplementation(() => {
      return Promise.resolve([{ name: 'app1' }, { name: 'app2' }]);
    });
  }

  function asyncTick() {
    return new Promise((resolve) => setTimeout(resolve));
  }

  it('lists applications on mount', () => {
    const spy = listApplicationsSpy();
    mount(<ApplicationsPickerInput />);
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('registers a validator that validates the selected application exists', async () => {
    listApplicationsSpy();
    const validationSpy = {
      addValidator: vi.fn(),
      removeValidator: vi.fn(),
      revalidate: vi.fn(),
    };
    mount(<ApplicationsPickerInput value={'app1'} validation={validationSpy} />);
    await asyncTick(); // let the listApplications promise resolve

    expect(validationSpy.addValidator).toHaveBeenCalledTimes(1);
    const validator: IValidator = validationSpy.addValidator.mock.lastCall[0];

    expect(validator('app1')).toBeFalsy();
    expect(validator('notexists')).toContain('notexists does not exist');
  });
});
