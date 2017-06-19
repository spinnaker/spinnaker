import { mock } from 'angular';
import { IInstance, IServerGroup } from 'core/domain';
import { Application } from '../application.model';
import { APPLICATION_MODEL_BUILDER, ApplicationModelBuilder } from '../applicationModel.builder';
import { AppListExtractor, LIST_EXTRACTOR_SERVICE } from './listExtractor.service';

describe('appListExtractorService', function () {

  let service: AppListExtractor,
      applicationModelBuilder: ApplicationModelBuilder;

  const buildApplication = (serverGroups: any[] = []): Application => {
    const application: Application = applicationModelBuilder.createApplication('app', {key: 'serverGroups', lazy: true});
    application.getDataSource('serverGroups').data = serverGroups;
    return application;
  };

  const asResult = (instance: IInstance) => {
    const {id, availabilityZone} = instance;
    return { id, availabilityZone };
  };

  beforeEach(
    mock.module(
      LIST_EXTRACTOR_SERVICE,
      APPLICATION_MODEL_BUILDER,
    )
  );

  beforeEach(
    mock.inject( (_appListExtractorService_: AppListExtractor, _applicationModelBuilder_: ApplicationModelBuilder) => {
      service = _appListExtractorService_;
      applicationModelBuilder = _applicationModelBuilder_;
    })
  );

  describe('Get Regions from a list of applications', function () {

    it('should get a empty list for one application w/ no clusters', function () {
      const application: Application = buildApplication();

      const result = service.getRegions([application]);
      expect(result.length).toEqual(0);
      expect(result).toEqual([]);
    });

    it('should get a list of 1 aws region for one application w/ one cluster with one server group in one region', function () {

      const application: Application = buildApplication([{region: 'us-west-1'}]);
      const result = service.getRegions([application]);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['us-west-1']);
    });

    it('should get a list of 2 aws region for one application w/ one cluster with two server groups in 2 region', function () {

      const application: Application = buildApplication([{region: 'us-west-1'}, {region: 'us-west-2'}]);
      const result = service.getRegions([application]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['us-west-1', 'us-west-2']);
    });

    it('should get a unique list of 2 aws region for one application w/ 3 server groups in 2 regions', function () {

      const application: Application = buildApplication([{region: 'us-west-1'}, {region: 'us-west-2'}, {region: 'us-west-1'}]);

      const result = service.getRegions([application]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['us-west-1', 'us-west-2']);
    });
  });

  describe('Get Stacks from a list of applications', function() {
    it('should get a empty list for one application w/ no clusters', function () {
      const application: Application = buildApplication();

      const result = service.getStacks([application]);
      expect(result.length).toEqual(0);
      expect(result).toEqual([]);
    });

    it('should get a list of 1 stack for one application w/ one cluster with one server group in one stack', function () {
      const application: Application = buildApplication([ {stack: 'prod'} ]);

      const result = service.getStacks([application]);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['prod']);
    });

    it('should get a list of 2 stacks for one application w/ multi cluster with one server group in one stack', function () {
      const application: Application = buildApplication([ {stack: 'prod'}, {stack: 'test'} ]);

      const result = service.getStacks([application]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['prod', 'test']);
    });

    it('should get a list of 2 stacks for two application w/ one cluster each with one server group in one stack', function () {
      const applicationA: Application = buildApplication([ {stack: 'prod'} ]);
      const applicationB: Application = buildApplication([ {stack: 'mceprod'} ]);

      const result = service.getStacks([applicationA, applicationB]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['mceprod', 'prod']);
    });

    it('should get a list of 1 stacks with a filter on the serverGroup name', function () {
      const application: Application = buildApplication([ {name: 'foo', stack: 'prod'}, {name: 'bar', stack: 'test'} ]);

      const filterByBar = (serverGroup: IServerGroup) => serverGroup.name === 'bar';
      const result = service.getStacks([application], filterByBar);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['test']);
    });
  });


  describe('Get Clusters from a list of applications', function() {
    it('should get a empty list for one application w/ no clusters', function () {
      const application: Application = buildApplication();

      const result = service.getClusters([application]);
      expect(result.length).toEqual(0);
      expect(result).toEqual([]);
    });

    it('should get a list of 1 cluster for one application w/ one cluster', function () {
      const application: Application = buildApplication([ {cluster: 'mahe-prod'} ]);

      const result = service.getClusters([application]);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['mahe-prod']);
    });

    it('should get a list of 2 cluster names for one application w/ multi clusters', function () {
      const application: Application = buildApplication([ {cluster: 'mahe-prod'}, {cluster: 'mahe-prestaging'} ]);

      const result = service.getClusters([application]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['mahe-prestaging', 'mahe-prod']);
    });

    it('should get a list of 2 clusters for two application w/ one cluster each', function () {
      const applicationA: Application = buildApplication([ {cluster: 'deck-main'} ]);
      const applicationB: Application = buildApplication([ {cluster: 'gate-main'} ]);

      const result = service.getClusters([applicationA, applicationB]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['deck-main', 'gate-main']);
    });

    it('should get a list of 1 cluster with a filter on the cluster name', function () {
      const application: Application = buildApplication([ {cluster: 'deck-main'}, {cluster: 'gate-main'} ]);

      const filterByGate = (serverGroup: IServerGroup) => serverGroup.cluster === 'gate-main';
      const result = service.getClusters([application], filterByGate);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['gate-main']);
    });
  });

  describe('Get ASGs from a list of applications', function() {
    it('should get a empty list for one application w/ no clusters', function () {
      const application: Application = buildApplication();
      const result = service.getAsgs([application]);
      expect(result.length).toEqual(0);
      expect(result).toEqual([]);
    });

    it('should get a list of 1 ASG for one application w/ one cluster', function () {
      const application: Application = buildApplication([ {name: 'mahe-main-v000'} ]);
      const result = service.getAsgs([application]);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['mahe-main-v000']);
    });

    it('should get a list of 2 ASG names for one application w/ multi clusters', function () {
      const application: Application = buildApplication([{name: 'mahe-main-v000'}, {name: 'mahe-prestaging-v002'}]);
      const result = service.getAsgs([application]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['mahe-main-v000', 'mahe-prestaging-v002' ]);
    });

    it('should get a list of 2 ASGs for two application w/ one cluster each', function () {
      const applicationA: Application = buildApplication([{name: 'mahe-prestaging-v002'}]);
      const applicationB: Application = buildApplication([{name: 'deck-main-v002'}]);
      const result = service.getAsgs([applicationA, applicationB]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['deck-main-v002', 'mahe-prestaging-v002']);
    });

    it('should get a list of 1 cluster with a filter on the cluster name', function () {
      const application: Application = buildApplication([
        {cluster: 'gate-main', name: 'gate-main-v000'},
        {cluster: 'deck-main', name: 'deck-main-v002'}
      ]);
      const filterByGate = (serverGroup: IServerGroup) => serverGroup.cluster === 'gate-main';
      const result = service.getAsgs([application], filterByGate);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['gate-main-v000']);
    });
  });



  describe('Get Zones from a list of applications', function() {
    it('should get a empty list for one application w/ no clusters', function () {
      const application: Application = buildApplication();
      const result = service.getZones([application]);
      expect(result.length).toEqual(0);
      expect(result).toEqual([]);
    });

    it('should get a list of 1 zone for one application w/ one cluster', function () {
      const application: Application = buildApplication([
        { name: 'mahe-main-v000', instances: [ { availabilityZone: 'us-west-2a' } ] }
      ]);
      const result = service.getZones([application]);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['us-west-2a']);
    });

    it('should get a list of 2 Zones names for one application w/ multi clusters', function () {
      const application: Application = buildApplication([
        { instances: [ { availabilityZone: 'us-west-2a' } ] },
        { instances: [ { availabilityZone: 'us-west-2d' } ] },
      ]);
      const result = service.getZones([application]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['us-west-2a', 'us-west-2d' ]);
    });

    it('should get a list of 2 Zones for two application w/ one cluster each', function () {
      const applicationA: Application = buildApplication([
        { instances: [ { availabilityZone: 'us-west-2a' } ] }
      ]);
      const applicationB: Application = buildApplication([
        { instances: [ { availabilityZone: 'us-west-2d' } ] }
      ]);
      const result = service.getZones([applicationA, applicationB]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['us-west-2a', 'us-west-2d']);
    });

    it('should get a list of 1 cluster with a filter on the cluster name and region', function () {
      const application: Application = buildApplication([
        { cluster: 'deck-main', region: 'us-west-2', instances: [ { availabilityZone: 'us-west-2a' } ] },
        { cluster: 'gate-main', region: 'us-west-2', instances: [ { availabilityZone: 'us-west-2d' } ] },
        { cluster: 'gate-main', region: 'us-east-1', instances: [ { availabilityZone: 'us-east-1d' } ] },
      ]);
      const filterByGate = (serverGroup: IServerGroup) => serverGroup.cluster === 'gate-main';
      const filterByRegion = (serverGroup: IServerGroup) => serverGroup.region === 'us-east-1';
      const result = service.getZones([application], filterByGate, filterByRegion);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['us-east-1d']);
    });


    it('should get a list of 1 cluster with a filter on the cluster name and region and serverGroupName', function () {
      const application: Application = buildApplication([
        { cluster: 'deck-main', region: 'us-west-2', name: 'deck-main-v001', instances: [ { availabilityZone: 'us-west-2a' } ] },
        { cluster: 'gate-main', region: 'us-west-2', name: 'gate-main-v002', instances: [ { availabilityZone: 'us-west-2d' } ] },
        { cluster: 'gate-main', region: 'us-east-1', name: 'gate-main-v003', instances: [ { availabilityZone: 'us-east-1d' } ] },
        { cluster: 'gate-main', region: 'eu-west-1', name: 'gate-main-v004', instances: [ { availabilityZone: 'eu-west-1b' } ] },
      ]);

      const filterByGate = (serverGroup: IServerGroup) => serverGroup.cluster === 'gate-main';
      const filterByRegion = (serverGroup: IServerGroup) => serverGroup.region === 'eu-west-1';
      const filterByServerGroupName = (serverGroup: IServerGroup) => serverGroup.name === 'gate-main-v004';
      const result = service.getZones([application], filterByGate, filterByRegion, filterByServerGroupName);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['eu-west-1b']);
    });
  });

  describe('Get Instances from a list of applications', function() {
    it('should get a empty list for one application w/ no clusters', function () {
      const application: Application = buildApplication();
      const result = service.getInstances([application]);
      expect(result.length).toEqual(0);
      expect(result).toEqual([]);
    });

    it('should get a list of 1 instance for one application w/ one cluster with one instance', function () {
      const application: Application = buildApplication([
        { instances: [ { id: 'i-1234' } ] }
      ]);
      const result = service.getInstances([application]);
      expect(result.length).toEqual(1);
      expect(result[0].id).toEqual('i-1234');
    });

    it('should get a list of 2 instances names for one application w/ multi clusters that have 1 instance each', function () {
      const application: Application = buildApplication([
        { instances: [ { availabilityZone: 'us-west-2a', id: 'i-1234' } ] },
        { instances: [ { availabilityZone: 'us-west-2d', id: 'i-4321' } ] }
      ]);
      const result = service.getInstances([application]);
      expect(result.length).toEqual(2);
      expect(result.map(asResult)).toEqual([
        {
          availabilityZone: 'us-west-2a',
          id: 'i-1234'
        },
        {
          availabilityZone: 'us-west-2d',
          id: 'i-4321'
        }
      ]);
    });

    it('should get a list of 2 Instances for two application w/ one cluster each with one instance', function () {
      const applicationA: Application = buildApplication([
        { instances: [ { availabilityZone: 'us-west-2a', id: 'i-4321' } ] }
      ]);
      const applicationB: Application = buildApplication([
        { instances: [ { availabilityZone: 'us-west-2d', id: 'i-1111' } ] }
      ]);

      const result = service.getInstances([applicationA, applicationB]);
      expect(result.length).toEqual(2);
      expect(result.map(asResult)).toEqual([
        {
          availabilityZone: 'us-west-2a',
          id: 'i-4321'
        },
        {
          availabilityZone: 'us-west-2d',
          id: 'i-1111'
        },


      ]);
    });

    it('should get a list of 1 cluster with a filter on the cluster name and region', function () {
      const application: Application = buildApplication([
        { cluster: 'deck-main', region: 'us-west-2', instances: [ { availabilityZone: 'us-west-2a', id: 'i-1234' } ] },
        { cluster: 'gate-main', region: 'us-west-2', instances: [ { availabilityZone: 'us-west-2d', id: 'i-3949' } ] },
        { cluster: 'gate-main', region: 'us-east-1', instances: [ { availabilityZone: 'us-east-1d', id: 'i-3333' } ] }
      ]);

      const filterByGate = (serverGroup: IServerGroup) => serverGroup.cluster === 'gate-main';
      const filterByRegion = (serverGroup: IServerGroup) => serverGroup.region === 'us-east-1';
      const result = service.getInstances([application], filterByGate, filterByRegion);
      expect(result.length).toEqual(1);
      expect(result.map(asResult)).toEqual([
        {
          availabilityZone: 'us-east-1d',
          id: 'i-3333'
        }
      ]);
    });


    it('should get a list of 1 instance with a filter on the cluster name and region and availabilityZone', function () {
      const application: Application = buildApplication([
        { cluster: 'deck-main', region: 'us-west-2', instances: [ { availabilityZone: 'us-west-2a' } ] },
        { cluster: 'gate-main', region: 'us-west-2', instances: [ { availabilityZone: 'us-west-2d' } ] },
        { cluster: 'gate-main', region: 'us-east-1', instances: [ { availabilityZone: 'us-east-1d' } ] },
        { cluster: 'gate-main', region: 'eu-west-1', instances: [ { availabilityZone: 'eu-west-1b', id: 'i-12344' } ] },
      ]);

      const filterByGate = (serverGroup: IServerGroup) => serverGroup.cluster === 'gate-main';
      const filterByRegion = (serverGroup: IServerGroup) => serverGroup.region === 'eu-west-1';
      const filterByAvailabilityZone = (instance: IInstance) => instance.availabilityZone === 'eu-west-1b';
      const result = service.getInstances([application], filterByGate, filterByRegion, filterByAvailabilityZone);
      expect(result.length).toEqual(1);
      expect(result.map(asResult)).toEqual([
        {
          availabilityZone: 'eu-west-1b',
          id: 'i-12344'
        }
      ]);
    });
  });
});


