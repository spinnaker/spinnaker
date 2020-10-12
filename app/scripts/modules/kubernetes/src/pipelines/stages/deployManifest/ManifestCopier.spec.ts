import { IQService, IScope, mock } from 'angular';

import { ApplicationModelBuilder, Application, noop } from '@spinnaker/core';
import { ManifestCopier } from './ManifestCopier';

describe('<ManifestCopier />', () => {
  let application: Application;

  beforeEach(
    mock.inject(($q: IQService, $rootScope: IScope) => {
      const $scope = $rootScope.$new();
      // The application model implicitly depends on a bunch of Angular things, which is why
      // we need the Angular mock environment (even though we're testing a React component).
      application = ApplicationModelBuilder.createApplicationForTests(
        'app',
        {
          key: 'serverGroups',
          loader: () =>
            $q.resolve([
              // Replica sets in same cluster, no manager.
              {
                name: 'replicaSet my-replicaSet-v002',
                region: 'default',
                category: 'serverGroup',
                account: 'my-k8s-account',
                cloudProvider: 'kubernetes',
                cluster: 'replicaSet my-replicaSet',
              },
              {
                name: 'replicaSet my-replicaSet-v001',
                region: 'default',
                category: 'serverGroup',
                account: 'my-k8s-account',
                cloudProvider: 'kubernetes',
                cluster: 'replicaSet my-replicaSet',
              },
              // Replica set managed by deployment.
              {
                name: 'replicaSet my-managed-replicaSet-v001',
                region: 'default',
                category: 'serverGroup',
                account: 'my-k8s-account',
                cloudProvider: 'kubernetes',
                cluster: 'deployment my-deployment',
                serverGroupManagers: [{ name: 'deployment my-deployment' }],
              },
            ]),
          onLoad: (_app: Application, data: any) => $q.resolve(data),
          defaultData: [],
        },
        {
          key: 'serverGroupManagers',
          loader: () =>
            $q.resolve([
              {
                name: 'deployment my-deployment',
                region: 'default',
                account: 'my-k8s-account',
                cloudProvider: 'kubernetes',
              },
            ]),
          onLoad: (_app: Application, data: any) => $q.resolve(data),
          defaultData: [],
        },
        {
          key: 'securityGroups',
          loader: () => $q.resolve([]),
          onLoad: (_app: Application, data: any) => $q.resolve(data),
          defaultData: [],
        },
        {
          key: 'loadBalancers',
          loader: () => $q.resolve([]),
          onLoad: (_app: Application, data: any) => $q.resolve(data),
          defaultData: [],
        },
      );
      application.refresh();
      $scope.$digest();
    }),
  );

  describe('dropdown grouping & ordering', () => {
    it('sorts deployments to the top of the list', () => {
      const state = ManifestCopier.getState(buildProps(application));
      expect(state.manifests.map((manifest) => manifest.name)[0]).toEqual('my-deployment');
    });

    it('only includes the most recent versioned resource', () => {
      const state = ManifestCopier.getState(buildProps(application));
      expect(state.manifests.map((manifest) => manifest.name)).toContain('my-replicaSet-v002');
      expect(state.manifests.map((manifest) => manifest.name)).not.toContain('my-replicaSet-v001');
    });

    it('does not include managed server groups', () => {
      const state = ManifestCopier.getState(buildProps(application));
      expect(state.manifests.map((manifest) => manifest.name)).not.toContain('my-managed-replicaSet-v001');
    });
  });
});

const buildProps = (application: Application) => ({
  application,
  cloudProvider: 'kubernetes',
  onDismiss: noop,
  onManifestSelected: noop,
  show: true,
});
