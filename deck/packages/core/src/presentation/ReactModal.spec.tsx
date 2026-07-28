import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import React from 'react';
import ReactDOM from 'react-dom';
import { act } from 'react-dom/test-utils';

import { ReactModal } from './ReactModal';
import { AccountService } from '../account';
import { ExpectedArtifactModal, ExpectedArtifactService } from '../artifact';
import { DeckRuntimeContext } from '../bootstrap/DeckRuntimeContext';
import type { DeckRuntimeServices } from '../bootstrap/DeckRuntimeServices';
import type { IModalComponentProps } from './modal';
import { setDirectRouter } from '../navigation/directRouter';
import type { IRouterInjectedProps } from '../navigation/routerContext';
import { withRouter } from '../navigation/routerContext';
import { diagnosticLogger } from '../utils/diagnosticLogger';
import { TaskMonitor, TaskMonitorWrapper } from '../task';
import { SpelText } from '../widgets/spelText/SpelText';

const TestModal = (_props: IModalComponentProps): JSX.Element => null;

const getLastRender = (renders: React.ReactElement[]): React.ReactElement =>
  renders[renders.length - 1] as React.ReactElement;

const flushPromises = (): Promise<void> => Promise.resolve();

describe('ReactModal', () => {
  beforeEach(() => setDirectRouter(null));
  afterEach(() => setDirectRouter(null));

  it('resolves after the modal exit completes', async () => {
    const renders: React.ReactElement[] = [];
    spyOn(ReactDOM, 'render').and.callFake((element) => {
      renders.push(element as React.ReactElement);
      return null;
    });
    const unmountSpy = spyOn(ReactDOM, 'unmountComponentAtNode').and.returnValue(true);

    let resolvedValue: string | null = null;
    const promise = ReactModal.show(TestModal);
    promise.then((value) => {
      resolvedValue = value as string;
    });

    const initialModal = getLastRender(renders);
    const closeModal = (initialModal.props.children as any).props.closeModal as (value?: string) => void;
    closeModal('gce');

    await flushPromises();
    expect(resolvedValue).toBeNull();

    const afterCloseModal = getLastRender(renders);
    afterCloseModal.props.onExited();

    await flushPromises();
    expect(resolvedValue).toBe('gce');
    expect(unmountSpy).toHaveBeenCalled();
  });

  it('rejects after the modal exit completes', async () => {
    const renders: React.ReactElement[] = [];
    spyOn(ReactDOM, 'render').and.callFake((element) => {
      renders.push(element as React.ReactElement);
      return null;
    });
    const unmountSpy = spyOn(ReactDOM, 'unmountComponentAtNode').and.returnValue(true);

    let rejectedValue: string | null = null;
    const promise = ReactModal.show(TestModal);
    promise.catch((value) => {
      rejectedValue = value as string;
    });

    const initialModal = getLastRender(renders);
    const dismissModal = (initialModal.props.children as any).props.dismissModal as (value?: string) => void;
    dismissModal('cancelled');

    await flushPromises();
    expect(rejectedValue).toBeNull();

    const afterDismissModal = getLastRender(renders);
    afterDismissModal.props.onExited();

    await flushPromises();
    expect(rejectedValue).toBe('cancelled');
    expect(unmountSpy).toHaveBeenCalled();
  });
});

