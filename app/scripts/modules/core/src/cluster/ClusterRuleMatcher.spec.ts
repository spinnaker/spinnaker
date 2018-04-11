import { DefaultClusterMatcher, IClusterMatcher, IClusterMatchRule } from './ClusterRuleMatcher';

describe('CustomRuleMatcher', () => {
  let matcher: IClusterMatcher;

  const account = 'test';
  const location = 'us-east-1';
  const stack = 'stack';
  const detail = 'detail';
  const clusterName = 'myapp-stack-detail';

  beforeEach(() => {
    matcher = new DefaultClusterMatcher();
  });

  it('returns null when no rules match on account, location, or stack/detail', () => {
    expect(matcher.getMatchingRule(account, location, clusterName, null)).toBe(null);
  });

  it('returns null when no rules match on account, location, or stack/detail', () => {
    const rules: IClusterMatchRule[] = [{ account, location, stack: '*', detail: '*', priority: 1 }];
    expect(matcher.getMatchingRule('prod', location, clusterName, rules)).toBe(null);
  });

  it('returns rule based on location if accounts are identical', () => {
    const expected: IClusterMatchRule = { account, location, stack: '*', detail: '*', priority: 1 };
    const rules: IClusterMatchRule[] = [{ account, location: '*', stack: '*', detail: '*', priority: 1 }, expected];
    expect(matcher.getMatchingRule(account, location, clusterName, rules)).toBe(expected);
  });

  it('returns rule based on stack if account and location are identical', () => {
    const expected: IClusterMatchRule = { account, location, stack, detail: '*', priority: 1 };
    const rules: IClusterMatchRule[] = [{ account, location, stack: '*', detail: '*', priority: 1 }, expected];
    expect(matcher.getMatchingRule(account, location, clusterName, rules)).toBe(expected);
  });

  it('returns rule based on detail if account, location, and stack are identical', () => {
    const expected: IClusterMatchRule = { account, location, stack, detail, priority: 1 };
    const rules: IClusterMatchRule[] = [{ account, location, stack, detail: '*', priority: 1 }, expected];
    expect(matcher.getMatchingRule(account, location, clusterName, rules)).toBe(expected);
  });

  it('returns rule based on priority if all other fields match', () => {
    const expected: IClusterMatchRule = { account, location, stack, detail, priority: 1 };
    const rules: IClusterMatchRule[] = [{ account, location, stack, detail, priority: 2 }, expected];
    expect(matcher.getMatchingRule(account, location, clusterName, rules)).toBe(expected);
  });

  it('specific account takes priority over all other wildcard fields', () => {
    const expected: IClusterMatchRule = { account, location: '*', stack: '*', detail: '*', priority: 1 };
    const rules: IClusterMatchRule[] = [{ account: '*', location, stack, detail, priority: 1 }, expected];
    expect(matcher.getMatchingRule(account, location, clusterName, rules)).toBe(expected);
  });

  it('specific location takes priority over wildcard stack, detail', () => {
    const expected: IClusterMatchRule = { account, location, stack: '*', detail: '*', priority: 1 };
    const rules: IClusterMatchRule[] = [{ account, location: '*', stack, detail, priority: 1 }, expected];
    expect(matcher.getMatchingRule(account, location, clusterName, rules)).toBe(expected);
  });

  it('specific stack takes priority over wildcard detail', () => {
    const expected: IClusterMatchRule = { account, location, stack, detail: '*', priority: 2 };
    const rules: IClusterMatchRule[] = [{ account, location, stack: '*', detail, priority: 1 }, expected];
    expect(matcher.getMatchingRule(account, location, clusterName, rules)).toBe(expected);
  });

  it('specific detail takes priority over priority', () => {
    const expected: IClusterMatchRule = { account, location, stack, detail, priority: 2 };
    const rules: IClusterMatchRule[] = [{ account, location, stack, detail: '*', priority: 1 }, expected];
    expect(matcher.getMatchingRule(account, location, clusterName, rules)).toBe(expected);
  });

  it('handles clusters without account or details values', () => {
    const expected: IClusterMatchRule = { account: '*', location: '*', stack: '*', detail: '*', priority: 1 };
    expect(matcher.getMatchingRule(account, location, 'myapp', [expected])).toBe(expected);
  });

  it('handles rules without account or details values, preferring them to wildcards', () => {
    let expected: IClusterMatchRule = { account: '*', location: '*', stack: null, detail: null, priority: 1 };
    expect(matcher.getMatchingRule(account, location, 'myapp', [expected])).toBe(expected);

    expected = { account: '*', location: '*', stack: '', detail: '', priority: 1 };
    expect(matcher.getMatchingRule(account, location, 'myapp', [expected])).toBe(expected);
  });
});
