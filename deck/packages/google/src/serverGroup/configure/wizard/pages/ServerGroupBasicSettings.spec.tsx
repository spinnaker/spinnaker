import type { Mock, Mocked } from 'vitest';
import type { FormikProps } from 'formik';
import React from 'react';
import { fireEvent, render } from '@testing-library/react';
import type { RenderResult } from '@testing-library/react';

import { ServerGroupBasicSettings } from './ServerGroupBasicSettings';
import { GceImageReader } from '../../../../image';
import type { IGceServerGroupCommand, IGceServerGroupWizardAdapter } from '../GceServerGroupWizard.types';
import { validateGceServerGroupBasicSettings } from './ServerGroupBasicSettings';

vi.mock('@spinnaker/core', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@spinnaker/core')>();
  const MockTaskReason = ({ onChange, reason }: any) => (
    <input
      aria-label="Task reason"
      data-component="MockTaskReason"
      onChange={(event) => onChange(event.target.value)}
      value={reason || ''}
    />
  );
  const MockDeploymentStrategySelector = ({ command, onFieldChange, onStrategyChange }: any) => (
    <div data-component="MockDeploymentStrategySelector">
      <output aria-label="Selected strategy">{command.strategy}</output>
      <button
        onClick={() =>
          onStrategyChange(
            { ...command, strategy: 'redblack', maxRemainingAsgs: 2, scaleDown: false },
            { key: 'redblack', label: 'Red/Black' },
          )
        }
        type="button"
      >
        Select red black
      </button>
      <button onClick={() => onFieldChange('maxRemainingAsgs', 3)} type="button">
        Update strategy field
      </button>
    </div>
  );
  return {
    ...actual,
    DeploymentStrategySelector: MockDeploymentStrategySelector,
    TaskReason: MockTaskReason,
  };
});

