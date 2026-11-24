import type { Mock } from 'vitest';
import type { FormikProps } from 'formik';
import React from 'react';
import { fireEvent, render } from '@testing-library/react';
import type { RenderResult } from '@testing-library/react';

import { DeckRuntimeContext } from '@spinnaker/core';

import type {
  IGceServerGroupCommand,
  IGceServerGroupWizardAdapter,
  IGceServerGroupWizardPageProps,
} from '../GceServerGroupWizard.types';
import { ServerGroupInstanceType, validateGceServerGroupInstanceType } from './ServerGroupInstanceType';

describe('ServerGroupInstanceType', () => {
  let runtimeServices: any;
  const renderPage = (component: React.ReactElement) => renderWithRuntime(component, runtimeServices);

  beforeEach(() => {
    runtimeServices = {
      instanceTypeService: {
        getInstanceTypeDetails: vi.fn().mockReturnValue(new Promise(() => undefined)),
      },
    };
  });

  it('renders accessible instance controls and preserves persisted unavailable references', () => {
    const values = command({
      instanceType: 'retired-machine',
      minCpuPlatform: 'Retired CPU',
      acceleratorConfigs: [{ acceleratorType: 'retired-gpu', acceleratorCount: 3 }],
    });

    const wrapper = renderPage(<ServerGroupInstanceType {...props(values)} />);

    expect(wrapper.getByText(/Machine type/, { selector: 'label' })).toHaveAttribute('for', 'gce-machine-type');
    expect(optionText(wrapper, '#gce-machine-type')).toContain('retired-machine (Unavailable)');
    expect(optionText(wrapper, '#gce-min-cpu-platform')).toContain('Retired CPU (Unavailable)');
    expect(optionText(wrapper, '#gce-accelerator-type-0')).toContain('retired-gpu (Unavailable)');
    expect(optionValues(wrapper, '#gce-accelerator-count-0')).toContain('3');
  });

  it('selects standard machine types without mutating current Formik values', () => {
    runtimeServices.instanceTypeService = {
      getInstanceTypeDetails: vi.fn().mockReturnValue(new Promise(() => undefined)),
    };
    const values = command({ instanceType: 'n1-standard-1' });
    const testProps = props(values);
    const wrapper = renderPage(<ServerGroupInstanceType {...testProps} />);

    fireEvent.change(control(wrapper, '#gce-machine-type'), { target: { value: 'n2-standard-2' } });

    expect(values.instanceType).toBe('n1-standard-1');
    expect(testProps.formik.setValues).toHaveBeenCalledWith(
      expect.objectContaining({
        instanceType: 'n2-standard-2',
        viewState: expect.objectContaining({ instanceProfile: 'custom' }),
      }),
    );
  });

  [
    { description: 'whole-field expression', instanceType: '${ parameters.machineType }' },
    { description: 'embedded expression', instanceType: 'n1-standard-${parameters.size}' },
    { description: 'partial expression input', instanceType: 'n1-standard-${parameters.si' },
  ].forEach(({ description, instanceType }) => {
    it(`publishes pipeline ${description} machine types without concrete type reconciliation`, () => {
      const instanceTypeService = {
        getInstanceTypeDetails: vi.fn(),
      };
      runtimeServices.instanceTypeService = instanceTypeService;
      const values = command({
        viewState: { mode: 'editPipeline', instanceProfile: 'custom', acceleratorTypes: [] },
      });
      const adapter = adapterForHandlers();
      const testProps = props(values, adapter);
      const wrapper = renderPage(<ServerGroupInstanceType {...testProps} />);

      fireEvent.change(control(wrapper, '#gce-machine-type'), { target: { value: instanceType } });

      expect(testProps.formik.setValues).toHaveBeenCalledWith(expect.objectContaining({ instanceType }));
      expect(instanceTypeService.getInstanceTypeDetails).not.toHaveBeenCalled();
      expect(adapter.applyCommandHandler).not.toHaveBeenCalled();
    });
  });

  it('loads standard machine type details and reconciles storage and accelerator capabilities', async () => {
    const detailsRequest = deferred<any>();
    const instanceTypeService = {
      getInstanceTypeDetails: vi.fn().mockReturnValue(detailsRequest.promise),
    };
    runtimeServices.instanceTypeService = instanceTypeService;
    const values = command({
      disks: [
        { type: 'pd-standard', sizeGb: 100 },
        { type: 'local-ssd', sizeGb: 375 },
      ],
      acceleratorConfigs: [{ acceleratorType: 'retired-gpu', acceleratorCount: 1 }],
      viewState: {
        mode: 'create',
        instanceProfile: 'custom',
        acceleratorTypes: [],
        overriddenStorageDescription: '1x100',
      },
    });
    const adapter = adapterForHandlers();
    (adapter.applyCommandHandler as Mock).mockImplementation(async (next, handler) => ({
      command: {
        ...next,
        acceleratorConfigs: [],
        viewState: {
          ...next.viewState,
          acceleratorTypes: [{ name: 'nvidia-tesla-t4', availableCardCounts: [1, 2, 4] }],
          handledBy: handler,
        },
      },
      result: { dirty: {} },
    }));
    const testProps = props(values, adapter);
    const wrapper = renderPage(<ServerGroupInstanceType {...testProps} />);

    fireEvent.change(control(wrapper, '#gce-machine-type'), { target: { value: 'n2-standard-2' } });

    expect(instanceTypeService.getInstanceTypeDetails).toHaveBeenCalledWith('gce', 'n2-standard-2');
    detailsRequest.resolve({
      name: 'n2-standard-2',
      storage: {
        localSSDSupported: false,
        defaultSettings: { disks: [{ type: 'pd-ssd', sizeGb: 20 }] },
      },
    });
    await settle();

    expect(adapter.applyCommandHandler).toHaveBeenCalledWith(
      expect.objectContaining({
        instanceType: 'n2-standard-2',
        disks: [{ type: 'pd-ssd', sizeGb: 20 }],
        viewState: expect.objectContaining({
          instanceTypeDetails: expect.objectContaining({ name: 'n2-standard-2' }),
        }),
      }),
      'zoneChanged',
    );
    const published = (testProps.formik.setValues as Mock).mock.lastCall[0];
    expect(published.disks).toEqual([{ type: 'pd-ssd', sizeGb: 20 }]);
    expect(published.viewState.overriddenStorageDescription).toBeUndefined();
    expect(published.viewState.instanceTypeDetails.storage.localSSDSupported).toBe(false);
    expect(published.viewState.acceleratorTypes).toEqual([{ name: 'nvidia-tesla-t4', availableCardCounts: [1, 2, 4] }]);
    expect(published.acceleratorConfigs).toEqual([]);
  });

  it('ignores an old machine type request that completes after a newer selection', async () => {
    const oldRequest = deferred<any>();
    const newRequest = deferred<any>();
    const instanceTypeService = {
      getInstanceTypeDetails: vi
        .fn()
        .mockImplementation((_provider, instanceType) =>
          instanceType === 'n2-standard-2' ? oldRequest.promise : newRequest.promise,
        ),
    };
    runtimeServices.instanceTypeService = instanceTypeService;
    const values = command();
    const adapter = adapterForHandlers();
    const testProps = props(values, adapter);
    const wrapper = renderPage(<ServerGroupInstanceType {...testProps} />);

    fireEvent.change(control(wrapper, '#gce-machine-type'), { target: { value: 'n2-standard-2' } });
    setFormikValues(wrapper, testProps, (testProps.formik.setValues as Mock).mock.lastCall[0], runtimeServices);
    fireEvent.change(control(wrapper, '#gce-machine-type'), { target: { value: 'n1-standard-1' } });

    newRequest.resolve(instanceTypeDetails('n1-standard-1', 30));
    await settle();
    oldRequest.resolve(instanceTypeDetails('n2-standard-2', 40));
    await settle();

    const published = (testProps.formik.setValues as Mock).mock.lastCall[0];
    expect(published.instanceType).toBe('n1-standard-1');
    expect(published.viewState.instanceTypeDetails.name).toBe('n1-standard-1');
    expect(published.disks).toEqual([{ type: 'pd-ssd', sizeGb: 30 }]);
    expect(adapter.applyCommandHandler).toHaveBeenCalledTimes(1);
  });

  it('ignores standard machine type details after the user switches to a custom type', async () => {
    const detailsRequest = deferred<any>();
    runtimeServices.instanceTypeService = {
      getInstanceTypeDetails: vi.fn().mockReturnValue(detailsRequest.promise),
    };
    const values = command();
    const adapter = adapterForHandlers();
    const testProps = props(values, adapter);
    const wrapper = renderPage(<ServerGroupInstanceType {...testProps} />);

    fireEvent.change(control(wrapper, '#gce-machine-type'), { target: { value: 'n2-standard-2' } });
    setFormikValues(wrapper, testProps, (testProps.formik.setValues as Mock).mock.lastCall[0], runtimeServices);
    fireEvent.click(control(wrapper, '#gce-machine-type-custom'));
    await settle();
    detailsRequest.resolve(instanceTypeDetails('n2-standard-2', 40));
    await settle();

    const published = (testProps.formik.setValues as Mock).mock.lastCall[0];
    expect(published.instanceType).toContain('custom-');
    expect(published.viewState.instanceTypeDetails).toBeUndefined();
    expect(published.disks).toEqual([{ type: 'pd-ssd', sizeGb: 20 }]);
    expect((adapter.applyCommandHandler as Mock).mock.calls.map((args) => args[1])).toEqual(['customInstanceChanged']);
  });

  it('preserves a disk edit made while machine type details are loading', async () => {
    const detailsRequest = deferred<any>();
    runtimeServices.instanceTypeService = {
      getInstanceTypeDetails: vi.fn().mockReturnValue(detailsRequest.promise),
    };
    const values = command();
    const testProps = props(values);
    const wrapper = renderPage(<ServerGroupInstanceType {...testProps} />);

    fireEvent.change(control(wrapper, '#gce-machine-type'), { target: { value: 'n2-standard-2' } });
    const selectedValues = (testProps.formik.setValues as Mock).mock.lastCall[0];
    setFormikValues(
      wrapper,
      testProps,
      {
        ...selectedValues,
        disks: [{ type: 'pd-standard', sizeGb: 500 }],
      },
      runtimeServices,
    );
    detailsRequest.resolve(instanceTypeDetails('n2-standard-2', 40));
    await settle();

    const published = (testProps.formik.setValues as Mock).mock.lastCall[0];
    expect(published.instanceType).toBe('n2-standard-2');
    expect(published.viewState.instanceTypeDetails.name).toBe('n2-standard-2');
    expect(published.disks).toEqual([{ type: 'pd-standard', sizeGb: 500 }]);
  });

  it('builds custom CPU and memory selections through the adapter handler', async () => {
    const values = command({
      instanceType: 'n2-custom-4-16384',
      viewState: {
        mode: 'create',
        instanceProfile: 'buildCustom',
        customInstance: { instanceFamily: 'N2', vCpuCount: 4, memory: 16, extendedMemory: false },
        acceleratorTypes: [],
      },
    });
    const adapter = adapterForHandlers();
    const testProps = props(values, adapter);
    const wrapper = renderPage(<ServerGroupInstanceType {...testProps} />);

    fireEvent.change(control(wrapper, '#gce-custom-cpu'), { target: { value: '8' } });
    await settle();

    expect(adapter.applyCommandHandler).toHaveBeenCalledWith(
      expect.objectContaining({
        instanceType: 'n2-custom-8-16384',
        viewState: expect.objectContaining({
          customInstance: expect.objectContaining({ vCpuCount: 8, memory: 16 }),
        }),
      }),
      'customInstanceChanged',
    );
    expect(testProps.formik.setValues).toHaveBeenCalledWith(
      expect.objectContaining({ instanceType: 'n2-custom-8-16384', handledBy: 'customInstanceChanged' }),
    );
    expect(values.instanceType).toBe('n2-custom-4-16384');
  });

  it('keeps spot scheduling fields compatible when preemptibility changes', () => {
    const values = command({ preemptible: false, automaticRestart: true, onHostMaintenance: 'MIGRATE' });
    const testProps = props(values);
    const wrapper = renderPage(<ServerGroupInstanceType {...testProps} />);

    fireEvent.click(control(wrapper, '#gce-preemptible'));

    expect(testProps.formik.setValues).toHaveBeenCalledWith(
      expect.objectContaining({ preemptible: true, automaticRestart: false, onHostMaintenance: 'TERMINATE' }),
    );
    expect(values.preemptible).toBe(false);
  });

  it('keeps accelerator type and count compatible while preserving unrelated configs', () => {
    const values = command({
      acceleratorConfigs: [
        { acceleratorType: 'nvidia-tesla-t4', acceleratorCount: 4 },
        { acceleratorType: 'retired-gpu', acceleratorCount: 3 },
      ],
    });
    const testProps = props(values);
    const wrapper = renderPage(<ServerGroupInstanceType {...testProps} />);

    fireEvent.change(control(wrapper, '#gce-accelerator-type-0'), { target: { value: 'nvidia-tesla-v100' } });

    expect(testProps.formik.setValues).toHaveBeenCalledWith(
      expect.objectContaining({
        acceleratorConfigs: [
          { acceleratorType: 'nvidia-tesla-v100', acceleratorCount: 2 },
          { acceleratorType: 'retired-gpu', acceleratorCount: 3 },
        ],
      }),
    );
    expect(values.acceleratorConfigs[0]).toEqual({ acceleratorType: 'nvidia-tesla-t4', acceleratorCount: 4 });
  });

  it('updates only the boot disk and uses finite numeric bounds', () => {
    const values = command({
      disks: [
        { type: 'pd-ssd', sizeGb: 20 },
        { type: 'local-ssd', sizeGb: 375 },
        { type: 'pd-standard', sizeGb: 100, sourceImage: 'secondary-image' },
      ],
    });
    const testProps = props(values);
    const wrapper = renderPage(<ServerGroupInstanceType {...testProps} />);

    expect(control(wrapper, '#gce-boot-disk-size')).toHaveAttribute('min', '10');
    expect(control(wrapper, '#gce-boot-disk-size')).toHaveAttribute('max', '65536');
    fireEvent.change(control(wrapper, '#gce-boot-disk-type'), { target: { value: 'pd-standard' } });

    expect(testProps.formik.setValues).toHaveBeenCalledWith(
      expect.objectContaining({
        disks: [
          { type: 'pd-standard', sizeGb: 20 },
          { type: 'local-ssd', sizeGb: 375 },
          { type: 'pd-standard', sizeGb: 100, sourceImage: 'secondary-image' },
        ],
      }),
    );
    expect(values.disks[0]).toEqual({ type: 'pd-ssd', sizeGb: 20 });
  });

  it('accepts pipeline expressions but validates finite concrete disk and accelerator values', () => {
    const invalid = command({
      instanceType: '',
      disks: [{ type: 'pd-ssd', sizeGb: Infinity }],
      acceleratorConfigs: [{ acceleratorType: 'nvidia-tesla-t4', acceleratorCount: 3 }],
    });

    expect(validateGceServerGroupInstanceType(invalid)).toEqual({
      instanceType: 'Machine type required.',
      disks: 'Boot disk size must be between 10 and 65536 GB.',
      acceleratorConfigs: 'Accelerator count is unavailable for the selected type.',
    });

    const expressionValues = command({
      viewState: { mode: 'editPipeline', instanceProfile: 'custom', acceleratorTypes: [] },
      disks: [{ type: 'pd-ssd', sizeGb: '${ parameters.diskSize }' }],
      acceleratorConfigs: [{ acceleratorType: 'retired-gpu', acceleratorCount: '${ parameters.gpuCount }' }],
    });
    expect(validateGceServerGroupInstanceType(expressionValues)).toEqual({});
    const expressionWrapper = renderPage(<ServerGroupInstanceType {...props(expressionValues)} />);
    expect(control(expressionWrapper, '#gce-boot-disk-size')).toHaveAttribute('type', 'text');
    expect(control(expressionWrapper, '#gce-accelerator-count-0')).toHaveAttribute('type', 'text');
  });

  it('renders and associates instance type, boot disk, and accelerator validation errors', () => {
    const values = command({
      instanceType: '',
      disks: [{ type: '', sizeGb: 0 }],
      acceleratorConfigs: [{ acceleratorType: '', acceleratorCount: 0 }],
    });
    const errors = {
      instanceType: 'Machine type required.',
      disks: 'Boot disk size must be between 10 and 65536 GB.',
      acceleratorConfigs: 'Accelerator count is unavailable for the selected type.',
    };
    const testProps = props(values, adapterForHandlers(), errors);
    const wrapper = renderPage(<ServerGroupInstanceType {...testProps} />);

    [
      ['#gce-machine-type', 'gce-machine-type-error'],
      ['#gce-boot-disk-type', 'gce-boot-disk-error'],
      ['#gce-boot-disk-size', 'gce-boot-disk-error'],
      ['#gce-accelerator-type-0', 'gce-accelerators-error'],
      ['#gce-accelerator-count-0', 'gce-accelerators-error'],
    ].forEach(([selector, errorId]) => expectAssociatedError(wrapper, selector, errorId));
    expectValidationAlert(wrapper, 'gce-machine-type-error', errors.instanceType);
    expectValidationAlert(wrapper, 'gce-boot-disk-error', errors.disks);
    expectValidationAlert(wrapper, 'gce-accelerators-error', errors.acceleratorConfigs);

    const customValues = command({
      instanceType: '',
      viewState: {
        mode: 'create',
        instanceProfile: 'buildCustom',
        customInstance: { instanceFamily: 'N2', vCpuCount: 4, memory: 16, extendedMemory: false },
        acceleratorTypes: [],
      },
    });
    rerenderInstanceType(
      wrapper,
      { ...testProps, formik: { ...testProps.formik, errors, values: customValues } },
      runtimeServices,
    );

    expectAssociatedError(wrapper, '#gce-custom-cpu', 'gce-machine-type-error');
    expectAssociatedError(wrapper, '#gce-custom-memory', 'gce-machine-type-error');
    expectValidationAlert(wrapper, 'gce-machine-type-error', errors.instanceType);
  });
});

