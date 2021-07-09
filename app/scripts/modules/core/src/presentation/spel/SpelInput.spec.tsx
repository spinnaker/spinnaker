import React from 'react';
import { mount } from 'enzyme';
import { IFormInputProps, IStageForSpelPreview, IValidator } from '..';
import { SpelService } from './SpelService';
import { SpelInput } from './SpelInput';

function defer() {
  let resolve: Function, reject: Function;
  const promise = new Promise((_resolve, _reject) => {
    resolve = _resolve;
    reject = _reject;
  });
  return { promise, resolve, reject };
}

describe('<SpelInput/>', () => {
  beforeEach(() => jasmine.clock().install());
  afterEach(() => jasmine.clock().uninstall());

  let inputProps: IFormInputProps;
  let evaluateExpressionSpy: jasmine.Spy;

  const previewStage: IStageForSpelPreview = {
    stageId: '123',
    executionId: 'abc',
    executionLabel: 'execution ran yesterday',
  };

  beforeEach(() => {
    inputProps = {
      name: 'name',
      onBlur: jasmine.createSpy('onBlur'),
      onChange: jasmine.createSpy('onChange'),
      value: 'abc123',
      validation: {
        revalidate: jasmine.createSpy('revalidate'),
        addValidator: jasmine.createSpy('addValidator'),
        removeValidator: jasmine.createSpy('removeValidator'),
        touched: true,
        messageNode: 'Theres an error',
        hidden: false,
        category: 'error',
      },
    };

    evaluateExpressionSpy = spyOn(SpelService, 'evaluateExpression');
  });

  it('should render a text area with the value in it', () => {
    const component = mount(<SpelInput {...inputProps} previewStage={previewStage} />);
    expect(component.render().is('textarea')).toBe(true);
    expect(component.render().text()).toBe('abc123');
  });

  it('should eagerly fetch the preview from the server on initial load', () => {
    mount(<SpelInput {...inputProps} previewStage={previewStage} />);
    expect(evaluateExpressionSpy).toHaveBeenCalledTimes(1);
  });

  it('should pass the value, pipeline, and stage ids to the SpelService', () => {
    mount(<SpelInput {...inputProps} previewStage={previewStage} />);
    expect(evaluateExpressionSpy).toHaveBeenCalledWith('abc123', 'abc', '123');
  });

  it('should debounce preview fetches when the input value changes', async () => {
    const deferred1 = defer();
    evaluateExpressionSpy.and.callFake(() => deferred1.promise);
    const component = mount(<SpelInput {...inputProps} previewStage={previewStage} />);
    expect(evaluateExpressionSpy).toHaveBeenCalledTimes(1);

    // First preview request resolves
    deferred1.resolve('async value');
    await deferred1.promise;
    component.setProps({});

    // Update value -- evaluate is not called yet
    component.setProps({ value: 'def456' });
    expect(evaluateExpressionSpy).toHaveBeenCalledTimes(1);

    // After debounce interval, evaluate is called again
    jasmine.clock().tick(1000);
    component.setProps({});
    expect(evaluateExpressionSpy).toHaveBeenCalledTimes(2);
  });

  it('should call revalidate whenever an async event occurs', async () => {
    const deferred1 = defer();
    evaluateExpressionSpy.and.callFake(() => deferred1.promise);
    const component = mount(<SpelInput {...inputProps} previewStage={previewStage} />);

    // [ NONE -> PENDING ] a promise was found, results pending
    expect(inputProps.validation.revalidate).toHaveBeenCalledTimes(1);

    // Result received from the server
    deferred1.resolve('async value1');
    await deferred1.promise;
    component.setProps({});

    // [ PENDING -> RESOLVED ]
    expect(inputProps.validation.revalidate).toHaveBeenCalledTimes(2);

    // Prepare the test for second async fetch
    const deferred2 = defer();
    evaluateExpressionSpy.and.callFake(() => deferred2.promise);
    component.setProps({ value: 'def456' });

    // [ notDebouncing -> isDebouncing ]
    expect(inputProps.validation.revalidate).toHaveBeenCalledTimes(3);
    jasmine.clock().tick(1000);
    component.setProps({});

    // [ isDebouncing -> notDebouncing ], [ RESOLVED -> PENDING ]
    expect(inputProps.validation.revalidate).toHaveBeenCalledTimes(5);

    deferred2.resolve('async value2');
    await deferred2.promise;
    component.setProps({});

    // [ PENDING -> RESOLVED ]
    expect(inputProps.validation.revalidate).toHaveBeenCalledTimes(6);
  });

  it('should add a validator on mount', () => {
    mount(<SpelInput {...inputProps} previewStage={previewStage} />);
    expect(inputProps.validation.addValidator).toHaveBeenCalledTimes(1);
  });

  it('should remove the same validator on unmount as it added on mount', () => {
    const addValidator = inputProps.validation.addValidator as jasmine.Spy;
    const removeValidator = inputProps.validation.removeValidator as jasmine.Spy;

    const component = mount(<SpelInput {...inputProps} previewStage={previewStage} />);

    expect(addValidator).toHaveBeenCalledTimes(1);
    expect(removeValidator).toHaveBeenCalledTimes(0);

    component.unmount();

    expect(addValidator).toHaveBeenCalledTimes(1);
    expect(removeValidator).toHaveBeenCalledTimes(1);

    expect(addValidator.calls.mostRecent().args[0]).toBe(removeValidator.calls.mostRecent().args[0]);
  });

  describe('async validation', () => {
    let validators: IValidator[];
    let mockValidate: jasmine.Spy;

    beforeEach(() => {
      validators = [];
      mockValidate = jasmine.createSpy('validate').and.callFake(() => {
        return validators.map((v) => v(null)).filter((x) => !!x)[0];
      });

      const addValidator = inputProps.validation.addValidator as jasmine.Spy;
      const removeValidator = inputProps.validation.removeValidator as jasmine.Spy;
      const revalidate = inputProps.validation.revalidate as jasmine.Spy;

      addValidator.and.callFake((v: IValidator) => validators.push(v));
      removeValidator.and.callFake((v: IValidator) => (validators = validators.filter((x) => x !== v)));
      revalidate.and.callFake(() => mockValidate());
    });

    it('should validate as "Async: *" when a SpelService fetch is pending', async () => {
      evaluateExpressionSpy.and.callFake(() => new Promise<any>(() => null));
      mount(<SpelInput {...inputProps} previewStage={previewStage} />);
      expect(mockValidate).toHaveBeenCalledTimes(1);
      expect(mockValidate.calls.mostRecent().returnValue).toMatch('Async: ');
    });

    it('should continue to render the previous result when a SpelService fetch is pending', async () => {
      const result1 = new Promise<any>((resolve) => resolve('preview result'));
      evaluateExpressionSpy.and.callFake(() => result1);
      const component = mount(<SpelInput {...inputProps} previewStage={previewStage} />);
      expect(mockValidate).toHaveBeenCalledTimes(1);
      expect(mockValidate.calls.mostRecent().returnValue).toMatch('Async: ');
      mockValidate.calls.reset();

      await result1;
      component.setProps({ value: 'some other value' });

      expect(mockValidate).toHaveBeenCalledTimes(2);
      expect(mockValidate.calls.first().returnValue).toMatch('Message: ');
      expect(mockValidate.calls.mostRecent().returnValue).toMatch('Async: ');
      expect(mockValidate.calls.mostRecent().returnValue).toMatch('preview result');
    });

    it('should validate as "Message: *" when a SpelService fetch is resolved with a result', async () => {
      const deferred = defer();
      evaluateExpressionSpy.and.callFake(() => deferred.promise);
      const component = mount(<SpelInput {...inputProps} previewStage={previewStage} />);
      expect(mockValidate).toHaveBeenCalledTimes(1);
      expect(mockValidate.calls.mostRecent().returnValue).toMatch('Async: ');

      deferred.resolve('expression result');
      await deferred.promise;
      component.setProps({});

      expect(mockValidate).toHaveBeenCalledTimes(2);
      expect(mockValidate.calls.mostRecent().returnValue).toMatch('Message: ');
      expect(mockValidate.calls.mostRecent().returnValue).toMatch('expression result');
    });

    it('should validate as "Warning: *" when a SpelService fetch is rejected', async () => {
      const deferred = defer();
      evaluateExpressionSpy.and.callFake(() => deferred.promise);
      const component = mount(<SpelInput {...inputProps} previewStage={previewStage} />);
      expect(mockValidate).toHaveBeenCalledTimes(1);
      expect(mockValidate.calls.mostRecent().returnValue).toMatch('Async: ');

      deferred.reject('something bad happened');
      try {
        await deferred.promise;
      } catch (error) {}
      component.setProps({});

      expect(mockValidate).toHaveBeenCalledTimes(2);
      expect(mockValidate.calls.mostRecent().returnValue).toMatch('Warning: something bad happened');
    });
  });
});
