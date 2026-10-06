import { act, render, screen } from '@testing-library/react';
import { setupUser } from '../utils/testUtils/userEvent';
import React from 'react';
import { Observable, Subscription } from 'rxjs';
import type { Subscriber } from 'rxjs';
import type { Mock } from 'vitest';

import { PagerDutySelectField } from './PagerDutySelectField';
import type { IPagerDutyService } from './pagerDuty.read.service';
import { PagerDutyReader } from './pagerDuty.read.service';
import { SETTINGS } from '../config/settings';
import type { IScheduler } from '../scheduler/SchedulerFactory';
import { SchedulerFactory } from '../scheduler/SchedulerFactory';

describe('PagerDutySelectField', () => {
  let reload: () => void;
  let schedulerSubscription: Subscription;
  let scheduler: IScheduler;
  let serviceObservers: Array<Subscriber<IPagerDutyService[]>>;
  let readerUnsubscribes: Mock[];
  let originalPagerDuty: typeof SETTINGS.pagerDuty;
  let offsetWidthSpy: Mock;

  beforeEach(() => {
    originalPagerDuty = SETTINGS.pagerDuty;
    SETTINGS.pagerDuty = { required: true };
    offsetWidthSpy = vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(300);
    serviceObservers = [];
    readerUnsubscribes = [];
    schedulerSubscription = new Subscription();
    vi.spyOn(schedulerSubscription, 'unsubscribe');
    scheduler = {
      scheduleImmediate: vi.fn(),
      subscribe: vi.fn().mockImplementation((callback: () => void) => {
        reload = callback;
        return schedulerSubscription;
      }),
      unsubscribe: vi.fn(),
    };
    vi.spyOn(SchedulerFactory, 'createScheduler').mockReturnValue(scheduler);
    vi.spyOn(PagerDutyReader, 'listServices').mockImplementation(() => {
      const unsubscribe = vi.fn();
      readerUnsubscribes.push(unsubscribe);
      return new Observable<IPagerDutyService[]>((observer) => {
        serviceObservers.push(observer);
        return unsubscribe;
      });
    });
  });

  afterEach(() => {
    SETTINGS.pagerDuty = originalPagerDuty;
    offsetWidthSpy.mockRestore();
  });

  it('loads services on mount, filters missing integration keys, and renders required help', async () => {
    const user = setupUser();
    const { container } = render(<PagerDutySelectField value={null} onChange={() => undefined} />);
    act(() => {
      serviceObservers[0].next([
        { integration_key: 'key-one', name: 'Service one' },
        { integration_key: '', name: 'Unavailable' },
      ] as IPagerDutyService[]);
    });

    expect(SchedulerFactory.createScheduler).toHaveBeenCalledWith(10000);
    expect(PagerDutyReader.listServices).toHaveBeenCalledTimes(1);
    expect(screen.getByText('PagerDuty *')).toBeInTheDocument();
    await user.hover(container.querySelector('.help-field') as HTMLElement);
    expect(await screen.findByText(/Generic API/)).toBeInTheDocument();
    await user.click(screen.getByLabelText('PagerDuty service'));
    expect(screen.getByRole('option', { name: 'Service one' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Unavailable' })).not.toBeInTheDocument();
  });

  it('emits the selected service integration key', async () => {
    const user = setupUser();
    const onChange = vi.fn();
    render(<PagerDutySelectField value={null} onChange={onChange} />);
    act(() => {
      serviceObservers[0].next([{ integration_key: 'key-one', name: 'Service one' }] as IPagerDutyService[]);
    });

    await user.click(screen.getByLabelText('PagerDuty service'));
    await user.click(screen.getByRole('option', { name: 'Service one' }));

    expect(onChange).toHaveBeenCalledWith('key-one');
  });

  it('replaces the active reader subscription on scheduled reload', () => {
    render(<PagerDutySelectField value={null} onChange={() => undefined} />);

    act(() => reload());

    expect(readerUnsubscribes[0]).toHaveBeenCalledTimes(1);
    expect(PagerDutyReader.listServices).toHaveBeenCalledTimes(2);
  });

  it('unsubscribes the reader and scheduler on unmount', () => {
    const { unmount } = render(<PagerDutySelectField value={null} onChange={() => undefined} />);

    unmount();

    expect(readerUnsubscribes[0]).toHaveBeenCalledTimes(1);
    expect(schedulerSubscription.unsubscribe).toHaveBeenCalledTimes(1);
    expect(scheduler.unsubscribe).toHaveBeenCalledTimes(1);
  });
});
