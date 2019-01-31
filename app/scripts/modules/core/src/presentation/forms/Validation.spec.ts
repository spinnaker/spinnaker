import { Validation, buildValidators, Validator, ArrayItemValidator, buildValidatorsAsync } from './Validation';

const { isRequired, minValue, maxValue } = Validation;

const makeAsync = (syncValidator: Validator): Validator => {
  return (value, label) => {
    const result = syncValidator(value, label);
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        result ? reject(result) : resolve();
      }, 1);
    });
  };
};

describe('Synchronous validation', () => {
  it('returns empty errors when validating no validators', () => {
    const values = { foo: 'bar' };

    const builder = buildValidators(values);
    builder.field('foo', 'Foo').validate([]);

    const result = builder.result();
    const expectedResult = {};
    expect(result).toEqual(expectedResult);
  });

  it('returns correct error when validating top level field', () => {
    const values = {};

    const builder = buildValidators(values);
    builder.field('foo', 'Foo').validate([isRequired()]);

    const result = builder.result();
    const expectedResult = {
      foo: 'Foo is required.',
    };
    expect(result).toEqual(expectedResult);
  });

  it('returns correct error when validating a deep field', () => {
    const values = {};

    const builder = buildValidators(values);
    builder.field('foo.bar.baz', 'Foo').validate([isRequired()]);

    const result = builder.result();
    const expectedResult = {
      foo: {
        bar: {
          baz: 'Foo is required.',
        },
      },
    };
    expect(result).toEqual(expectedResult);
  });

  it('aggregates multiple levels of errors correctly', () => {
    const values = {};

    const builder = buildValidators(values);
    builder.field('foo', 'Foo').validate([isRequired()]);
    builder.field('bar.baz', 'Baz').validate([isRequired()]);

    const result = builder.result();
    const expectedResult = {
      foo: 'Foo is required.',
      bar: {
        baz: 'Baz is required.',
      },
    };
    expect(result).toEqual(expectedResult);
  });

  it('validates arrays and aggregates them correctly', () => {
    const values = {
      lotsastuff: [1, 2, 3, 4, 5],
    };

    const builder = buildValidators(values);
    const arrayNotEmpty: Validator = (array, label) => array.length < 1 && `${label} must have at least 1 item.`;
    const { arrayForEach } = builder;

    builder.field('lotsastuff', 'Array').validate([
      isRequired(),
      arrayNotEmpty,
      arrayForEach(itemBuilder => {
        itemBuilder.item('Item').validate([isRequired(), maxValue(3)]);
      }),
    ]);

    const result = builder.result();
    const expectedResult = {
      lotsastuff: [undefined, undefined, undefined, 'Item cannot be greater than 3', 'Item cannot be greater than 3'],
    };
    expect(result).toEqual(expectedResult);
  });

  it('arrays without errors should not be aggregated', () => {
    const values = {
      lotsastuff: [1, 2, 3, 4, 5],
    };

    const builder = buildValidators(values);
    const arrayNotEmpty: Validator = (array, label) => array.length < 1 && `${label} must have at least 1 item.`;
    const { arrayForEach } = builder;

    builder.field('lotsastuff', 'Array').validate([
      isRequired(),
      arrayNotEmpty,
      arrayForEach(itemBuilder => {
        itemBuilder.item('Item').validate([isRequired()]);
      }),
    ]);

    const result = builder.result();
    const expectedResult = {};
    expect(result).toEqual(expectedResult);
  });

  it('validates keys on array items and aggregates errors into resulting arrays correctly', () => {
    const values = {
      lotsastuff: [{ key: 1 }, { value: 2 }, 3, 4, 5],
    };

    const builder = buildValidators(values);
    const { arrayForEach } = builder;
    builder.field('lotsastuff', 'Array').validate([
      (array, label) => array.length < 1 && `${label} must have at least 1 item.`,
      arrayForEach(itemBuilder => {
        itemBuilder.field(`key`, `Item Key`).validate([isRequired()]);
        itemBuilder.field(`value`, `Item Value`).validate([isRequired()]);
      }),
    ]);

    const result = builder.result();
    const expectedResult = {
      lotsastuff: [
        { value: 'Item Value is required.' },
        { key: 'Item Key is required.' },
        { key: 'Item Key is required.', value: 'Item Value is required.' },
        { key: 'Item Key is required.', value: 'Item Value is required.' },
        { key: 'Item Key is required.', value: 'Item Value is required.' },
      ],
    };
    expect(result).toEqual(expectedResult);
  });

  it('validates crazy complicated arrays of objects with arrays of objects', () => {
    const values = {
      letsgetcrazy: [
        {},
        {
          key: 'array',
          data: [
            { all: 1, of: 2, the: 3, things: 4 },
            { all: '', of: 2, the: 3, things: 4 },
            { all: 1, of: '', the: 3, things: 4 },
            { all: 1, of: 2, the: '', things: 4 },
            { all: 1, of: 2, the: 3, things: '' },
            {},
          ],
        },
        {
          key: 'nothotdog',
          data: { foo: 'bar' },
        },
      ],
    };

    const builder = buildValidators(values);
    const isArray: Validator = (array, label) => !Array.isArray(array) && `${label} must be an array.`;
    const allOfTheThingsValidator: ArrayItemValidator = itemBuilder => {
      itemBuilder.field(`all`, 'All').validate([isRequired()]);
      itemBuilder.field(`of`, 'Of').validate([isRequired()]);
      itemBuilder.field(`the`, 'The').validate([isRequired()]);
      itemBuilder.field(`things`, 'Things').validate([isRequired()]);
    };

    const outerArrayItemValidator: ArrayItemValidator = itemBuilder => {
      itemBuilder.field('key', 'Item key').validate([isRequired()]);
      itemBuilder.field('data', 'Item data').validate([isRequired(), isArray, arrayForEach(allOfTheThingsValidator)]);
    };

    const { arrayForEach } = builder;
    builder.field('letsgetcrazy', 'Outer array').validate([arrayForEach(outerArrayItemValidator)]);

    const result = builder.result();
    const expectedResult = {
      letsgetcrazy: [
        { key: 'Item key is required.', data: 'Item data is required.' },
        {
          data: [
            undefined,
            { all: 'All is required.' },
            { of: 'Of is required.' },
            { the: 'The is required.' },
            { things: 'Things is required.' },
            {
              all: 'All is required.',
              of: 'Of is required.',
              the: 'The is required.',
              things: 'Things is required.',
            },
          ],
        },
        { data: 'Item data must be an array.' },
      ],
    };
    expect(result).toEqual(expectedResult);
  });
});