describe('ServerGroupBasicSettings', () => {
  it('renders accessible location fields and preserves persisted unavailable references', () => {
    const { formik } = testProps();
    const wrapper = renderPage(<ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} />);

    expect(selectOptions(wrapper, 'Account')).toEqual([
      ['', 'Select...'],
      ['known-account', 'known-account'],
      ['persisted-account', 'persisted-account (unavailable)'],
    ]);
    expect(selectOptions(wrapper, 'Region')).toContainEqual(['persisted-region', 'persisted-region (unavailable)']);
    expect(selectOptions(wrapper, 'Zone')).toContainEqual(['persisted-zone', 'persisted-zone (unavailable)']);
    expect(selectOptions(wrapper, 'Network')).toContainEqual(['persisted-network', 'persisted-network (unavailable)']);
    expect(selectOptions(wrapper, 'Subnet')).toContainEqual(['persisted-subnet', 'persisted-subnet (unavailable)']);
    expect(wrapper.getByLabelText('Location mode')).toHaveValue('zonal');
    expect(wrapper.getByLabelText('Stack')).toHaveValue('main');
    expect(wrapper.getByLabelText('Detail')).toHaveValue('detail');
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
      const wrapper = renderPage(
        <ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} adapter={adapter} />,
      );

      fireEvent.change(wrapper.getByLabelText(label), { target: { value: selected } });
      await flush();

      const changedCommand = adapter.applyCommandHandler.mock.lastCall[0];
      expect(changedCommand[field]).toBe(field === 'regional' ? true : selected);
      expect(adapter.applyCommandHandler).toHaveBeenCalledWith(changedCommand, handler);
      expect(formik.setValues).toHaveBeenCalledWith(reconciled);
    });
  });

  it('reloads account-scoped images in addition to reconciling account-dependent fields', async () => {
    const images = [{ imageName: 'new-account-image' }];
    vi.spyOn(GceImageReader, 'findImages').mockResolvedValue(images);
    const values = command({
      backingData: {
        ...command().backingData,
        allImages: [{ imageName: 'stale-account-image' }],
      },
    });
    const { adapter, formik } = testProps(values);
    adapter.applyCommandHandler.mockImplementation((working: IGceServerGroupCommand) =>
      Promise.resolve({ command: { ...working, region: null }, result: { dirty: {} } }),
    );
    const wrapper = renderPage(
      <ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} adapter={adapter} />,
    );

    fireEvent.change(wrapper.getByLabelText('Account'), { target: { value: 'known-account' } });
    await flush();

    expect(GceImageReader.findImages).toHaveBeenCalledWith({
      account: 'known-account',
      provider: 'gce',
      q: '*',
    });
    expect(adapter.applyCommandHandler).toHaveBeenCalledWith(
      expect.objectContaining({ credentials: 'known-account' }),
      'credentialsChanged',
    );
    expect(formik.setValues.mock.lastCall[0].backingData.allImages).toEqual(images);
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
      vi.spyOn(GceImageReader, 'findImages').mockResolvedValue(images as any);
      const values = command({
        image,
        backingData: {
          ...command().backingData,
          allImages: [{ imageName: 'old-account-image' }],
        },
      });
      const formik = publishingFormik(values);
      const adapter = imageValidatingAdapter();
      const wrapper = renderPage(
        <ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} adapter={adapter} />,
      );

      fireEvent.change(wrapper.getByLabelText('Account'), { target: { value: 'known-account' } });
      await flush();

      expect(adapter.applyCommandHandler.mock.lastCall[0].backingData.allImages).toEqual(images);
      expect(formik.values.image).toBe(expectedImage);
    });
  });

  it('keeps the latest account images and user edits when account reloads finish out of order', async () => {
    const firstImages = deferred<Array<{ imageName: string }>>();
    const secondImages = deferred<Array<{ imageName: string }>>();
    vi.spyOn(GceImageReader, 'findImages').mockImplementation(({ account }) =>
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
      applyCommandHandler: vi
        .fn()
        .mockImplementation((working: IGceServerGroupCommand) =>
          Promise.resolve({ command: working, result: { dirty: {} } }),
        ),
    } as unknown) as Mocked<IGceServerGroupWizardAdapter>;
    const wrapper = renderPage(
      <ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} adapter={adapter} />,
    );
    fireEvent.change(wrapper.getByLabelText('Account'), { target: { value: 'first-account' } });
    fireEvent.change(wrapper.getByLabelText('Account'), { target: { value: 'second-account' } });
    secondImages.resolve([{ imageName: 'second-account-image' }]);
    await flush();
    formik.values = { ...formik.values, freeFormDetails: 'edited-while-loading' };
    firstImages.resolve([{ imageName: 'first-account-image' }]);
    await flush();

    expect(formik.values.credentials).toBe('second-account');
    expect(formik.values.backingData.allImages).toEqual([{ imageName: 'second-account-image' }]);
    expect(formik.values.freeFormDetails).toBe('edited-while-loading');
  });

  it('updates subnet, stack, and detail without invoking a parent handler', () => {
    const { adapter, formik } = testProps();
    const wrapper = renderPage(
      <ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} adapter={adapter} />,
    );

    fireEvent.change(wrapper.getByLabelText('Subnet'), { target: { value: 'known-subnet' } });
    fireEvent.change(wrapper.getByLabelText('Stack'), { target: { value: 'new-stack' } });
    fireEvent.change(wrapper.getByLabelText('Detail'), { target: { value: 'new-detail' } });

    expect(formik.setFieldValue.mock.calls).toEqual([
      ['subnet', 'known-subnet'],
      ['stack', 'new-stack'],
      ['freeFormDetails', 'new-detail'],
    ]);
    expect(adapter.applyCommandHandler).not.toHaveBeenCalled();
  });

  (['create', 'clone'] as const).forEach((mode) => {
    it(`binds the optional task reason to the command in ${mode} mode`, () => {
      const { formik } = testProps(command({ reason: 'existing reason', viewState: { mode } }));
      const wrapper = renderPage(<ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} />);
      expect(wrapper.getByLabelText('Task reason')).toHaveValue('existing reason');
      fireEvent.change(wrapper.getByLabelText('Task reason'), { target: { value: 'updated reason' } });
      expect(formik.setFieldValue).toHaveBeenCalledWith('reason', 'updated reason');
    });
  });

  it('hides the zonal field in regional mode', () => {
    const { formik } = testProps(command({ regional: true, zone: null }));
    const wrapper = renderPage(<ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} />);

    expect(wrapper.container.querySelector('[aria-label="Zone"]')).not.toBeInTheDocument();
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
      const wrapper = renderPage(<ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} />);
      const traffic = wrapper.getByLabelText('Send client requests to new instances');

      expect(traffic).toHaveProperty('checked', enableTraffic);
      expect(wrapper.getByRole('group', { name: 'Deployment strategy' })).toBeInTheDocument();
      expect(wrapper.getByLabelText('Selected strategy')).toHaveTextContent('custom');

      fireEvent.click(traffic);
      expect(formik.setFieldValue).toHaveBeenCalledWith('enableTraffic', !enableTraffic);
    });
  });

  it('forwards strategy commands and strategy-specific fields through Formik and the command callback', () => {
    const onStrategyChange = vi.fn();
    const values = command({
      onStrategyChange,
      strategy: '',
      viewState: { mode: 'editPipeline', disableStrategySelection: false },
    });
    const { formik } = testProps(values);
    const wrapper = renderPage(<ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} />);
    const strategy = { key: 'redblack', label: 'Red/Black' } as any;
    const updatedCommand = { ...values, strategy: 'redblack', maxRemainingAsgs: 2, scaleDown: false };

    fireEvent.click(wrapper.getByRole('button', { name: 'Select red black' }));
    fireEvent.click(wrapper.getByRole('button', { name: 'Update strategy field' }));

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
    const wrapper = renderPage(<ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} />);

    expect(wrapper.container.querySelector('[aria-label="Deployment strategy"]')).not.toBeInTheDocument();
    expect(values.strategy).toBe('custom');
    expect(values.strategyApplication).toBe('strategy-app');
    expect(values.strategyPipeline).toBe('strategy-pipeline');
    expect(formik.setValues).not.toHaveBeenCalled();
    expect(formik.setFieldValue).not.toHaveBeenCalled();
  });

  it('validates required fields and naming rules owned by the page', () => {
    const { formik } = testProps();
    expect(
      validateGceServerGroupBasicSettings(
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
    const wrapper = renderPage(<ServerGroupBasicSettings app={{ name: 'app' } as any} formik={formik} />);

    [
      ['Account', 'credentials', 'Account required.'],
      ['Region', 'region', 'Region required.'],
      ['Zone', 'zone', 'Zone required.'],
      ['Stack', 'stack', 'Stack can only contain letters and numbers.'],
      ['Detail', 'freeFormDetails', 'Detail can only contain letters, numbers, and dashes.'],
    ].forEach(([label, field, message]) => {
      const control = wrapper.getByLabelText(label);
      const errorId = `gce-server-group-${field}-error`;
      const alert = wrapper.container.querySelector(`[id="${errorId}"][role="alert"]`);

      expect(control, label).toHaveAttribute('aria-invalid', 'true');
      expect(control, label).toHaveAttribute('aria-describedby', errorId);
      expect(alert, label).toBeInTheDocument();
      expect(alert, label).toHaveTextContent(message);
    });
  });

  it('allows expression syntax in stack and detail when templating is enabled', () => {
    const values = command({
      stack: '${parameters.stack}',
      freeFormDetails: 'detail-${parameters.suffix}',
      viewState: { mode: 'editPipeline', templatingEnabled: true },
    });
    const { formik } = testProps(values);
    expect(validateGceServerGroupBasicSettings(values)).toEqual({});
  });
});

