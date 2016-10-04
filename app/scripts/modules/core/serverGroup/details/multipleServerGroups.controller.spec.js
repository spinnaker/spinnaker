import modelBuilderModule from '../../application/applicationModel.builder';

describe('Controller: MultipleServerGroups', function () {

  var controller;
  var scope;
  var MultiselectModel;

  beforeEach(
    window.module(
      require('./multipleServerGroups.controller'),
      require('../../application/service/applications.read.service'),
      modelBuilderModule
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller, _$q_, _MultiselectModel_, applicationReader, ClusterFilterModel, applicationModelBuilder) {
      scope = $rootScope.$new();
      MultiselectModel = _MultiselectModel_;
      ClusterFilterModel.sortFilter.multiselect = true;

      this.createController = function (serverGroups) {
        let application = applicationModelBuilder.createApplication({key: 'serverGroups', lazy: true});
        application.serverGroups.data = serverGroups;
        this.application = application;

        controller = $controller('MultipleServerGroupsCtrl', {
          $scope: scope,
          app: application,
        });
      };
    })
  );

  beforeEach(function () {
    this.serverGroupA = { type: 'aws', name: 'asg-v001', account: 'prod', region: 'us-east-1', instanceCounts: { a: 1}};
    this.serverGroupB = { type: 'gce', name: 'asg-v002', account: 'test', region: 'us-west-1', isDisabled: true, instanceCounts: { b: 2}};
    this.serverGroupC = { type: 'gce', name: 'asg-v003', account: 'test', region: 'us-west-1', instanceCounts: { c: 3}};
    MultiselectModel.clearAll();
    spyOn(MultiselectModel, 'syncNavigation').and.callFake(angular.noop);
  });

  describe('server group retrieval', function () {

    it('adds copies of server groups, not the server groups themselves', function () {
      MultiselectModel.toggleServerGroup(this.serverGroupB);
      this.createController([this.serverGroupA, this.serverGroupB, this.serverGroupC]);

      expect(controller.serverGroups[0].name).toBe(this.serverGroupB.name);
      expect(controller.serverGroups[0]).not.toBe(this.serverGroupB);
      expect(controller.serverGroups[0].instanceCounts).toEqual(this.serverGroupB.instanceCounts);
      expect(controller.serverGroups[0].instanceCounts).not.toBe(this.serverGroupB.instanceCounts);
    });

    it('gets details for each server group and adds model to scope', function () {
      MultiselectModel.toggleServerGroup(this.serverGroupA);
      MultiselectModel.toggleServerGroup(this.serverGroupB);
      this.createController([this.serverGroupA, this.serverGroupB, this.serverGroupC]);

      expect(MultiselectModel.serverGroups.length).toBe(2);
      expect(controller.serverGroups.length).toBe(2);

      let groupA = controller.serverGroups[0],
          groupB = controller.serverGroups[1];

      expect(groupA.instanceCounts.a).toBe(1);
      expect(groupA.disabled).toBeFalsy();

      expect(groupB.instanceCounts.b).toBe(2);
      expect(groupB.disabled).toBe(true);
    });

    it('re-retrieves details when serverGroups refresh', function () {
      MultiselectModel.toggleServerGroup(this.serverGroupA);
      MultiselectModel.toggleServerGroup(this.serverGroupB);

      this.createController([this.serverGroupA, this.serverGroupB, this.serverGroupC]);

      this.serverGroupA.isDisabled = true;
      this.serverGroupB.instanceCounts.d = 3;
      this.application.serverGroups.dataUpdated();

      expect(controller.serverGroups[0].disabled).toBe(true);
      expect(controller.serverGroups[1].instanceCounts.d).toBe(3);
    });

  });

  describe('actions', function () {
    it('can disable when all groups are enabled', function () {
      MultiselectModel.toggleServerGroup(this.serverGroupA);
      MultiselectModel.toggleServerGroup(this.serverGroupB);
      this.createController([this.serverGroupA, this.serverGroupB]);
      expect(controller.canDisable()).toBe(false);

      this.serverGroupB.isDisabled = false;
      this.application.serverGroups.dataUpdated();
      expect(controller.canDisable()).toBe(true);
    });

    it('can enable when all groups are disabled', function () {
      MultiselectModel.toggleServerGroup(this.serverGroupA);
      MultiselectModel.toggleServerGroup(this.serverGroupB);
      this.createController([this.serverGroupA, this.serverGroupB]);
      expect(controller.canEnable()).toBe(false);

      this.serverGroupA.isDisabled = true;
      this.application.serverGroups.dataUpdated();
      expect(controller.canEnable()).toBe(true);
    });
  });

});
