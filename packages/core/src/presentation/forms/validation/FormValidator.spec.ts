import { Validators } from './validators';
import { FormValidator } from './FormValidator';
import { IValidator, IArrayItemValidator } from './validation';

const { maxValue, arrayNotEmpty } = Validators;

describe('FormValidator validation', () => {
  describe('of optional fields', () => {
    it('returns empty errors when the value is present', () => {
      const values = { foo: 'bar' };
      const formValidator = new FormValidator(values);
      formValidator.field('foo', 'Foo').optional();

      const result = formValidator.validateForm();
      const expectedResult = {};
      expect(result).toEqual(expectedResult);
    });

    it('returns empty errors when the value is missing', () => {
      const values = {};
      const formValidator = new FormValidator(values);
      formValidator.field('foo', 'Foo').optional();

      const result = formValidator.validateForm();
      const expectedResult = {};
      expect(result).toEqual(expectedResult);
    });

    it('returns empty errors when the value is the empty string', () => {
      const values = { foo: '' };
      const formValidator = new FormValidator(values);
      formValidator.field('foo', 'Foo').optional();

      const result = formValidator.validateForm();
      const expectedResult = {};
      expect(result).toEqual(expectedResult);
    });

    it('returns correct error when the value is invalid', () => {
      const values = { foo: 42 };
      const formValidator = new FormValidator(values);
      formValidator.field('foo', 'Foo').optional().withValidators(maxValue(1));

      const result = formValidator.validateForm();
      const expectedResult = {
        foo: 'Foo cannot be greater than 1',
      };
      expect(result).toEqual(expectedResult);
    });

    it('returns empty errors when optionally validating a deep field that is absent', () => {
      const values = {};
      const formValidator = new FormValidator(values);
      formValidator.field('foo.bar.baz', 'Foo').optional().withValidators(maxValue(1));

      const result = formValidator.validateForm();
      const expectedResult = {};
      expect(result).toEqual(expectedResult);
    });

    it('returns correct error when optionally validating a deep field', () => {
      const values = { foo: { bar: { baz: 42 } } };
      const formValidator = new FormValidator(values);
      formValidator.field('foo.bar.baz', 'Foo').optional().withValidators(maxValue(1));

      const result = formValidator.validateForm();
      const expectedResult = {
        foo: {
          bar: {
            baz: 'Foo cannot be greater than 1',
          },
        },
      };
      expect(result).toEqual(expectedResult);
    });
  });

  describe('of required fields', () => {
    it('returns empty errors when the value is present', () => {
      const values = { foo: 'bar' };
      const formValidator = new FormValidator(values);
      formValidator.field('foo', 'Foo').required();

      const result = formValidator.validateForm();
      const expectedResult = {};
      expect(result).toEqual(expectedResult);
    });

    it('returns an error when the value is missing', () => {
      const values = {};
      const formValidator = new FormValidator(values);
      formValidator.field('foo', 'Foo').required();

      const result = formValidator.validateForm();
      const expectedResult = { foo: 'Foo is required.' };
      expect(result).toEqual(expectedResult);
    });

    it('returns a custom error message', () => {
      const values = { foo: '' };
      const formValidator = new FormValidator(values);
      formValidator.field('foo', 'Foo').required('GIVE FOO');

      const result = formValidator.validateForm();
      const expectedResult = { foo: 'GIVE FOO' };
      expect(result).toEqual(expectedResult);
    });

    it('returns correct error when the value is invalid', () => {
      const values = { foo: 42 };
      const formValidator = new FormValidator(values);
      formValidator.field('foo', 'Foo').required().withValidators(maxValue(1));

      const result = formValidator.validateForm();
      const expectedResult = {
        foo: 'Foo cannot be greater than 1',
      };
      expect(result).toEqual(expectedResult);
    });
  });

  describe('spel aware fields', () => {
    it('returns the correct error if the value is invalid', () => {
      const values = { foo: 42 };
      const formValidator = new FormValidator(values);
      formValidator.field('foo', 'Foo').spelAware().withValidators(maxValue(1));

      const result = formValidator.validateForm();
      const expectedResult = {
        foo: 'Foo cannot be greater than 1',
      };
      expect(result).toEqual(expectedResult);
    });

    it('short circuits validation if the value contains SpEL', () => {
      const values = { foo: '${parameters.foo}' };
      const formValidator = new FormValidator(values);
      formValidator.field('foo', 'Foo').spelAware().withValidators(maxValue(1));

      const result = formValidator.validateForm();
      const expectedResult = {};
      expect(result).toEqual(expectedResult);
    });

    it('does not change the behavior of .required()', () => {
      const values = {};
      const formValidator = new FormValidator(values);
      formValidator.field('foo', 'Foo').required().spelAware().withValidators(maxValue(1));

      const result = formValidator.validateForm();
      const expectedResult = { foo: 'Foo is required.' };
      expect(result).toEqual(expectedResult);
    });
  });

  describe('FormValidator.spelAware()', () => {
    it('applies spelAware(true) value to all ValidatableFields', () => {
      const values = { foo: '${spel}', bar: '${spel}' };
      const formValidator = new FormValidator(values).spelAware();
      formValidator.field('foo').withValidators(maxValue(1));
      formValidator.field('bar').withValidators(maxValue(1));

      const result = formValidator.validateForm();
      const expectedResult = {};
      expect(result).toEqual(expectedResult);
    });

    it('applies spelAware(false) value to all ValidatableFields', () => {
      const values = { foo: '${spel}', bar: '${spel}' };
      const formValidator = new FormValidator(values).spelAware(false);
      formValidator.field('foo').withValidators(maxValue(1));
      formValidator.field('bar').withValidators(maxValue(1));

      const result = formValidator.validateForm();
      const expectedResult = {
        foo: 'Foo must be a number',
        bar: 'Bar must be a number',
      };
      expect(result).toEqual(expectedResult);
    });

    it('set to true can be overriden by a field calling .spelAware()', () => {
      const values = { foo: '${spel}', bar: '${spel}' };
      const formValidator = new FormValidator(values).spelAware();
      formValidator.field('foo').withValidators(maxValue(1)).spelAware(false);
      formValidator.field('bar').withValidators(maxValue(1));

      const result = formValidator.validateForm();
      const expectedResult = { foo: 'Foo must be a number' };
      expect(result).toEqual(expectedResult);
    });

    it('set to false can be overriden by a field calling .spelAware(false', () => {
      const values = { foo: '${spel}', bar: '${spel}' };

      const formValidator = new FormValidator(values).spelAware(false);
      formValidator.field('foo').withValidators(maxValue(1)).spelAware();
      formValidator.field('bar').withValidators(maxValue(1));

      const result = formValidator.validateForm();
      const expectedResult = { bar: 'Bar must be a number' };
      expect(result).toEqual(expectedResult);
    });
  });

  it('.field() without a label argument derives a label from the field name', () => {
    const values = {};
    const formValidator = new FormValidator(values);
    formValidator.field('foo').required();
    formValidator.field('bar').required();
    formValidator.field('camelCase').required();
    formValidator.field('camelCaseWithLotsOfHumps').required();

    const result = formValidator.validateForm();
    const expectedResult = {
      foo: 'Foo is required.',
      bar: 'Bar is required.',
      camelCase: 'Camel Case is required.',
      camelCaseWithLotsOfHumps: 'Camel Case With Lots Of Humps is required.',
    };

    expect(result).toEqual(expectedResult);
  });

  it('aggregates multiple levels of errors correctly', () => {
    const values = {};
    const formValidator = new FormValidator(values);
    formValidator.field('foo', 'Foo').required();
    formValidator.field('bar.baz', 'Baz').required();

    const result = formValidator.validateForm();
    const expectedResult = {
      foo: 'Foo is required.',
      bar: {
        baz: 'Baz is required.',
      },
    };
    expect(result).toEqual(expectedResult);
  });

  it('validates arrays and aggregates them correctly', () => {
    const values = { lotsastuff: [1, 2, 3, 4, 5] };
    const formValidator = new FormValidator(values);
    const { arrayForEach } = formValidator;

    formValidator
      .field('lotsastuff', 'Array')
      .required()
      .withValidators(
        arrayNotEmpty(),
        arrayForEach((itemBuilder) => {
          itemBuilder.item('Item').required().withValidators(maxValue(3));
        }),
      );

    const result = formValidator.validateForm();
    const expectedResult = {
      lotsastuff: [undefined, undefined, undefined, 'Item cannot be greater than 3', 'Item cannot be greater than 3'],
    };
    expect(result).toEqual(expectedResult);
  });

  it('arrays without errors should not be aggregated', () => {
    const values = {
      lotsastuff: [1, 2, 3, 4, 5],
    };

    const formValidator = new FormValidator(values);
    const { arrayForEach } = formValidator;

    formValidator
      .field('lotsastuff', 'Array')
      .required()
      .withValidators(
        arrayNotEmpty(),
        arrayForEach((itemBuilder) => {
          itemBuilder.item('Item').required();
        }),
      );

    const result = formValidator.validateForm();
    const expectedResult = {};
    expect(result).toEqual(expectedResult);
  });

  it('validates keys on array items and aggregates errors into resulting arrays correctly', () => {
    const values = {
      lotsastuff: [{ key: 1 }, { value: 2 }, 3, 4, 5],
    };

    const formValidator = new FormValidator(values);
    const { arrayForEach } = formValidator;
    formValidator
      .field('lotsastuff', 'Array')
      .required()
      .withValidators(
        (array, label) => array.length < 1 && `${label} must have at least 1 item.`,
        arrayForEach((itemBuilder) => {
          itemBuilder.field(`key`, `Item Key`).required();
          itemBuilder.field(`value`, `Item Value`).required();
        }),
      );

    const result = formValidator.validateForm();
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

    const formValidator = new FormValidator(values);
    const isArray: IValidator = (array, label) => !Array.isArray(array) && `${label} must be an array.`;
    const allOfTheThingsValidator: IArrayItemValidator = (itemBuilder) => {
      itemBuilder.field(`all`, 'All').required();
      itemBuilder.field(`of`, 'Of').required();
      itemBuilder.field(`the`, 'The').required();
      itemBuilder.field(`things`, 'Things').required();
    };

    const outerArrayItemValidator: IArrayItemValidator = (itemBuilder) => {
      itemBuilder.field('key', 'Item key').required();
      itemBuilder.field('data', 'Item data').required().withValidators(isArray, arrayForEach(allOfTheThingsValidator));
    };

    const { arrayForEach } = formValidator;
    formValidator.field('letsgetcrazy', 'Outer array').optional().withValidators(arrayForEach(outerArrayItemValidator));

    const result = formValidator.validateForm();
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
