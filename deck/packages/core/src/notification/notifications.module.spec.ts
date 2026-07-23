import { mock } from 'angular';
import React from 'react';

import type { INotificationSettings } from '../config';
import { SETTINGS } from '../config';
import { Registry } from '../registry';

import type { INotificationParameter } from './NotificationService';
import { NotificationService } from './NotificationService';
import { BUILTIN_NOTIFICATION_KEYS, registerBuiltinNotificationTypes } from './notification.types';
import {
  CORE_NOTIFICATION_NOTIFICATIONS_MODULE,
  initializeDynamicNotificationTypes,
  registerDynamicNotificationTypes,
} from './notifications.module';

describe('notification runtime registration', () => {
  const originalNotifications = SETTINGS.notifications;
  let originalPipeline: typeof Registry.pipeline;
  let originalUrlBuilder: typeof Registry.urlBuilder;

  beforeEach(() => {
    originalPipeline = Registry.pipeline;
    originalUrlBuilder = Registry.urlBuilder;
    Registry.reinitialize();
  });

  afterEach(() => {
    Registry.pipeline = originalPipeline;
    Registry.urlBuilder = originalUrlBuilder;
    SETTINGS.notifications = originalNotifications;
  });

  it('does not register built-in notifications when notification types are imported', () => {
    const notificationTypesModule = require.resolve('./notification.types');
    delete require.cache[notificationTypesModule];

    require('./notification.types');

    expect(Registry.pipeline.getNotificationTypes()).toEqual([]);
  });

  it('preserves built-in notification settings filtering and config merging without duplicates', () => {
    SETTINGS.notifications = {
      email: { enabled: false },
      githubStatus: { enabled: false },
      googlechat: { enabled: false },
      microsoftteams: { enabled: false },
      pubsub: { enabled: false },
      slack: { enabled: true, botName: 'runtime-bot' },
      sms: { enabled: false },
      cdevents: { enabled: false },
    } as INotificationSettings;

    registerBuiltinNotificationTypes();
    registerBuiltinNotificationTypes();

    const notificationTypes = Registry.pipeline.getNotificationTypes();
    expect(notificationTypes.map(({ key }) => key)).toEqual(['slack']);
    expect(notificationTypes[0].config as any).toEqual({ enabled: true, botName: 'runtime-bot' });
  });

  it('exposes every built-in notification key regardless of settings', () => {
    expect(Array.from(BUILTIN_NOTIFICATION_KEYS)).toEqual([
      'email',
      'githubStatus',
      'googlechat',
      'microsoftteams',
      'pubsub',
      'slack',
      'sms',
      'cdevents',
    ]);
  });

  it('does not replace an enabled built-in notification with dynamic metadata', async () => {
    SETTINGS.notifications = {
      email: { enabled: false },
      githubStatus: { enabled: false },
      googlechat: { enabled: false },
      microsoftteams: { enabled: false },
      pubsub: { enabled: false },
      slack: { enabled: true },
      sms: { enabled: false },
      cdevents: { enabled: false },
    } as INotificationSettings;
    registerBuiltinNotificationTypes();
    spyOn(NotificationService, 'getNotificationTypeMetadata').and.returnValue(
      Promise.resolve([
        { notificationType: 'SLACK', uiType: 'BASIC', parameters: [] },
        { notificationType: 'runtime-basic', uiType: 'BASIC', parameters: [] },
      ]),
    );

    await registerDynamicNotificationTypes();
    await registerDynamicNotificationTypes();

    const notificationKeys = Registry.pipeline.getNotificationTypes().map(({ key }) => key);
    expect(notificationKeys.filter((key) => key.toLowerCase() === 'slack')).toEqual(['slack']);
    expect(notificationKeys.filter((key) => key === 'runtime-basic').length).toBe(1);
  });

  it('does not restore a disabled built-in notification from dynamic metadata', async () => {
    SETTINGS.notifications = {
      email: { enabled: false },
      githubStatus: { enabled: false },
      googlechat: { enabled: false },
      microsoftteams: { enabled: false },
      pubsub: { enabled: false },
      slack: { enabled: false },
      sms: { enabled: false },
      cdevents: { enabled: false },
    } as INotificationSettings;
    registerBuiltinNotificationTypes();
    spyOn(NotificationService, 'getNotificationTypeMetadata').and.returnValue(
      Promise.resolve([
        { notificationType: 'SLACK', uiType: 'BASIC', parameters: [] },
        { notificationType: 'runtime-basic', uiType: 'BASIC', parameters: [] },
      ]),
    );

    await registerDynamicNotificationTypes();
    await registerDynamicNotificationTypes();

    const notificationKeys = Registry.pipeline.getNotificationTypes().map(({ key }) => key);
    expect(notificationKeys.some((key) => key.toLowerCase() === 'slack')).toBeFalse();
    expect(notificationKeys.filter((key) => key === 'runtime-basic').length).toBe(1);
  });

  it('awaits BASIC metadata registration and ignores unsupported UI types without duplicates', async () => {
    const parameters: INotificationParameter[] = [
      {
        name: 'channel',
        defaultValue: 'deployments',
        type: 'string',
        label: 'Channel',
        description: 'Destination channel',
      },
    ];
    const getMetadata = spyOn(NotificationService, 'getNotificationTypeMetadata').and.returnValue(
      Promise.resolve([
        { notificationType: 'runtime-basic', uiType: 'BASIC', parameters },
        { notificationType: 'runtime-custom', uiType: 'CUSTOM', parameters: [] },
      ]),
    );

    await registerDynamicNotificationTypes();
    await registerDynamicNotificationTypes();

    expect(getMetadata).toHaveBeenCalledTimes(2);
    const notificationTypes = Registry.pipeline.getNotificationTypes();
    expect(notificationTypes.map(({ key }) => key)).toEqual(['runtime-basic']);
    expect(notificationTypes[0]).toEqual(
      jasmine.objectContaining({ key: 'runtime-basic', label: 'runtime-basic', config: {} }),
    );

    const component = new (notificationTypes[0].component as any)({});
    const fields = React.Children.toArray(component.render().props.children) as React.ReactElement<any>[];
    expect(fields.length).toBe(1);
    expect(fields[0].props).toEqual(
      jasmine.objectContaining({ name: 'channel', label: 'Channel', input: jasmine.any(Function) }),
    );
  });

  it('preserves case-distinct dynamic notification keys without duplicating exact keys', async () => {
    spyOn(NotificationService, 'getNotificationTypeMetadata').and.returnValue(
      Promise.resolve([
        { notificationType: 'custom', uiType: 'BASIC', parameters: [] },
        { notificationType: 'CUSTOM', uiType: 'BASIC', parameters: [] },
      ]),
    );

    await registerDynamicNotificationTypes();
    await registerDynamicNotificationTypes();

    expect(Registry.pipeline.getNotificationTypes().map(({ key }) => key)).toEqual(['custom', 'CUSTOM']);
  });

  it('propagates metadata failures to registration callers', async () => {
    const failure = new Error('metadata unavailable');
    spyOn(NotificationService, 'getNotificationTypeMetadata').and.returnValue(Promise.reject(failure));

    await expectAsync(registerDynamicNotificationTypes()).toBeRejectedWith(failure);
  });

  it('isolates Angular-compatible metadata failures and logs once', async () => {
    const failure = new Error('metadata unavailable');
    spyOn(NotificationService, 'getNotificationTypeMetadata').and.returnValue(Promise.reject(failure));
    const consoleError = spyOn(console, 'error').and.stub();

    await expectAsync(initializeDynamicNotificationTypes()).toBeResolved();

    expect(consoleError).toHaveBeenCalledTimes(1);
    expect(consoleError).toHaveBeenCalledWith('Failed to load notification type metadata', failure);
  });
});

