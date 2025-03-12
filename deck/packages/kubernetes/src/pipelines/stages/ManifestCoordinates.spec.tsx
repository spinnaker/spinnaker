import { formatLabelSelectors, SelectorKind } from './ManifestCoordinates';

describe('<ManifestCoordinates />', () => {
  it('handles ANY', () => {
    const selectors = {
      selectors: [
        {
          kind: SelectorKind.ANY,
        },
      ],
    };
    expect(formatLabelSelectors(selectors)).toEqual('');
  });

  it('handles EQUALS', () => {
    const selectors = {
      selectors: [
        {
          key: 'environment',
          kind: SelectorKind.EQUALS,
          values: ['prod'],
        },
      ],
    };
    expect(formatLabelSelectors(selectors)).toEqual('environment = prod');
  });

  it('handles NOT_EQUALS', () => {
    const selectors = {
      selectors: [
        {
          key: 'environment',
          kind: SelectorKind.NOT_EQUALS,
          values: ['prod'],
        },
      ],
    };
    expect(formatLabelSelectors(selectors)).toEqual('environment != prod');
  });

  it('handles CONTAINS', () => {
    const selectors = {
      selectors: [
        {
          key: 'environment',
          kind: SelectorKind.CONTAINS,
          values: ['prod', 'staging'],
        },
      ],
    };
    expect(formatLabelSelectors(selectors)).toEqual('environment in (prod, staging)');
  });

  it('handles NOT_CONTAINS', () => {
    const selectors = {
      selectors: [
        {
          key: 'environment',
          kind: SelectorKind.NOT_CONTAINS,
          values: ['prod', 'staging'],
        },
      ],
    };
    expect(formatLabelSelectors(selectors)).toEqual('environment notin (prod, staging)');
  });

  it('handles EXISTS', () => {
    const selectors = {
      selectors: [
        {
          key: 'environment',
          kind: SelectorKind.EXISTS,
        },
      ],
    };
    expect(formatLabelSelectors(selectors)).toEqual('environment');
  });

  it('handles NOT_EXISTS', () => {
    const selectors = {
      selectors: [
        {
          key: 'environment',
          kind: SelectorKind.NOT_EXISTS,
        },
      ],
    };
    expect(formatLabelSelectors(selectors)).toEqual('!environment');
  });
});
