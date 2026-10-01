import React from 'react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';

import { ReactModal } from '@spinnaker/core';

import { GceProxyLoadBalancerModal } from '../common/GceProxyLoadBalancerModal';
import { GceHttpLoadBalancerModal } from '../http/GceHttpLoadBalancerModal';
import { GceNetworkLoadBalancerModal } from '../network/GceNetworkLoadBalancerModal';
import {
  GCE_LOAD_BALANCER_CHOICES,
  GceLoadBalancerChoiceModal,
  getGceLoadBalancerModal,
} from './GceLoadBalancerChoiceModal';

describe('GceLoadBalancerChoiceModal', () => {
  const application = { name: 'fnord' } as any;

  it('routes every exposed GCE load balancer type to its React modal', () => {
    expect(GCE_LOAD_BALANCER_CHOICES.map(({ type }) => type)).toEqual([
      'NETWORK',
      'INTERNAL',
      'TCP',
      'SSL',
      'HTTP',
      'INTERNAL_MANAGED',
    ]);
    expect(getGceLoadBalancerModal('NETWORK')).toBe(GceNetworkLoadBalancerModal);
    (['INTERNAL', 'TCP', 'SSL'] as const).forEach((type) => {
      expect(getGceLoadBalancerModal(type), type).toBe(GceProxyLoadBalancerModal);
    });
    (['HTTP', 'INTERNAL_MANAGED'] as const).forEach((type) => {
      expect(getGceLoadBalancerModal(type), type).toBe(GceHttpLoadBalancerModal);
    });
  });

  it('advertises pipeline support only at the fully routed choice entry point', () => {
    expect(GceLoadBalancerChoiceModal.supportsPipelineConfig).toBe(true);
  });

  it('passes modal sizing as ReactModal dialog options instead of component props', () => {
    const result = Promise.resolve();
    const show = vi.spyOn(ReactModal, 'show').mockReturnValue(result);

    const opened = GceLoadBalancerChoiceModal.show({ application } as any);

    expect(opened).toBe(result);
    expect(show).toHaveBeenCalledExactlyOnceWith(
      GceLoadBalancerChoiceModal,
      { application },
      { dialogClassName: 'create-pipeline-modal-overflow-visible modal-lg' },
    );
  });

  it('renders native modal sections and controls', () => {
    const { container } = render(<GceLoadBalancerChoiceModal application={application} />);

    expect(container.querySelectorAll('.modal-header')).toHaveLength(1);
    expect(container.querySelectorAll('.modal-body')).toHaveLength(1);
    expect(container.querySelectorAll('.modal-footer')).toHaveLength(1);
    expect(screen.getByRole('button', { name: /Configure Load Balancer/ })).toBeInTheDocument();
  });

  it('renders each type card as a native pressed-state button', () => {
    const { container } = render(<GceLoadBalancerChoiceModal application={application} />);
    const choices = Array.from(container.querySelectorAll<HTMLButtonElement>('button.card'));

    expect(choices).toHaveLength(GCE_LOAD_BALANCER_CHOICES.length);
    choices.forEach((choice, index) => {
      expect(choice.type, GCE_LOAD_BALANCER_CHOICES[index].type).toBe('button');
      expect(choice, GCE_LOAD_BALANCER_CHOICES[index].type).toHaveAttribute('aria-pressed', String(index === 0));
      expect(choice, GCE_LOAD_BALANCER_CHOICES[index].type).not.toBeDisabled();
    });

    fireEvent.click(choices[2]);

    expect(choices[0]).toHaveAttribute('aria-pressed', 'false');
    expect(choices[2]).toHaveAttribute('aria-pressed', 'true');
    expect(choices[2]).toHaveClass('active');
  });

  it('opens every exposed type in create and pipeline modes', () => {
    vi.spyOn(GceNetworkLoadBalancerModal, 'show').mockReturnValue(Promise.resolve() as any);
    vi.spyOn(GceProxyLoadBalancerModal, 'show').mockReturnValue(Promise.resolve() as any);
    vi.spyOn(GceHttpLoadBalancerModal, 'show').mockReturnValue(Promise.resolve() as any);

    ([false, true] as const).forEach((forPipelineConfig) => {
      GCE_LOAD_BALANCER_CHOICES.forEach((choice) => {
        render(<GceLoadBalancerChoiceModal application={application} forPipelineConfig={forPipelineConfig} />);
        fireEvent.click(choiceButton(choice.label));
        fireEvent.click(screen.getByRole('button', { name: /Configure Load Balancer/ }));

        expect(
          getGceLoadBalancerModal(choice.type).show,
          `${choice.type} ${forPipelineConfig ? 'pipeline' : 'create'}`,
        ).toHaveBeenCalledWith(
          expect.objectContaining({
            forPipelineConfig,
            isNew: !forPipelineConfig,
            loadBalancer: null,
            loadBalancerType: choice.type,
            mode: forPipelineConfig ? 'pipeline' : 'create',
          }),
        );
        cleanup();
      });
    });
  });

  it('opens a selected type in create mode without persisted data', () => {
    const closeModal = vi.fn();
    const result = Promise.resolve({ loadBalancerType: 'SSL' });
    const show = vi.spyOn(GceProxyLoadBalancerModal, 'show').mockReturnValue(result as any);
    render(<GceLoadBalancerChoiceModal application={application} closeModal={closeModal} />);

    fireEvent.click(choiceButton('SSL'));
    fireEvent.click(screen.getByRole('button', { name: /Configure Load Balancer/ }));

    expect(show).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({
        app: application,
        application,
        forPipelineConfig: false,
        isNew: true,
        loadBalancer: null,
        loadBalancerType: 'SSL',
        mode: 'create',
      }),
    );
    expect(closeModal).toHaveBeenCalledExactlyOnceWith(result);
  });

  it('opens a selected type in pipeline mode and propagates its command promise', async () => {
    const closeModal = vi.fn();
    const command = { loadBalancerType: 'INTERNAL_MANAGED', type: 'upsertLoadBalancer' };
    const result = Promise.resolve(command);
    const show = vi.spyOn(GceHttpLoadBalancerModal, 'show').mockReturnValue(result);
    render(<GceLoadBalancerChoiceModal application={application} closeModal={closeModal} forPipelineConfig={true} />);

    fireEvent.click(choiceButton('Internal HTTP(S)'));
    fireEvent.click(screen.getByRole('button', { name: /Configure Load Balancer/ }));

    expect(show).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({
        forPipelineConfig: true,
        loadBalancer: null,
        loadBalancerType: 'INTERNAL_MANAGED',
        mode: 'pipeline',
      }),
    );
    expect(closeModal).toHaveBeenCalledExactlyOnceWith(result);
    await expectAsync(closeModal.mock.lastCall[0]).toBeResolvedTo(command);
  });

  it('routes every exposed type directly to edit mode with the current load balancer', () => {
    vi.spyOn(GceNetworkLoadBalancerModal, 'show').mockImplementation(
      (props: any) => Promise.resolve(props.loadBalancer) as any,
    );
    vi.spyOn(GceProxyLoadBalancerModal, 'show').mockImplementation(
      (props: any) => Promise.resolve(props.loadBalancer) as any,
    );
    vi.spyOn(GceHttpLoadBalancerModal, 'show').mockImplementation(
      (props: any) => Promise.resolve(props.loadBalancer) as any,
    );
    const reactModalShow = vi.spyOn(ReactModal, 'show').mockReturnValue(undefined);

    GCE_LOAD_BALANCER_CHOICES.forEach((choice) => {
      const current = { account: 'account-a', loadBalancerType: choice.type, name: `fnord-${choice.type}` };
      GceLoadBalancerChoiceModal.show({ application, isNew: false, loadBalancer: current } as any);

      expect(getGceLoadBalancerModal(choice.type).show, choice.type).toHaveBeenCalledWith(
        expect.objectContaining({
          app: application,
          application,
          forPipelineConfig: false,
          isNew: false,
          loadBalancer: current,
          loadBalancerType: choice.type,
          mode: 'edit',
        }),
      );
    });

    expect(reactModalShow).not.toHaveBeenCalled();
  });

  it('routes every existing type to pipeline mode when editing pipeline configuration', () => {
    vi.spyOn(GceNetworkLoadBalancerModal, 'show').mockImplementation(
      (props: any) => Promise.resolve(props.loadBalancer) as any,
    );
    vi.spyOn(GceProxyLoadBalancerModal, 'show').mockImplementation(
      (props: any) => Promise.resolve(props.loadBalancer) as any,
    );
    vi.spyOn(GceHttpLoadBalancerModal, 'show').mockImplementation(
      (props: any) => Promise.resolve(props.loadBalancer) as any,
    );
    const reactModalShow = vi.spyOn(ReactModal, 'show').mockReturnValue(undefined);

    GCE_LOAD_BALANCER_CHOICES.forEach((choice) => {
      const current = { account: 'account-a', loadBalancerType: choice.type, name: `fnord-${choice.type}` };
      GceLoadBalancerChoiceModal.show({
        application,
        forPipelineConfig: true,
        isNew: false,
        loadBalancer: current,
      } as any);

      expect(getGceLoadBalancerModal(choice.type).show, choice.type).toHaveBeenCalledWith(
        expect.objectContaining({
          app: application,
          application,
          forPipelineConfig: true,
          isNew: false,
          loadBalancer: current,
          loadBalancerType: choice.type,
          mode: 'pipeline',
        }),
      );
    });

    expect(reactModalShow).not.toHaveBeenCalled();
  });

  ([undefined, '', 'http', 'UNKNOWN'] as const).forEach((persistedType) => {
    it(`blocks edit routing for unsupported persisted type ${String(persistedType)}`, () => {
      const networkShow = vi.spyOn(GceNetworkLoadBalancerModal, 'show').mockReturnValue(undefined);
      const proxyShow = vi.spyOn(GceProxyLoadBalancerModal, 'show').mockReturnValue(undefined);
      const httpShow = vi.spyOn(GceHttpLoadBalancerModal, 'show').mockReturnValue(undefined);
      const blockedResult = Promise.resolve();
      const reactModalShow = vi.spyOn(ReactModal, 'show').mockReturnValue(blockedResult);
      const loadBalancer = { name: 'fnord', loadBalancerType: persistedType };

      const result = GceLoadBalancerChoiceModal.show({ application, isNew: false, loadBalancer } as any);

      expect(result).toBe(blockedResult);
      expect(reactModalShow).toHaveBeenCalledExactlyOnceWith(
        GceLoadBalancerChoiceModal,
        { application, isNew: false, loadBalancer },
        { dialogClassName: 'create-pipeline-modal-overflow-visible modal-lg' },
      );
      expect(networkShow).not.toHaveBeenCalled();
      expect(proxyShow).not.toHaveBeenCalled();
      expect(httpShow).not.toHaveBeenCalled();
    });
  });

  ([undefined, 'http', 'UNKNOWN'] as const).forEach((persistedType) => {
    it(`renders a non-submittable blocked pipeline-edit state for ${String(persistedType)}`, () => {
      const { container } = render(
        <GceLoadBalancerChoiceModal
          application={application}
          forPipelineConfig={true}
          isNew={false}
          loadBalancer={{ name: 'fnord', loadBalancerType: persistedType }}
        />,
      );

      expect(screen.getByRole('alert')).toHaveTextContent('cannot be edited');
      expect(screen.getByRole('alert')).toHaveTextContent(persistedType || 'missing');
      const choices = Array.from(container.querySelectorAll<HTMLButtonElement>('button.card'));
      expect(choices).toHaveLength(GCE_LOAD_BALANCER_CHOICES.length);
      choices.forEach((choice) => {
        expect(choice).toBeDisabled();
        expect(choice).toHaveAttribute('aria-pressed', 'false');
      });
      expect(screen.getByRole('button', { name: /Configure Load Balancer/ })).toBeDisabled();
    });
  });

  it('does not open a type modal when blocked submission is invoked programmatically', () => {
    const networkShow = vi.spyOn(GceNetworkLoadBalancerModal, 'show').mockReturnValue(undefined);
    const proxyShow = vi.spyOn(GceProxyLoadBalancerModal, 'show').mockReturnValue(undefined);
    const httpShow = vi.spyOn(GceHttpLoadBalancerModal, 'show').mockReturnValue(undefined);
    render(
      <GceLoadBalancerChoiceModal
        application={application}
        forPipelineConfig={true}
        isNew={false}
        loadBalancer={{ name: 'fnord', loadBalancerType: 'http' }}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: /Configure Load Balancer/ }));

    expect(networkShow).not.toHaveBeenCalled();
    expect(proxyShow).not.toHaveBeenCalled();
    expect(httpShow).not.toHaveBeenCalled();
  });
});

function choiceButton(label: string): HTMLButtonElement {
  const button = screen.getByText(label, { selector: '.load-balancer-label' }).closest('button');
  if (!(button instanceof HTMLButtonElement)) {
    throw new Error(`No choice button found for ${label}`);
  }
  return button;
}