describe('Asynchronous validation of synchronous validators', () => {
  it('returns empty errors when validating no validators - should also work with async', done => {
    const values = { foo: 'bar' };

    const builder = buildValidatorsAsync(values);
    builder.field('foo', 'Foo').validate([]);

    const result = builder.result();
    const expectedResult = {};
    result.then((errors: any) => {
      expect(errors).toEqual(expectedResult);
      done();
    });
  });

  it('returns correct error when validating top level field - should also work with async', done => {
    const values = {};

    const builder = buildValidatorsAsync(values);
    builder.field('foo', 'Foo').validate([isRequired()]);

    const result = builder.result();
    const expectedResult = {
      foo: 'Foo is required.',
    };
    result.catch((errors: any) => {
      expect(errors).toEqual(expectedResult);
      done();
    });
  });

  it('returns correct error when validating a deep field - should also work with async', done => {
    const values = {};

    const builder = buildValidatorsAsync(values);
    builder.field('foo.bar.baz', 'Foo').validate([isRequired()]);

    const result = builder.result();
    const expectedResult = {
      foo: {
        bar: {
          baz: 'Foo is required.',
        },
      },
    };
    result.catch((errors: any) => {
      expect(errors).toEqual(expectedResult);
      done();
    });
  });

  it('aggregates multiple levels of errors correctly - should also work with async', done => {
    const values = {};

    const builder = buildValidatorsAsync(values);
    builder.field('foo', 'Foo').validate([isRequired()]);
    builder.field('bar.baz', 'Baz').validate([isRequired()]);

    const result = builder.result();
    const expectedResult = {
      foo: 'Foo is required.',
      bar: {
        baz: 'Baz is required.',
      },
    };
    result.catch((errors: any) => {
      expect(errors).toEqual(expectedResult);
      done();
    });
  });

  it('validates arrays and aggregates them correctly - should also work with async', done => {
    const values = {
      lotsastuff: [1, 2, 3, 4, 5],
    };

    const builder = buildValidatorsAsync(values);
    const arrayNotEmpty: Validator = (array, label) => array.length < 1 && `${label} must have at least 1 item.`;
    const { arrayForEach } = builder;

    builder.field('lotsastuff', 'Array').validate([
      isRequired(),
      arrayNotEmpty,
      arrayForEach(itemBuilder => {
        itemBuilder.item('Item').validate([isRequired(), maxValue(3)]);
      }),
    ]);

    const result = builder.result();
    const expectedResult = {
      lotsastuff: [undefined, undefined, undefined, 'Item cannot be greater than 3', 'Item cannot be greater than 3'],
    };
    result.catch((errors: any) => {
      expect(errors).toEqual(expectedResult);
      done();
    });
  });

  it('validates keys on array items and aggregates errors into resulting arrays correctly - should also work with async', done => {
    const values = {
      lotsastuff: [{ key: 1 }, { value: 2 }, 3, 4, 5],
    };

    const builder = buildValidatorsAsync(values);
    const { arrayForEach } = builder;
    builder.field('lotsastuff', 'Array').validate([
      (array, label) => array.length < 1 && `${label} must have at least 1 item.`,
      arrayForEach(itemBuilder => {
        itemBuilder.field(`key`, `Item Key`).validate([isRequired()]);
        itemBuilder.field(`value`, `Item Value`).validate([isRequired()]);
      }),
    ]);

    const result = builder.result();
    const expectedResult = {
      lotsastuff: [
        { value: 'Item Value is required.' },
        { key: 'Item Key is required.' },
        { key: 'Item Key is required.', value: 'Item Value is required.' },
        { key: 'Item Key is required.', value: 'Item Value is required.' },
        { key: 'Item Key is required.', value: 'Item Value is required.' },
      ],
    };
    result.catch((errors: any) => {
      expect(errors).toEqual(expectedResult);
      done();
    });
  });

  it('validates crazy complicated arrays of objects with arrays of objects - should also work with async', done => {
    const values = {
      letsgetcrazy: [
        {},
        {
          key: 'array',
          data: [
            { all: 1, of: 2, the: 3, things: 4 },
            { all: '', of: 2, the: 3, things: 4 },
            { all: 1, of: '', the: 3, things: 4 },
            { all: 1, of: 2, the: '', things: 4 },
            { all: 1, of: 2, the: 3, things: '' },
            {},
          ],
        },
        {
          key: 'nothotdog',
          data: { foo: 'bar' },
        },
      ],
    };

    const builder = buildValidatorsAsync(values);
    const isArray: Validator = (array, label) => !Array.isArray(array) && `${label} must be an array.`;
    const allOfTheThingsValidator: ArrayItemValidator = itemBuilder => {
      itemBuilder.field(`all`, 'All').validate([isRequired()]);
      itemBuilder.field(`of`, 'Of').validate([isRequired()]);
      itemBuilder.field(`the`, 'The').validate([isRequired()]);
      itemBuilder.field(`things`, 'Things').validate([isRequired()]);
    };

    const outerArrayItemValidator: ArrayItemValidator = itemBuilder => {
      itemBuilder.field('key', 'Item key').validate([isRequired()]);
      itemBuilder.field('data', 'Item data').validate([isRequired(), isArray, arrayForEach(allOfTheThingsValidator)]);
    };

    const { arrayForEach } = builder;
    builder.field('letsgetcrazy', 'Outer array').validate([arrayForEach(outerArrayItemValidator)]);

    const result = builder.result();
    const expectedResult = {
      letsgetcrazy: [
        { key: 'Item key is required.', data: 'Item data is required.' },
        {
          data: [
            undefined,
            { all: 'All is required.' },
            { of: 'Of is required.' },
            { the: 'The is required.' },
            { things: 'Things is required.' },
            {
              all: 'All is required.',
              of: 'Of is required.',
              the: 'The is required.',
              things: 'Things is required.',
            },
          ],
        },
        { data: 'Item data must be an array.' },
      ],
    };
    result.catch((errors: any) => {
      expect(errors).toEqual(expectedResult);
      done();
    });
  });
});

