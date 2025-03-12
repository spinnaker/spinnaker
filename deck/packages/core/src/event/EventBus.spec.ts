import { EventBus } from './EventBus';

describe('EventBus', () => {
  describe('observe', () => {
    it('only picks up messages with the specified key', () => {
      const results: number[] = [];
      const subscription = EventBus.observe<number>('z').subscribe((n) => results.push(n));
      EventBus.publish('a', 1);
      EventBus.publish('z', 2);
      EventBus.publish('b', 3);
      EventBus.publish('z', 4);
      expect(results).toEqual([2, 4]);
      subscription.unsubscribe();
    });
  });
});
