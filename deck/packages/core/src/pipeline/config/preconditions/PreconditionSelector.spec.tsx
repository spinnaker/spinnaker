import { mount } from 'enzyme';
import React from 'react';

import { AccountService } from '../../../account/AccountService';
import { PreconditionSelector } from './PreconditionSelector';
import { StageStatusPreconditionConfig } from './types/stageStatus/StageStatusPreconditionConfig';

describe('<PreconditionSelector />', () => {
  beforeEach(() => {
    spyOn(AccountService, 'listAccounts').and.returnValue(Promise.resolve([]) as any);
  });

  const createProps = (overrides = {}) => ({
    application: { getDataSource: () => ({ data: [] }) } as any,
    onChange: jasmine.createSpy('onChange'),
    precondition: {} as any,
    strategy: false,
    upstreamStages: [] as any[],
    ...overrides,
  });

  it('initializes missing precondition fields using the first registered type', () => {
    const props = createProps();

    mount(<PreconditionSelector {...props} />);

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
    const component = mount(<PreconditionSelector {...props} />);

    component.find('select[name="preconditionType"]').simulate('change', { target: { value: 'stageStatus' } });

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
    const component = mount(<PreconditionSelector {...props} />);

    component.find('textarea[name="expression"]').simulate('change', { target: { value: '${bar}' } });

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
    const component = mount(<PreconditionSelector {...props} />);

    component.find('input[name="failPipeline"]').simulate('change', { target: { checked: false } });

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
    const component = mount(<PreconditionSelector {...props} />);

    component.find('textarea[name="failureMessage"]').simulate('change', { target: { value: 'keep going' } });

    expect(props.onChange).toHaveBeenCalledWith({
      context: { expression: '${foo}', failureMessage: 'keep going' },
      failPipeline: true,
      type: 'expression',
    });
  });

  it('renders the stage status editor and forwards context updates', () => {
    const upstreamStages = [{ name: 'Bake' }, { name: 'Deploy' }] as any[];
    const props = createProps({
      precondition: {
        context: { stageName: 'Bake', stageStatus: 'SUCCEEDED' },
        failPipeline: true,
        type: 'stageStatus',
      },
      upstreamStages,
    });
    const component = mount(<PreconditionSelector {...props} />);

    const editor = component.find(StageStatusPreconditionConfig);

    expect(editor.length).toBe(1);
    expect(editor.prop('preconditionContext')).toEqual({ stageName: 'Bake', stageStatus: 'SUCCEEDED' });
    expect(editor.prop('upstreamStages')).toBe(upstreamStages);

    editor.prop('updatePreconditionContext')({ stageName: 'Deploy', stageStatus: 'TERMINAL' });

    expect(props.onChange).toHaveBeenCalledWith({
      context: { stageName: 'Deploy', stageStatus: 'TERMINAL' },
      failPipeline: true,
      type: 'stageStatus',
    });
  });

  it('updates the cluster size account and clears the selected cluster', () => {
    const props = createProps({
      precondition: {
        cloudProvider: 'aws',
        context: { cluster: 'api', credentials: 'test', moniker: { app: 'api' }, regions: ['us-west-1'] },
        failPipeline: true,
        type: 'clusterSize',
      },
    });
    const component = mount(<PreconditionSelector {...props} />);

    component.setState({
      accounts: [
        { name: 'prod', type: 'aws' },
        { name: 'test', type: 'aws' },
        { name: 'cf-prod', type: 'cloudfoundry' },
      ],
    });
    component.find('select[name="credentials"]').simulate('change', { target: { value: 'prod' } });

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
    const component = mount(<PreconditionSelector {...props} />);

    component.find('input[name="regions"][value="us-east-1"]').simulate('change', {
      target: { checked: true, value: 'us-east-1' },
    });

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
    const component = mount(<PreconditionSelector {...props} />);

    expect(component.find('select[name="cluster"] option').map((option) => option.prop('value'))).toEqual(['', 'api']);
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
    const component = mount(<PreconditionSelector {...props} />);

    component.find('select[name="cluster"]').simulate('change', { target: { value: 'api' } });

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

  it('updates cluster size expected-size fields', () => {
    const props = createProps({
      precondition: {
        cloudProvider: 'aws',
        context: { comparison: '==', expected: 2 },
        failPipeline: true,
        type: 'clusterSize',
      },
    });
    const component = mount(<PreconditionSelector {...props} />);

    component.find('select[name="comparison"]').simulate('change', { target: { value: '>=' } });

    expect(props.onChange).toHaveBeenCalledWith({
      cloudProvider: 'aws',
      context: { comparison: '>=', expected: 2 },
      failPipeline: true,
      type: 'clusterSize',
    });

    props.onChange.calls.reset();
    component.find('input[name="expected"]').simulate('change', { target: { value: '4' } });

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
    const component = mount(<PreconditionSelector {...props} />);

    expect(component.find('select[name="credentials"]').length).toBe(0);
    expect(component.find('input[name="regions"]').length).toBe(0);
    expect(component.find('select[name="cluster"]').length).toBe(0);
    expect(component.find('select[name="comparison"]').length).toBe(1);
    expect(component.find('input[name="expected"]').length).toBe(1);
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
    const component = mount(<PreconditionSelector {...props} />);

    component.find('input[name="failPipeline"]').simulate('change', { target: { checked: false } });

    expect(props.onChange).toHaveBeenCalledWith({
      cloudProvider: 'aws',
      context: { comparison: '==', expected: 2 },
      failPipeline: false,
      type: 'clusterSize',
    });
  });
});
