import type { Mock } from 'vitest';

import type { IValidator } from '../../../forms/validation';
import { renderHookHarness } from '../../../../utils/testUtils/hookHarness';
import type { IFormInputProps, IFormInputValidation } from '../interface';
import { useInternalValidator } from './useInternalValidator.hook';

type IHookProps = IFormInputProps & { validator?: IValidator; revalidateDeps?: any[] };

function renderInternalValidator(props: IHookProps) {
  return renderHookHarness((hookProps: IHookProps) => {
    const { validator = () => null as string, validation, revalidateDeps = [] } = hookProps;
    return useInternalValidator(validation, validator, revalidateDeps);
  }, props);
}

interface IFormInputValidationMock extends IFormInputValidation {
  revalidate: Mock & IFormInputValidation['revalidate'];
  addValidator: Mock & IFormInputValidation['addValidator'];
  removeValidator: Mock & IFormInputValidation['removeValidator'];
}

function validationMock(): IFormInputValidationMock {
  return {
    touched: true,
    hidden: false,
    category: null,
    messageNode: null,
    revalidate: vi.fn(),
    addValidator: vi.fn(),
    removeValidator: vi.fn(),
  };
}

describe('useInternalValidator', () => {
  it('should call addValidator once when mounted', () => {
    const validation = validationMock();

    renderInternalValidator({ validation });
    expect(validation.addValidator).toHaveBeenCalledTimes(1);
    expect(validation.removeValidator).toHaveBeenCalledTimes(0);
  });

  it('should call revalidate when the deps list changes', () => {
    const validation = validationMock();

    const rendered = renderInternalValidator({ validation, revalidateDeps: ['a', 'b'] });
    expect(validation.revalidate).toHaveBeenCalledTimes(0);

    rendered.rerenderHook({ validation, revalidateDeps: ['c', 'd'] });
    expect(validation.revalidate).toHaveBeenCalledTimes(1);
  });

  it('should call removeValidator when unmounted', () => {
    const validation = validationMock();

    const rendered = renderInternalValidator({ validation });
    expect(validation.addValidator).toHaveBeenCalledTimes(1);
    expect(validation.removeValidator).toHaveBeenCalledTimes(0);

    rendered.unmount();
    expect(validation.addValidator).toHaveBeenCalledTimes(1);
    expect(validation.removeValidator).toHaveBeenCalledTimes(1);
  });

  it('should call removeValidator with the same validator object reference', () => {
    const validation = validationMock();

    let addedValidator: any, removedValidator: any;
    validation.addValidator.mockImplementation((arg: any) => (addedValidator = arg));
    validation.removeValidator.mockImplementation((arg: any) => (removedValidator = arg));

    const rendered = renderInternalValidator({ validation });
    rendered.unmount();
    expect(validation.addValidator).toHaveBeenCalledTimes(1);
    expect(validation.removeValidator).toHaveBeenCalledTimes(1);
    expect(addedValidator).toBe(removedValidator);
  });

  it('should call removeValidator with the same validator object reference after multiple renders', () => {
    const validation = validationMock();

    let addedValidator: any, removedValidator: any;
    validation.addValidator.mockImplementation((arg: any) => (addedValidator = arg));
    validation.removeValidator.mockImplementation((arg: any) => (removedValidator = arg));

    const rendered = renderInternalValidator({ validation });
    rendered.rerenderHook({ validation });
    rendered.rerenderHook({ validation });
    rendered.unmount();
    expect(validation.addValidator).toHaveBeenCalledTimes(1);
    expect(validation.removeValidator).toHaveBeenCalledTimes(1);
    expect(addedValidator).toBe(removedValidator);
  });

  it('should call the latest validate function prop', () => {
    const validation = validationMock();
    let validators: IValidator[] = [];
    validation.addValidator.mockImplementation((v: IValidator) => validators.push(v));
    validation.removeValidator.mockImplementation((v: IValidator) => (validators = validators.filter((x) => x !== v)));
    validation.revalidate.mockImplementation(() => validators.forEach((v) => v(null, null)));

    const initialValidator: IValidator = vi.fn();
    const rendered = renderInternalValidator({ validation, validator: initialValidator });

    validation.revalidate();
    expect(initialValidator).toHaveBeenCalledTimes(1);

    const updatedValidator: IValidator = vi.fn();
    rendered.rerenderHook({ validation, validator: updatedValidator });
    validation.revalidate();

    expect(initialValidator).toHaveBeenCalledTimes(1); // Didn't get called again
    expect(updatedValidator).toHaveBeenCalledTimes(1);
  });

  it('should call removeValidator with the same validator object reference even after updating the validator', () => {
    const validation = validationMock();

    let addedValidator: any, removedValidator: any;
    validation.addValidator.mockImplementation((arg: any) => (addedValidator = arg));
    validation.removeValidator.mockImplementation((arg: any) => (removedValidator = arg));

    const rendered = renderInternalValidator({ validation, validator: () => 'Error: 1' });
    rendered.rerenderHook({ validation, validator: () => 'Error: 1' });
    rendered.rerenderHook({ validation, validator: () => 'Error: 2' });
    rendered.rerenderHook({ validation, validator: () => 'Error: 2' });
    rendered.unmount();
    expect(validation.addValidator).toHaveBeenCalledTimes(1);
    expect(validation.removeValidator).toHaveBeenCalledTimes(1);
    expect(addedValidator).toBe(removedValidator);
  });
});
