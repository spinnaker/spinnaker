import {mock} from 'angular';
import {LIST_EXTRACTOR_SERVICE, AppListExtractor} from './listExtractor.service';
import {APPLICATION_MODEL_BUILDER, ApplicationModelBuilder} from '../applicationModel.builder';
import {Application} from '../application.model';
import {ServerGroup} from '../../domain/serverGroup';
import {Instance} from '../../domain/instance';

describe('appListExtractorService', function () {

  let service: AppListExtractor,
      applicationModelBuilder: ApplicationModelBuilder;

  let buildApplication = (serverGroups: any[] = []): Application => {
    let application: Application = applicationModelBuilder.createApplication({key: 'serverGroups', lazy: true});
    application.getDataSource('serverGroups').data = serverGroups;
    return application;
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
      let application: Application = buildApplication();

      let result = service.getRegions([application]);
      expect(result.length).toEqual(0);
      expect(result).toEqual([]);
    });

    it('should get a list of 1 aws region for one application w/ one cluster with one server group in one region', function () {

      let application: Application = buildApplication([{region: 'us-west-1'}]);
      let result = service.getRegions([application]);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['us-west-1']);
    });

    it('should get a list of 2 aws region for one application w/ one cluster with two server groups in 2 region', function () {

      let application: Application = buildApplication([{region: 'us-west-1'}, {region: 'us-west-2'}]);
      let result = service.getRegions([application]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['us-west-1', 'us-west-2']);
    });

    it('should get a unique list of 2 aws region for one application w/ 3 server groups in 2 regions', function () {

      let application: Application = buildApplication([{region: 'us-west-1'}, {region: 'us-west-2'}, {region: 'us-west-1'}]);

      let result = service.getRegions([application]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['us-west-1', 'us-west-2']);
    });
  });

  describe('Get Stacks from a list of applications', function() {
    it('should get a empty list for one application w/ no clusters', function () {
      let application: Application = buildApplication();

      let result = service.getStacks([application]);
      expect(result.length).toEqual(0);
      expect(result).toEqual([]);
    });

    it('should get a list of 1 stack for one application w/ one cluster with one server group in one stack', function () {
      let application: Application = buildApplication([ {stack: 'prod'} ]);

      let result = service.getStacks([application]);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['prod']);
    });

    it('should get a list of 2 stacks for one application w/ multi cluster with one server group in one stack', function () {
      let application: Application = buildApplication([ {stack: 'prod'}, {stack: 'test'} ]);

      let result = service.getStacks([application]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['prod', 'test']);
    });

    it('should get a list of 2 stacks for two application w/ one cluster each with one server group in one stack', function () {
      let applicationA: Application = buildApplication([ {stack: 'prod'} ]);
      let applicationB: Application = buildApplication([ {stack: 'mceprod'} ]);

      let result = service.getStacks([applicationA, applicationB]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['mceprod', 'prod']);
    });

    it('should get a list of 1 stacks with a filter on the serverGroup name', function () {
      let application: Application = buildApplication([ {name: 'foo', stack: 'prod'}, {name: 'bar', stack: 'test'} ]);

      let filterByBar = (serverGroup: ServerGroup) => serverGroup.name === 'bar';
      let result = service.getStacks([application], filterByBar);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['test']);
    });
  });


  describe('Get Clusters from a list of applications', function() {
    it('should get a empty list for one application w/ no clusters', function () {
      let application: Application = buildApplication();

      let result = service.getClusters([application]);
      expect(result.length).toEqual(0);
      expect(result).toEqual([]);
    });

    it('should get a list of 1 cluster for one application w/ one cluster', function () {
      let application: Application = buildApplication([ {cluster: 'mahe-prod'} ]);

      let result = service.getClusters([application]);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['mahe-prod']);
    });

    it('should get a list of 2 cluster names for one application w/ multi clusters', function () {
      let application: Application = buildApplication([ {cluster: 'mahe-prod'}, {cluster: 'mahe-prestaging'} ]);

      let result = service.getClusters([application]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['mahe-prestaging', 'mahe-prod']);
    });

    it('should get a list of 2 clusters for two application w/ one cluster each', function () {
      let applicationA: Application = buildApplication([ {cluster: 'deck-main'} ]);
      let applicationB: Application = buildApplication([ {cluster: 'gate-main'} ]);

      let result = service.getClusters([applicationA, applicationB]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['deck-main', 'gate-main']);
    });

    it('should get a list of 1 cluster with a filter on the cluster name', function () {
      let application: Application = buildApplication([ {cluster: 'deck-main'}, {cluster: 'gate-main'} ]);

      let filterByGate = (serverGroup: ServerGroup) => serverGroup.cluster === 'gate-main';
      let result = service.getClusters([application], filterByGate);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['gate-main']);
    });
  });

  describe('Get ASGs from a list of applications', function() {
    it('should get a empty list for one application w/ no clusters', function () {
      let application: Application = buildApplication();
      let result = service.getAsgs([application]);
      expect(result.length).toEqual(0);
      expect(result).toEqual([]);
    });

    it('should get a list of 1 ASG for one application w/ one cluster', function () {
      let application: Application = buildApplication([ {name: 'mahe-main-v000'} ]);
      let result = service.getAsgs([application]);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['mahe-main-v000']);
    });

    it('should get a list of 2 ASG names for one application w/ multi clusters', function () {
      let application: Application = buildApplication([{name: 'mahe-main-v000'}, {name: 'mahe-prestaging-v002'}]);
      let result = service.getAsgs([application]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['mahe-main-v000', 'mahe-prestaging-v002' ]);
    });

    it('should get a list of 2 ASGs for two application w/ one cluster each', function () {
      let applicationA: Application = buildApplication([{name: 'mahe-prestaging-v002'}]);
      let applicationB: Application = buildApplication([{name: 'deck-main-v002'}]);
      let result = service.getAsgs([applicationA, applicationB]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['deck-main-v002', 'mahe-prestaging-v002']);
    });

    it('should get a list of 1 cluster with a filter on the cluster name', function () {
      let application: Application = buildApplication([
        {cluster: 'gate-main', name: 'gate-main-v000'},
        {cluster: 'deck-main', name: 'deck-main-v002'}
      ]);
      let filterByGate = (serverGroup: ServerGroup) => serverGroup.cluster === 'gate-main';
      let result = service.getAsgs([application], filterByGate);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['gate-main-v000']);
    });
  });



  describe('Get Zones from a list of applications', function() {
    it('should get a empty list for one application w/ no clusters', function () {
      let application: Application = buildApplication();
      let result = service.getZones([application]);
      expect(result.length).toEqual(0);
      expect(result).toEqual([]);
    });

    it('should get a list of 1 zone for one application w/ one cluster', function () {
      let application: Application = buildApplication([
        { name: 'mahe-main-v000', instances: [ { availabilityZone: 'us-west-2a' } ] }
      ]);
      let result = service.getZones([application]);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['us-west-2a']);
    });

    it('should get a list of 2 Zones names for one application w/ multi clusters', function () {
      let application: Application = buildApplication([
        { instances: [ { availabilityZone: 'us-west-2a' } ] },
        { instances: [ { availabilityZone: 'us-west-2d' } ] },
      ]);
      let result = service.getZones([application]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['us-west-2a', 'us-west-2d' ]);
    });

    it('should get a list of 2 Zones for two application w/ one cluster each', function () {
      let applicationA: Application = buildApplication([
        { instances: [ { availabilityZone: 'us-west-2a' } ] }
      ]);
      let applicationB: Application = buildApplication([
        { instances: [ { availabilityZone: 'us-west-2d' } ] }
      ]);
      let result = service.getZones([applicationA, applicationB]);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['us-west-2a', 'us-west-2d']);
    });

    it('should get a list of 1 cluster with a filter on the cluster name and region', function () {
      let application: Application = buildApplication([
        { cluster: 'deck-main', region: 'us-west-2', instances: [ { availabilityZone: 'us-west-2a' } ] },
        { cluster: 'gate-main', region: 'us-west-2', instances: [ { availabilityZone: 'us-west-2d' } ] },
        { cluster: 'gate-main', region: 'us-east-1', instances: [ { availabilityZone: 'us-east-1d' } ] },
      ]);
      let filterByGate = (serverGroup: ServerGroup) => serverGroup.cluster === 'gate-main';
      let filterByRegion = (serverGroup: ServerGroup) => serverGroup.region === 'us-east-1';
      let result = service.getZones([application], filterByGate, filterByRegion);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['us-east-1d']);
    });


    it('should get a list of 1 cluster with a filter on the cluster name and region and serverGroupName', function () {
      let application: Application = buildApplication([
        { cluster: 'deck-main', region: 'us-west-2', name: 'deck-main-v001', instances: [ { availabilityZone: 'us-west-2a' } ] },
        { cluster: 'gate-main', region: 'us-west-2', name: 'gate-main-v002', instances: [ { availabilityZone: 'us-west-2d' } ] },
        { cluster: 'gate-main', region: 'us-east-1', name: 'gate-main-v003', instances: [ { availabilityZone: 'us-east-1d' } ] },
        { cluster: 'gate-main', region: 'eu-west-1', name: 'gate-main-v004', instances: [ { availabilityZone: 'eu-west-1b' } ] },
      ]);

      let filterByGate = (serverGroup: ServerGroup) => serverGroup.cluster === 'gate-main';
      let filterByRegion = (serverGroup: ServerGroup) => serverGroup.region === 'eu-west-1';
      let filterByServerGroupName = (serverGroup: ServerGroup) => serverGroup.name === 'gate-main-v004';
      let result = service.getZones([application], filterByGate, filterByRegion, filterByServerGroupName);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['eu-west-1b']);
    });
  });

  describe('Get Instances from a list of applications', function() {
    it('should get a empty list for one application w/ no clusters', function () {
      let application: Application = buildApplication();
      let result = service.getInstances([application]);
      expect(result.length).toEqual(0);
      expect(result).toEqual([]);
    });

    it('should get a list of 1 instance for one application w/ one cluster with one instance', function () {
      let application: Application = buildApplication([
        { instances: [ { id: 'i-1234' } ] }
      ]);
      let result = service.getInstances([application]);
      expect(result.length).toEqual(1);
      expect(result[0].id).toEqual('i-1234');
    });

    it('should get a list of 2 instances names for one application w/ multi clusters that have 1 instance each', function () {
      let application: Application = buildApplication([
        { instances: [ { availabilityZone: 'us-west-2a', id: 'i-1234' } ] },
        { instances: [ { availabilityZone: 'us-west-2d', id: 'i-4321' } ] }
      ]);
      let result = service.getInstances([application]);
      expect(result.length).toEqual(2);
      expect(result).toEqual([
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
      let applicationA: Application = buildApplication([
        { instances: [ { availabilityZone: 'us-west-2a', id: 'i-4321' } ] }
      ]);
      let applicationB: Application = buildApplication([
        { instances: [ { availabilityZone: 'us-west-2d', id: 'i-1111' } ] }
      ]);

      let result = service.getInstances([applicationA, applicationB]);
      expect(result.length).toEqual(2);
      expect(result).toEqual([
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
      let application: Application = buildApplication([
        { cluster: 'deck-main', region: 'us-west-2', instances: [ { availabilityZone: 'us-west-2a', id: 'i-1234' } ] },
        { cluster: 'gate-main', region: 'us-west-2', instances: [ { availabilityZone: 'us-west-2d', id: 'i-3949' } ] },
        { cluster: 'gate-main', region: 'us-east-1', instances: [ { availabilityZone: 'us-east-1d', id: 'i-3333' } ] }
      ]);

      let filterByGate = (serverGroup: ServerGroup) => serverGroup.cluster === 'gate-main';
      let filterByRegion = (serverGroup: ServerGroup) => serverGroup.region === 'us-east-1';
      let result = service.getInstances([application], filterByGate, filterByRegion);
      expect(result.length).toEqual(1);
      expect(result).toEqual([
        {
          availabilityZone: 'us-east-1d',
          id: 'i-3333'
        }
      ]);
    });


    it('should get a list of 1 instance with a filter on the cluster name and region and availabilityZone', function () {
      let application: Application = buildApplication([
        { cluster: 'deck-main', region: 'us-west-2', instances: [ { availabilityZone: 'us-west-2a' } ] },
        { cluster: 'gate-main', region: 'us-west-2', instances: [ { availabilityZone: 'us-west-2d' } ] },
        { cluster: 'gate-main', region: 'us-east-1', instances: [ { availabilityZone: 'us-east-1d' } ] },
        { cluster: 'gate-main', region: 'eu-west-1', instances: [ { availabilityZone: 'eu-west-1b', id: 'i-12344' } ] },
      ]);

      let filterByGate = (serverGroup: ServerGroup) => serverGroup.cluster === 'gate-main';
      let filterByRegion = (serverGroup: ServerGroup) => serverGroup.region === 'eu-west-1';
      let filterByAvailabilityZone = (instance: Instance) => instance.availabilityZone === 'eu-west-1b';
      let result = service.getInstances([application], filterByGate, filterByRegion, filterByAvailabilityZone);
      expect(result.length).toEqual(1);
      expect(result).toEqual([
        {
          availabilityZone: 'eu-west-1b',
          id: 'i-12344'
        }
      ]);
    });
  });
});


