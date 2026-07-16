import type { FormikProps } from 'formik';
import React from 'react';
import { shallow } from 'enzyme';

import { DeploymentStrategySelector, TaskReason } from '@spinnaker/core';

import { ServerGroupBasicSettings } from './ServerGroupBasicSettings';
import { GceImageReader } from '../../../../image';
import type { IGceServerGroupCommand, IGceServerGroupWizardAdapter } from '../GceServerGroupWizard.types';

describe('ServerGroupBasicSettings', () => {
  it('renders accessible location fields and preserves persisted unavailable references', () => {
    const { formik } = testProps();
    const wrapper = shallow(<ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} />);

    expect(selectOptions(wrapper, 'Account')).toEqual([
      ['', 'Select...'],
      ['known-account', 'known-account'],
      ['persisted-account', 'persisted-account (unavailable)'],
    ]);
    expect(selectOptions(wrapper, 'Region')).toContain(['persisted-region', 'persisted-region (unavailable)']);
    expect(selectOptions(wrapper, 'Zone')).toContain(['persisted-zone', 'persisted-zone (unavailable)']);
    expect(selectOptions(wrapper, 'Network')).toContain(['persisted-network', 'persisted-network (unavailable)']);
    expect(selectOptions(wrapper, 'Subnet')).toContain(['persisted-subnet', 'persisted-subnet (unavailable)']);
    expect(wrapper.find('[aria-label="Location mode"]').prop('value')).toBe('zonal');
    expect(wrapper.find('input[aria-label="Stack"]').prop('value')).toBe('main');
    expect(wrapper.find('input[aria-label="Detail"]').prop('value')).toBe('detail');
    expect(formik.setValues).not.toHaveBeenCalled();
    expect(formik.setFieldValue).not.toHaveBeenCalled();
  });

  [
    ['Region', 'region', 'known-region', 'regionChanged'],
    ['Location mode', 'regional', 'regional', 'regionalChanged'],
    ['Zone', 'zone', 'known-zone', 'zoneChanged'],
    ['Network', 'network', 'known-network', 'networkChanged'],
  ].forEach(([label, field, selected, handler]) => {
    it(`invokes ${handler} with the changed ${field} value and publishes its reconciled command`, async () => {
      const reconciled = command({
        credentials: 'known-account',
        region: null,
        zone: null,
        network: null,
        subnet: '',
      });
      const { adapter, formik } = testProps(command(), reconciled);
      const wrapper = shallow(
        <ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} adapter={adapter} />,
      );

      wrapper.find(`[aria-label="${label}"]`).simulate('change', { target: { value: selected } });
      await flush();

      const changedCommand = adapter.applyCommandHandler.calls.mostRecent().args[0];
      expect(changedCommand[field]).toBe(field === 'regional' ? true : selected);
      expect(adapter.applyCommandHandler).toHaveBeenCalledWith(changedCommand, handler);
      expect(formik.setValues).toHaveBeenCalledWith(reconciled);
    });
  });

  it('reloads account-scoped images in addition to reconciling account-dependent fields', async () => {
    const images = [{ imageName: 'new-account-image' }];
    spyOn(GceImageReader, 'findImages').and.resolveTo(images);
    const values = command({
      backingData: {
        ...command().backingData,
        allImages: [{ imageName: 'stale-account-image' }],
      },
    });
    const { adapter, formik } = testProps(values);
    adapter.applyCommandHandler.and.callFake((working: IGceServerGroupCommand) =>
      Promise.resolve({ command: { ...working, region: null }, result: { dirty: {} } }),
    );
    const wrapper = shallow(
      <ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} adapter={adapter} />,
    );

    wrapper.find('[aria-label="Account"]').simulate('change', { target: { value: 'known-account' } });
    await flush();

    expect(GceImageReader.findImages).toHaveBeenCalledWith({
      account: 'known-account',
      provider: 'gce',
      q: '*',
    });
    expect(adapter.applyCommandHandler).toHaveBeenCalledWith(
      jasmine.objectContaining({ credentials: 'known-account' }),
      'credentialsChanged',
    );
    expect(formik.setValues.calls.mostRecent().args[0].backingData.allImages).toEqual(images);
  });

  ([
    {
      description: 'rejects an image available only in the old account',
      image: 'old-account-image',
      images: [{ imageName: 'new-account-image' }],
      expectedImage: null,
    },
    {
      description: 'retains an image available in the new account',
      image: 'new-account-image',
      images: [{ imageName: 'new-account-image' }],
      expectedImage: 'new-account-image',
    },
  ] as const).forEach(({ description, expectedImage, image, images }) => {
    it(description, async () => {
      spyOn(GceImageReader, 'findImages').and.resolveTo(images as any);
      const values = command({
        image,
        backingData: {
          ...command().backingData,
          allImages: [{ imageName: 'old-account-image' }],
        },
      });
      const formik = publishingFormik(values);
      const adapter = imageValidatingAdapter();
      const wrapper = shallow(
        <ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} adapter={adapter} />,
      );

      const request = (wrapper.find('[aria-label="Account"]').prop('onChange') as any)({
        target: { value: 'known-account' },
      });
      await request;

      expect(adapter.applyCommandHandler.calls.mostRecent().args[0].backingData.allImages).toEqual(images);
      expect(formik.values.image).toBe(expectedImage);
    });
  });

  it('keeps the latest account images and user edits when account reloads finish out of order', async () => {
    const firstImages = deferred<Array<{ imageName: string }>>();
    const secondImages = deferred<Array<{ imageName: string }>>();
    spyOn(GceImageReader, 'findImages').and.callFake(({ account }) =>
      account === 'first-account' ? firstImages.promise : secondImages.promise,
    );
    const values = command({
      backingData: {
        ...command().backingData,
        accounts: [{ name: 'first-account' }, { name: 'second-account' }],
        allImages: [{ imageName: 'stale-account-image' }],
      },
    });
    const formik = publishingFormik(values);
    const adapter = ({
      applyCommandHandler: jasmine
        .createSpy('applyCommandHandler')
        .and.callFake((working: IGceServerGroupCommand) =>
          Promise.resolve({ command: working, result: { dirty: {} } }),
        ),
    } as unknown) as jasmine.SpyObj<IGceServerGroupWizardAdapter>;
    const wrapper = shallow(
      <ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} adapter={adapter} />,
    );
    const changeAccount = wrapper.find('[aria-label="Account"]').prop('onChange') as any;

    const firstRequest = changeAccount({ target: { value: 'first-account' } });
    const secondRequest = changeAccount({ target: { value: 'second-account' } });
    secondImages.resolve([{ imageName: 'second-account-image' }]);
    await secondRequest;
    formik.values = { ...formik.values, freeFormDetails: 'edited-while-loading' };
    firstImages.resolve([{ imageName: 'first-account-image' }]);
    await firstRequest;

    expect(formik.values.credentials).toBe('second-account');
    expect(formik.values.backingData.allImages).toEqual([{ imageName: 'second-account-image' }]);
    expect(formik.values.freeFormDetails).toBe('edited-while-loading');
  });

  it('updates subnet, stack, and detail without invoking a parent handler', () => {
    const { adapter, formik } = testProps();
    const wrapper = shallow(
      <ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} adapter={adapter} />,
    );

    wrapper.find('[aria-label="Subnet"]').simulate('change', { target: { value: 'known-subnet' } });
    wrapper.find('input[aria-label="Stack"]').simulate('change', { target: { value: 'new-stack' } });
    wrapper.find('input[aria-label="Detail"]').simulate('change', { target: { value: 'new-detail' } });

    expect(formik.setFieldValue.calls.allArgs()).toEqual([
      ['subnet', 'known-subnet'],
      ['stack', 'new-stack'],
      ['freeFormDetails', 'new-detail'],
    ]);
    expect(adapter.applyCommandHandler).not.toHaveBeenCalled();
  });

  (['create', 'clone'] as const).forEach((mode) => {
    it(`binds the optional task reason to the command in ${mode} mode`, () => {
      const { formik } = testProps(command({ reason: 'existing reason', viewState: { mode } }));
      const wrapper = shallow(<ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} />);
      const taskReason = wrapper.find(TaskReason);

      expect(taskReason.prop('reason')).toBe('existing reason');
      taskReason.prop('onChange')('updated reason');
      expect(formik.setFieldValue).toHaveBeenCalledWith('reason', 'updated reason');
    });
  });

  it('hides the zonal field in regional mode', () => {
    const { formik } = testProps(command({ regional: true, zone: null }));
    const wrapper = shallow(<ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} />);

    expect(wrapper.find('[aria-label="Zone"]').exists()).toBe(false);
  });

  ([
    ['create', true],
    ['editPipeline', false],
  ] as const).forEach(([mode, enableTraffic]) => {
    it(`renders controlled traffic and shared strategy values in ${mode} mode`, () => {
      const values = command({
        enableTraffic,
        strategy: 'custom',
        viewState: { mode, disableStrategySelection: false },
      });
      const { formik } = testProps(values);
      const wrapper = shallow(<ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} />);
      const traffic = wrapper.find('[aria-label="Send client requests to new instances"]');

      expect(traffic.prop('checked')).toBe(enableTraffic);
      expect(wrapper.find('[aria-label="Deployment strategy"]').prop('role')).toBe('group');
      expect(wrapper.find(DeploymentStrategySelector).prop('command')).toBe(values as any);

      traffic.simulate('change', { target: { checked: !enableTraffic } });
      expect(formik.setFieldValue).toHaveBeenCalledWith('enableTraffic', !enableTraffic);
    });
  });

  it('forwards strategy commands and strategy-specific fields through Formik and the command callback', () => {
    const onStrategyChange = jasmine.createSpy('onStrategyChange');
    const values = command({
      onStrategyChange,
      strategy: '',
      viewState: { mode: 'editPipeline', disableStrategySelection: false },
    });
    const { formik } = testProps(values);
    const wrapper = shallow(<ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} />);
    const selector = wrapper.find(DeploymentStrategySelector);
    const strategy = { key: 'redblack', label: 'Red/Black' } as any;
    const updatedCommand = { ...values, strategy: 'redblack', maxRemainingAsgs: 2, scaleDown: false };

    selector.prop('onStrategyChange')(updatedCommand as any, strategy);
    selector.prop('onFieldChange')('maxRemainingAsgs', 3);

    expect(onStrategyChange).toHaveBeenCalledWith(updatedCommand, strategy);
    expect(formik.setValues).toHaveBeenCalledWith(updatedCommand);
    expect(formik.setFieldValue).toHaveBeenCalledWith('maxRemainingAsgs', 3);
  });

  it('preserves strategy state without rendering the selector when selection is disabled', () => {
    const values = command({
      strategy: 'custom',
      strategyApplication: 'strategy-app',
      strategyPipeline: 'strategy-pipeline',
      viewState: { mode: 'create', disableStrategySelection: true },
    });
    const { formik } = testProps(values);
    const wrapper = shallow(<ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} />);

    expect(wrapper.find(DeploymentStrategySelector).exists()).toBe(false);
    expect(wrapper.find('[aria-label="Deployment strategy"]').exists()).toBe(false);
    expect(values.strategy).toBe('custom');
    expect(values.strategyApplication).toBe('strategy-app');
    expect(values.strategyPipeline).toBe('strategy-pipeline');
    expect(formik.setValues).not.toHaveBeenCalled();
    expect(formik.setFieldValue).not.toHaveBeenCalled();
  });

  it('validates required fields and naming rules owned by the page', () => {
    const { formik } = testProps();
    const page = new ServerGroupBasicSettings({ app: { name: 'app' } as any, formik } as any);

    expect(
      page.validate(
        command({ credentials: '', region: '', zone: '', stack: 'invalid stack', freeFormDetails: 'bad_detail' }),
      ),
    ).toEqual({
      credentials: 'Account required.',
      region: 'Region required.',
      zone: 'Zone required.',
      stack: 'Stack can only contain letters and numbers.',
      freeFormDetails: 'Detail can only contain letters, numbers, and dashes.',
    });
  });

  it('associates every page-owned validation error with its control', () => {
    const values = command({
      credentials: '',
      region: '',
      zone: '',
      stack: 'invalid stack',
      freeFormDetails: 'bad_detail',
    });
    const { formik } = testProps(values);
    const wrapper = shallow(<ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} />);

    [
      ['Account', 'credentials', 'Account required.'],
      ['Region', 'region', 'Region required.'],
      ['Zone', 'zone', 'Zone required.'],
      ['Stack', 'stack', 'Stack can only contain letters and numbers.'],
      ['Detail', 'freeFormDetails', 'Detail can only contain letters, numbers, and dashes.'],
    ].forEach(([label, field, message]) => {
      const control = wrapper.find(`[aria-label="${label}"]`);
      const errorId = `gce-server-group-${field}-error`;
      const alert = wrapper.find(`[id="${errorId}"][role="alert"]`);

      expect(control.prop('aria-invalid')).withContext(label).toBe(true);
      expect(control.prop('aria-describedby')).withContext(label).toBe(errorId);
      expect(alert.length).withContext(label).toBe(1);
      expect(alert.text()).withContext(label).toBe(message);
    });
  });

  it('allows expression syntax in stack and detail when templating is enabled', () => {
    const values = command({
      stack: '${parameters.stack}',
      freeFormDetails: 'detail-${parameters.suffix}',
      viewState: { mode: 'editPipeline', templatingEnabled: true },
    });
    const { formik } = testProps(values);
    const page = new ServerGroupBasicSettings({ app: { name: 'app' } as any, formik } as any);

    expect(page.validate(values)).toEqual({});
  });
});

