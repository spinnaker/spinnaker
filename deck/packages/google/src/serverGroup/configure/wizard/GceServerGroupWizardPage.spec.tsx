import type { FormikProps } from 'formik';
import React from 'react';

import {
  GCE_SERVER_GROUP_OPERATION_MODES,
  GceServerGroupWizardAdapter,
  GceServerGroupWizardPage,
  createGceServerGroupWizardCommandState,
  getGceServerGroupLocationMode,
  preservePersistedReference,
  validateGceServerGroupCommand,
} from './index';
import type {
  GceConfigurationRefreshMethod,
  GceConfigurationUpdateMethod,
  IGceServerGroupCommand,
  IGceServerGroupWizardCommandState,
  IGceServerGroupWizardPageProps,
} from './index';

describe('GceServerGroupWizard foundation', () => {
  it('defines all operation modes and derives zonal or regional location mode', () => {
    expect(GCE_SERVER_GROUP_OPERATION_MODES).toEqual(['create', 'clone', 'createPipeline', 'editPipeline']);
    expect(getGceServerGroupLocationMode(command({ regional: false }))).toBe('zonal');
    expect(getGceServerGroupLocationMode(command({ regional: true }))).toBe('regional');
  });

  it('preserves an unresolved persisted reference without changing resolved options', () => {
    const options = [{ id: 'known' }];

    expect(
      preservePersistedReference(
        options,
        'persisted',
        (option) => option.id,
        (id) => ({ id }),
      ),
    ).toEqual([
      { value: { id: 'known' }, unresolved: false },
      { value: { id: 'persisted' }, unresolved: true },
    ]);
    expect(
      preservePersistedReference(
        options,
        'known',
        (option) => option.id,
        (id) => ({ id }),
      ),
    ).toEqual([{ value: { id: 'known' }, unresolved: false }]);
  });

  describe('shared validation', () => {
    it('requires application, account, zonal location, image, and desired capacity', () => {
      expect(
        validateGceServerGroupCommand(
          command({
            application: '',
            credentials: '',
            region: '',
            zone: '',
            stack: '',
            freeFormDetails: '',
            image: '',
            capacity: { desired: null },
          }),
        ),
      ).toEqual({
        application: 'Application required.',
        credentials: 'Account required.',
        region: 'Region required.',
        zone: 'Zone required.',
        image: 'Image required.',
        capacity: { desired: 'Desired capacity required.' },
      });
    });

    it('requires selected zones only for explicitly zoned regional commands', () => {
      const errors = validateGceServerGroupCommand(
        command({ regional: true, zone: undefined, selectZones: true, distributionPolicy: { zones: [] } }),
      );

      expect(errors.zone).toBeUndefined();
      expect(errors.distributionPolicy).toEqual({ zones: 'At least one zone required.' });
    });

    it('accepts zero desired capacity and disabled image selection', () => {
      expect(
        validateGceServerGroupCommand(
          command({
            image: undefined,
            capacity: { desired: 0 },
            viewState: { mode: 'editPipeline', disableImageSelection: true },
          }),
        ),
      ).toEqual({});
    });
  });

  describe('adapter', () => {
    it('delegates the four surviving command-builder signatures', async () => {
      const builder = {
        buildNewServerGroupCommand: jasmine.createSpy().and.resolveTo(command()),
        buildNewServerGroupCommandForPipeline: jasmine.createSpy().and.resolveTo(command()),
        buildServerGroupCommandFromExisting: jasmine.createSpy().and.resolveTo(command()),
        buildServerGroupCommandFromPipeline: jasmine.createSpy().and.resolveTo(command()),
      };
      const adapter = new GceServerGroupWizardAdapter(builder as any, configurationService());
      const app = { name: 'app' } as any;
      const stage = { type: 'deploy' };
      const pipeline = { stages: [stage] };
      const serverGroup = { name: 'app-main-v001' };
      const cluster = { account: 'account' };

      await adapter.buildNewServerGroupCommand(app, { account: 'account', mode: 'create' });
      await adapter.buildNewServerGroupCommandForPipeline(stage, pipeline);
      await adapter.buildServerGroupCommandFromExisting(app, serverGroup, 'clone');
      await adapter.buildServerGroupCommandFromPipeline(app, cluster, stage, pipeline);

      expect(builder.buildNewServerGroupCommand).toHaveBeenCalledWith(app, { account: 'account', mode: 'create' });
      expect(builder.buildNewServerGroupCommandForPipeline).toHaveBeenCalledWith(stage, pipeline);
      expect(builder.buildServerGroupCommandFromExisting).toHaveBeenCalledWith(app, serverGroup, 'clone');
      expect(builder.buildServerGroupCommandFromPipeline).toHaveBeenCalledWith(app, cluster, stage, pipeline);
    });

    it('configures a cloned command and leaves Formik values unchanged', async () => {
      const original = command({ backingData: undefined });
      const service = configurationService();
      service.configureCommand.and.callFake(async (_app: any, working: IGceServerGroupCommand) => {
        working.backingData = { accounts: ['account'] };
      });
      const adapter = new GceServerGroupWizardAdapter(commandBuilder(), service);

      const configured = await adapter.configureCommand({ name: 'app' } as any, original);

      expect(configured).not.toBe(original);
      expect(configured.backingData).toEqual({ accounts: ['account'] });
      expect(original.backingData).toBeUndefined();
    });

    it('applies configuration updates immutably and processes dirty results', async () => {
      const processCommandUpdateResult = jasmine.createSpy('processCommandUpdateResult');
      const original = command({
        processCommandUpdateResult,
        viewState: { mode: 'create', dirty: { existing: true } },
      });
      const service = configurationService();
      service.configureImages.and.callFake((working: IGceServerGroupCommand) => {
        working.image = 'new-image';
        return { dirty: { image: true } };
      });
      const adapter = new GceServerGroupWizardAdapter(commandBuilder(), service);

      const update = await adapter.applyConfigurationUpdate(original, 'configureImages');

      expect(update.command).not.toBe(original);
      expect(update.command.image).toBe('new-image');
      expect(original.image).toBe('image');
      expect(update.command.viewState.dirty).toEqual({ existing: true, image: true });
      expect(processCommandUpdateResult).toHaveBeenCalledWith({ dirty: { image: true } });
    });

    it('invokes command handlers against a clone', async () => {
      const credentialsChanged = jasmine.createSpy().and.callFake((working: IGceServerGroupCommand) => {
        working.region = 'new-region';
        return { dirty: { region: true } };
      });
      const original = command({ credentialsChanged });
      const adapter = new GceServerGroupWizardAdapter(commandBuilder(), configurationService());

      const update = await adapter.applyCommandHandler(original, 'credentialsChanged');

      expect(update.command.region).toBe('new-region');
      expect(original.region).toBe('region');
      expect(credentialsChanged.calls.mostRecent().args[0]).not.toBe(original);
    });

    it('forwards refresh options while refreshing a cloned command', async () => {
      const original = command();
      const service = configurationService();
      service.refreshHealthChecks.and.callFake(async (working: IGceServerGroupCommand) => {
        working.backingData = { healthChecks: ['health-check'] };
      });
      const adapter = new GceServerGroupWizardAdapter(commandBuilder(), service);

      const update = await adapter.applyConfigurationRefresh(original, 'refreshHealthChecks', true);

      expect(service.refreshHealthChecks.calls.mostRecent().args[0]).not.toBe(original);
      expect(service.refreshHealthChecks.calls.mostRecent().args[1]).toBe(true);
      expect(update.command.backingData).toEqual({ healthChecks: ['health-check'] });
      expect(original.backingData).toBeUndefined();
    });
  });

  describe('page request safety', () => {
    it('composes rapid updates while Formik setValues propagation is asynchronous', async () => {
      const { page, setValues } = testPage();
      const requestCommands: IGceServerGroupCommand[] = [];

      await page.requestUpdate({ region: 'first-region' }, async (nextCommand) => {
        requestCommands.push(nextCommand);
        return { ...nextCommand, backingData: { regions: ['first-region'] } };
      });
      await page.requestUpdate({ zone: 'second-zone' }, async (nextCommand) => {
        requestCommands.push(nextCommand);
        return nextCommand;
      });

      expect(setValues.calls.count()).toBe(2);
      expect(requestCommands[1]).toEqual(
        jasmine.objectContaining({
          backingData: { regions: ['first-region'] },
          region: 'first-region',
          zone: 'second-zone',
        }),
      );
    });

    it('merges an older cross-page completion without replacing newer edits or backing data', async () => {
      const older = deferred<IGceServerGroupCommand>();
      const newer = deferred<IGceServerGroupCommand>();
      const formik = publishingFormik(command({ backingData: { regions: ['initial'] } }));
      const commandState = createGceServerGroupWizardCommandState(formik.values);
      const firstPage = testPage(formik, commandState).page;
      const secondPage = testPage(formik, commandState).page;

      const olderRequest = firstPage.requestUpdate({ credentials: 'new-account' }, () => older.promise);
      const newerRequest = secondPage.requestUpdate({ zone: 'new-zone' }, () => newer.promise);
      newer.resolve(
        command({
          backingData: { regions: ['newer'] },
          credentials: 'new-account',
          zone: 'new-zone',
        }),
      );
      await newerRequest;
      formik.values = { ...formik.values, freeFormDetails: 'newer-user-edit' };
      older.resolve(
        command({
          backingData: { regions: ['older'] },
          credentials: 'new-account',
          freeFormDetails: 'detail',
          region: 'dependent-region',
        }),
      );
      await olderRequest;

      expect(formik.values).toEqual(
        jasmine.objectContaining({
          backingData: { regions: ['newer'] },
          credentials: 'new-account',
          freeFormDetails: 'newer-user-edit',
          region: 'dependent-region',
          zone: 'new-zone',
        }),
      );
    });

    it('lets the newest result replace older derived conflicts while preserving user edits', async () => {
      const older = deferred<IGceServerGroupCommand>();
      const newer = deferred<IGceServerGroupCommand>();
      const formik = publishingFormik(command({ backingData: { regions: ['initial'] } }));
      const commandState = createGceServerGroupWizardCommandState(formik.values);
      const firstPage = testPage(formik, commandState).page;
      const secondPage = testPage(formik, commandState).page;

      const olderRequest = firstPage.requestUpdate({ credentials: 'new-account' }, () => older.promise);
      const newerRequest = secondPage.requestUpdate({ zone: 'new-zone' }, () => newer.promise);
      older.resolve(
        command({
          backingData: { regions: ['option-a'] },
          credentials: 'new-account',
        }),
      );
      await olderRequest;
      formik.values = { ...formik.values, freeFormDetails: 'newer-user-edit' };
      newer.resolve(
        command({
          backingData: { regions: ['option-b'] },
          credentials: 'new-account',
          zone: 'new-zone',
        }),
      );
      await newerRequest;

      expect(formik.values).toEqual(
        jasmine.objectContaining({
          backingData: { regions: ['option-b'] },
          credentials: 'new-account',
          freeFormDetails: 'newer-user-edit',
          zone: 'new-zone',
        }),
      );
    });

    it('publishes only the latest command request', async () => {
      const first = deferred<IGceServerGroupCommand>();
      const second = deferred<IGceServerGroupCommand>();
      const { page, setValues, onLoadingChanged } = testPage();

      const firstRequest = page.request(() => first.promise);
      const secondRequest = page.request(() => second.promise);
      second.resolve(command({ region: 'second' }));
      await secondRequest;
      first.resolve(command({ region: 'first' }));
      await firstRequest;

      expect(setValues.calls.count()).toBe(1);
      expect(setValues.calls.mostRecent().args[0].region).toBe('second');
      expect(onLoadingChanged.calls.allArgs()).toEqual([[true], [true], [false]]);
    });

    it('does not publish a command after unmount', async () => {
      const request = deferred<IGceServerGroupCommand>();
      const { page, setValues } = testPage();

      const pending = page.request(() => request.promise);
      page.componentWillUnmount();
      request.resolve(command({ region: 'late' }));
      await pending;

      expect(setValues).not.toHaveBeenCalled();
    });
  });
});

