import { WarmPoolService } from './WarmPoolService';
import type { IAmazonServerGroup } from '../../../domain';

describe('WarmPoolService', () => {
  describe('getWarmPoolConfiguration', () => {
    it('returns undefined if no asg or warmPoolConfiguration present on server group', () => {
      expect(WarmPoolService.getWarmPoolConfiguration({} as IAmazonServerGroup)).toBeUndefined();
      expect(WarmPoolService.getWarmPoolConfiguration({ asg: {} } as IAmazonServerGroup)).toBeUndefined();
    });

    it('returns the warm pool configuration when present', () => {
      const asg = { warmPoolConfiguration: { minSize: 2, poolState: 'Stopped' } } as any;
      expect(WarmPoolService.getWarmPoolConfiguration({ asg } as IAmazonServerGroup)).toEqual({
        minSize: 2,
        poolState: 'Stopped',
      });
    });
  });

  describe('isEnabled', () => {
    it('returns false when no warm pool is configured', () => {
      expect(WarmPoolService.isEnabled({ asg: {} } as IAmazonServerGroup)).toBe(false);
    });

    it('returns true when a warm pool is configured', () => {
      const asg = { warmPoolConfiguration: { minSize: 1 } } as any;
      expect(WarmPoolService.isEnabled({ asg } as IAmazonServerGroup)).toBe(true);
    });
  });
});
