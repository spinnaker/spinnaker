import { IInstanceCounts } from 'core/domain';

export const mockInstanceCounts: IInstanceCounts = {
  total: 1,
  up: 1,
  down: 0,
  unknown: 0,
  outOfService: 0,
  starting: 0,
  succeeded: 1,
  failed: 1,
};