function expectAssociatedError(wrapper: RenderResult, selector: string, errorId: string): void {
  const element = control(wrapper, selector);
  expect(element, selector).toHaveAttribute('aria-invalid', 'true');
  expect(element, selector).toHaveAttribute('aria-describedby', errorId);
}

function expectValidationAlert(wrapper: RenderResult, id: string, message: string): void {
  const alert = control(wrapper, `#${id}`);
  expect(alert, id).toHaveAttribute('role', 'alert');
  expect(alert, id).toHaveTextContent(message);
}

function optionText(wrapper: RenderResult, selector: string): string[] {
  return Array.from(control(wrapper, selector).querySelectorAll('option')).map((option) => option.textContent || '');
}

function optionValues(wrapper: RenderResult, selector: string): string[] {
  return Array.from(control(wrapper, selector).querySelectorAll('option')).map((option) => option.value);
}

function control(wrapper: RenderResult, selector: string): HTMLElement {
  const element = wrapper.container.querySelector<HTMLElement>(selector);
  if (!element) {
    throw new Error(`Missing control: ${selector}`);
  }
  return element;
}

function renderWithRuntime(component: React.ReactElement, runtimeServices: any): RenderResult {
  const container = document.body.appendChild(document.createElement('div'));
  return render(
    <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>{component}</DeckRuntimeContext.Provider>,
    { baseElement: container, container },
  );
}

