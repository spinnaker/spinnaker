import { NameUtils } from '../naming';

export interface IClusterMatchRule {
  account: string;
  location: string;
  stack: string;
  detail: string;
  priority?: number;
}

export interface IClusterMatcher {
  getMatchingRule(
    account: string,
    location: string,
    clusterName: string,
    rules: IClusterMatchRule[],
  ): IClusterMatchRule;
}

export class DefaultClusterMatcher implements IClusterMatcher {
  public getMatchingRule(
    account: string,
    location: string,
    clusterName: string,
    rules: IClusterMatchRule[],
  ): IClusterMatchRule {
    if (!rules || !rules.length) {
      return null;
    }
    const nameParts = NameUtils.parseClusterName(clusterName);

    const matchedRules = rules
      .filter((r) => {
        const ruleAccount = r.account || '';
        const ruleLocation = r.location || '';
        const ruleStack = r.stack || '';
        const ruleDetail = r.detail || '';
        const stack = nameParts.stack || '';
        const detail = nameParts.freeFormDetails || '';
        return (
          ('*' === ruleAccount || account === ruleAccount) &&
          ('*' === ruleLocation || location === ruleLocation) &&
          ('*' === ruleStack || stack === ruleStack) &&
          ('*' === ruleDetail || detail === ruleDetail)
        );
      })
      .sort((a, b) => {
        if (a.account !== b.account) {
          return a.account === '*' ? 1 : -1;
        }
        if (a.location !== b.location) {
          return a.location === '*' ? 1 : -1;
        }
        if (a.stack !== b.stack) {
          return a.stack === '*' ? 1 : -1;
        }
        if (a.detail !== b.detail) {
          return a.detail === '*' ? 1 : -1;
        }
        return a.priority - b.priority;
      });

    return matchedRules.length ? matchedRules[0] : null;
  }
}

export const ClusterMatcher = new DefaultClusterMatcher();
