import { range, some } from 'lodash';

import { RecentHistoryService } from '../history/recentHistory.service';
import { DeckCacheFactory, ICache } from '../cache/deckCacheFactory';

describe('recent history service', () => {
  let backingCache: ICache;

  beforeEach(() => {
    DeckCacheFactory.clearCache('history', 'user');
    backingCache = DeckCacheFactory.getCache('history', 'user');
  });

  describe('getItems returns items most recent first', () => {
    it('returns an empty list if none found', () => {
      expect(RecentHistoryService.getItems('something')).toEqual([]);
    });

    it('returns whatever is in cache if something is found, ordered by accessTime', () => {
      backingCache.put('whatever', [{ accessTime: 1 }, { accessTime: 3 }, { accessTime: 2 }]);
      expect(
        RecentHistoryService.getItems('whatever').map((item) => {
          return item.accessTime;
        }),
      ).toEqual([3, 2, 1]);
    });
  });

  describe('addItem is fancy', () => {
    function initializeCache(count: number) {
      const start = Date.now() - 1;
      const currentItems = range(0, count).map((idx: number) => {
        return { id: '' + idx, params: { id: idx }, accessTime: start - idx };
      });
      backingCache.put('whatever', currentItems);
    }

    it('puts items in cache most recent first', () => {
      initializeCache(2);
      RecentHistoryService.addItem('whatever', 'state', { id: 'new item' });
      const ids = RecentHistoryService.getItems('whatever').map((item) => {
        return item.params.id;
      });
      expect(ids).toEqual(['new item', 0, 1]);
    });

    it('replaces oldest item if cache is full', () => {
      initializeCache(15);
      RecentHistoryService.addItem('whatever', 'state', { id: 'new item' });
      const ids = RecentHistoryService.getItems('whatever').map((item) => {
        return item.params.id;
      });
      expect(ids).toEqual(['new item', 0, 1, 2, 3]);
    });

    it('removes previous entry and adds replacement if params match, ignoring undefined values', () => {
      initializeCache(3);
      RecentHistoryService.addItem('whatever', 'state', { id: 1, someUndefinedValue: undefined });
      const ids = RecentHistoryService.getItems('whatever').map((item) => {
        return item.params.id;
      });
      expect(ids).toEqual([1, 0, 2]);
    });

    it('only matches on specified params if supplied', () => {
      const start = Date.now() - 1;
      const currentItems = range(0, 3).map((idx: number) => {
        return { params: { id: idx, importantParam: idx, ignoredParam: idx + 1 }, accessTime: start - idx };
      });
      backingCache.put('whatever', currentItems);
      RecentHistoryService.addItem('whatever', 'state', { id: 1, importantParam: 1, ignoredParam: 1000 }, [
        'importantParam',
        'id',
      ]);
      let ids = RecentHistoryService.getItems('whatever').map((item) => {
        return item.params.id;
      });
      expect(ids).toEqual([1, 0, 2]);
      RecentHistoryService.addItem('whatever', 'state', { id: 1, importantParam: 1, ignoredParam: 1001 }, [
        'ignoredParam',
      ]);
      ids = RecentHistoryService.getItems('whatever').map((item) => {
        return item.params.id;
      });
      expect(ids).toEqual([1, 1, 0, 2]);
    });
  });

  describe('remove item from history cache by app name', () => {
    beforeEach(() => {
      const start = Date.now() - 1;
      const currentItems = ['foo', 'bar', 'baz'].map((appName, index) => {
        return { params: { application: appName, accessTime: start - index } };
      });
      backingCache.put('applications', currentItems);
    });

    it('should have 3 items in the "applications" cache', () => {
      const items = RecentHistoryService.getItems('applications');
      expect(items.length).toBe(3);
    });

    it('should have 2 items in the "application" cache when we remove "foo" by application name', () => {
      RecentHistoryService.removeByAppName('foo');
      const items = RecentHistoryService.getItems('applications');
      expect(items.length).toBe(2);
      expect(some(items, { params: { application: 'foo' } })).toBeFalsy();
    });
  });
});
