'use strict';

describe('appListExtractorService', function () {

  let service;

  beforeEach(
    window.module(
      require('./listExtractor.service')
    )
  );

  beforeEach(
    window.inject( (_appListExtractorService_) => {
      service = _appListExtractorService_;
    })
  );

  it('should have the service instantiated', function () {
    expect(service).toBeDefined();
  });


  describe('Get Regions from a list of applications', function () {

    it('should get a empty list for one application w/ no clusters', function () {
      let appList = [{}];

      let result = service.getRegions(appList);
      expect(result.length).toEqual(0);
      expect(result).toEqual([]);
    });

    it('should get a list of 1 aws region for one application w/ one cluster with one server group in one region', function () {

      let appList = [{
        clusters: [
          {
            serverGroups: [
              {region: 'us-west-1'}
            ]
          },
        ]
      }];

      let result = service.getRegions(appList);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['us-west-1']);
    });

    it('should get a list of 2 aws region for one application w/ one cluster with two server groups in 2 region', function () {

      let appList = [{
        clusters: [
          {
            serverGroups: [
              {region: 'us-west-1'},
              {region: 'us-west-2'}
            ]
          },
        ]
      }];

      let result = service.getRegions(appList);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['us-west-1', 'us-west-2']);
    });

    it('should get a list of 2 aws region for one application w/ 2 cluster with one server groups in 1 region', function () {

      let appList = [{
        clusters: [
          {
            serverGroups: [
              {region: 'us-west-1'},
            ]
          },
          {
            serverGroups: [
              {region: 'us-west-2'},
            ]
          },
        ]
      }];

      let result = service.getRegions(appList);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['us-west-1', 'us-west-2']);
    });

    it('should get a list of 3 aws region for 2 application w/ one cluster each with multiple server groups accorss multiple clusters', function () {

      let appList = [
        {
          clusters: [
            {
              serverGroups: [
                {region: 'us-west-1'},
                {region: 'us-west-2'}
              ]
            },
          ]
        },
        {
          clusters: [
            {
              serverGroups: [
                {region: 'eu-east-1'}
              ]
            },
          ]
        },
      ];

      let result = service.getRegions(appList);
      expect(result.length).toEqual(3);
      expect(result).toEqual(['eu-east-1', 'us-west-1', 'us-west-2']);
    });

  });

  describe('Get Stacks from a list of applications', function() {
    it('should get a empty list for one application w/ no clusters', function () {
      let appList = [{}];

      let result = service.getStacks(appList);
      expect(result.length).toEqual(0);
      expect(result).toEqual([]);
    });

    it('should get a list of 1 stack for one application w/ one cluster with one server group in one stack', function () {

      let appList = [{
        clusters: [
          {
            serverGroups: [
              {stack: 'prod'}
            ]
          },
        ]
      }];

      let result = service.getStacks(appList);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['prod']);
    });

    it('should get a list of 2 stacks for one application w/ multi cluster with one server group in one stack', function () {

      let appList = [{
        clusters: [
          {
            serverGroups: [
              {stack: 'prod'}
            ]
          },
          {
            serverGroups: [
              {stack: 'test'}
            ]
          },
        ]
      }];

      let result = service.getStacks(appList);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['prod', 'test']);
    });

    it('should get a list of 2 stacks for two application w/ one cluster each with one server group in one stack', function () {

      let appList = [
        {
          clusters: [
            {
              serverGroups: [
                {stack: 'prod'}
              ]
            },
          ]
        },

        {
          clusters: [
            {
              serverGroups: [
                {stack: 'mceprod'}
              ]
            },
          ]
        },
      ];

      let result = service.getStacks(appList);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['mceprod', 'prod']);
    });

    it('should get a list of 1 stacks with a filter on the serverGroup name', function () {

      let appList = [{
        clusters: [
          {
            serverGroups: [
              {
                name: 'foo',
                stack: 'prod'
              }
            ]
          },
          {
            serverGroups: [
              {
                name: 'bar',
                stack: 'test'
              }
            ]
          },
        ]
      }];

      let filterByBar = (serverGroup) => serverGroup.name === 'bar';
      let result = service.getStacks(appList, filterByBar);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['test']);
    });
  });


  describe('Get Clusters from a list of applications', function() {
    it('should get a empty list for one application w/ no clusters', function () {
      let appList = [{}];

      let result = service.getClusters(appList);
      expect(result.length).toEqual(0);
      expect(result).toEqual([]);
    });

    it('should get a list of 1 cluster for one application w/ one cluster', function () {

      let appList = [{
        clusters: [
          {
            name:'mahe-prod'
          },
        ]
      }];

      let result = service.getClusters(appList);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['mahe-prod']);
    });

    it('should get a list of 2 cluster names for one application w/ multi clusters', function () {

      let appList = [{
        clusters: [
          {
            name: 'mahe-prod'
          },
          {
            name: 'mahe-prestaging'
          },
        ]
      }];

      let result = service.getClusters(appList);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['mahe-prestaging', 'mahe-prod']);
    });

    it('should get a list of 2 clusters for two application w/ one cluster each', function () {

      let appList = [
        {
          clusters: [
            {
              name: 'deck-main'
            },
          ]
        },
        {
          clusters: [
            {
              name: 'gate-main',
            },
          ]
        },
      ];

      let result = service.getClusters(appList);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['deck-main', 'gate-main']);
    });

    it('should get a list of 1 cluster with a filter on the cluster name', function () {

      let appList = [{
        clusters: [
          {
            name: 'deck-main',
          },
          {
            name: 'gate-main',
          },
        ]
      }];

      let filterByGate = (cluster) => cluster.name === 'gate-main';
      let result = service.getClusters(appList, filterByGate);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['gate-main']);
    });
  });

  describe('Get ASGs from a list of applications', function() {
    it('should get a empty list for one application w/ no clusters', function () {
      let appList = [{}];

      let result = service.getAsgs(appList);
      expect(result.length).toEqual(0);
      expect(result).toEqual([]);
    });

    it('should get a list of 1 ASG for one application w/ one cluster', function () {

      let appList = [{
        clusters: [
          {
            serverGroups: [
              {name: 'mahe-main-v000'}
            ]
          },
        ]
      }];

      let result = service.getAsgs(appList);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['mahe-main-v000']);
    });

    it('should get a list of 2 ASG names for one application w/ multi clusters', function () {

      let appList = [{
        clusters: [
          {
            name: 'mahe-prod',
            serverGroups: [
              {name: 'mahe-main-v000'}
            ]
          },
          {
            name: 'mahe-prestaging',
            serverGroups: [
              {name: 'mahe-prestaging-v002'}
            ]
          },
        ]
      }];

      let result = service.getAsgs(appList);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['mahe-prestaging-v002', 'mahe-main-v000' ]);
    });

    it('should get a list of 2 ASGs for two application w/ one cluster each', function () {

      let appList = [
        {
          clusters: [
            {
              serverGroups: [
                {name: 'mahe-prestaging-v002'}
              ]
            },
          ]
        },
        {
          clusters: [
            {
              serverGroups: [
                {name: 'deck-main-v002'}
              ]
            },
          ]
        },
      ];

      let result = service.getAsgs(appList);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['mahe-prestaging-v002', 'deck-main-v002']);
    });

    it('should get a list of 1 cluster with a filter on the cluster name', function () {

      let appList = [{
        clusters: [
          {
            name: 'deck-main',
            serverGroups: [
              {name: 'deck-main-v001'}
            ]
          },
          {
            name: 'gate-main',
            serverGroups: [
              {name: 'gate-main-v002'}
            ]
          },
        ]
      }];

      let filterByGate = (cluster) => cluster.name === 'gate-main';
      let result = service.getAsgs(appList, filterByGate);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['gate-main-v002']);
    });
  });



  describe('Get Zones from a list of applications', function() {
    it('should get a empty list for one application w/ no clusters', function () {
      let appList = [{}];

      let result = service.getZones(appList);
      expect(result.length).toEqual(0);
      expect(result).toEqual([]);
    });

    it('should get a list of 1 zone for one application w/ one cluster', function () {

      let appList = [{
        clusters: [
          {
            serverGroups: [
              {
                name: 'mahe-main-v000',
                instances: [
                  {
                    availabilityZone: 'us-west-2a'
                  }
                ]
              }
            ]
          },
        ]
      }];

      let result = service.getZones(appList);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['us-west-2a']);
    });

    it('should get a list of 2 Zones names for one application w/ multi clusters', function () {

      let appList = [{
        clusters: [
          {
            name: 'mahe-prod',
            serverGroups: [
              {
                name: 'mahe-main-v000',
                instances: [
                  {
                    availabilityZone: 'us-west-2a'
                  }
                ]
              }
            ]
          },
          {
            name: 'mahe-prestaging',
            serverGroups: [
              {
                name: 'mahe-prestaging-v002',
                instances: [
                  {
                    availabilityZone: 'us-west-2d'
                  }
                ]
              }
            ]
          },
        ]
      }];

      let result = service.getZones(appList);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['us-west-2a', 'us-west-2d' ]);
    });

    it('should get a list of 2 Zones for two application w/ one cluster each', function () {

      let appList = [
        {
          clusters: [
            {
              serverGroups: [
                {
                  name: 'mahe-prestaging-v002',
                  instances: [
                    {
                      availabilityZone: 'us-west-2a'
                    }
                  ]
                }
              ]
            },
          ]
        },
        {
          clusters: [
            {
              serverGroups: [
                {
                  name: 'deck-main-v002',
                  instances: [
                    {
                      availabilityZone: 'us-west-2d'
                    }
                  ]
                }
              ]
            },
          ]
        },
      ];

      let result = service.getZones(appList);
      expect(result.length).toEqual(2);
      expect(result).toEqual(['us-west-2a', 'us-west-2d']);
    });

    it('should get a list of 1 cluster with a filter on the cluster name and region', function () {

      let appList = [{
        clusters: [
          {
            name: 'deck-main',
            serverGroups: [
              {
                name: 'deck-main-v001',
                instances: [
                  {
                    availabilityZone: 'us-west-2a'
                  }
                ]
              }
            ]
          },
          {
            name: 'gate-main',
            serverGroups: [
              {
                name: 'gate-main-v002',
                region: 'us-west-2',
                instances: [
                  {
                    availabilityZone: 'us-west-2d'
                  }
                ]
              },

              {
                name: 'gate-main-v003',
                region: 'us-east-1',
                instances: [
                  {
                    availabilityZone: 'us-east-1d'
                  }
                ]
              }
            ]
          },
        ]
      }];

      let filterByGate = (cluster) => cluster.name === 'gate-main';
      let filterByRegion = (serverGroup) => serverGroup.region === 'us-east-1';
      let result = service.getZones(appList, filterByGate, filterByRegion);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['us-east-1d']);
    });


    it('should get a list of 1 cluster with a filter on the cluster name and region and serverGroupName', function () {
      let appList = [{
        clusters: [
          {
            name: 'deck-main',
            serverGroups: [
              {
                name: 'deck-main-v001',
                instances: [
                  {
                    availabilityZone: 'us-west-2a'
                  }
                ]
              }
            ]
          },
          {
            name: 'gate-main',
            serverGroups: [
              {
                name: 'gate-main-v002',
                region: 'us-west-2',
                instances: [
                  {
                    availabilityZone: 'us-west-2d'
                  }
                ]
              },

              {
                name: 'gate-main-v003',
                region: 'us-east-1',
                instances: [
                  {
                    availabilityZone: 'us-east-1d'
                  }
                ]
              },
              {
                name: 'gate-main-v004',
                region: 'eu-west-1',
                instances: [
                  {
                    availabilityZone: 'eu-west-1b'
                  }
                ]
              }
            ]
          },
        ]
      }];

      let filterByGate = (cluster) => cluster.name === 'gate-main';
      let filterByRegion = (serverGroup) => serverGroup.region === 'eu-west-1';
      let filterByServerGroupName = (serverGroup) => serverGroup.name === 'gate-main-v004';
      let result = service.getZones(appList, filterByGate, filterByRegion, filterByServerGroupName);
      expect(result.length).toEqual(1);
      expect(result).toEqual(['eu-west-1b']);
    });
  });

  describe('Get Instances from a list of applications', function() {
    it('should get a empty list for one application w/ no clusters', function () {
      let appList = [{}];

      let result = service.getInstances(appList);
      expect(result.length).toEqual(0);
      expect(result).toEqual([]);
    });

    it('should get a list of 1 instance for one application w/ one cluster with one instance', function () {

      let appList = [{
        clusters: [
          {
            serverGroups: [
              {
                name: 'mahe-main-v000',
                instances: [
                  {
                    availabilityZone: 'us-west-2a',
                    id: 'i-1234'
                  }
                ]
              }
            ]
          },
        ]
      }];

      let result = service.getInstances(appList);
      expect(result.length).toEqual(1);
      expect(result[0].id).toEqual('i-1234');
    });

    it('should get a list of 2 instances names for one application w/ multi clusters that have 1 instance each', function () {

      let appList = [{
        clusters: [
          {
            name: 'mahe-prod',
            serverGroups: [
              {
                name: 'mahe-main-v000',
                instances: [
                  {
                    availabilityZone: 'us-west-2a',
                    id: 'i-1234'
                  }
                ]
              }
            ]
          },
          {
            name: 'mahe-prestaging',
            serverGroups: [
              {
                name: 'mahe-prestaging-v002',
                instances: [
                  {
                    availabilityZone: 'us-west-2d',
                    id: 'i-4321'
                  }
                ]
              }
            ]
          },
        ]
      }];

      let result = service.getInstances(appList);
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

      let appList = [
        {
          clusters: [
            {
              serverGroups: [
                {
                  name: 'mahe-prestaging-v002',
                  instances: [
                    {
                      availabilityZone: 'us-west-2a',
                      id: 'i-4321'
                    }
                  ]
                }
              ]
            },
          ]
        },
        {
          clusters: [
            {
              serverGroups: [
                {
                  name: 'deck-main-v002',
                  instances: [
                    {
                      availabilityZone: 'us-west-2d',
                      id: 'i-1111'
                    }
                  ]
                }
              ]
            },
          ]
        },
      ];

      let result = service.getInstances(appList);
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

      let appList = [{
        clusters: [
          {
            name: 'deck-main',
            serverGroups: [
              {
                name: 'deck-main-v001',
                instances: [
                  {
                    availabilityZone: 'us-west-2a',
                    id: '1-1234'
                  }
                ]
              }
            ]
          },
          {
            name: 'gate-main',
            serverGroups: [
              {
                name: 'gate-main-v002',
                region: 'us-west-2',
                instances: [
                  {
                    availabilityZone: 'us-west-2d',
                    id: 'i-3949'
                  }
                ]
              },

              {
                name: 'gate-main-v003',
                region: 'us-east-1',
                instances: [
                  {
                    availabilityZone: 'us-east-1d',
                    id: 'i-3333'
                  }
                ]
              }
            ]
          },
        ]
      }];

      let filterByGate = (cluster) => cluster.name === 'gate-main';
      let filterByRegion = (serverGroup) => serverGroup.region === 'us-east-1';
      let result = service.getInstances(appList, filterByGate, filterByRegion);
      expect(result.length).toEqual(1);
      expect(result).toEqual([
        {
          availabilityZone: 'us-east-1d',
          id: 'i-3333'
        }
      ]);
    });


    it('should get a list of 1 instance with a filter on the cluster name and region and availablityZone', function () {
      let appList = [{
        clusters: [
          {
            name: 'deck-main',
            serverGroups: [
              {
                name: 'deck-main-v001',
                instances: [
                  {
                    availabilityZone: 'us-west-2a'
                  }
                ]
              }
            ]
          },
          {
            name: 'gate-main',
            serverGroups: [
              {
                name: 'gate-main-v002',
                region: 'us-west-2',
                instances: [
                  {
                    availabilityZone: 'us-west-2d'
                  }
                ]
              },

              {
                name: 'gate-main-v003',
                region: 'us-east-1',
                instances: [
                  {
                    availabilityZone: 'us-east-1d'
                  }
                ]
              },
              {
                name: 'gate-main-v004',
                region: 'eu-west-1',
                instances: [
                  {
                    availabilityZone: 'eu-west-1b',
                    id: 'i-12344'
                  }
                ]
              }
            ]
          },
        ]
      }];

      let filterByGate = (cluster) => cluster.name === 'gate-main';
      let filterByRegion = (serverGroup) => serverGroup.region === 'eu-west-1';
      let filterByAvailabilityZone = (instance) => instance.availabilityZone === 'eu-west-1b';
      let result = service.getInstances(appList, filterByGate, filterByRegion, filterByAvailabilityZone);
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


