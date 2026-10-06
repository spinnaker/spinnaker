import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { setupUser } from '../../../utils/testUtils/userEvent';
import React from 'react';

import { AccountService } from '../../../account/AccountService';
import { PreconditionSelector } from './PreconditionSelector';

describe('<PreconditionSelector />', () => {
  const getClusterControl = () => {
    const toggle = screen.getByRole('button', { name: /Toggle for/ });
    return toggle.parentElement.previousElementSibling.querySelector('select, input') as HTMLInputElement;
  };

  beforeEach(() => {
    vi.spyOn(AccountService, 'listAccounts').mockReturnValue(Promise.resolve([]) as any);
  });

  const createProps = (overrides = {}) => ({
    application: { getDataSource: () => ({ data: [] }) } as any,
    onChange: vi.fn(),
    precondition: {} as any,
    strategy: false,
    upstreamStages: [] as any[],
    ...overrides,
  });

  it('initializes missing precondition fields using the first registered type', () => {
    const props = createProps();

    render(<PreconditionSelector {...props} />);

    expect(props.onChange).toHaveBeenCalledWith({
      context: {},
      failPipeline: true,
      type: 'clusterSize',
    });
  });

  it('clears context when the selected precondition type changes', () => {
    const props = createProps({
      precondition: {
        context: { expression: '${foo}' },
        failPipeline: false,
        type: 'expression',
      },
    });
    const { container } = render(<PreconditionSelector {...props} />);

    fireEvent.change(container.querySelector('select[name="preconditionType"]'), { target: { value: 'stageStatus' } });

    expect(props.onChange).toHaveBeenCalledWith({
      context: null,
      failPipeline: false,
      type: 'stageStatus',
    });
  });

  it('updates the expression context field', () => {
    const props = createProps({
      precondition: {
        context: { expression: '${foo}', failureMessage: 'stop' },
        failPipeline: true,
        type: 'expression',
      },
    });
    const { container } = render(<PreconditionSelector {...props} />);

    fireEvent.change(container.querySelector('textarea[name="expression"]'), { target: { value: '${bar}' } });

    expect(props.onChange).toHaveBeenCalledWith({
      context: { expression: '${bar}', failureMessage: 'stop' },
      failPipeline: true,
      type: 'expression',
    });
  });

  it('updates the fail pipeline flag for expression preconditions', () => {
    const props = createProps({
      precondition: {
        context: { expression: '${foo}' },
        failPipeline: true,
        type: 'expression',
      },
    });
    const { container } = render(<PreconditionSelector {...props} />);

    fireEvent.click(container.querySelector('input[name="failPipeline"]'));

    expect(props.onChange).toHaveBeenCalledWith({
      context: { expression: '${foo}' },
      failPipeline: false,
      type: 'expression',
    });
  });

  it('updates the expression failure message context field', () => {
    const props = createProps({
      precondition: {
        context: { expression: '${foo}', failureMessage: 'stop' },
        failPipeline: true,
        type: 'expression',
      },
    });
    const { container } = render(<PreconditionSelector {...props} />);

    fireEvent.change(container.querySelector('textarea[name="failureMessage"]'), { target: { value: 'keep going' } });

    expect(props.onChange).toHaveBeenCalledWith({
      context: { expression: '${foo}', failureMessage: 'keep going' },
      failPipeline: true,
      type: 'expression',
    });
  });

  it('renders the stage status editor and forwards context updates', async () => {
    const user = setupUser();
    const upstreamStages = [{ name: 'Bake' }, { name: 'Deploy' }] as any[];
    const onChange = vi.fn();
    const props = createProps({
      precondition: {
        context: { stageName: 'Bake', stageStatus: 'SUCCEEDED' },
        failPipeline: true,
        type: 'stageStatus',
      },
      upstreamStages,
      onChange,
    });
    const ControlledSelector = () => {
      const [precondition, setPrecondition] = React.useState(props.precondition);
      return (
        <PreconditionSelector
          {...props}
          precondition={precondition}
          onChange={(updated) => {
            onChange(updated);
            setPrecondition(updated);
          }}
        />
      );
    };
    render(<ControlledSelector />);

    expect(screen.getByRole('option', { name: 'Bake' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('option', { name: 'Succeeded' })).toHaveAttribute('aria-selected', 'true');
    await user.click(screen.getByRole('combobox', { name: 'Stage' }));
    await user.click(screen.getByText('Deploy'));
    await user.click(screen.getByRole('combobox', { name: 'Status' }));
    await user.click(screen.getByText('Terminal'));

    expect(onChange).toHaveBeenLastCalledWith({
      context: { stageName: 'Deploy', stageStatus: 'TERMINAL' },
      failPipeline: true,
      type: 'stageStatus',
    });
  });

  it('updates the cluster size account and clears the selected cluster', async () => {
    const props = createProps({
      precondition: {
        cloudProvider: 'aws',
        context: { cluster: 'api', credentials: 'test', moniker: { app: 'api' }, regions: ['us-west-1'] },
        failPipeline: true,
        type: 'clusterSize',
      },
    });
    vi.mocked(AccountService.listAccounts).mockResolvedValue([
      { name: 'prod', type: 'aws' },
      { name: 'test', type: 'aws' },
      { name: 'cf-prod', type: 'cloudfoundry' },
    ] as any);
    const { container } = render(<PreconditionSelector {...props} />);

    await waitFor(() => expect(screen.getByRole('option', { name: 'prod' })).toBeInTheDocument());
    fireEvent.change(container.querySelector('select[name="credentials"]'), { target: { value: 'prod' } });

    expect(props.onChange).toHaveBeenCalledWith({
      cloudProvider: 'aws',
      context: {
        cluster: undefined,
        credentials: 'prod',
        moniker: undefined,
        regions: ['us-west-1'],
      },
      failPipeline: true,
      type: 'clusterSize',
    });
  });

  it('updates cluster size regions and clears the selected cluster', () => {
    const application = {
      getDataSource: () => ({
        data: [
          { account: 'prod', cluster: 'api', region: 'us-west-1' },
          { account: 'prod', cluster: 'api', region: 'us-east-1' },
          { account: 'test', cluster: 'api', region: 'eu-west-1' },
        ],
      }),
    } as any;
    const props = createProps({
      application,
      precondition: {
        cloudProvider: 'aws',
        context: { cluster: 'api', credentials: 'prod', moniker: { app: 'api' }, regions: ['us-west-1'] },
        failPipeline: true,
        type: 'clusterSize',
      },
    });
    const { container } = render(<PreconditionSelector {...props} />);

    fireEvent.click(container.querySelector('input[name="regions"][value="us-east-1"]'));

    expect(props.onChange).toHaveBeenCalledWith({
      cloudProvider: 'aws',
      context: {
        cluster: undefined,
        credentials: 'prod',
        moniker: undefined,
        regions: ['us-west-1', 'us-east-1'],
      },
      failPipeline: true,
      type: 'clusterSize',
    });
  });

  it('lists cluster size clusters for the selected account and regions', () => {
    const application = {
      getDataSource: () => ({
        data: [
          { account: 'prod', cluster: 'api', region: 'us-west-1' },
          { account: 'prod', cluster: 'worker', region: 'us-east-1' },
          { account: 'test', cluster: 'test-api', region: 'us-west-1' },
        ],
      }),
    } as any;
    const props = createProps({
      application,
      precondition: {
        cloudProvider: 'aws',
        context: { credentials: 'prod', regions: ['us-west-1'] },
        failPipeline: true,
        type: 'clusterSize',
      },
    });
    const { container } = render(<PreconditionSelector {...props} />);

    const clusterSelector = getClusterControl();
    expect(clusterSelector).toHaveValue('');
    expect(clusterSelector.querySelectorAll('option')).toHaveLength(2);
    expect(screen.getByRole('option', { name: 'api' })).toBeInTheDocument();
  });

  it('renders the cluster dropdown for a new empty cluster-size precondition', () => {
    const props = createProps({
      precondition: {
        cloudProvider: 'aws',
        context: {},
        failPipeline: true,
        type: 'clusterSize',
      },
    });
    const { container } = render(<PreconditionSelector {...props} />);

    expect(getClusterControl()).toBeInstanceOf(HTMLSelectElement);
  });

  it('updates the cluster size cluster and matching moniker without sequence', () => {
    const application = {
      getDataSource: () => ({
        data: [
          {
            account: 'prod',
            cluster: 'api',
            moniker: { app: 'api', cluster: 'api', sequence: 1 },
            region: 'us-west-1',
          },
        ],
      }),
    } as any;
    const props = createProps({
      application,
      precondition: {
        cloudProvider: 'aws',
        context: { credentials: 'prod', regions: ['us-west-1'] },
        failPipeline: true,
        type: 'clusterSize',
      },
    });
    const { container } = render(<PreconditionSelector {...props} />);

    fireEvent.change(getClusterControl(), { target: { value: 'api' } });

    expect(props.onChange).toHaveBeenCalledWith({
      cloudProvider: 'aws',
      context: {
        cluster: 'api',
        credentials: 'prod',
        moniker: { app: 'api', cluster: 'api', sequence: undefined },
        regions: ['us-west-1'],
      },
      failPipeline: true,
      type: 'clusterSize',
    });
  });

  ['custom-cluster', '${parameters.cluster}'].forEach((cluster) => {
    it(`accepts the free-form cluster value ${cluster} and clears the moniker`, async () => {
      const user = setupUser();
      const application = {
        getDataSource: () => ({
          data: [
            {
              account: 'prod',
              cluster: 'api',
              moniker: { app: 'api', cluster: 'api', sequence: 1 },
              region: 'us-west-1',
            },
          ],
        }),
      } as any;
      const props = createProps({
        application,
        precondition: {
          cloudProvider: 'aws',
          context: {
            cluster,
            credentials: 'prod',
            moniker: { app: 'api', cluster: 'api' },
            regions: ['us-west-1'],
          },
          failPipeline: true,
          type: 'clusterSize',
        },
      });
      const onChange = vi.fn();
      const ControlledSelector = () => {
        const [precondition, setPrecondition] = React.useState(props.precondition);
        return (
          <PreconditionSelector
            {...props}
            precondition={precondition}
            onChange={(updated) => {
              onChange(updated);
              setPrecondition(updated);
            }}
          />
        );
      };
      render(<ControlledSelector />);

      const clusterInput = getClusterControl();
      expect(clusterInput).toHaveValue(cluster);
      await user.clear(clusterInput);
      await user.paste(cluster);

      expect(onChange).toHaveBeenLastCalledWith({
        cloudProvider: 'aws',
        context: {
          cluster,
          credentials: 'prod',
          moniker: undefined,
          regions: ['us-west-1'],
        },
        failPipeline: true,
        type: 'clusterSize',
      });
    });
  });

  it('updates cluster size expected-size fields', () => {
    const props = createProps({
      precondition: {
        cloudProvider: 'aws',
        context: { comparison: '==', expected: 2 },
        failPipeline: true,
        type: 'clusterSize',
      },
    });
    const { container } = render(<PreconditionSelector {...props} />);

    fireEvent.change(container.querySelector('select[name="comparison"]'), { target: { value: '>=' } });

    expect(props.onChange).toHaveBeenCalledWith({
      cloudProvider: 'aws',
      context: { comparison: '>=', expected: 2 },
      failPipeline: true,
      type: 'clusterSize',
    });

    props.onChange.mockClear();
    fireEvent.change(container.querySelector('input[name="expected"]'), { target: { value: '4' } });

    expect(props.onChange).toHaveBeenCalledWith({
      cloudProvider: 'aws',
      context: { comparison: '==', expected: 4 },
      failPipeline: true,
      type: 'clusterSize',
    });
  });

  it('hides cluster selection fields in strategy mode but keeps expected-size fields', () => {
    const props = createProps({
      precondition: {
        cloudProvider: 'aws',
        context: { comparison: '==', expected: 2 },
        failPipeline: true,
        type: 'clusterSize',
      },
      strategy: true,
    });
    const { container } = render(<PreconditionSelector {...props} />);

    expect(container.querySelector('select[name="credentials"]')).not.toBeInTheDocument();
    expect(container.querySelector('input[name="regions"]')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Toggle for/ })).not.toBeInTheDocument();
    expect(container.querySelector('select[name="comparison"]')).toBeInTheDocument();
    expect(container.querySelector('input[name="expected"]')).toBeInTheDocument();
  });

  it('updates the fail pipeline flag for cluster size preconditions', () => {
    const props = createProps({
      precondition: {
        cloudProvider: 'aws',
        context: { comparison: '==', expected: 2 },
        failPipeline: true,
        type: 'clusterSize',
      },
    });
    const { container } = render(<PreconditionSelector {...props} />);

    fireEvent.click(container.querySelector('input[name="failPipeline"]'));

    expect(props.onChange).toHaveBeenCalledWith({
      cloudProvider: 'aws',
      context: { comparison: '==', expected: 2 },
      failPipeline: false,
      type: 'clusterSize',
    });
  });
});