describe('ReactModal router context', () => {
  let router: UIRouterReact;

  beforeEach(() => {
    router = new UIRouterReact();
    setDirectRouter(router);
  });

  afterEach(() => {
    ReactModal.dismissAll('test-cleanup');
    setDirectRouter(null);
    router.dispose();
  });

  it('provides the direct router to routed modal components', () => {
    const renders: React.ReactElement[] = [];
    spyOn(ReactDOM, 'render').and.callFake((element) => {
      renders.push(element as React.ReactElement);
      return null;
    });

    const RoutedModalComponent = withRouter(
      class extends React.Component<IModalComponentProps & IRouterInjectedProps> {
        public render(): React.ReactNode {
          return null;
        }
      },
    );

    ReactModal.show(RoutedModalComponent, {} as any, { animation: false });

    const provider = getLastRender(renders);
    expect(provider.type).toBe(UIRouterContext.Provider);
    expect(provider.props.value).toBe(router);
    expect(provider.props.children.props.children.type).toBe(RoutedModalComponent);
  });

  it('provides explicitly supplied runtime services to modal components', () => {
    const renders: React.ReactElement[] = [];
    spyOn(ReactDOM, 'render').and.callFake((element) => {
      renders.push(element as React.ReactElement);
      return null;
    });
    const runtimeServices = {} as DeckRuntimeServices;

    class RuntimeModalComponent extends React.Component<IModalComponentProps> {
      public render(): React.ReactNode {
        return null;
      }
    }

    ReactModal.show(RuntimeModalComponent, {} as any, { animation: false }, runtimeServices);

    const routerProvider = getLastRender(renders);
    const runtimeProvider = routerProvider.props.children;
    expect(runtimeProvider.type).toBe(DeckRuntimeContext.Provider);
    expect(runtimeProvider.props.value.services).toBe(runtimeServices);
    expect(runtimeProvider.props.children.props.children.type).toBe(RuntimeModalComponent);
  });

  it('provides the active default runtime services when none are explicitly supplied', async () => {
    const runtimeServices = {} as DeckRuntimeServices;

    class RuntimeModalComponent extends React.Component<IModalComponentProps> {
      public static contextType = DeckRuntimeContext;
      public declare context: React.ContextType<typeof DeckRuntimeContext>;

      public componentDidMount(): void {
        this.props.closeModal(this.context?.services === runtimeServices);
      }

      public render(): React.ReactNode {
        return null;
      }
    }

    try {
      ReactModal.setDefaultRuntimeServices(runtimeServices);

      const result = await ReactModal.show(RuntimeModalComponent, {} as any, { animation: false });

      expect(result).toBe(true);
    } finally {
      ReactModal.setDefaultRuntimeServices(null);
    }
  });

  it('prefers explicitly supplied runtime services over the active default', async () => {
    const defaultServices = {} as DeckRuntimeServices;
    const explicitServices = {} as DeckRuntimeServices;

    class RuntimeModalComponent extends React.Component<IModalComponentProps> {
      public static contextType = DeckRuntimeContext;
      public declare context: React.ContextType<typeof DeckRuntimeContext>;

      public componentDidMount(): void {
        this.props.closeModal(this.context?.services);
      }

      public render(): React.ReactNode {
        return null;
      }
    }

    try {
      ReactModal.setDefaultRuntimeServices(defaultServices);

      const result = await ReactModal.show(RuntimeModalComponent, {} as any, { animation: false }, explicitServices);

      expect(result).toBe(explicitServices);
    } finally {
      ReactModal.setDefaultRuntimeServices(null);
    }
  });

  it('provides default runtime services to SpelText rendered by ExpectedArtifactModal', async () => {
    const runtimeServices = { executionService: {} } as DeckRuntimeServices;
    const spelMount = spyOn(SpelText.prototype, 'componentDidMount').and.callThrough();
    spyOn(AccountService, 'getArtifactAccounts').and.returnValue(
      Promise.resolve([{ name: 'custom-artifact', types: ['custom/object'] }]),
    );
    ReactModal.setDefaultRuntimeServices(runtimeServices);
    const modal = ExpectedArtifactModal.show({
      expectedArtifact: ExpectedArtifactService.createEmptyArtifact(),
      pipeline: { stages: [] } as any,
    } as any);
    const settlement = modal.catch((reason) => reason);

    try {
      await new Promise((resolve) => setTimeout(resolve, 0));

      expect(spelMount).toHaveBeenCalled();
      expect((spelMount.calls.mostRecent().object as SpelText).context?.services).toBe(runtimeServices);
    } finally {
      ReactModal.dismissAll('test-cleanup');
      await settlement;
      ReactModal.setDefaultRuntimeServices(null);
    }
  });

  it('dismisses all active modals with the supplied reason', async () => {
    const OpenModal = (): React.ReactElement => null;
    const firstModal = ReactModal.show(OpenModal, {} as any, { animation: false });
    const secondModal = ReactModal.show(OpenModal, {} as any, { animation: false });
    const firstRejection = expectAsync(firstModal).toBeRejectedWith('reauthentication');
    const secondRejection = expectAsync(secondModal).toBeRejectedWith('reauthentication');

    ReactModal.dismissAll('reauthentication');

    await Promise.all([firstRejection, secondRejection]);
    expect(() => ReactModal.dismissAll('reauthentication')).not.toThrow();
  });

  it('synchronously force-dismisses an animated modal and cancels active task polling', async () => {
    jasmine.clock().install();
    const root = document.createElement('div');
    try {
      const poll = jasmine.createSpy('poll');
      const monitor = new TaskMonitor({ title: 'Active task' });
      monitor.submitting = true;
      monitor.task = { poller: window.setTimeout(poll, 100) } as any;
      const onModalClose = spyOn(monitor, 'onModalClose').and.callThrough();
      const render = spyOn(ReactDOM, 'render').and.callThrough();
      const ActiveTaskModal = () => <TaskMonitorWrapper monitor={monitor} />;
      let modal: Promise<unknown>;

      act(() => {
        modal = ReactModal.show(ActiveTaskModal, {} as any);
      });
      const modalRoot = render.calls.mostRecent().args[1] as HTMLElement;
      root.appendChild(modalRoot);
      document.body.appendChild(root);
      const rejectionReasons: unknown[] = [];
      modal.catch((reason) => rejectionReasons.push(reason));

      ReactModal.dismissAll('runtime-disposed');
      ReactModal.dismissAll('ignored-repeat');

      expect(modalRoot.isConnected).toBe(false);
      expect(onModalClose).toHaveBeenCalledTimes(1);
      jasmine.clock().tick(100);
      expect(poll).not.toHaveBeenCalled();
      await Promise.resolve();
      expect(rejectionReasons).toEqual(['runtime-disposed']);
    } finally {
      root.remove();
      jasmine.clock().uninstall();
    }
  });

  it('force-unmounts an animated modal that was already closing', async () => {
    const render = spyOn(ReactDOM, 'render').and.callThrough();
    let dismissModal: (reason?: unknown) => void;
    const OpenModal = ({ dismissModal: dismiss }: IModalComponentProps): React.ReactElement => {
      dismissModal = dismiss;
      return null;
    };
    const modal = ReactModal.show(OpenModal, {} as any);
    const modalRoot = render.calls.mostRecent().args[1] as HTMLElement;
    document.body.appendChild(modalRoot);
    const settlement = modal.catch((reason) => reason);
    let didSettle = false;
    settlement.then(() => {
      didSettle = true;
    });

    dismissModal('user-dismissed');
    await Promise.resolve();
    expect(didSettle).toBe(false);
    expect(modalRoot.isConnected).toBe(true);

    ReactModal.dismissAll('runtime-disposed');

    expect(modalRoot.isConnected).toBe(false);
    expect(await settlement).toBe('user-dismissed');
  });

  it('continues force-dismissing active modals when one unmount fails', async () => {
    const unmountFailure = new Error('unmount failed');
    const reportError = spyOn(diagnosticLogger, 'error');
    const render = spyOn(ReactDOM, 'render').and.callThrough();
    const OpenModal = (): React.ReactElement => null;
    const firstRejections: unknown[] = [];
    const secondRejections: unknown[] = [];

    ReactModal.show(OpenModal, {} as any).catch((reason) => firstRejections.push(reason));
    const failingRoot = render.calls.mostRecent().args[1] as Element;
    document.body.appendChild(failingRoot);
    ReactModal.show(OpenModal, {} as any).catch((reason) => secondRejections.push(reason));
    const healthyRoot = render.calls.mostRecent().args[1] as Element;
    document.body.appendChild(healthyRoot);

    const actualUnmount = ReactDOM.unmountComponentAtNode;
    spyOn(ReactDOM, 'unmountComponentAtNode').and.callFake((container) => {
      actualUnmount(container);
      if (container === failingRoot) {
        throw unmountFailure;
      }
      return true;
    });

    expect(() => ReactModal.dismissAll('runtime-disposed')).not.toThrow();
    expect(failingRoot.isConnected).toBe(false);
    expect(healthyRoot.isConnected).toBe(false);
    await Promise.resolve();

    expect(firstRejections).toEqual(['runtime-disposed']);
    expect(secondRejections).toEqual(['runtime-disposed']);
    expect(reportError).toHaveBeenCalledOnceWith('Failed to force-unmount React modal', unmountFailure);

    expect(() => ReactModal.dismissAll('runtime-disposed')).not.toThrow();
    await Promise.resolve();
    expect(firstRejections).toEqual(['runtime-disposed']);
    expect(secondRejections).toEqual(['runtime-disposed']);
  });

  it('reports an asynchronous exit unmount failure and still removes the root', async () => {
    setDirectRouter(null);
    const unmountFailure = new Error('unmount failed');
    const reportError = spyOn(diagnosticLogger, 'error');
    const render = spyOn(ReactDOM, 'render').and.callThrough();
    let dismissModal: (reason?: unknown) => void;
    const OpenModal = ({ dismissModal: dismiss }: IModalComponentProps): React.ReactElement => {
      dismissModal = dismiss;
      return null;
    };
    const modal = ReactModal.show(OpenModal, {} as any, { animation: true });
    const renderedModal = render.calls.mostRecent().args[0] as React.ReactElement;
    const onExited = renderedModal.props.onExited as () => void;
    const root = render.calls.mostRecent().args[1] as HTMLElement;
    document.body.appendChild(root);
    render.and.stub();
    const actualUnmount = ReactDOM.unmountComponentAtNode;
    spyOn(ReactDOM, 'unmountComponentAtNode').and.callFake((container) => {
      actualUnmount(container);
      throw unmountFailure;
    });

    const rejection = modal.catch((reason) => reason);
    dismissModal('dismissed');

    expect(() => onExited()).not.toThrow();
    expect(await rejection).toBe('dismissed');
    expect(root.isConnected).toBe(false);
    expect(reportError).toHaveBeenCalledOnceWith('Failed to unmount React modal after exit animation', unmountFailure);
  });
});
