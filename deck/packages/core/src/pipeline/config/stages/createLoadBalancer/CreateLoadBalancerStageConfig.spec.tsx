import { render, screen } from '@testing-library/react';
import { setupUser } from '../../../../utils/testUtils/userEvent';
import React from 'react';

import { CloudProviderRegistry, ProviderSelectionService } from '../../../../cloudProvider';
import { DeckRuntimeContext } from '../../../../bootstrap/DeckRuntimeContext';
import { CreateLoadBalancerStageConfig } from './CreateLoadBalancerStageConfig';

describe('<CreateLoadBalancerStageConfig />', () => {
  const runtimeServices = {} as any;

  function createProps(loadBalancers: any[] = []) {
    const stage = {
      loadBalancers,
      refId: '1',
      requisiteStageRefIds: [],
      type: 'upsertLoadBalancers',
    };

    return {
      application: { name: 'fnord' } as any,
      pipeline: { stages: [stage] } as any,
      stage,
      stageFieldUpdated: vi.fn(),
      updateStage: vi.fn(),
      updateStageField: vi.fn(),
    };
  }

  function resolveModalWith(result: any): void {
    vi.spyOn(ProviderSelectionService, 'selectProvider').mockReturnValue(Promise.resolve('test'));
    vi.spyOn(CloudProviderRegistry, 'getProvider').mockReturnValue({
      loadBalancer: {
        CreateLoadBalancerModal: {
          supportsPipelineConfig: true,
          show: vi.fn().mockReturnValue(Promise.resolve(result)),
        },
      },
    } as any);
  }

  async function flushModalResult(): Promise<void> {
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
  }

  function renderConfig(props: ReturnType<typeof createProps>) {
    return render(
      <DeckRuntimeContext.Provider value={{ services: runtimeServices }}>
        <CreateLoadBalancerStageConfig {...props} />
      </DeckRuntimeContext.Provider>,
    );
  }

  it('appends every operation returned when creating a load balancer', async () => {
    const user = setupUser();
    const existing = { name: 'existing' };
    const originalLoadBalancers = [existing];
    const created = [{ name: 'listener-1' }, { name: 'listener-2' }];
    const props = createProps(originalLoadBalancers);
    resolveModalWith(created);
    renderConfig(props);

    await user.click(screen.getByRole('button', { name: /Add load balancer/i }));
    await flushModalResult();

    expect(props.stage.loadBalancers).toEqual([existing, ...created]);
    expect(props.stage.loadBalancers).not.toBe(originalLoadBalancers);
    expect(originalLoadBalancers).toEqual([existing]);
    expect(props.stageFieldUpdated).toHaveBeenCalledTimes(1);
  });

  it('appends a single operation returned when creating a load balancer', async () => {
    const user = setupUser();
    const existing = { name: 'existing' };
    const created = { name: 'created' };
    const props = createProps([existing]);
    resolveModalWith(created);
    renderConfig(props);

    await user.click(screen.getByRole('button', { name: /Add load balancer/i }));
    await flushModalResult();

    expect(props.stage.loadBalancers).toEqual([existing, created]);
    expect(props.stageFieldUpdated).toHaveBeenCalledTimes(1);
  });

  it('replaces the edited slot with every returned operation in order', async () => {
    const user = setupUser();
    const before = { name: 'before' };
    const edited = { name: 'edited' };
    const after = { name: 'after' };
    const originalLoadBalancers = [before, edited, after];
    const replacements = [{ name: 'listener-1' }, { name: 'listener-2' }];
    const props = createProps(originalLoadBalancers);
    resolveModalWith(replacements);
    renderConfig(props);

    await user.click(screen.getAllByRole('button', { name: 'Edit' })[1]);
    await flushModalResult();

    expect(props.stage.loadBalancers).toEqual([before, ...replacements, after]);
    expect(props.stage.loadBalancers).not.toBe(originalLoadBalancers);
    expect(originalLoadBalancers).toEqual([before, edited, after]);
    expect(props.stageFieldUpdated).toHaveBeenCalledTimes(1);
  });

  it('replaces the edited slot with a single returned operation', async () => {
    const user = setupUser();
    const before = { name: 'before' };
    const edited = { name: 'edited' };
    const after = { name: 'after' };
    const replacement = { name: 'replacement' };
    const props = createProps([before, edited, after]);
    resolveModalWith(replacement);
    renderConfig(props);

    await user.click(screen.getAllByRole('button', { name: 'Edit' })[1]);
    await flushModalResult();

    expect(props.stage.loadBalancers).toEqual([before, replacement, after]);
    expect(props.stageFieldUpdated).toHaveBeenCalledTimes(1);
  });
});
