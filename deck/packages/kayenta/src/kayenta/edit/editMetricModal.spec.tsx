import { mount } from 'enzyme';
import * as Creators from '../actions/creators';
import { KayentaAccountType } from '../domain';
import type { ICanaryMetricConfig } from '../domain/ICanaryConfig';
import React from 'react';
import { Provider } from 'react-redux';
import { createStore } from 'redux';

import EditMetricModal from './editMetricModal';

describe('EditMetricModal', () => {
  const mockProps = {
    metric: {
      id: '1',
      name: 'Test Metric',
      groups: ['Test Group'],
      type: 'Test Type',
      analysisConfigurations: {
        canary: {
          direction: 'increase',
          nanStrategy: 'default',
          critical: false,
        },
      },
    },
    groups: ['Group 1', 'Group 2'],
    isTemplateValid: true,
    disableEdit: false,
    validationErrors: {},
    rename: jasmine.createSpy(),
    changeGroup: jasmine.createSpy(),
    updateDirection: jasmine.createSpy(),
    updateNanStrategy: jasmine.createSpy(),
    updateCriticality: jasmine.createSpy(),
    updateDataRequired: jasmine.createSpy(),
    confirm: jasmine.createSpy(),
    cancel: jasmine.createSpy(),
  };

  const mockState = {
    app: {
      disableConfigEdit: false,
    },
    data: {
      kayentaAccounts: {
        data: [
          {
            name: 'account-1',
            type: 'prometheus',
            supportedTypes: [KayentaAccountType.MetricsStore],
            metricsStoreType: 'prometheus',
          },
        ],
      },
    },
    selectedConfig: {
      editingMetric: {
        name: 'Test Metric',
        query: {
          serviceType: 'prometheus',
        },
        groups: ['Group 1'],
      },
      editingTemplate: {},
      group: {
        list: ['Group 1', 'Group 2'],
      },
      metricList: [] as ICanaryMetricConfig[],
    },
  };

  const store = createStore(() => mockState);
  const dispatch = jasmine.createSpy('dispatch');
  store.dispatch = dispatch;

  beforeEach(() => dispatch.calls.reset());

  const buildComponent = (props: object) =>
    mount(
      <Provider store={store}>
        <EditMetricModal {...mockProps} {...props} />
      </Provider>,
    ).find(EditMetricModal);

  it('renders without crashing', () => {
    const component = buildComponent({});
    expect(component.exists()).toBe(true);
  });

  it('calls cancel when the cancel button is clicked', () => {
    const component = buildComponent({});
    const cancelBtn = component
      .find('button')
      .filterWhere((btn) => btn.text().trim() === 'Cancel')
      .at(0);
    cancelBtn.simulate('click');
    expect(store.dispatch).toHaveBeenCalledWith(Creators.editMetricCancel());
  });

  it('calls confirm when the confirm button is clicked', () => {
    const component = buildComponent({});
    const confirmBtn = component
      .find('button')
      .filterWhere((btn) => btn.text().trim() === 'OK')
      .at(0);
    confirmBtn.simulate('click');
    expect(store.dispatch).toHaveBeenCalledWith(Creators.editMetricConfirm());
  });

  it('calls updateDirection when a direction radio button is clicked', () => {
    const component = buildComponent({});
    const changeDirectionEvent = { target: { value: 'increase', dataset: { id: '1' } } };
    component.find('input[name="direction"][value="increase"]').simulate('change', changeDirectionEvent);
    expect(store.dispatch).toHaveBeenCalledWith(Creators.updateMetricDirection({ id: '1', direction: 'increase' }));
  });

  it('calls updateNanStrategy when the nan strategy is changed', () => {
    const component = buildComponent({});
    const changeNanStrategyEvent = { target: { value: 'replace', dataset: { id: '1' } } };
    component.find('input[name="nanStrategy"][value="replace"]').simulate('change', changeNanStrategyEvent);
    expect(store.dispatch).toHaveBeenCalledWith(Creators.updateMetricNanStrategy({ id: '1', strategy: 'replace' }));
  });

  it('calls updateOutlierStrategy when the outlier strategy is changed', () => {
    const component = buildComponent({});
    const changeOutlierStrategyEvent = { target: { value: 'remove', dataset: { id: '1' } } };
    component.find('input[name="outlierStrategy"][value="remove"]').simulate('change', changeOutlierStrategyEvent);
    expect(store.dispatch).toHaveBeenCalledWith(Creators.updateMetricOutlierStrategy({ id: '1', strategy: 'remove' }));
  });

  it('calls updateCriticality when the criticality checkbox is changed', () => {
    const component = buildComponent({});
    const changeCriticalityEvent = { target: { checked: true, dataset: { id: '1' } } };
    component.find('input[type="checkbox"][name="criticality"]').simulate('change', changeCriticalityEvent);
    expect(store.dispatch).toHaveBeenCalledWith(Creators.updateMetricCriticality({ id: '1', critical: true }));
  });

  it('calls updateDataRequired when the data required checkbox is changed', () => {
    const component = buildComponent({ disableEdit: true });
    const changeDataRequiredEvent = { target: { checked: true, dataset: { id: '1' } } };
    component.find('input[type="checkbox"][name="dataRequired"]').simulate('change', changeDataRequiredEvent);
    expect(store.dispatch).toHaveBeenCalledWith(Creators.updateMetricDataRequired({ id: '1', mustHaveData: true }));
  });
});