class TestWizardPage extends GceServerGroupWizardPage {
  public request(request: () => Promise<IGceServerGroupCommand>): Promise<IGceServerGroupCommand | undefined> {
    return this.runLatestCommandRequest(this.props.formik.values, request);
  }

  public requestUpdate(
    changes: Partial<IGceServerGroupCommand>,
    request: (command: IGceServerGroupCommand) => Promise<IGceServerGroupCommand>,
  ): Promise<IGceServerGroupCommand | undefined> {
    const command = { ...this.props.formik.values, ...changes };
    return this.runLatestCommandRequest(command, request);
  }

  public render(): React.ReactNode {
    return null;
  }
}

function testPage(formik = asyncFormik(command()), commandState?: IGceServerGroupWizardCommandState) {
  const setValues = formik.setValues as jasmine.Spy;
  const onLoadingChanged = jasmine.createSpy('onLoadingChanged');
  const props = {
    app: { name: 'app' },
    commandState,
    formik,
    onLoadingChanged,
  } as IGceServerGroupWizardPageProps;
  return { page: new TestWizardPage(props), setValues, onLoadingChanged };
}

function asyncFormik(values: IGceServerGroupCommand): FormikProps<IGceServerGroupCommand> {
  const formik = ({ values } as unknown) as FormikProps<IGceServerGroupCommand>;
  formik.setValues = jasmine.createSpy('setValues').and.resolveTo(undefined);
  return formik;
}