function selectOptions(wrapper: RenderResult, label: string): string[][] {
  return Array.from(wrapper.getByLabelText(label).querySelectorAll('option')).map((option) => [
    option.value,
    option.textContent || '',
  ]);
}

function testProps(values = command(), adapterResult = values) {
  const formik = ({
    values,
    setFieldValue: vi.fn(),
    setValues: vi.fn(),
  } as unknown) as FormikProps<IGceServerGroupCommand>;
  const adapter = ({
    applyCommandHandler: vi.fn().mockResolvedValue({ command: adapterResult, result: { dirty: {} } }),
  } as unknown) as Mocked<IGceServerGroupWizardAdapter>;
  return { adapter, formik };
}

function publishingFormik(values = command()): FormikProps<IGceServerGroupCommand> {
  const formik = ({
    values,
    setFieldValue: vi.fn(),
    setValues: vi.fn(),
  } as unknown) as FormikProps<IGceServerGroupCommand>;
  (formik.setValues as Mock).mockImplementation((nextValues: IGceServerGroupCommand) => {
    formik.values = nextValues;
  });
  return formik;
}

function imageValidatingAdapter(): Mocked<IGceServerGroupWizardAdapter> {
  return ({
    applyCommandHandler: vi.fn().mockImplementation((working: IGceServerGroupCommand) => {
      const imageAvailable = (working.backingData.allImages || []).some(
        ({ imageName }: { imageName: string }) => imageName === working.image,
      );
      return Promise.resolve({
        command: { ...working, image: imageAvailable ? working.image : null },
        result: { dirty: {} },
      });
    }),
  } as unknown) as Mocked<IGceServerGroupWizardAdapter>;
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

function renderPage(component: React.ReactElement): RenderResult {
  const container = document.body.appendChild(document.createElement('div'));
  return render(component, { baseElement: container, container });
}
