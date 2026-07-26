import { mount } from 'enzyme';
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
      stageFieldUpdated: jasmine.createSpy('stageFieldUpdated'),
      updateStage: jasmine.createSpy('updateStage'),
      updateStageField: jasmine.createSpy('updateStageField'),
    };
  }

  function resolveModalWith(result: any): void {
    spyOn(ProviderSelectionService, 'selectProvider').and.returnValue(Promise.resolve('test'));
    spyOn(CloudProviderRegistry, 'getProvider').and.returnValue({
      loadBalancer: {
        CreateLoadBalancerModal: {
          supportsPipelineConfig: true,
          show: jasmine.createSpy('show').and.returnValue(Promise.resolve(result)),
        },
      },
    } as any);
  }

  async function flushModalResult(): Promise<void> {
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
  }

  function mountConfig(props: ReturnType<typeof createProps>) {
    return mount(
      <DeckRuntimeContext.Provider value={{ services: runtimeServices }}>
        <CreateLoadBalancerStageConfig {...props} />
      </DeckRuntimeContext.Provider>,
    );
  }

  it('appends every operation returned when creating a load balancer', async () => {
    const existing = { name: 'existing' };
    const originalLoadBalancers = [existing];
    const created = [{ name: 'listener-1' }, { name: 'listener-2' }];
    const props = createProps(originalLoadBalancers);
    resolveModalWith(created);
    const component = mountConfig(props);

    component.find('button.add-new').simulate('click');
    await flushModalResult();

    expect(props.stage.loadBalancers).toEqual([existing, ...created]);
    expect(props.stage.loadBalancers).not.toBe(originalLoadBalancers);
    expect(originalLoadBalancers).toEqual([existing]);
    expect(props.stageFieldUpdated).toHaveBeenCalledTimes(1);
  });

  it('appends a single operation returned when creating a load balancer', async () => {
    const existing = { name: 'existing' };
    const created = { name: 'created' };
    const props = createProps([existing]);
    resolveModalWith(created);
    const component = mountConfig(props);

    component.find('button.add-new').simulate('click');
    await flushModalResult();

    expect(props.stage.loadBalancers).toEqual([existing, created]);
    expect(props.stageFieldUpdated).toHaveBeenCalledTimes(1);
  });

  it('replaces the edited slot with every returned operation in order', async () => {
    const before = { name: 'before' };
    const edited = { name: 'edited' };
    const after = { name: 'after' };
    const originalLoadBalancers = [before, edited, after];
    const replacements = [{ name: 'listener-1' }, { name: 'listener-2' }];
    const props = createProps(originalLoadBalancers);
    resolveModalWith(replacements);
    const component = mountConfig(props);

    component
      .find('button')
      .filterWhere((button) => button.text() === 'Edit')
      .at(1)
      .simulate('click');
    await flushModalResult();

    expect(props.stage.loadBalancers).toEqual([before, ...replacements, after]);
    expect(props.stage.loadBalancers).not.toBe(originalLoadBalancers);
    expect(originalLoadBalancers).toEqual([before, edited, after]);
    expect(props.stageFieldUpdated).toHaveBeenCalledTimes(1);
  });

  it('replaces the edited slot with a single returned operation', async () => {
    const before = { name: 'before' };
    const edited = { name: 'edited' };
    const after = { name: 'after' };
    const replacement = { name: 'replacement' };
    const props = createProps([before, edited, after]);
    resolveModalWith(replacement);
    const component = mountConfig(props);

    component
      .find('button')
      .filterWhere((button) => button.text() === 'Edit')
      .at(1)
      .simulate('click');
    await flushModalResult();

    expect(props.stage.loadBalancers).toEqual([before, replacement, after]);
    expect(props.stageFieldUpdated).toHaveBeenCalledTimes(1);
  });
});