describe('Angular-compatible notification registration', () => {
  const originalNotifications = SETTINGS.notifications;
  let originalPipeline: typeof Registry.pipeline;
  let originalUrlBuilder: typeof Registry.urlBuilder;

  beforeEach(() => {
    originalPipeline = Registry.pipeline;
    originalUrlBuilder = Registry.urlBuilder;
    Registry.reinitialize();
    (SETTINGS as any).notifications = undefined;
  });

  afterEach(() => {
    Registry.pipeline = originalPipeline;
    Registry.urlBuilder = originalUrlBuilder;
    SETTINGS.notifications = originalNotifications;
  });

  describe('successful metadata loading', () => {
    const registrationEvents: string[] = [];

    beforeEach(() => {
      registrationEvents.length = 0;
      const registerNotification = Registry.pipeline.registerNotification.bind(Registry.pipeline);
      spyOn(Registry.pipeline, 'registerNotification').and.callFake((config) => {
        registrationEvents.push(`register:${config.key}`);
        registerNotification(config);
      });
      spyOn(NotificationService, 'getNotificationTypeMetadata').and.callFake(() => {
        registrationEvents.push('load-dynamic');
        return new Promise(() => undefined);
      });
    });

    beforeEach(mock.module(CORE_NOTIFICATION_NOTIFICATIONS_MODULE));
    beforeEach(mock.inject(() => undefined));

    it('registers built-ins synchronously before loading dynamic metadata', () => {
      expect(registrationEvents).toEqual([
        ...Array.from(BUILTIN_NOTIFICATION_KEYS, (key) => `register:${key}`),
        'load-dynamic',
      ]);
    });
  });

  describe('failed metadata loading', () => {
    const failure = new Error('metadata unavailable');
    let consoleError: jasmine.Spy;

    beforeEach(() => {
      spyOn(NotificationService, 'getNotificationTypeMetadata').and.returnValue(Promise.reject(failure));
      consoleError = spyOn(console, 'error').and.stub();
    });

    beforeEach(mock.module(CORE_NOTIFICATION_NOTIFICATIONS_MODULE));
    beforeEach(mock.inject(() => undefined));

    it('isolates dynamic metadata failures and logs once', async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));

      expect(consoleError).toHaveBeenCalledTimes(1);
      expect(consoleError).toHaveBeenCalledWith('Failed to load notification type metadata', failure);
    });
  });

  describe('after direct registration', () => {
    beforeEach(() => {
      spyOn(NotificationService, 'getNotificationTypeMetadata').and.returnValue(Promise.resolve([]));
      registerBuiltinNotificationTypes();
      registerBuiltinNotificationTypes();
    });

    beforeEach(mock.module(CORE_NOTIFICATION_NOTIFICATIONS_MODULE));
    beforeEach(mock.inject(() => undefined));

    it('keeps built-in notifications idempotent across direct and Angular initialization', () => {
      const notificationKeys = Registry.pipeline.getNotificationTypes().map(({ key }) => key);

      expect(notificationKeys).toEqual(Array.from(BUILTIN_NOTIFICATION_KEYS));
    });
  });
});
