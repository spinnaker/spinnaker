import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import React from 'react';
import ReactDOM from 'react-dom';

import { DeckRuntimeContext } from '../bootstrap/DeckRuntimeContext';
import type { DeckRuntimeServices } from '../bootstrap/DeckRuntimeServices';
import { setDirectRouter } from '../navigation/directRouter';
import type { IRouterInjectedProps } from '../navigation/routerContext';
import { withRouter } from '../navigation/routerContext';
import type { IModalComponentProps } from './modal';
import { ReactModal } from './ReactModal';

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
});
