import { mount } from 'enzyme';
import { ICanaryMetricConfig } from 'kayenta';
import * as Creators from 'kayenta/actions/creators';
import React from 'react';
import { Provider } from 'react-redux';
import { createMockStore } from 'redux-test-utils';

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
    useInlineTemplateEditor: false,
    disableEdit: false,
    validationErrors: {},
    rename: jest.fn(),
    changeGroup: jest.fn(),
    updateDirection: jest.fn(),
    updateNanStrategy: jest.fn(),
    updateCriticality: jest.fn(),
    updateDataRequired: jest.fn(),
    confirm: jest.fn(),
    cancel: jest.fn(),
  };

  const mockState = {
    app: {
      disableConfigEdit: false,
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

  const store = createMockStore(mockState);
  store.dispatch = jest.fn();

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