function rerenderInstanceType(
  rendered: RenderResult,
  pageProps: IGceServerGroupWizardPageProps,
  runtimeServices: any,
): void {
  rendered.rerender(
    <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>
      <ServerGroupInstanceType {...pageProps} />
    </DeckRuntimeContext.Provider>,
  );
}

function props(
  values: IGceServerGroupCommand,
  adapter = adapterForHandlers(),
  errors: Record<string, string> = {},
): IGceServerGroupWizardPageProps {
  return {
    app: { name: 'app' } as any,
    adapter,
    formik: ({ errors, values, setValues: vi.fn() } as unknown) as FormikProps<IGceServerGroupCommand>,
  };
}

function adapterForHandlers(): IGceServerGroupWizardAdapter {
  return {
    configureCommand: vi.fn().mockImplementation(async (_app, next) => next),
    applyCommandHandler: vi.fn().mockImplementation(async (next, handler) => ({
      command: { ...next, handledBy: handler },
      result: { dirty: {} },
    })),
    applyConfigurationUpdate: vi.fn().mockImplementation(async (next) => ({
      command: next,
      result: { dirty: {} },
    })),
    applyConfigurationRefresh: vi.fn().mockImplementation(async (next) => ({
      command: next,
      result: { dirty: {} },
    })),
  };
}

