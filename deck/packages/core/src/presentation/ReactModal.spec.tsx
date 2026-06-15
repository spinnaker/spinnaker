import React from 'react';
import ReactDOM from 'react-dom';

import type { IModalComponentProps } from './modal';
import { ReactModal } from './ReactModal';

const TestModal = (_props: IModalComponentProps): JSX.Element => null;

const getLastRender = (renders: React.ReactElement[]): React.ReactElement =>
  renders[renders.length - 1] as React.ReactElement;

const flushPromises = (): Promise<void> => Promise.resolve();

describe('ReactModal', () => {
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
