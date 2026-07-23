import { mount } from 'enzyme';
import React from 'react';
import { Observable } from 'rxjs';

import { PagerDutySelectField } from './PagerDutySelectField';
import type { IPagerDutyService } from './pagerDuty.read.service';
import { PagerDutyReader } from './pagerDuty.read.service';
import { SETTINGS } from '../config/settings';
import { HelpField } from '../help/HelpField';
import { ReactSelectInput } from '../presentation';
import { SchedulerFactory } from '../scheduler/SchedulerFactory';

describe('PagerDutySelectField', () => {
  let reload: () => void;
  let schedulerSubscription: { unsubscribe: jasmine.Spy };
  let scheduler: { subscribe: jasmine.Spy; scheduleImmediate: jasmine.Spy; unsubscribe: jasmine.Spy };
  let serviceObserver: any;
  let readerUnsubscribes: jasmine.Spy[];
  let originalPagerDuty: typeof SETTINGS.pagerDuty;

  beforeEach(() => {
    originalPagerDuty = SETTINGS.pagerDuty;
    SETTINGS.pagerDuty = { required: true } as any;
    readerUnsubscribes = [];
    schedulerSubscription = { unsubscribe: jasmine.createSpy('scheduler subscription unsubscribe') };
    scheduler = {
      scheduleImmediate: jasmine.createSpy('scheduleImmediate'),
      subscribe: jasmine.createSpy('subscribe').and.callFake((callback: () => void) => {
        reload = callback;
        return schedulerSubscription;
      }),
      unsubscribe: jasmine.createSpy('scheduler unsubscribe'),
    };
    spyOn(SchedulerFactory, 'createScheduler').and.returnValue(scheduler as any);
    spyOn(PagerDutyReader, 'listServices').and.callFake(() => {
      const unsubscribe = jasmine.createSpy('reader unsubscribe');
      readerUnsubscribes.push(unsubscribe);
      return new Observable<IPagerDutyService[]>((observer) => {
        serviceObserver = observer;
        return unsubscribe;
      });
    });
  });

  afterEach(() => {
    SETTINGS.pagerDuty = originalPagerDuty;
  });

  it('loads services on mount, filters missing integration keys, and renders required help', () => {
    const wrapper = mount(<PagerDutySelectField value={null} onChange={() => undefined} />);
    serviceObserver.next([
      { integration_key: 'key-one', name: 'Service one' },
      { integration_key: '', name: 'Unavailable' },
    ] as IPagerDutyService[]);
    wrapper.update();

    expect(SchedulerFactory.createScheduler).toHaveBeenCalledWith(10000);
    expect(PagerDutyReader.listServices).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain('PagerDuty *');
    expect(wrapper.find(HelpField).prop('content')).toContain('Generic API');
    expect(wrapper.find(ReactSelectInput).prop('options')).toEqual([{ label: 'Service one', value: 'key-one' }]);
  });

  it('emits the selected service integration key', () => {
    const onChange = jasmine.createSpy('onChange');
    const wrapper = mount(<PagerDutySelectField value={null} onChange={onChange} />);
    serviceObserver.next([{ integration_key: 'key-one', name: 'Service one' }] as IPagerDutyService[]);
    wrapper.update();

    wrapper.find(ReactSelectInput).prop('onChange')({ target: { value: 'key-one' } } as any);

    expect(onChange).toHaveBeenCalledWith('key-one');
  });

  it('replaces the active reader subscription on scheduled reload', () => {
    mount(<PagerDutySelectField value={null} onChange={() => undefined} />);

    reload();

    expect(readerUnsubscribes[0]).toHaveBeenCalled();
    expect(PagerDutyReader.listServices).toHaveBeenCalledTimes(2);
  });

  it('unsubscribes the reader and scheduler on unmount', () => {
    const wrapper = mount(<PagerDutySelectField value={null} onChange={() => undefined} />);

    wrapper.unmount();

    expect(readerUnsubscribes[0]).toHaveBeenCalled();
    expect(schedulerSubscription.unsubscribe).toHaveBeenCalled();
    expect(scheduler.unsubscribe).toHaveBeenCalled();
  });
});
