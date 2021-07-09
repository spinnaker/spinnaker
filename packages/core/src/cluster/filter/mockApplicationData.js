import { module } from 'angular';

export const CORE_CLUSTER_FILTER_MOCKAPPLICATIONDATA = 'cluster.test.data';
export const name = CORE_CLUSTER_FILTER_MOCKAPPLICATIONDATA; // for backwards compatibility
module(CORE_CLUSTER_FILTER_MOCKAPPLICATIONDATA, [])
  .value('applicationJSON', {
    clusters: [
      { name: 'in-eu-east-2-only', account: 'prod', region: 'eu-east-2', category: 'serverGroup' },
      { name: 'in-us-west-1-only', account: 'test', region: 'us-west-1', category: 'serverGroup' },
    ],
    serverGroups: {
      data: [
        {
          cluster: 'in-eu-east-2-only',
          account: 'prod',
          region: 'eu-east-2',
          instances: [],
          name: 'in-eu-east-2-only',
          instanceCounts: { total: 0, up: 0, down: 0, unknown: 0, starting: 0, outOfService: 0 },
          isDisabled: true,
          type: 'gce',
          instanceType: 'm3.medium',
          vpcName: '',
          category: 'serverGroup',
          labels: {
            app: 'spinnaker',
            source: 'prod',
          },
        },
        {
          cluster: 'in-us-west-1-only',
          account: 'test',
          region: 'us-west-1',
          instances: [{}],
          name: 'in-us-west-1-only',
          instanceCounts: { total: 1, up: 0, down: 1, unknown: 0, starting: 0, outOfService: 0 },
          isDisabled: false,
          type: 'aws',
          instanceType: 'm3.large',
          vpcName: 'Main',
          category: 'serverGroup',
          labels: {
            app: 'spinnaker',
          },
        },
      ],
    },
  })
  .constant('groupedJSON', [
    {
      heading: 'prod',
      key: 'prod',
      subgroups: [
        {
          heading: 'in-eu-east-2-only',
          category: 'serverGroup',
          key: 'in-eu-east-2-only:serverGroup',
          hasDiscovery: false,
          hasLoadBalancers: false,
          entityTags: undefined,
          isManaged: false,
          managedResourceSummary: undefined,
          subgroups: [
            {
              heading: 'eu-east-2',
              category: 'serverGroup',
              key: 'eu-east-2:serverGroup',
              entityTags: undefined,
              isManaged: false,
              managedResourceSummary: undefined,
              serverGroups: [
                {
                  cluster: 'in-eu-east-2-only',
                  category: 'serverGroup',
                  account: 'prod',
                  region: 'eu-east-2',
                  instances: [],
                  name: 'in-eu-east-2-only',
                  instanceCounts: {
                    total: 0,
                    up: 0,
                    down: 0,
                    unknown: 0,
                    starting: 0,
                    outOfService: 0,
                  },
                  isDisabled: true,
                  labels: {
                    app: 'spinnaker',
                    source: 'prod',
                  },
                  type: 'gce',
                  instanceType: 'm3.medium',
                  vpcName: '',
                },
              ],
            },
          ],
        },
      ],
    },
    {
      heading: 'test',
      key: 'test',
      subgroups: [
        {
          heading: 'in-us-west-1-only',
          category: 'serverGroup',
          key: 'in-us-west-1-only:serverGroup',
          hasDiscovery: false,
          hasLoadBalancers: false,
          entityTags: undefined,
          isManaged: false,
          managedResourceSummary: undefined,
          subgroups: [
            {
              heading: 'us-west-1',
              category: 'serverGroup',
              key: 'us-west-1:serverGroup',
              entityTags: undefined,
              isManaged: false,
              managedResourceSummary: undefined,
              serverGroups: [
                {
                  cluster: 'in-us-west-1-only',
                  category: 'serverGroup',
                  account: 'test',
                  region: 'us-west-1',
                  instances: [{}],
                  name: 'in-us-west-1-only',
                  instanceCounts: {
                    total: 1,
                    up: 0,
                    down: 1,
                    unknown: 0,
                    starting: 0,
                    outOfService: 0,
                  },
                  isDisabled: false,
                  labels: {
                    app: 'spinnaker',
                  },
                  type: 'aws',
                  instanceType: 'm3.large',
                  vpcName: 'Main',
                },
              ],
            },
          ],
        },
      ],
    },
  ]);