function command(overrides: Partial<IGceServerGroupCommand> = {}): IGceServerGroupCommand {
  return {
    application: 'app',
    credentials: 'test',
    regional: false,
    region: 'us-central1',
    zone: 'us-central1-a',
    instanceType: 'n1-standard-1',
    minCpuPlatform: '(Automatic)',
    preemptible: false,
    automaticRestart: true,
    onHostMaintenance: 'MIGRATE',
    disks: [{ type: 'pd-ssd', sizeGb: 20 }],
    acceleratorConfigs: [],
    backingData: {
      persistentDiskTypes: ['pd-standard', 'pd-ssd', 'hyperdisk-balanced'],
      customInstanceTypes: {
        instanceFamilyList: ['N1', 'N2'],
        vCpuList: [1, 2, 4, 8],
        memoryList: [4, 8, 16, 32],
      },
      filtered: {
        cpuPlatforms: ['(Automatic)', 'Intel Haswell'],
        instanceTypes: ['n1-standard-1', 'n2-standard-2'],
      },
    },
    capacity: { desired: 1 },
    distributionPolicy: { zones: [] },
    stack: 'main',
    freeFormDetails: 'detail',
    image: 'image',
    selectedProvider: 'gce',
    viewState: {
      mode: 'create',
      instanceProfile: 'custom',
      acceleratorTypes: [
        {
          name: 'nvidia-tesla-t4',
          description: 'NVIDIA T4',
          availableCardCounts: [1, 2, 4],
          maximumCardsPerInstance: 4,
        },
        {
          name: 'nvidia-tesla-v100',
          description: 'NVIDIA V100',
          availableCardCounts: [1, 2],
          maximumCardsPerInstance: 2,
        },
      ],
    },
    ...overrides,
  };
}

async function settle(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
}

function deferred<T>(): { promise: Promise<T>; resolve: (value: T) => void } {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => {
    resolve = promiseResolve;
  });
  return { promise, resolve };
}

function instanceTypeDetails(name: string, diskSize: number): any {
  return {
    name,
    storage: {
      localSSDSupported: false,
      defaultSettings: { disks: [{ type: 'pd-ssd', sizeGb: diskSize }] },
    },
  };
}

function setFormikValues(
  wrapper: RenderResult,
  testProps: IGceServerGroupWizardPageProps,
  values: IGceServerGroupCommand,
  runtimeServices: any,
): void {
  const formik = ({ ...testProps.formik, values } as unknown) as FormikProps<IGceServerGroupCommand>;
  rerenderInstanceType(wrapper, { ...testProps, formik }, runtimeServices);
}
