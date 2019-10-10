import {
  asyncMessage,
  categorizeValidationMessage,
  categorizeValidationMessages,
  errorMessage,
  infoMessage,
  messageMessage,
  successMessage,
  warningMessage,
} from './categories';

describe('categorizeErrorMessage', () => {
  it('returns an array of length 2', () => {
    const result = categorizeValidationMessage('');
    expect(Array.isArray(result)).toBeTruthy();
    expect(result.length).toBe(2);
  });

  it('returns the matching category key if the input starts with a category label and ": "', () => {
    expect(categorizeValidationMessage('Error: ')[0]).toEqual('error');
    expect(categorizeValidationMessage('Warning: ')[0]).toEqual('warning');
    expect(categorizeValidationMessage('Async: ')[0]).toEqual('async');
    expect(categorizeValidationMessage('Message: ')[0]).toEqual('message');
  });

  it('returns "error" category key by default if no category label is present', () => {
    expect(categorizeValidationMessage('there was an error')[0]).toEqual('error');
  });

  it('returns the entire error message if no category label is present', () => {
    expect(categorizeValidationMessage('there was an error')[1]).toEqual('there was an error');
  });

  it('returns the error message without the label prefix', () => {
    expect(categorizeValidationMessage('Warning: something sorta bad')[1]).toEqual('something sorta bad');
  });

  it('supports newlines embedded in the message', () => {
    const [status, message] = categorizeValidationMessage('Warning: something sorta bad\n\nhappened');
    expect(status).toBe('warning');
    expect(message).toBe('something sorta bad\n\nhappened');
  });
});

describe('category message builder', () => {
  it('asyncMessage should prefix a message with Async:', () => {
    expect(asyncMessage('the quick brown fox')).toBe('Async: the quick brown fox');
  });

  it('errorMessage should prefix a message with Error:', () => {
    expect(errorMessage('the quick brown fox')).toBe('Error: the quick brown fox');
  });

  it('infoMessage should prefix a message with Info:', () => {
    expect(infoMessage('the quick brown fox')).toBe('Info: the quick brown fox');
  });

  it('messageMessage should prefix a message with Message:', () => {
    expect(messageMessage('the quick brown fox')).toBe('Message: the quick brown fox');
  });

  it('successMessage should prefix a message with Success:', () => {
    expect(successMessage('the quick brown fox')).toBe('Success: the quick brown fox');
  });

  it('warningMessage should prefix a message with Warning:', () => {
    expect(warningMessage('the quick brown fox')).toBe('Warning: the quick brown fox');
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
    const categories = categorizeValidationMessages({});
    expect(Object.keys(categories).sort()).toEqual(['async', 'error', 'info', 'message', 'success', 'warning']);
  });

  it('categorizes an unlabeled error message into "error"', () => {
    const rawErrors = { foo: 'An error with foo' };
    const categorized = categorizeValidationMessages(rawErrors);

    const error = { foo: 'An error with foo' };
    expect(categorized).toEqual({ ...emptyErrors, error });
  });

  it('categorizes a labeled warning message into "warning"', () => {
    const rawErrors = { foo: 'Warning: A warning about foo' };
    const categorized = categorizeValidationMessages(rawErrors);

    const warning = { foo: 'A warning about foo' };
    expect(categorized).toEqual({ ...emptyErrors, warning });
  });

  it('categorizes multiple labeled messages into their respective buckets', () => {
    const rawErrors = {
      foo: 'Warning: A warning about foo',
      bar: 'Async: Loading some data',
      baz: 'Message: The sky is blue',
    };
    const categorized = categorizeValidationMessages(rawErrors);

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
    const categorized = categorizeValidationMessages(rawErrors);

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
    const categorized = categorizeValidationMessages(rawErrors);

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