function publishingFormik(values: IGceServerGroupCommand): FormikProps<IGceServerGroupCommand> {
  const formik = asyncFormik(values);
  (formik.setValues as jasmine.Spy).and.callFake((nextValues: IGceServerGroupCommand) => {
    formik.values = nextValues;
  });
  return formik;
}

function command(overrides: Partial<IGceServerGroupCommand> = {}): IGceServerGroupCommand {
  return {
    application: 'app',
    credentials: 'account',
    regional: false,
    region: 'region',
    zone: 'zone',
    stack: 'main',
    freeFormDetails: 'detail',
    image: 'image',
    capacity: { desired: 1 },
    distributionPolicy: { zones: [] },
    viewState: { mode: 'create' },
    ...overrides,
  };
}

function commandBuilder() {
  return {
    buildNewServerGroupCommand: jasmine.createSpy().and.resolveTo(command()),
    buildNewServerGroupCommandForPipeline: jasmine.createSpy().and.resolveTo(command()),
    buildServerGroupCommandFromExisting: jasmine.createSpy().and.resolveTo(command()),
    buildServerGroupCommandFromPipeline: jasmine.createSpy().and.resolveTo(command()),
  };
}

function configurationService(): Record<
  GceConfigurationRefreshMethod | GceConfigurationUpdateMethod | 'configureCommand',
  jasmine.Spy
> {
  return {
    configureCommand: jasmine.createSpy().and.resolveTo(undefined),
    configureImages: jasmine.createSpy().and.returnValue({ dirty: {} }),
    configureInstanceTypes: jasmine.createSpy().and.returnValue({ dirty: {} }),
    configureLoadBalancerOptions: jasmine.createSpy().and.returnValue({ dirty: {} }),
    configureSubnets: jasmine.createSpy().and.returnValue({ dirty: {} }),
    configureZones: jasmine.createSpy().and.returnValue({ dirty: {} }),
    refreshHealthChecks: jasmine.createSpy().and.resolveTo(undefined),
    refreshInstanceTypes: jasmine.createSpy().and.resolveTo(undefined),
    refreshLoadBalancers: jasmine.createSpy().and.resolveTo(undefined),
    refreshSecurityGroups: jasmine.createSpy().and.resolveTo(undefined),
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => {
    resolve = promiseResolve;
  });
  return { promise, resolve };
}
