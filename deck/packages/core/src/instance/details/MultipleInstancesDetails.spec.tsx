import type { UIRouterReact } from '@uirouter/react';
import { UIRouterContext, UIViewContext } from '@uirouter/react';
import { mock } from 'angular';
import { mount } from 'enzyme';
import React from 'react';

import { AccountService } from '../../account';
import { DeckRuntimeContext } from '../../bootstrap/DeckRuntimeContext';
import { ProviderSelectionService } from '../../cloudProvider/providerSelection/ProviderSelectionService';
import { ConfirmationModalService } from '../../confirmationModal';
import { CollapsibleSection } from '../../presentation';
import { REACT_MODULE } from '../../reactShims';
import { ClusterState } from '../../state';
import { InstanceWriter } from '../instance.write.service';
import { MultipleInstancesDetails } from './MultipleInstancesDetails';

describe('<MultipleInstancesDetails />', () => {
  const providerServiceDelegate = {} as any;
  let previousMultiselectModel: any;
  let $uiRouter: UIRouterReact;

  const app = {
    serverGroups: {
      data: [
        {
          account: 'prod',
          name: 'app-v001',
          region: 'us-west-2',
          loadBalancers: ['lb-a'],
          instances: [
            {
              id: 'i-1',
              name: 'instance-one',
              availabilityZone: 'us-west-2a',
              healthState: 'Up',
              health: [{ type: 'Discovery', state: 'Up' }],
            },
          ],
        },
      ],
      onRefresh: jasmine.createSpy('onRefresh').and.returnValue(() => null),
    },
  } as any;

  const mountDetails = () =>
    mount(
      <UIRouterContext.Provider value={$uiRouter}>
        <UIViewContext.Provider
          value={{
            fqn: 'application.insight.multipleInstances',
            context: $uiRouter.stateRegistry.get('application.insight.multipleInstances') as any,
          }}
        >
          <DeckRuntimeContext.Provider value={{ services: { providerServiceDelegate } } as any}>
            <MultipleInstancesDetails app={app} />
          </DeckRuntimeContext.Provider>
        </UIViewContext.Provider>
      </UIRouterContext.Provider>,
    );

  beforeEach(mock.module(REACT_MODULE));
  beforeEach(
    mock.inject((_$uiRouter_: UIRouterReact) => {
      $uiRouter = _$uiRouter_;
      ['application', 'application.insight', 'application.insight.multipleInstances'].forEach((name) => {
        if (!$uiRouter.stateRegistry.get(name)) {
          $uiRouter.stateRegistry.register({ name, url: `/${name.split('.').pop()}` } as any);
        }
      });
    }),
  );

  beforeEach(() => {
    previousMultiselectModel = ClusterState.multiselectModel;
    ClusterState.multiselectModel = {
      instanceGroups: [],
      instancesStream: { subscribe: () => null },
      deselectAllInstances: () => null,
    } as any;

    spyOn(AccountService, 'challengeDestructiveActions').and.returnValue(Promise.resolve(false));
    spyOn(ProviderSelectionService, 'isDisabled').and.returnValue(Promise.resolve(false));
    spyOn(ConfirmationModalService, 'confirm').and.stub();
    spyOn(InstanceWriter, 'terminateInstances').and.returnValue(Promise.resolve({}) as any);
    spyOn(ClusterState.multiselectModel.instancesStream, 'subscribe').and.callFake((callback: any) => {
      callback();
      return { unsubscribe: jasmine.createSpy('unsubscribe') } as any;
    });
    spyOn(ClusterState.multiselectModel, 'deselectAllInstances').and.stub();
    ClusterState.multiselectModel.instanceGroups = [
      {
        account: 'prod',
        cloudProvider: 'aws',
        instanceIds: ['i-1'],
        region: 'us-west-2',
        serverGroup: 'app-v001',
      },
    ] as any;
  });

  afterEach(() => {
    ClusterState.multiselectModel = previousMultiselectModel;
  });

  it('renders selected instances grouped by server group', () => {
    const wrapper = mountDetails();

    expect(wrapper.find('.details-panel h3').text()).toContain('1 Instance');
    expect(wrapper.text()).toContain('app-v001');
    expect(wrapper.text()).toContain('prod');
    expect(wrapper.text()).toContain('us-west-2');
    expect(wrapper.text()).toContain('instance-one');

    wrapper.unmount();
  });

  it('renders server groups in the shared collapsible section with the legacy wrapper element', () => {
    const wrapper = mountDetails();
    const section = wrapper.find(CollapsibleSection);

    expect(section.prop('heading')).toBe('Server Groups');
    expect(section.prop('defaultExpanded')).toBe(true);
    expect(wrapper.find('.multiple-instance-server-group').length).toBe(1);

    wrapper.unmount();
  });

  it('opens terminate confirmation using selected groups', () => {
    const wrapper = mountDetails();

    wrapper.find('button.dropdown-toggle').simulate('click');
    wrapper
      .find('a')
      .filterWhere((node) => node.text() === 'Terminate')
      .simulate('click');

    expect(ConfirmationModalService.confirm).toHaveBeenCalledWith(
      jasmine.objectContaining({
        buttonText: 'Terminate 1 instance',
        textToVerify: '1',
      }),
    );
    const confirmation = (ConfirmationModalService.confirm as jasmine.Spy).calls.mostRecent().args[0];

    confirmation.submitMethod();

    expect(InstanceWriter.terminateInstances).toHaveBeenCalledWith(
      [
        jasmine.objectContaining({
          account: 'prod',
          cloudProvider: 'aws',
          instanceIds: ['i-1'],
          instances: [
            jasmine.objectContaining({
              availabilityZone: 'us-west-2a',
              healthState: 'Up',
              id: 'i-1',
              name: 'instance-one',
            }),
          ],
          loadBalancers: ['lb-a'],
          region: 'us-west-2',
          serverGroup: 'app-v001',
        }),
      ],
      app,
      providerServiceDelegate,
    );

    wrapper.unmount();
  });

  it('closes the actions dropdown when an action is selected', () => {
    const wrapper = mountDetails();

    wrapper.find('button.dropdown-toggle').simulate('click');
    wrapper.update();
    expect(wrapper.find('.dropdown.open').exists()).toBe(true);

    wrapper
      .find('a')
      .filterWhere((node) => node.text() === 'Terminate')
      .simulate('click');
    wrapper.update();

    expect(wrapper.find('.dropdown.open').exists()).toBe(false);

    wrapper.unmount();
  });

  it('clears selected instances on unmount', () => {
    const wrapper = mountDetails();

    wrapper.unmount();

    expect(ClusterState.multiselectModel.deselectAllInstances).toHaveBeenCalled();
  });
});
