import { cloneDeep, filter } from 'lodash';

import type { Application } from '../../application/application.model';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import { ClusterService } from '../cluster.service';
import { ProviderServiceDelegate } from '../../cloudProvider';
import { getDirectRouter, setDirectRouter } from '../../navigation/directRouter';
import * as State from '../../state';
import { nativePromiseService } from '../../utils/nativePromiseService';
import { applicationJSONFixture, groupedJSONFixture } from './ClusterFilterService.fixture';

const testContext: any = {};

const ClusterState = State.ClusterState;

// Most of this logic has been moved to filter.model.service.js, so these act more as integration tests now
describe('Service: clusterFilterService', function () {
  const debounceTimeout = 30;

  let clusterService: ClusterService;
  let applicationJSON: any;
  let groupedJSON: any;
  let application: Application;
  let previousRouter: ReturnType<typeof getDirectRouter>;

  beforeEach(function () {
    previousRouter = getDirectRouter();
    clusterService = new ClusterService(
      nativePromiseService,
      { normalizeServerGroup: (serverGroup: any) => Promise.resolve(serverGroup) },
      new ProviderServiceDelegate(nativePromiseService),
    );

    applicationJSON = cloneDeep(applicationJSONFixture);
    groupedJSON = cloneDeep(groupedJSONFixture);
    groupedJSON[0].subgroups[0].cluster = applicationJSON.clusters[0];
    groupedJSON[1].subgroups[0].cluster = applicationJSON.clusters[1];

    testContext.buildApplication = (json: any) => {
      const app = ApplicationModelBuilder.createApplicationForTests('app', {
        key: 'serverGroups',
        lazy: true,
        defaultData: [],
      });
      if (json.serverGroups) {
        app.getDataSource('serverGroups').data = cloneDeep(json.serverGroups.data);
      }
      if (json.clusters) {
        app.clusters = json.clusters;
      }
      return app;
    };

    testContext.verifyTags = function (expectedTags: any[]) {
      const actual: any = ClusterState.filterModel.asFilterModel.tags;
      expect(actual.length).toBe(expectedTags.length);
      expectedTags.forEach(function (expected: any) {
        expect(
          actual.some(function (test: any) {
            return test.key === expected.key && test.label === expected.label && test.value === expected.value;
          }),
        ).toBe(true);
      });
    };

    application = testContext.buildApplication(applicationJSON);
    State.initialize();
    // Materialize lodash-decorators' lazy @Debounce getter before vi.spyOn inspects it.
    void ClusterState.filterService.updateClusterGroups;
  });

  afterEach(() => setDirectRouter(previousRouter));

  describe('Updating the cluster group', function () {
    it('no filter: should be transformed', () =>
      new Promise((done, reject) => {
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(groupedJSON);
          done();
        }, debounceTimeout);
      }));

    describe('filter by cluster', function () {
      it('should filter by cluster name as an exact match', () =>
        new Promise((done, reject) => {
          ClusterState.filterModel.asFilterModel.sortFilter.filter = 'cluster:in-us-west-1-only';
          const expected: any = filter(groupedJSON, { subgroups: [{ heading: 'in-us-west-1-only' }] });
          ClusterState.filterService.updateClusterGroups(application);
          setTimeout(() => {
            expect(ClusterState.filterModel.asFilterModel.groups).toEqual(expected);
            done();
          }, debounceTimeout);
        }));

      it('should not match on partial cluster name', () =>
        new Promise((done, reject) => {
          ClusterState.filterModel.asFilterModel.sortFilter.filter = 'cluster:in-us-west-1';
          ClusterState.filterService.updateClusterGroups(application);
          setTimeout(() => {
            expect(ClusterState.filterModel.asFilterModel.groups).toEqual([]);
            done();
          }, debounceTimeout);
        }));
    });

    describe('filter by vpc', function () {
      it('should filter by vpc name as an exact match', () =>
        new Promise((done, reject) => {
          ClusterState.filterModel.asFilterModel.sortFilter.filter = 'vpc:main';
          const expected: any = filter(groupedJSON, { subgroups: [{ heading: 'in-us-west-1-only' }] });
          ClusterState.filterService.updateClusterGroups(application);
          setTimeout(() => {
            expect(ClusterState.filterModel.asFilterModel.groups).toEqual(expected);
            done();
          }, debounceTimeout);
        }));

      it('should not match on partial vpc name', () =>
        new Promise((done, reject) => {
          ClusterState.filterModel.asFilterModel.sortFilter.filter = 'vpc:main-old';
          ClusterState.filterService.updateClusterGroups(application);
          setTimeout(() => {
            expect(ClusterState.filterModel.asFilterModel.groups).toEqual([]);
            done();
          }, debounceTimeout);
        }));
    });

    describe('filter by clusters', function () {
      it('should filter by cluster names as an exact match', () =>
        new Promise((done, reject) => {
          ClusterState.filterModel.asFilterModel.sortFilter.filter = 'clusters:in-us-west-1-only';
          const expected: any = filter(groupedJSON, { subgroups: [{ heading: 'in-us-west-1-only' }] });
          ClusterState.filterService.updateClusterGroups(application);
          setTimeout(() => {
            expect(ClusterState.filterModel.asFilterModel.groups).toEqual(expected);
            done();
          }, debounceTimeout);
        }));

      it('should not match on partial cluster name', () =>
        new Promise((done, reject) => {
          ClusterState.filterModel.asFilterModel.sortFilter.filter = 'clusters:in-us-west-1';
          ClusterState.filterService.updateClusterGroups(application);
          setTimeout(() => {
            expect(ClusterState.filterModel.asFilterModel.groups).toEqual([]);
            done();
          }, debounceTimeout);
        }));

      it('should perform an OR match on comma separated list, ignoring spaces', () =>
        new Promise((done, reject) => {
          ClusterState.filterModel.asFilterModel.sortFilter.filter = 'clusters:in-us-west-1-only, in-eu-east-2-only';
          ClusterState.filterService.updateClusterGroups(application);
          setTimeout(() => {
            expect(ClusterState.filterModel.asFilterModel.groups).toEqual(groupedJSON);
            done();
          }, debounceTimeout);
        }));
    });

    describe('filtering by account type', function () {
      it('1 account filter: should be transformed showing only prod accounts', () =>
        new Promise((done, reject) => {
          ClusterState.filterModel.asFilterModel.sortFilter.account = { prod: true };
          const expectedProd: any = filter(groupedJSON, { heading: 'prod' });
          ClusterState.filterService.updateClusterGroups(application);
          setTimeout(() => {
            expect(ClusterState.filterModel.asFilterModel.groups).toEqual(expectedProd);
            testContext.verifyTags([{ key: 'account', label: 'account', value: 'prod' }]);
            done();
          }, debounceTimeout);
        }));

      it('All account filters: should show all accounts', () =>
        new Promise((done, reject) => {
          ClusterState.filterModel.asFilterModel.sortFilter.account = { prod: true, test: true };
          ClusterState.filterService.updateClusterGroups(application);
          setTimeout(() => {
            expect(ClusterState.filterModel.asFilterModel.groups).toEqual(groupedJSON);
            testContext.verifyTags([
              { key: 'account', label: 'account', value: 'prod' },
              { key: 'account', label: 'account', value: 'test' },
            ]);
            done();
          }, debounceTimeout);
        }));
    });
  });

  describe('filter by region', function () {
    it('1 region: should filter by that region ', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.region = { 'us-west-1': true };
        const expected: any = filter(groupedJSON, { subgroups: [{ heading: 'in-us-west-1-only' }] });
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(expected);
          testContext.verifyTags([{ key: 'region', label: 'region', value: 'us-west-1' }]);
          done();
        }, debounceTimeout);
      }));
  });

  describe('filter by healthy status', function () {
    it('should filter by health if checked', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.status = { healthy: true };
        const expected: any = filter(groupedJSON, {
          subgroups: [
            {
              subgroups: [
                {
                  serverGroups: [
                    {
                      instances: [{ health: [{ state: 'Up' }] }],
                    },
                  ],
                },
              ],
            },
          ],
        });
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(expected);
          testContext.verifyTags([{ key: 'status', label: 'status', value: 'healthy' }]);
          done();
        }, debounceTimeout);
      }));

    it('should not filter by healthy if unchecked', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.status = { healthy: false };
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(groupedJSON);
          testContext.verifyTags([]);
          done();
        }, debounceTimeout);
      }));
  });

  describe('filter by unhealthy status', function () {
    it('should filter by unhealthy status if checked', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.status = { unhealthy: true };
        const expected: any = filter(groupedJSON, {
          subgroups: [
            {
              subgroups: [
                {
                  serverGroups: [
                    {
                      instances: [{ health: [{ state: 'Down' }] }],
                    },
                  ],
                },
              ],
            },
          ],
        });

        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(expected);
          testContext.verifyTags([{ key: 'status', label: 'status', value: 'unhealthy' }]);
          done();
        }, debounceTimeout);
      }));

    it('should not filter by unhealthy if unchecked', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.status = { unhealthy: false };
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(groupedJSON);
          testContext.verifyTags([]);
          done();
        }, debounceTimeout);
      }));
  });

  describe('filter by both healthy and unhealthy status', function () {
    it('should not filter by healthy if unchecked', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.status = { unhealthy: true, healthy: true };
        const expected: any = filter(groupedJSON, {
          subgroups: [
            {
              subgroups: [
                {
                  serverGroups: [
                    {
                      instances: [{ health: [{ state: 'Down' }] }],
                    },
                  ],
                },
              ],
            },
          ],
        });
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(expected);
          testContext.verifyTags([
            { key: 'status', label: 'status', value: 'healthy' },
            { key: 'status', label: 'status', value: 'unhealthy' },
          ]);
          done();
        }, debounceTimeout);
      }));
  });

  describe('filter by disabled status', function () {
    it('should filter by disabled status if checked', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.status = { Disabled: true };
        const expected: any = filter(groupedJSON, {
          subgroups: [
            {
              subgroups: [
                {
                  serverGroups: [
                    {
                      isDisabled: true,
                    },
                  ],
                },
              ],
            },
          ],
        });
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(expected);
          testContext.verifyTags([{ key: 'status', label: 'status', value: 'Disabled' }]);
          done();
        }, debounceTimeout);
      }));

    it('should not filter if the status is unchecked', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.status = { Disabled: false };
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(groupedJSON);
          testContext.verifyTags([]);
          done();
        }, debounceTimeout);
      }));
  });

  describe('filter by starting status', function () {
    it('should filter by starting status if checked', () =>
      new Promise((done, reject) => {
        const starting: any = { healthState: 'Unknown' };
        const serverGroup = application.getDataSource('serverGroups').data[0];
        serverGroup.instances.push(starting);

        ClusterState.filterModel.asFilterModel.sortFilter.status = { Starting: true };
        ClusterState.filterService.updateClusterGroups(application);

        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual([]);

          starting.healthState = 'Starting';
          serverGroup.instanceCounts.starting = 1;
          ClusterState.filterService.updateClusterGroups(application);
          setTimeout(() => {
            expect(ClusterState.filterModel.asFilterModel.groups.length).toBe(1);
            testContext.verifyTags([{ key: 'status', label: 'status', value: 'Starting' }]);
            done();
          }, debounceTimeout);
        }, debounceTimeout);
      }));
  });

  describe('filter by out of service status', function () {
    it('should filter out by out of service status if checked', () =>
      new Promise((done, reject) => {
        const starting: any = { healthState: 'Unknown' };
        const serverGroup = application.getDataSource('serverGroups').data[0];
        serverGroup.instances.push(starting);

        ClusterState.filterModel.asFilterModel.sortFilter.status = { OutOfService: true };
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual([]);
          done();
        }, debounceTimeout);
      }));
  });

  describe('filter by unknown status', function () {
    it('should filter out by unknown status if checked', () =>
      new Promise((done, reject) => {
        const starting: any = { healthState: 'Up' };
        const serverGroup = application.getDataSource('serverGroups').data[0];
        serverGroup.instances.push(starting);

        ClusterState.filterModel.asFilterModel.sortFilter.status = { Unknown: true };
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual([]);
          done();
        }, debounceTimeout);
      }));
  });

  describe('filtered by provider type', function () {
    it('should filter by aws if checked', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.providerType = { aws: true };
        const expected: any = filter(groupedJSON, {
          subgroups: [
            {
              subgroups: [
                {
                  serverGroups: [
                    {
                      type: 'aws',
                    },
                  ],
                },
              ],
            },
          ],
        });
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(expected);
          testContext.verifyTags([{ key: 'providerType', label: 'provider', value: 'aws' }]);
          done();
        }, debounceTimeout);
      }));

    it('should not filter if no provider type is selected', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.providerType = undefined;
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(groupedJSON);
          testContext.verifyTags([]);
          done();
        }, debounceTimeout);
      }));

    it('should not filter if all provider are selected', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.providerType = { aws: true, gce: true };
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(groupedJSON);
          testContext.verifyTags([
            { key: 'providerType', label: 'provider', value: 'aws' },
            { key: 'providerType', label: 'provider', value: 'gce' },
          ]);
          done();
        }, debounceTimeout);
      }));
  });

  describe('filtered by instance type', function () {
    it('should filter by m3.large if checked', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.instanceType = { 'm3.large': true };
        const expected: any = filter(groupedJSON, {
          subgroups: [
            {
              subgroups: [
                {
                  serverGroups: [
                    {
                      instanceType: 'm3.large',
                    },
                  ],
                },
              ],
            },
          ],
        });
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(expected);
          testContext.verifyTags([{ key: 'instanceType', label: 'instance type', value: 'm3.large' }]);
          done();
        }, debounceTimeout);
      }));

    it('should not filter if no instance type selected', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.instanceType = undefined;
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(groupedJSON);
          testContext.verifyTags([]);
          done();
        }, debounceTimeout);
      }));

    it('should not filter if the instance type is unchecked', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.instanceType = { 'm3.large': false };
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(groupedJSON);
          testContext.verifyTags([]);
          done();
        }, debounceTimeout);
      }));
  });

  describe('filter by instance counts', function () {
    it('should filter by min instances', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.minInstances = 1;
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual([groupedJSON[1]]);
          testContext.verifyTags([{ key: 'minInstances', label: 'instance count (min)', value: 1 }]);
          done();
        }, debounceTimeout);
      }));

    it('should filter by max instances', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.maxInstances = 0;
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual([groupedJSON[0]]);
          testContext.verifyTags([{ key: 'maxInstances', label: 'instance count (max)', value: 0 }]);
          done();
        }, debounceTimeout);
      }));
  });

  describe('filter by label (with search string)', function () {
    it('should filter by label key and value as exact, case-sensitive matches', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.filter = 'labels:source=prod';
        const expected: any = filter(groupedJSON, {
          subgroups: [
            {
              subgroups: [
                {
                  serverGroups: [
                    {
                      labels: {
                        source: 'prod',
                      },
                    },
                  ],
                },
              ],
            },
          ],
        });
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(expected);
          done();
        }, debounceTimeout);
      }));

    it('should not match on partial value', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.filter = 'labels:source=pro';
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual([]);
          done();
        }, debounceTimeout);
      }));

    it('should not match on case-insensitive value', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.filter = 'labels:source=Prod';
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual([]);
          done();
        }, debounceTimeout);
      }));

    it('should perform an AND match on comma separated list, ignoring spaces', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.filter = 'labels: source=prod, app=spinnaker';
        const expected: any = filter(groupedJSON, {
          subgroups: [
            {
              subgroups: [
                {
                  serverGroups: [
                    {
                      labels: {
                        app: 'spinnaker',
                        source: 'prod',
                      },
                    },
                  ],
                },
              ],
            },
          ],
        });
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(expected);
          done();
        }, debounceTimeout);
      }));
  });

  describe('filter by label (with filters)', function () {
    it('should filter by label key and value as exact, case sensitive matches', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.labels = { 'source:prod': true };
        const expected: any = filter(groupedJSON, {
          subgroups: [
            {
              subgroups: [
                {
                  serverGroups: [
                    {
                      labels: {
                        source: 'prod',
                      },
                    },
                  ],
                },
              ],
            },
          ],
        });
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(expected);
          done();
        }, debounceTimeout);
      }));

    it('should not match on partial value', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.labels = { 'source:pr': true };
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual([]);
          done();
        }, debounceTimeout);
      }));

    it('should not match on case-insensitive value', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.labels = { 'source:Prod': true };
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual([]);
          done();
        }, debounceTimeout);
      }));

    it('should perform an AND match on multiple key-value pairs', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.labels = { 'source:prod': true, 'app:spinnaker': true };
        const expected: any = filter(groupedJSON, {
          subgroups: [
            {
              subgroups: [
                {
                  serverGroups: [
                    {
                      labels: {
                        app: 'spinnaker',
                        source: 'prod',
                      },
                    },
                  ],
                },
              ],
            },
          ],
        });
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups).toEqual(expected);
          done();
        }, debounceTimeout);
      }));
  });

  describe('multiInstance filtering', function () {
    beforeEach(function () {
      testContext.navigationSynced = false;
      ClusterState.filterModel.asFilterModel.sortFilter.multiselect = true;
      vi.spyOn(ClusterState.multiselectModel, 'syncNavigation').mockImplementation(
        () => (testContext.navigationSynced = true),
      );
    });

    it('should remove all instanceIds if server group is no longer visible, and add back when visible again', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.listInstances = true;
        const serverGroup = application.getDataSource('serverGroups').data[0];
        const multiselectGroup = ClusterState.multiselectModel.getOrCreateInstanceGroup(serverGroup);

        serverGroup.instances.push({ id: 'i-1234' });
        ClusterState.multiselectModel.toggleSelectAll(serverGroup, ['i-1234']);
        expect(multiselectGroup.instanceIds).toEqual(['i-1234']);

        ClusterState.filterModel.asFilterModel.sortFilter.region['us-east-3'] = true;
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(multiselectGroup.instanceIds).toEqual([]);

          ClusterState.filterModel.asFilterModel.sortFilter.region['us-east-3'] = false;
          ClusterState.filterService.updateClusterGroups(application);

          setTimeout(() => {
            expect(multiselectGroup.instanceIds).toEqual(['i-1234']);
            done();
          }, debounceTimeout);
        }, debounceTimeout);
      }));

    it('should remove instances that are no longer visible', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.listInstances = true;
        const serverGroup = application.getDataSource('serverGroups').data[0];

        ClusterState.multiselectModel.toggleInstance(serverGroup, 'i-1234');
        ClusterState.multiselectModel.toggleInstance(serverGroup, 'i-2345');
        serverGroup.instances.push({ id: 'i-1234' });

        expect(ClusterState.multiselectModel.instanceIsSelected(serverGroup, 'i-1234')).toBe(true);
        expect(ClusterState.multiselectModel.instanceIsSelected(serverGroup, 'i-2345')).toBe(true);

        ClusterState.filterService.updateClusterGroups(application);

        setTimeout(() => {
          expect(ClusterState.multiselectModel.instanceIsSelected(serverGroup, 'i-1234')).toBe(true);
          expect(ClusterState.multiselectModel.instanceIsSelected(serverGroup, 'i-2345')).toBe(false);

          expect(testContext.navigationSynced).toBe(true);
          done();
        }, debounceTimeout);
      }));

    it('should add all instances when selectAll is selected and new instances appear in server group', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.listInstances = true;
        const serverGroup = application.getDataSource('serverGroups').data[0];

        ClusterState.multiselectModel.getOrCreateInstanceGroup(serverGroup).selectAll = true;
        ClusterState.multiselectModel.toggleInstance(serverGroup, 'i-1234');
        serverGroup.instances.push({ id: 'i-1234' });
        serverGroup.instances.push({ id: 'i-2345' });

        ClusterState.filterService.updateClusterGroups(application);

        setTimeout(() => {
          expect(ClusterState.multiselectModel.instanceIsSelected(serverGroup, 'i-1234')).toBe(true);
          expect(ClusterState.multiselectModel.instanceIsSelected(serverGroup, 'i-2345')).toBe(true);

          expect(testContext.navigationSynced).toBe(true);
          done();
        }, debounceTimeout);
      }));

    it('should remove all instance groups when listInstances is false', () =>
      new Promise((done, reject) => {
        ClusterState.filterModel.asFilterModel.sortFilter.listInstances = false;
        const serverGroup = application.getDataSource('serverGroups').data[0];

        ClusterState.multiselectModel.toggleInstance(serverGroup, 'i-1234');

        expect(ClusterState.multiselectModel.instanceGroups.length).toBe(1);
        ClusterState.filterService.updateClusterGroups(application);

        setTimeout(() => {
          expect(ClusterState.multiselectModel.instanceGroups.length).toBe(0);
          expect(testContext.navigationSynced).toBe(true);
          done();
        }, debounceTimeout);
      }));
  });

  describe('clear all filters', function () {
    it('should clear set providerType filter', function () {
      setDirectRouter(null);
      ClusterState.filterModel.asFilterModel.sortFilter.providerType = { aws: true };
      expect(ClusterState.filterModel.asFilterModel.sortFilter.providerType).toBeDefined();
      ClusterState.filterService.clearFilters();
      expect(ClusterState.filterModel.asFilterModel.sortFilter.providerType).toBeUndefined();
      testContext.verifyTags([]);
    });
  });

  describe('overriding filters from a URL', function () {
    it('does not update cluster groups before the direct router is available', function () {
      setDirectRouter(null);
      const updateClusterGroups = vi
        .spyOn(ClusterState.filterService, 'updateClusterGroups')
        .mockReturnValue(undefined);

      ClusterState.filterService.overrideFiltersForUrl({ href: '/clusters', application: 'app' });

      expect(updateClusterGroups).not.toHaveBeenCalled();
    });

    it('updates cluster groups when the URL targets the direct router application', function () {
      setDirectRouter({ stateService: { params: { application: 'app' } } } as any);
      const updateClusterGroups = vi
        .spyOn(ClusterState.filterService, 'updateClusterGroups')
        .mockReturnValue(undefined);

      ClusterState.filterService.overrideFiltersForUrl({ href: '/clusters', application: 'app' });

      expect(updateClusterGroups).toHaveBeenCalled();
    });
  });

  describe('group diffing', function () {
    beforeEach(function () {
      testContext.clusterA = { account: 'prod', category: 'serverGroup', name: 'cluster-a' };
      testContext.clusterB = { account: 'prod', category: 'serverGroup', name: 'cluster-b' };
      testContext.serverGroup001 = {
        cluster: 'cluster-a',
        name: 'cluster-a-v001',
        account: 'prod',
        region: 'us-east-1',
        stringVal: 'original',
        category: 'serverGroup',
        instances: [],
      };
      testContext.serverGroup000 = {
        cluster: 'cluster-a',
        name: 'cluster-a-v000',
        account: 'prod',
        region: 'us-east-1',
        stringVal: 'should be deleted',
        category: 'serverGroup',
        instances: [],
      };
      ClusterState.filterModel.asFilterModel.groups = [
        {
          heading: 'prod',
          key: 'prod',
          subgroups: [
            {
              heading: 'cluster-a',
              key: 'cluster-a:serverGroup',
              category: 'serverGroup',
              cluster: { name: 'cluster-a' },
              subgroups: [
                {
                  heading: 'us-east-1',
                  category: 'serverGroup',
                  key: 'us-east-1:serverGroup',
                  serverGroups: [testContext.serverGroup000, testContext.serverGroup001],
                },
              ],
            },
          ],
        },
      ];
    });

    it('adds a group when new one provided', () =>
      new Promise((done, reject) => {
        application = testContext.buildApplication({
          serverGroups: {
            data: [
              testContext.serverGroup000,
              testContext.serverGroup001,
              {
                cluster: 'cluster-a',
                name: 'cluster-a-v003',
                account: 'test',
                region: 'us-east-1',
                stringVal: 'new',
                category: 'serverGroup',
              },
            ],
          },
          clusters: [testContext.clusterA, { name: 'cluster-a', account: 'test', category: 'serverGroup' }],
        });
        ClusterState.filterService.updateClusterGroups(application);

        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups.length).toBe(2);
          expect(ClusterState.filterModel.asFilterModel.groups[1].heading).toBe('test');
          expect(ClusterState.filterModel.asFilterModel.groups[1].subgroups.length).toBe(1);
          expect(ClusterState.filterModel.asFilterModel.groups[1].subgroups[0].heading).toBe('cluster-a');
          expect(ClusterState.filterModel.asFilterModel.groups[1].subgroups[0].subgroups.length).toBe(1);
          expect(ClusterState.filterModel.asFilterModel.groups[1].subgroups[0].subgroups[0].heading).toBe('us-east-1');
          expect(ClusterState.filterModel.asFilterModel.groups[1].subgroups[0].subgroups[0].serverGroups.length).toBe(
            1,
          );
          expect(ClusterState.filterModel.asFilterModel.groups[1].subgroups[0].subgroups[0].serverGroups[0].name).toBe(
            'cluster-a-v003',
          );
          done();
        }, debounceTimeout);
      }));

    it('adds a subgroup when new one provided', () =>
      new Promise((done, reject) => {
        application = testContext.buildApplication({
          serverGroups: {
            data: [
              testContext.serverGroup000,
              testContext.serverGroup001,
              {
                cluster: 'cluster-b',
                name: 'cluster-a-v003',
                account: 'prod',
                region: 'us-east-1',
                stringVal: 'new',
                category: 'serverGroup',
              },
            ],
          },
          clusters: [testContext.clusterA, testContext.clusterB],
        });
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups.length).toBe(1);
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups.length).toBe(2);
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[1].heading).toBe('cluster-b');
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[1].subgroups.length).toBe(1);
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[1].subgroups[0].heading).toBe('us-east-1');
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[1].subgroups[0].serverGroups.length).toBe(
            1,
          );
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[1].subgroups[0].serverGroups[0].name).toBe(
            'cluster-a-v003',
          );
          done();
        }, debounceTimeout);
      }));

    it('adds a sub-subgroup when new one provided', () =>
      new Promise((done, reject) => {
        application = testContext.buildApplication({
          serverGroups: {
            data: [
              testContext.serverGroup000,
              testContext.serverGroup001,
              {
                cluster: 'cluster-a',
                name: 'cluster-a-v003',
                account: 'prod',
                region: 'us-west-1',
                stringVal: 'new',
                category: 'serverGroup',
                instances: [],
              },
            ],
          },
        });
        application.clusters = clusterService.createServerGroupClusters(application.getDataSource('serverGroups').data);
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups.length).toBe(1);
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups.length).toBe(1);
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups.length).toBe(2);
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups[1].heading).toBe('us-west-1');
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups[1].serverGroups.length).toBe(
            1,
          );
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups[1].serverGroups[0].name).toBe(
            'cluster-a-v003',
          );
          done();
        }, debounceTimeout);
      }));

    it('adds a server group when new one provided in same sub-sub-group', () =>
      new Promise((done, reject) => {
        application = testContext.buildApplication({
          serverGroups: {
            data: [
              testContext.serverGroup000,
              testContext.serverGroup001,
              {
                cluster: 'cluster-a',
                name: 'cluster-a-v003',
                account: 'prod',
                region: 'us-east-1',
                stringVal: 'new',
                category: 'serverGroup',
                instances: [],
              },
            ],
          },
        });
        application.clusters = clusterService.createServerGroupClusters(application.getDataSource('serverGroups').data);
        ClusterState.filterService.updateClusterGroups(application);
        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups.length).toBe(1);
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups.length).toBe(1);
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups.length).toBe(1);
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups.length).toBe(
            3,
          );
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[2].name).toBe(
            'cluster-a-v003',
          );
          done();
        }, debounceTimeout);
      }));

    it('removes a group when one goes away', () =>
      new Promise((done, reject) => {
        application = testContext.buildApplication({
          serverGroups: {
            data: [
              testContext.serverGroup000,
              testContext.serverGroup001,
              {
                cluster: 'cluster-a',
                name: 'cluster-a-v003',
                account: 'test',
                region: 'us-east-1',
                stringVal: 'new',
                category: 'serverGroup',
                instances: [],
              },
            ],
          },
        });
        application.clusters = clusterService.createServerGroupClusters(application.getDataSource('serverGroups').data);
        ClusterState.filterService.updateClusterGroups(application);

        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups.length).toBe(2);
          application.getDataSource('serverGroups').data.splice(0, 2);
          ClusterState.filterService.updateClusterGroups(application);

          setTimeout(() => {
            expect(ClusterState.filterModel.asFilterModel.groups.length).toBe(1);
            expect(ClusterState.filterModel.asFilterModel.groups[0].heading).toBe('test');
            done();
          }, debounceTimeout);
        }, debounceTimeout);
      }));

    it('removes a subgroup when one goes away', () =>
      new Promise((done, reject) => {
        application = testContext.buildApplication({
          serverGroups: {
            data: [
              testContext.serverGroup000,
              testContext.serverGroup001,
              {
                cluster: 'cluster-b',
                name: 'cluster-a-v003',
                account: 'prod',
                region: 'us-east-1',
                stringVal: 'new',
                category: 'serverGroup',
                instances: [],
              },
            ],
          },
        });
        application.clusters = clusterService.createServerGroupClusters(application.getDataSource('serverGroups').data);
        ClusterState.filterService.updateClusterGroups(application);

        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups.length).toBe(1);
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups.length).toBe(2);

          application.getDataSource('serverGroups').data.splice(0, 2);
          ClusterState.filterService.updateClusterGroups(application);

          setTimeout(() => {
            expect(ClusterState.filterModel.asFilterModel.groups.length).toBe(1);
            expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups.length).toBe(1);
            expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].heading).toBe('cluster-b');
            done();
          }, debounceTimeout);
        }, debounceTimeout);
      }));

    it('removes a sub-subgroup when one goes away', () =>
      new Promise((done, reject) => {
        application = testContext.buildApplication({
          serverGroups: {
            data: [
              testContext.serverGroup000,
              testContext.serverGroup001,
              {
                cluster: 'cluster-a',
                name: 'cluster-a-v003',
                account: 'prod',
                region: 'us-west-1',
                stringVal: 'new',
                category: 'serverGroup',
                instances: [],
              },
            ],
          },
        });
        application.clusters = clusterService.createServerGroupClusters(application.getDataSource('serverGroups').data);
        ClusterState.filterService.updateClusterGroups(application);

        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups.length).toBe(1);
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups.length).toBe(1);
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups.length).toBe(2);

          application.getDataSource('serverGroups').data.splice(0, 2);
          ClusterState.filterService.updateClusterGroups(application);

          setTimeout(() => {
            expect(ClusterState.filterModel.asFilterModel.groups.length).toBe(1);
            expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups.length).toBe(1);
            expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups.length).toBe(1);
            expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups[0].heading).toBe(
              'us-west-1',
            );
            done();
          }, debounceTimeout);
        }, debounceTimeout);
      }));

    it('removes a server group when one goes away', () =>
      new Promise((done, reject) => {
        application = testContext.buildApplication({
          serverGroups: {
            data: [testContext.serverGroup001],
          },
        });
        application.clusters = clusterService.createServerGroupClusters(application.getDataSource('serverGroups').data);
        ClusterState.filterService.updateClusterGroups(application);

        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups.length).toBe(1);
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups.length).toBe(1);
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups.length).toBe(1);
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups.length).toBe(
            1,
          );
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[0].name).toBe(
            'cluster-a-v001',
          );
          done();
        }, debounceTimeout);
      }));

    it('leaves server groups alone when stringVal does not change', () =>
      new Promise((done, reject) => {
        application = testContext.buildApplication({
          serverGroups: {
            data: [
              {
                cluster: 'cluster-a',
                name: 'cluster-a-v000',
                account: 'prod',
                region: 'us-east-1',
                stringVal: 'should be deleted',
                category: 'serverGroup',
                instances: [],
              },
              {
                cluster: 'cluster-a',
                name: 'cluster-a-v001',
                account: 'prod',
                region: 'us-east-1',
                stringVal: 'original',
                category: 'serverGroup',
                instances: [],
              },
            ],
          },
        });
        application.clusters = clusterService.createServerGroupClusters(application.getDataSource('serverGroups').data);
        ClusterState.filterService.updateClusterGroups(application);

        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[0]).toBe(
            testContext.serverGroup000,
          );
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[1]).toBe(
            testContext.serverGroup001,
          );
          done();
        }, debounceTimeout);
      }));

    it('replaces server group when stringVal changes', () =>
      new Promise((done, reject) => {
        application = testContext.buildApplication({
          serverGroups: {
            data: [
              {
                cluster: 'cluster-a',
                name: 'cluster-a-v000',
                account: 'prod',
                region: 'us-east-1',
                stringVal: 'mutated',
                category: 'serverGroup',
                instances: [],
              },
              {
                cluster: 'cluster-a',
                name: 'cluster-a-v001',
                account: 'prod',
                region: 'us-east-1',
                stringVal: 'original',
                category: 'serverGroup',
                instances: [],
              },
            ],
          },
        });
        application.clusters = clusterService.createServerGroupClusters(application.getDataSource('serverGroups').data);
        ClusterState.filterService.updateClusterGroups(application);

        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[0]).not.toBe(
            testContext.serverGroup000,
          );
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[0]).toBe(
            application.getDataSource('serverGroups').data[0],
          );
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[1]).toBe(
            testContext.serverGroup001,
          );
          done();
        }, debounceTimeout);
      }));

    it('adds executions and running tasks, even when stringVal does not change', () =>
      new Promise((done, reject) => {
        const runningTasks: any = [{ name: 'a' }];
        const executions: any = [{ name: 'b' }];
        application = testContext.buildApplication({
          serverGroups: {
            data: [
              {
                cluster: 'cluster-a',
                name: 'cluster-a-v001',
                account: 'prod',
                region: 'us-east-1',
                stringVal: 'original',
                category: 'serverGroup',
                instances: [],
              },
            ],
          },
        });
        application.getDataSource('serverGroups').data[0].runningTasks = runningTasks;
        application.getDataSource('serverGroups').data[0].runningExecutions = executions;
        application.clusters = clusterService.createServerGroupClusters(application.getDataSource('serverGroups').data);
        ClusterState.filterService.updateClusterGroups(application);

        setTimeout(() => {
          expect(ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[0]).toBe(
            testContext.serverGroup001,
          );
          expect(
            ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[0].runningTasks,
          ).toBe(runningTasks);
          expect(
            ClusterState.filterModel.asFilterModel.groups[0].subgroups[0].subgroups[0].serverGroups[0]
              .runningExecutions,
          ).toBe(executions);
          done();
        }, debounceTimeout);
      }));
  });
});