describe('Guarantees identical interfaces and results between sync and async', () => {
  it('crazy complicated arrays of objects with arrays of objects', done => {
    const isArray: Validator = (array, label) => !Array.isArray(array) && `${label} must be an array.`;
    const allOfTheThingsValidator: ArrayItemValidator = itemBuilder => {
      itemBuilder.field(`all`, 'All').validate([isRequired()]);
      itemBuilder.field(`of`, 'Of').validate([isRequired()]);
      itemBuilder.field(`the`, 'The').validate([isRequired()]);
      itemBuilder.field(`things`, 'Things').validate([isRequired()]);
    };

    const values = {
      letsgetcrazy: [
        {},
        {
          key: 'array',
          data: [
            { all: 1, of: 2, the: 3, things: 4 },
            { all: '', of: 2, the: 3, things: 4 },
            { all: 1, of: '', the: 3, things: 4 },
            { all: 1, of: 2, the: '', things: 4 },
            { all: 1, of: 2, the: 3, things: '' },
            {},
          ],
        },
        {
          key: 'nothotdog',
          data: { foo: 'bar' },
        },
      ],
    };

    const builderSync = buildValidators(values);
    const builderAsync = buildValidatorsAsync(values);

    [builderSync, builderAsync].forEach(builder => {
      const { arrayForEach } = builder;
      const outerArrayItemValidator: ArrayItemValidator = itemBuilder => {
        itemBuilder.field('key', 'Item key').validate([isRequired()]);
        itemBuilder.field('data', 'Item data').validate([isRequired(), isArray, arrayForEach(allOfTheThingsValidator)]);
      };
      builder.field('letsgetcrazy', 'Outer array').validate([arrayForEach(outerArrayItemValidator)]);
    });

    const expectedResult = {
      letsgetcrazy: [
        { key: 'Item key is required.', data: 'Item data is required.' },
        {
          data: [
            undefined,
            { all: 'All is required.' },
            { of: 'Of is required.' },
            { the: 'The is required.' },
            { things: 'Things is required.' },
            {
              all: 'All is required.',
              of: 'Of is required.',
              the: 'The is required.',
              things: 'Things is required.',
            },
          ],
        },
        { data: 'Item data must be an array.' },
      ],
    };

    const resultSync = builderSync.result();
    const resultAsync = builderAsync.result();

    expect(resultSync).toEqual(expectedResult);
    resultAsync.catch((errors: any) => {
      expect(errors).toEqual(expectedResult);
      done();
    });
  });
});

