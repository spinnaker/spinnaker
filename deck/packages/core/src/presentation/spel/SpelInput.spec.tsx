import { act, render, screen } from '@testing-library/react';
import React from 'react';
import type { Mock } from 'vitest';

import type { IFormInputProps, IStageForSpelPreview, IValidator } from '..';
import { SpelInput } from './SpelInput';
import { SpelService } from './SpelService';

function defer<T = unknown>() {
  let resolve: (value: T) => void;
  let reject: (reason: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

describe('<SpelInput/>', () => {
  let inputProps: IFormInputProps;
  let evaluateExpression: Mock;

  const previewStage: IStageForSpelPreview = {
    stageId: '123',
    executionId: 'abc',
    executionLabel: 'execution ran yesterday',
  };

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
    inputProps = {
      name: 'name',
      onBlur: vi.fn(),
      onChange: vi.fn(),
      value: 'abc123',
      validation: {
        revalidate: vi.fn(),
        addValidator: vi.fn(),
        removeValidator: vi.fn(),
        touched: true,
        messageNode: 'Theres an error',
        hidden: false,
        category: 'error',
      },
    };
    evaluateExpression = vi.spyOn(SpelService, 'evaluateExpression').mockReturnValue(undefined);
  });

  afterEach(() => vi.useRealTimers());

  it('renders a text area with the value in it', () => {
    render(<SpelInput {...inputProps} previewStage={previewStage} />);

    expect(screen.getByRole('textbox')).toHaveValue('abc123');
  });

  it('eagerly fetches the preview using the value, pipeline, and stage ids', () => {
    render(<SpelInput {...inputProps} previewStage={previewStage} />);

    expect(evaluateExpression).toHaveBeenCalledTimes(1);
    expect(evaluateExpression).toHaveBeenCalledWith('abc123', 'abc', '123');
  });

  it('debounces preview fetches when the input value changes', async () => {
    const deferred = defer<string>();
    evaluateExpression.mockReturnValue(deferred.promise);
    const { rerender } = render(<SpelInput {...inputProps} previewStage={previewStage} />);
    await act(async () => deferred.resolve('async value'));

    rerender(<SpelInput {...inputProps} value="def456" previewStage={previewStage} />);
    expect(evaluateExpression).toHaveBeenCalledTimes(1);

    act(() => vi.advanceTimersByTime(300));

    expect(evaluateExpression).toHaveBeenCalledTimes(2);
  });

  it('revalidates whenever an async event occurs', async () => {
    const first = defer<string>();
    evaluateExpression.mockReturnValue(first.promise);
    const { rerender } = render(<SpelInput {...inputProps} previewStage={previewStage} />);
    expect(inputProps.validation.revalidate).toHaveBeenCalledTimes(1);

    await act(async () => first.resolve('async value1'));
    expect(inputProps.validation.revalidate).toHaveBeenCalledTimes(2);

    const second = defer<string>();
    evaluateExpression.mockReturnValue(second.promise);
    rerender(<SpelInput {...inputProps} value="def456" previewStage={previewStage} />);
    expect(inputProps.validation.revalidate).toHaveBeenCalledTimes(3);

    act(() => vi.advanceTimersByTime(300));
    expect(inputProps.validation.revalidate).toHaveBeenCalledTimes(5);

    await act(async () => second.resolve('async value2'));
    expect(inputProps.validation.revalidate).toHaveBeenCalledTimes(6);
  });

  it('adds a validator on mount and removes the same validator on unmount', () => {
    const { unmount } = render(<SpelInput {...inputProps} previewStage={previewStage} />);
    const validator = (inputProps.validation.addValidator as Mock).mock.lastCall[0];

    expect(inputProps.validation.addValidator).toHaveBeenCalledTimes(1);
    unmount();

    expect(inputProps.validation.removeValidator).toHaveBeenCalledTimes(1);
    expect((inputProps.validation.removeValidator as Mock).mock.lastCall[0]).toBe(validator);
  });

  describe('async validation', () => {
    let validators: IValidator[];
    let validate: Mock;

    beforeEach(() => {
      validators = [];
      validate = vi.fn(() => validators.map((validator) => validator(null)).filter(Boolean)[0]);
      (inputProps.validation.addValidator as Mock).mockImplementation((validator: IValidator) =>
        validators.push(validator),
      );
      (inputProps.validation.removeValidator as Mock).mockImplementation(
        (validator: IValidator) => (validators = validators.filter((candidate) => candidate !== validator)),
      );
      (inputProps.validation.revalidate as Mock).mockImplementation(() => validate());
    });

    it('validates as async while a preview is pending', () => {
      evaluateExpression.mockReturnValue(new Promise(() => undefined));

      render(<SpelInput {...inputProps} previewStage={previewStage} />);

      expect(validate.mock.results.at(-1).value).toMatch('Async: ');
    });

    it('continues to include the previous result while a new preview is pending', async () => {
      evaluateExpression.mockResolvedValue('preview result');
      const { rerender } = render(<SpelInput {...inputProps} previewStage={previewStage} />);
      await act(async () => Promise.resolve());
      validate.mockClear();

      evaluateExpression.mockReturnValue(new Promise(() => undefined));
      rerender(<SpelInput {...inputProps} value="some other value" previewStage={previewStage} />);

      expect(validate.mock.results.at(-1).value).toMatch('Async: ');
      expect(validate.mock.results.at(-1).value).toMatch('preview result');
    });

    it('validates as a message when a preview resolves', async () => {
      const deferred = defer<string>();
      evaluateExpression.mockReturnValue(deferred.promise);
      render(<SpelInput {...inputProps} previewStage={previewStage} />);

      await act(async () => deferred.resolve('expression result'));

      expect(validate.mock.results.at(-1).value).toMatch('Message: ');
      expect(validate.mock.results.at(-1).value).toMatch('expression result');
    });

    it('validates as a warning when a preview is rejected', async () => {
      const deferred = defer<string>();
      evaluateExpression.mockReturnValue(deferred.promise);
      render(<SpelInput {...inputProps} previewStage={previewStage} />);

      await act(async () => {
        deferred.reject('something bad happened');
        await deferred.promise.catch(() => undefined);
      });

      expect(validate.mock.results.at(-1).value).toMatch('Warning: something bad happened');
    });

    it('ignores an older preview response that resolves after the latest response', async () => {
      const older = defer<string>();
      const latest = defer<string>();
      evaluateExpression.mockReturnValueOnce(older.promise).mockReturnValueOnce(latest.promise);
      const { rerender } = render(<SpelInput {...inputProps} previewStage={previewStage} />);

      rerender(<SpelInput {...inputProps} value="latest expression" previewStage={previewStage} />);
      act(() => vi.advanceTimersByTime(300));
      await act(async () => latest.resolve('latest result'));
      expect(validate.mock.results.at(-1).value).toMatch('latest result');

      await act(async () => older.resolve('stale result'));

      expect(validators[0](null)).toMatch('latest result');
      expect(validators[0](null)).not.toMatch('stale result');
    });
  });
});