function selectOptions(wrapper: ReturnType<typeof shallow>, label: string): string[][] {
  return wrapper
    .find(`[aria-label="${label}"] option`)
    .map((option) => [option.prop('value') as string, option.text()]);
}

function testProps(values = command(), adapterResult = values) {
  const formik = ({
    values,
    setFieldValue: jasmine.createSpy('setFieldValue'),
    setValues: jasmine.createSpy('setValues'),
  } as unknown) as FormikProps<IGceServerGroupCommand>;
  const adapter = ({
    applyCommandHandler: jasmine
      .createSpy('applyCommandHandler')
      .and.resolveTo({ command: adapterResult, result: { dirty: {} } }),
  } as unknown) as jasmine.SpyObj<IGceServerGroupWizardAdapter>;
  return { adapter, formik };
}

function publishingFormik(values = command()): FormikProps<IGceServerGroupCommand> {
  const formik = ({
    values,
    setFieldValue: jasmine.createSpy('setFieldValue'),
    setValues: jasmine.createSpy('setValues'),
  } as unknown) as FormikProps<IGceServerGroupCommand>;
  (formik.setValues as jasmine.Spy).and.callFake((nextValues: IGceServerGroupCommand) => {
    formik.values = nextValues;
  });
  return formik;
}

function imageValidatingAdapter(): jasmine.SpyObj<IGceServerGroupWizardAdapter> {
  return ({
    applyCommandHandler: jasmine.createSpy('applyCommandHandler').and.callFake((working: IGceServerGroupCommand) => {
      const imageAvailable = (working.backingData.allImages || []).some(
        ({ imageName }: { imageName: string }) => imageName === working.image,
      );
      return Promise.resolve({
        command: { ...working, image: imageAvailable ? working.image : null },
        result: { dirty: {} },
      });
    }),
  } as unknown) as jasmine.SpyObj<IGceServerGroupWizardAdapter>;
}

function command(overrides: Partial<IGceServerGroupCommand> = {}): IGceServerGroupCommand {
  return {
    application: 'app',
    credentials: 'persisted-account',
    regional: false,
    region: 'persisted-region',
    zone: 'persisted-zone',
    network: 'persisted-network',
    subnet: 'persisted-subnet',
    stack: 'main',
    freeFormDetails: 'detail',
    image: 'image',
    enableTraffic: true,
    selectedProvider: 'gce',
    strategy: '',
    capacity: { desired: 1 },
    distributionPolicy: { zones: [] },
    backingData: {
      accounts: [{ name: 'known-account' }],
      filtered: {
        regions: [{ name: 'known-region' }],
        zones: ['known-zone'],
        networks: [{ id: 'known-network' }],
        subnets: ['known-subnet'],
      },
    },
    viewState: { mode: 'create' },
    ...overrides,
  };
}

async function flush(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => {
    resolve = promiseResolve;
  });
  return { promise, resolve };
}
