import type { IExecutionStage } from '../../../../domain';
import { getCreatedLoadBalancers, hasLoadBalancerSubnetDeployments } from './CreateLoadBalancerExecutionDetails';

describe('CreateLoadBalancerExecutionDetails helpers', () => {
  const createStage = (context: any = {}): IExecutionStage => ({
    id: 'stage-id',
    name: 'Create Load Balancers',
    type: 'upsertLoadBalancers',
    refId: '1',
    requisiteStageRefIds: [],
    status: 'SUCCEEDED',
    startTime: 0,
    endTime: 0,
    tasks: [],
    context,
  });

  it('returns an empty list when kato task results are missing', () => {
    expect(getCreatedLoadBalancers(createStage())).toEqual([]);
    expect(getCreatedLoadBalancers(createStage({ 'kato.tasks': [] }))).toEqual([]);
    expect(getCreatedLoadBalancers(createStage({ 'kato.tasks': [{ resultObjects: [] }] }))).toEqual([]);
  });

  it('extracts created load balancer DNS results from kato task output', () => {
    const created = getCreatedLoadBalancers(
      createStage({
        application: 'fnord',
        account: 'test',
        providerType: 'aws',
        'kato.tasks': [
          {
            resultObjects: [
              {
                loadBalancers: {
                  'eu-west-1': { name: 'fnord-main', dnsName: 'fnord-main.example.com' },
                  'eu-central-1': { name: 'fnord-central', dnsName: 'fnord-central.example.com' },
                },
              },
            ],
          },
        ],
      }),
    );

    expect(created).toEqual([
      {
        type: 'loadBalancers',
        application: 'fnord',
        name: 'fnord-main',
        region: 'eu-west-1',
        account: 'test',
        dnsName: 'fnord-main.example.com',
        provider: 'aws',
      },
      {
        type: 'loadBalancers',
        application: 'fnord',
        name: 'fnord-central',
        region: 'eu-central-1',
        account: 'test',
        dnsName: 'fnord-central.example.com',
        provider: 'aws',
      },
    ]);
  });

  it('defaults created load balancer provider to aws', () => {
    const created = getCreatedLoadBalancers(
      createStage({
        application: 'fnord',
        account: 'test',
        'kato.tasks': [{ resultObjects: [{ loadBalancers: { 'eu-west-1': { name: 'main' } } }] }],
      }),
    );

    expect(created[0].provider).toBe('aws');
  });

  it('shows the subnet column only when configured load balancers include subnet types', () => {
    expect(hasLoadBalancerSubnetDeployments(createStage())).toBe(false);
    expect(hasLoadBalancerSubnetDeployments(createStage({ loadBalancers: [{ name: 'main' }] }))).toBe(false);
    expect(
      hasLoadBalancerSubnetDeployments(createStage({ loadBalancers: [{ name: 'main', subnetType: 'internal' }] })),
    ).toBe(true);
  });
});
