import { Validators } from 'core/presentation/forms/validation/validators';
import { buildValidators, IValidator, IArrayItemValidator } from './validation';

const { maxValue, arrayNotEmpty } = Validators;

describe('Synchronous validation', () => {
  it('returns empty errors when validating no validators for an optional field', () => {
    const values = { foo: 'bar' };

    const builder = buildValidators(values);
    builder.field('foo', 'Foo').optional([]);

    const result = builder.result();
    const expectedResult = {};
    expect(result).toEqual(expectedResult);
  });

  it('returns correct error when validating an optional top level field', () => {
    const values = { foo: 42 };

    const builder = buildValidators(values);
    builder.field('foo', 'Foo').optional([maxValue(1)]);

    const result = builder.result();
    const expectedResult = {
      foo: 'Foo cannot be greater than 1',
    };
    expect(result).toEqual(expectedResult);
  });

  it('returns no error when optionally validating a deep field that is absent', () => {
    const values = {};

    const builder = buildValidators(values);
    builder.field('foo.bar.baz', 'Foo').optional([maxValue(1)]);

    const result = builder.result();
    const expectedResult = {};
    expect(result).toEqual(expectedResult);
  });

  it('returns correct error when optionally validating a deep field', () => {
    const values = { foo: { bar: { baz: 42 } } };

    const builder = buildValidators(values);
    builder.field('foo.bar.baz', 'Foo').optional([maxValue(1)]);

    const result = builder.result();
    const expectedResult = {
      foo: {
        bar: {
          baz: 'Foo cannot be greater than 1',
        },
      },
    };
    expect(result).toEqual(expectedResult);
  });

  it('aggregates multiple levels of errors correctly', () => {
    const values = {};

    const builder = buildValidators(values);
    builder.field('foo', 'Foo').required();
    builder.field('bar.baz', 'Baz').required();

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
    const { arrayForEach } = builder;

    builder.field('lotsastuff', 'Array').required([
      arrayNotEmpty(),
      arrayForEach(itemBuilder => {
        itemBuilder.item('Item').required([maxValue(3)]);
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
    const { arrayForEach } = builder;

    builder.field('lotsastuff', 'Array').required([
      arrayNotEmpty(),
      arrayForEach(itemBuilder => {
        itemBuilder.item('Item').required();
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
    builder.field('lotsastuff', 'Array').required([
      (array, label) => array.length < 1 && `${label} must have at least 1 item.`,
      arrayForEach(itemBuilder => {
        itemBuilder.field(`key`, `Item Key`).required();
        itemBuilder.field(`value`, `Item Value`).required();
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
    const isArray: IValidator = (array, label) => !Array.isArray(array) && `${label} must be an array.`;
    const allOfTheThingsValidator: IArrayItemValidator = itemBuilder => {
      itemBuilder.field(`all`, 'All').required();
      itemBuilder.field(`of`, 'Of').required();
      itemBuilder.field(`the`, 'The').required();
      itemBuilder.field(`things`, 'Things').required();
    };

    const outerArrayItemValidator: IArrayItemValidator = itemBuilder => {
      itemBuilder.field('key', 'Item key').required();
      itemBuilder.field('data', 'Item data').required([isArray, arrayForEach(allOfTheThingsValidator)]);
    };

    const { arrayForEach } = builder;
    builder.field('letsgetcrazy', 'Outer array').optional([arrayForEach(outerArrayItemValidator)]);

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
