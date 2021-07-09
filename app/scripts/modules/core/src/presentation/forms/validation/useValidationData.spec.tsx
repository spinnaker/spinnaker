import { asyncMessage, errorMessage, infoMessage, messageMessage, successMessage, warningMessage } from './categories';
import React from 'react';

import { mount } from 'enzyme';
import { IValidationData, useValidationData } from './useValidationData';

interface IComponentProps {
  validationMessage: React.ReactNode;
  touched: boolean;
  callback: Function;
}

describe('useValidationData hook', () => {
  function Component({ validationMessage, touched, callback }: IComponentProps) {
    callback(useValidationData(validationMessage, touched));
    return <></>;
  }

  function runUseValidationDataHook(validationMessage?: React.ReactNode, touched = true): IValidationData {
    let ran = false;
    let data = null;
    const callback = (result: IValidationData) => {
      if (ran) {
        throw new Error('already ran');
      }

      data = result;
      ran = true;
    };

    mount(<Component validationMessage={validationMessage} touched={touched} callback={callback} />);

    if (!ran) {
      throw new Error('Callback never ran');
    }

    return data;
  }

  it('should default to error category', () => {
    const data = runUseValidationDataHook('Test');
    expect(data.messageNode).toBe('Test');
    expect(data.category).toBe('error');
  });

  it('should pass JSX.Element nodes through, with category: null and hidden: false', () => {
    const element = <div />;
    const data = runUseValidationDataHook(element);
    expect(data.messageNode).toBe(element);
    expect(data.category).toBeNull();
    expect(data.hidden).toBe(false);
  });

  it('should pass non-JSX.Element/non-string nodes through, with category: null and hidden: true', () => {
    const element = { foo: 'bar', baz: [1] };
    const data = runUseValidationDataHook(element);
    expect(data.messageNode).toBe(element);
    expect(data.category).toBeNull();
    expect(data.hidden).toBe(true);
  });

  it('should return async category when asyncMessage is provided', () => {
    const data = runUseValidationDataHook(asyncMessage('Test'));
    expect(data.messageNode).toBe('Test');
    expect(data.category).toBe('async');
  });

  it('should return error category when errorMessage is provided', () => {
    const data = runUseValidationDataHook(errorMessage('Test'));
    expect(data.messageNode).toBe('Test');
    expect(data.category).toBe('error');
  });

  it('should return info category when infoMessage is provided', () => {
    const data = runUseValidationDataHook(infoMessage('Test'));
    expect(data.messageNode).toBe('Test');
    expect(data.category).toBe('info');
  });

  it('should return message category when messageMessage is provided', () => {
    const data = runUseValidationDataHook(messageMessage('Test'));
    expect(data.messageNode).toBe('Test');
    expect(data.category).toBe('message');
  });

  it('should return success category when successMessage is provided', () => {
    const data = runUseValidationDataHook(successMessage('Test'));
    expect(data.messageNode).toBe('Test');
    expect(data.category).toBe('success');
  });

  it('should return warning category when warningMessage is provided', () => {
    const data = runUseValidationDataHook(warningMessage('Test'));
    expect(data.messageNode).toBe('Test');
    expect(data.category).toBe('warning');
  });

  it('should return hidden: true when a message is provided but category == error and touched == false', () => {
    const data = runUseValidationDataHook(errorMessage('Test'), false);
    expect(data.messageNode).toBe('Test');
    expect(data.category).toBe('error');
    expect(data.hidden).toBe(true);
  });

  it('should return hidden: true when a message is provided but category == warning and touched == false', () => {
    const data = runUseValidationDataHook(warningMessage('Test'), false);
    expect(data.messageNode).toBe('Test');
    expect(data.category).toBe('warning');
    expect(data.hidden).toBe(true);
  });

  it('should return hidden: false when a message is provided and category is not (warning || error) even when touched == false', () => {
    let data = runUseValidationDataHook(asyncMessage('Test'), false);
    expect(data.messageNode).toBe('Test');
    expect(data.category).toBe('async');
    expect(data.hidden).toBe(false);

    data = runUseValidationDataHook(infoMessage('Test'), false);
    expect(data.messageNode).toBe('Test');
    expect(data.category).toBe('info');
    expect(data.hidden).toBe(false);

    data = runUseValidationDataHook(messageMessage('Test'), false);
    expect(data.messageNode).toBe('Test');
    expect(data.category).toBe('message');
    expect(data.hidden).toBe(false);

    data = runUseValidationDataHook(successMessage('Test'), false);
    expect(data.messageNode).toBe('Test');
    expect(data.category).toBe('success');
    expect(data.hidden).toBe(false);
  });

  it('should return hidden: true when no message is provided regardless of the value of touched', () => {
    let data = runUseValidationDataHook(null, false);
    expect(data.messageNode).toBeNull();
    expect(data.category).toBeNull();
    expect(data.hidden).toBe(true);

    data = runUseValidationDataHook(null, true);
    expect(data.messageNode).toBeNull();
    expect(data.category).toBeNull();
    expect(data.hidden).toBe(true);

    data = runUseValidationDataHook(undefined, false);
    expect(data.messageNode).toBeNull();
    expect(data.category).toBeNull();
    expect(data.hidden).toBe(true);

    data = runUseValidationDataHook(undefined, true);
    expect(data.messageNode).toBeNull();
    expect(data.category).toBeNull();
    expect(data.hidden).toBe(true);

    data = runUseValidationDataHook('', false);
    expect(data.messageNode).toBeNull();
    expect(data.category).toBeNull();
    expect(data.hidden).toBe(true);

    data = runUseValidationDataHook('', true);
    expect(data.messageNode).toBeNull();
    expect(data.category).toBeNull();
    expect(data.hidden).toBe(true);

    data = runUseValidationDataHook(errorMessage(''), false);
    expect(data.messageNode).toBeNull();
    expect(data.category).toBeNull();
    expect(data.hidden).toBe(true);

    data = runUseValidationDataHook(errorMessage(''), true);
    expect(data.messageNode).toBeNull();
    expect(data.category).toBeNull();
    expect(data.hidden).toBe(true);
  });
});
