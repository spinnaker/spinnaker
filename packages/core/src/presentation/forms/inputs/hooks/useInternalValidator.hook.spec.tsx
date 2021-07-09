import React from 'react';
import { mount } from 'enzyme';
import { IValidator } from '../../../forms/validation';
import { IFormInputProps, IFormInputValidation } from '../interface';
import { useInternalValidator } from './useInternalValidator.hook';

function TestInputComponent(props: IFormInputProps & { validator?: IValidator; revalidateDeps?: any[] }) {
  const { validator = () => null as string, validation, revalidateDeps = [], ...rest } = props;
  useInternalValidator(validation, validator, revalidateDeps);
  return <input type="text" {...rest} />;
}

interface IFormInputValidationMock extends IFormInputValidation {
  revalidate: jasmine.Spy & IFormInputValidation['revalidate'];
  addValidator: jasmine.Spy & IFormInputValidation['addValidator'];
  removeValidator: jasmine.Spy & IFormInputValidation['removeValidator'];
}

function validationMock(): IFormInputValidationMock {
  return {
    touched: true,
    hidden: false,
    category: null,
    messageNode: null,
    revalidate: jasmine.createSpy('validation.revalidate'),
    addValidator: jasmine.createSpy('validation.addValidator'),
    removeValidator: jasmine.createSpy('validation.removeValidator'),
  };
}

describe('useInternalValidator', () => {
  it('should call addValidator once when mounted', () => {
    const validation = validationMock();

    mount(<TestInputComponent validation={validation} />);
    expect(validation.addValidator).toHaveBeenCalledTimes(1);
    expect(validation.removeValidator).toHaveBeenCalledTimes(0);
  });

  it('should call revalidate when the deps list changes', () => {
    const validation = validationMock();

    const component = mount(<TestInputComponent validation={validation} revalidateDeps={['a', 'b']} />);
    expect(validation.revalidate).toHaveBeenCalledTimes(0);

    component.setProps({ revalidateDeps: ['c', 'd'] });
    expect(validation.revalidate).toHaveBeenCalledTimes(1);
  });

  it('should call removeValidator when unmounted', () => {
    const validation = validationMock();

    const component = mount(<TestInputComponent validation={validation} />);
    expect(validation.addValidator).toHaveBeenCalledTimes(1);
    expect(validation.removeValidator).toHaveBeenCalledTimes(0);

    component.unmount();
    expect(validation.addValidator).toHaveBeenCalledTimes(1);
    expect(validation.removeValidator).toHaveBeenCalledTimes(1);
  });

  it('should call removeValidator with the same validator object reference', () => {
    const validation = validationMock();

    let addedValidator: any, removedValidator: any;
    validation.addValidator.and.callFake((arg: any) => (addedValidator = arg));
    validation.removeValidator.and.callFake((arg: any) => (removedValidator = arg));

    const component = mount(<TestInputComponent validation={validation} />);
    component.unmount();
    expect(validation.addValidator).toHaveBeenCalledTimes(1);
    expect(validation.removeValidator).toHaveBeenCalledTimes(1);
    expect(addedValidator).toBe(removedValidator);
  });

  it('should call removeValidator with the same validator object reference after multiple renders', () => {
    const validation = validationMock();

    let addedValidator: any, removedValidator: any;
    validation.addValidator.and.callFake((arg: any) => (addedValidator = arg));
    validation.removeValidator.and.callFake((arg: any) => (removedValidator = arg));

    const component = mount(<TestInputComponent validation={validation} />);
    component.render();
    component.render();
    component.unmount();
    expect(validation.addValidator).toHaveBeenCalledTimes(1);
    expect(validation.removeValidator).toHaveBeenCalledTimes(1);
    expect(addedValidator).toBe(removedValidator);
  });

  it('should call the latest validate function prop', () => {
    const validation = validationMock();
    let validators: IValidator[] = [];
    validation.addValidator.and.callFake((v: IValidator) => validators.push(v));
    validation.removeValidator.and.callFake((v: IValidator) => (validators = validators.filter((x) => x !== v)));
    validation.revalidate.and.callFake(() => validators.forEach((v) => v(null, null)));

    const initialValidator: IValidator = jasmine.createSpy('initialValidator', () => 'initial');
    const component = mount(<TestInputComponent validation={validation} validator={initialValidator} />);

    validation.revalidate();
    expect(initialValidator).toHaveBeenCalledTimes(1);

    const updatedValidator: IValidator = jasmine.createSpy('updatedValidator', () => 'updated');
    component.setProps({ validator: updatedValidator });
    validation.revalidate();

    expect(initialValidator).toHaveBeenCalledTimes(1); // Didn't get called again
    expect(updatedValidator).toHaveBeenCalledTimes(1);
  });

  it('should call removeValidator with the same validator object reference even after updating the validator', () => {
    const validation = validationMock();

    let addedValidator: any, removedValidator: any;
    validation.addValidator.and.callFake((arg: any) => (addedValidator = arg));
    validation.removeValidator.and.callFake((arg: any) => (removedValidator = arg));

    const component = mount(<TestInputComponent validation={validation} validator={() => 'Error: 1'} />);
    component.render();
    component.setProps({ validator: () => 'Error: 2' });
    component.render();
    component.unmount();
    expect(validation.addValidator).toHaveBeenCalledTimes(1);
    expect(validation.removeValidator).toHaveBeenCalledTimes(1);
    expect(addedValidator).toBe(removedValidator);
  });
});