describe('Asynchronous simple validation', () => {
  it('Validate nothing', done => {
    const values = { foo: 'bar' };

    const builder = buildValidatorsAsync(values);
    builder.field('foo', 'Foo').validate([]);

    builder.result().then((result: any) => {
      expect(result).toEqual({});
      done();
    });
  });

  it('Validate mixed sync/async', done => {
    const values = { bar: 1, baz: 2 };

    const builder = buildValidatorsAsync(values);
    builder.field('foo', 'Foo').validate([isRequired()]);
    builder.field('bar', 'Bar').validate([makeAsync(isRequired()), minValue(2)]);
    builder.field('baz', 'Baz').validate([isRequired(), makeAsync(maxValue(1))]);

    builder.result().catch((result: any) => {
      expect(result).toEqual({
        foo: 'Foo is required.',
        bar: 'Bar cannot be less than 2',
        baz: 'Baz cannot be greater than 1',
      });
      done();
    });
  });
});

describe('Asynchronous array validation', () => {
  it('Simple array validation', done => {
    const values = {
      lotsastuff: [1, 2, 3, 4, 5],
    };
    const builder = buildValidators(values, true);
    const { arrayForEach } = builder;

    builder.field('lotsastuff', 'Array').validate([
      makeAsync(isRequired()),
      arrayForEach(itemBuilder => {
        itemBuilder.item('Item').validate([makeAsync(isRequired()), makeAsync(maxValue(3))]);
      }),
    ]);

    builder.result().catch((result: any) => {
      expect(result).toEqual({
        lotsastuff: [undefined, undefined, undefined, 'Item cannot be greater than 3', 'Item cannot be greater than 3'],
      });
      done();
    });
  });
});

describe('Errors', () => {
  it('Sneaking a promise into synchronous validation', () => {
    const values = { foo: 'bar' };

    const builder = buildValidators(values);

    expect(() => {
      builder.field('foo', 'Foo').validate([isRequired(), () => Promise.resolve(undefined)]);
    }).toThrowError(
      Error,
      'Synchronous validator cannot return a Promise (while validating foo). Use buildValidatorsAsync(values) instead.',
    );
    expect(() => {
      builder.field('bar', 'Bar').validate([() => Promise.reject('Bars should be awesome.')]);
    }).toThrowError(
      Error,
      'Synchronous validator cannot return a Promise (while validating bar). Use buildValidatorsAsync(values) instead.',
    );
  });

  it('Made one too many promises', done => {
    const values = {
      lotsastuff: [1, 2, 3, 4, 5],
    };
    const builder = buildValidators(values, true);
    const { arrayForEach } = builder;

    builder.field('lotsastuff', 'Array').validate([
      makeAsync(isRequired()),
      makeAsync(
        arrayForEach(itemBuilder => {
          itemBuilder.item('Item').validate([makeAsync(isRequired()), makeAsync(maxValue(3))]);
        }),
      ),
    ]);

    builder.result().catch((error: any) => {
      expect(error).toEqual(
        new Error(
          'Warning: caught nested Promise while validating lotsastuff. Async Validators should only be rejecting undefined or string, not Promises.',
        ),
      );
      done();
    });
  });
});
