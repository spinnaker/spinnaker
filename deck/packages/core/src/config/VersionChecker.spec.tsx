import { VersionChecker } from './VersionChecker';
import type { IScheduler } from '../scheduler/SchedulerFactory';
import { SchedulerFactory } from '../scheduler/SchedulerFactory';

describe('VersionChecker', () => {
  beforeEach(() => VersionChecker.resetForTests());
  afterEach(() => VersionChecker.resetForTests());

  it('creates and subscribes one scheduler when initialized repeatedly', () => {
    const scheduler: IScheduler = {
      scheduleImmediate: jasmine.createSpy('scheduleImmediate'),
      subscribe: jasmine.createSpy('subscribe'),
      unsubscribe: jasmine.createSpy('unsubscribe'),
    };
    const createScheduler = spyOn(SchedulerFactory, 'createScheduler').and.returnValue(scheduler);

    VersionChecker.initialize();
    VersionChecker.initialize();

    expect(createScheduler).toHaveBeenCalledTimes(1);
    expect(scheduler.subscribe).toHaveBeenCalledTimes(1);
  });

  it('unsubscribes the active scheduler and permits fresh initialization after reset', () => {
    const scheduler: IScheduler = {
      scheduleImmediate: jasmine.createSpy('scheduleImmediate'),
      subscribe: jasmine.createSpy('subscribe'),
      unsubscribe: jasmine.createSpy('unsubscribe'),
    };
    const createScheduler = spyOn(SchedulerFactory, 'createScheduler').and.returnValue(scheduler);

    VersionChecker.initialize();
    VersionChecker.resetForTests();
    VersionChecker.initialize();

    expect(scheduler.unsubscribe).toHaveBeenCalledTimes(1);
    expect(createScheduler).toHaveBeenCalledTimes(2);
  });
});
