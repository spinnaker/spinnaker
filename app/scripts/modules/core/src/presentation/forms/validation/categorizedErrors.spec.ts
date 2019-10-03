import { categorizeErrorMessage, categorizeErrors } from './categorizedErrors';

describe('categorizeErrorMessage', () => {
  it('returns an array of length 2', () => {
    const result = categorizeErrorMessage('');
    expect(Array.isArray(result)).toBeTruthy();
    expect(result.length).toBe(2);
  });

  it('returns the matching category key if the input starts with a category label and ": "', () => {
    expect(categorizeErrorMessage('Error: ')[0]).toEqual('error');
    expect(categorizeErrorMessage('Warning: ')[0]).toEqual('warning');
    expect(categorizeErrorMessage('Async: ')[0]).toEqual('async');
    expect(categorizeErrorMessage('Message: ')[0]).toEqual('message');
  });

  it('returns "error" category key by default if no category label is present', () => {
    expect(categorizeErrorMessage('there was an error')[0]).toEqual('error');
  });

  it('returns the entire error message if no category label is present', () => {
    expect(categorizeErrorMessage('there was an error')[1]).toEqual('there was an error');
  });

  it('returns the error message without the label prefix', () => {
    expect(categorizeErrorMessage('Warning: something sorta bad')[1]).toEqual('something sorta bad');
  });
});

describe('categorizedErrors', () => {
  const emptyErrors = Object.freeze({
    async: {},
    error: {},
    info: {},
    message: {},
    success: {},
    warning: {},
  });

  it('returns an object with all error categories as keys', () => {
    const categories = categorizeErrors({});
    expect(Object.keys(categories).sort()).toEqual(['async', 'error', 'info', 'message', 'success', 'warning']);
  });

  it('categorizes an unlabeled error message into "error"', () => {
    const rawErrors = { foo: 'An error with foo' };
    const categorized = categorizeErrors(rawErrors);

    const error = { foo: 'An error with foo' };
    expect(categorized).toEqual({ ...emptyErrors, error });
  });

  it('categorizes a labeled warning message into "warning"', () => {
    const rawErrors = { foo: 'Warning: A warning about foo' };
    const categorized = categorizeErrors(rawErrors);

    const warning = { foo: 'A warning about foo' };
    expect(categorized).toEqual({ ...emptyErrors, warning });
  });

  it('categorizes multiple labeled messages into their respective buckets', () => {
    const rawErrors = {
      foo: 'Warning: A warning about foo',
      bar: 'Async: Loading some data',
      baz: 'Message: The sky is blue',
    };
    const categorized = categorizeErrors(rawErrors);

    const warning = { foo: 'A warning about foo' };
    const async = { bar: 'Loading some data' };
    const message = { baz: 'The sky is blue' };
    expect(categorized).toEqual({ ...emptyErrors, warning, async, message });
  });

  it('categorizes multiple messages with the same label into the respective bucket', () => {
    const rawErrors = {
      foo: 'Message: Two plus two is four',
      bar: 'Message: Fear leads to anger',
      baz: 'Message: The sky is blue',
    };
    const categorized = categorizeErrors(rawErrors);

    const message = {
      foo: 'Two plus two is four',
      bar: 'Fear leads to anger',
      baz: 'The sky is blue',
    };
    expect(categorized).toEqual({ ...emptyErrors, message });
  });

  it('maps deeply nested error messages to the same path into each respective bucket', () => {
    const rawErrors = {
      people: [
        {
          name: {
            first: 'Warning: First name is required',
            last: 'Warning: Last name is required',
          },
        },
        {
          age: 'Error: Age cannot be negative',
        },
      ],
      deep: {
        nested: {
          object: 'Error: This is a deeply nested error message',
        },
      },
    };
    const categorized = categorizeErrors(rawErrors);

    const warning = {
      people: [
        {
          name: {
            first: 'First name is required',
            last: 'Last name is required',
          },
        },
      ],
    };

    const error = {
      people: [
        // empty array element because array indexes must match up with the source errors object
        undefined,
        { age: 'Age cannot be negative' },
      ],
      deep: {
        nested: {
          object: 'This is a deeply nested error message',
        },
      },
    };
    expect(categorized).toEqual({ ...emptyErrors, warning, error });
  });
});
