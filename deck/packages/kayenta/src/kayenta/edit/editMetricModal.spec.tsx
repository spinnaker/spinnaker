import { render, screen, within } from '@testing-library/react';
import { setupUser } from '../../../../core/src/utils/testUtils/userEvent';
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
    useInlineTemplateEditor: false,
    disableEdit: false,
    validationErrors: {},
    rename: vi.fn(),
    changeGroup: vi.fn(),
    updateDirection: vi.fn(),
    updateNanStrategy: vi.fn(),
    updateCriticality: vi.fn(),
    updateDataRequired: vi.fn(),
    confirm: vi.fn(),
    cancel: vi.fn(),
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
        id: '1',
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
  const dispatch = vi.fn();
  store.dispatch = dispatch;

  beforeEach(() => dispatch.mockClear());

  const buildComponent = (props: object) =>
    render(
      <Provider store={store}>
        <EditMetricModal {...mockProps} {...props} />
      </Provider>,
    );
  const getDialog = () => screen.getByRole('dialog', { name: 'Configure Metric' });

  it('renders without crashing', () => {
    buildComponent({});

    expect(getDialog()).toBeVisible();
    expect(within(getDialog()).getByText('Configure Metric')).toBeVisible();
  });

  it('calls cancel when the cancel button is clicked', async () => {
    const user = setupUser();
    buildComponent({});

    await user.click(within(getDialog()).getByRole('button', { name: 'Cancel' }));

    expect(store.dispatch).toHaveBeenCalledWith(Creators.editMetricCancel());
  });

  it('calls confirm when the confirm button is clicked', async () => {
    const user = setupUser();
    buildComponent({});

    await user.click(within(getDialog()).getByRole('button', { name: 'OK' }));

    expect(store.dispatch).toHaveBeenCalledWith(Creators.editMetricConfirm());
  });

  it('calls updateDirection when a direction radio button is clicked', async () => {
    const user = setupUser();
    buildComponent({});

    await user.click(within(getDialog()).getByRole('radio', { name: 'Increase' }));

    expect(store.dispatch).toHaveBeenCalledWith(Creators.updateMetricDirection({ id: '1', direction: 'increase' }));
  });

  it('calls updateNanStrategy when the nan strategy is changed', async () => {
    const user = setupUser();
    buildComponent({});

    await user.click(within(getDialog()).getByRole('radio', { name: 'Replace with zero' }));

    expect(store.dispatch).toHaveBeenCalledWith(Creators.updateMetricNanStrategy({ id: '1', strategy: 'replace' }));
  });

  it('calls updateOutlierStrategy when the outlier strategy is changed', async () => {
    const user = setupUser();
    buildComponent({});

    await user.click(within(getDialog()).getAllByRole('radio', { name: 'Remove' })[1]);

    expect(store.dispatch).toHaveBeenCalledWith(Creators.updateMetricOutlierStrategy({ id: '1', strategy: 'remove' }));
  });

  it('calls updateCriticality when the criticality checkbox is changed', async () => {
    const user = setupUser();
    buildComponent({});

    await user.click(within(getDialog()).getByRole('checkbox', { name: 'Fail the canary if this metric fails' }));

    expect(store.dispatch).toHaveBeenCalledWith(Creators.updateMetricCriticality({ id: '1', critical: true }));
  });

  it('calls updateDataRequired when the data required checkbox is changed', async () => {
    const user = setupUser();
    buildComponent({ disableEdit: true });

    await user.click(within(getDialog()).getByRole('checkbox', { name: 'Fail the metric if data is missing' }));

    expect(store.dispatch).toHaveBeenCalledWith(Creators.updateMetricDataRequired({ id: '1', mustHaveData: true }));
  });
});
