import {mock} from 'angular';

import {Application} from '../application/application.model';
import modelBuilderModule, {ApplicationModelBuilder} from '../application/applicationModel.builder';
import tagModule, {LoadBalancersTagController} from './loadBalancersTag.component';
import {InstanceCounts} from '../domain/instanceCounts';

describe('Component: loadBalancersTag', () => {

  let applicationModelBuilder: ApplicationModelBuilder,
      $componentController: ng.IComponentControllerService,
      ctrl: LoadBalancersTagController,
      serverGroup: any,
      application: Application,
      $q: ng.IQService,
      $scope: ng.IScope;

  let initialize = () => {
    application = applicationModelBuilder.createApplication({
      key: 'loadBalancers',
      loader: () => $q.when(null),
      onLoad: () => $q.when(null),
      loaded: true,
    });

    ctrl = <LoadBalancersTagController> $componentController(
      'loadBalancersTag',
      { $scope: null },
      { application: application, serverGroup: serverGroup }
    );
    ctrl.$onInit();
  };

  beforeEach(mock.module(
    tagModule,
    modelBuilderModule
  ));

  beforeEach(mock.inject(
    (_applicationModelBuilder_: ApplicationModelBuilder, _$componentController_: ng.IComponentControllerService,
     _$q_: ng.IQService, $rootScope: ng.IRootScopeService) => {
      applicationModelBuilder = _applicationModelBuilder_;
      $componentController = _$componentController_;
      $q = _$q_;
      $scope = $rootScope.$new();
    }));

  describe('model creation', () => {
    beforeEach(() => {
      serverGroup = {
        account: 'prod',
        region: 'us-east-1',
        type: 'aws',
        loadBalancers: ['lb1'],
        instances: [],
      };
    });

    it ('extracts load balancer from data', () => {
      initialize();
      application.getDataSource('loadBalancers').data = [
        { name: 'lb1', account: 'prod', region: 'us-east-1', vpcId: 'vpc-1' },
        { name: 'lb2', account: 'prod', region: 'us-east-1' },
      ];

      $scope.$digest();
      expect(ctrl.loadBalancers.length).toBe(1);
      expect(ctrl.loadBalancers[0].name).toBe('lb1');
    });

    it('attaches instance counts', () => {
      initialize();
      application.getDataSource('loadBalancers').data = [
        { name: 'lb1', account: 'prod', region: 'us-east-1', vpcId: 'vpc-1' },
        { name: 'lb2', account: 'prod', region: 'us-east-1' },
      ];
      serverGroup.loadBalancers = ['lb1', 'lb2'];
      serverGroup.instances = [
        {
          id: 'not-in-lb',
          health: [
            { type: 'Discovery' },
          ]
        },
        {
          id: 'in-one-lb',
          health: [
            {
              type: 'LoadBalancer',
              loadBalancers: [
                { name: 'lb1', healthState: 'Up' },
                { name: 'some-other-lb', healthState: 'Down' }
              ]
            }
          ]
        },
        {
          id: 'in-two-lbs',
          health: [
            {
              type: 'LoadBalancer',
              loadBalancers: [
                { name: 'lb1', healthState: 'Up' },
                { name: 'lb2', healthState: 'Down' }
              ]
            }
          ]
        }
      ];

      $scope.$digest();
      expect(ctrl.loadBalancers.length).toBe(2);
      expect(ctrl.loadBalancers[0].instanceCounts).toEqual(<InstanceCounts>{up: 2, down: 0, succeeded: 0, failed: 0, unknown: 0 });
      expect(ctrl.loadBalancers[1].instanceCounts).toEqual(<InstanceCounts>{up: 0, down: 1, succeeded: 0, failed: 0, unknown: 0 });
    });
  });
});
