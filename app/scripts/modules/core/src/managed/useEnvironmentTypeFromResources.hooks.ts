import { $q } from 'ngimport';
import { uniq } from 'lodash';

import { IManagedResourceSummary } from '../domain';
import { AccountService } from 'core/account';
import { useData } from 'core/presentation';

export const useEnvironmentTypeFromResources = (resources: IManagedResourceSummary[]): boolean => {
  const accountNames = uniq(resources.map((resource) => resource?.locations?.account).filter(Boolean));
  return useData<boolean>(
    () =>
      $q
        .all(accountNames.map((accountName) => AccountService.challengeDestructiveActions(accountName)))
        .then((result) => result.some((value) => !!value)),
    false,
    [accountNames.sort().join()],
  ).result;
};
