'use strict';

describe('Service: diffService ', function () {

  var $rootScope, diffService;

  beforeEach(
      window.module(
          require('./diff.service')
      )
  );

  beforeEach(
      window.inject(function (_$rootScope_, _diffService_) {
        $rootScope = _$rootScope_;
        diffService = _diffService_;
      })
  );

  var justOneDiff = {
    "allIdentifiers": [
      {
        "location": {"account": "test", "region": "us-east-1"},
        "identity": {"autoScalingGroupName": "helloclay--test-v003"}
      }
    ],
    "attributeGroups": [
      {
        "identifiers": [
          {
            "location": {"account": "test", "region": "us-east-1"},
            "identity": {"autoScalingGroupName": "helloclay--test-v003"}
          }
        ],
        "commonAttributes": {
          "ebsOptimized": false,
          "terminationPolicies": ["Default"],
          "healthCheckGracePeriod": 0,
          "healthCheckType": "EC2",
          "iamInstanceProfile": "role",
          "instanceType": "m3.medium",
          "defaultCooldown": 0,
          "loadBalancerNames": ["helloworld--frontend"],
          "associatePublicIpAddress": null,
          "suspendedProcesses": [],
          "imageId": "ami-123",
          "minSize": 0,
          "desiredCapacity": 0,
          "maxSize": 9,
          "keyName": "test-keypair",
          "instanceMonitoring": true,
          "availabilityZones": ["us-east-1a", "us-east-1c"],
          "securityGroups": ["helloworld", "default"]
        }
      }
    ]
  };

  var severalSecurityGroupPermutationsDiff = {
    "allIdentifiers": [
      {"location":{"account":"test", "region":"us-east-1"}, "identity":{"autoScalingGroupName":"helloclay--test-v003"}},
      {"location":{"account":"test", "region":"us-west-1"}, "identity":{"autoScalingGroupName":"helloclay--test-v003"}},
      {"location":{"account":"test", "region":"us-west-1"}, "identity":{"autoScalingGroupName":"helloclay--test-v004"}},
      {"location":{"account":"test", "region":"us-west-2"}, "identity":{"autoScalingGroupName":"helloclay--test-v010"}},
    ],
    "attributeGroups": [
      {
        "identifiers": [
          {"location":{"account":"test", "region":"us-east-1"}, "identity":{"autoScalingGroupName":"helloclay--test-v003"}},
          {"location":{"account":"test", "region":"us-west-1"}, "identity":{"autoScalingGroupName":"helloclay--test-v003"}},
          {"location":{"account":"test", "region":"us-west-1"}, "identity":{"autoScalingGroupName":"helloclay--test-v004"}},
          {"location":{"account":"test", "region":"us-west-2"}, "identity":{"autoScalingGroupName":"helloclay--test-v010"}},
        ],
        "commonAttributes": {
          "ebsOptimized":false,
          "terminationPolicies":["Default"],
          "healthCheckGracePeriod":0,
          "healthCheckType":"EC2",
          "iamInstanceProfile":"role",
          "instanceType":"m3.medium",
          "defaultCooldown":0,
          "loadBalancerNames":["helloworld--frontend"],
          "associatePublicIpAddress":null,
          "suspendedProcesses":[],
          "imageId":"ami-123",
          "minSize":0,
          "maxSize":9,
          "keyName":"test-keypair",
          "instanceMonitoring":true
        }
      },
      {
        "identifiers": [
          {"location":{"account":"test", "region":"us-east-1"}, "identity":{"autoScalingGroupName":"helloclay--test-v003"}},
          {"location":{"account":"test", "region":"us-west-1"}, "identity":{"autoScalingGroupName":"helloclay--test-v003"}},
          {"location":{"account":"test", "region":"us-west-2"}, "identity":{"autoScalingGroupName":"helloclay--test-v010"}},
        ],
        "commonAttributes": {
          "desiredCapacity":0
        }
      },
      {
        "identifiers": [
          {"location":{"account":"test", "region":"us-west-1"}, "identity":{"autoScalingGroupName":"helloclay--test-v003"}},
          {"location":{"account":"test", "region":"us-west-2"}, "identity":{"autoScalingGroupName":"helloclay--test-v010"}},
        ],
        "commonAttributes": {
          "availabilityZones":["us-west-1a", "us-west-1c"],
          "securityGroups":["helloworld", "default"]
        }
      },
      {
        "identifiers": [
          {"location":{"account":"test", "region":"us-west-1"}, "identity":{"autoScalingGroupName":"helloclay--test-v005"}},
        ],
        "commonAttributes": {
          "desiredCapacity":1,
          "securityGroups":["helloworld"]
        }
      },
      {
        "identifiers": [
          {"location":{"account":"test", "region":"us-east-1"}, "identity":{"autoScalingGroupName":"helloclay--test-v003"}},
        ],
        "commonAttributes": {
          "availabilityZones":["us-east-1a", "us-east-1b"],
          "securityGroups":[]
        }
      },
      {
        "identifiers": [
          {"location":{"account":"test", "region":"us-west-2"}, "identity":{"autoScalingGroupName":"helloclay--test-v010"}},
        ],
        "commonAttributes": {
          "availabilityZones":["us-west-2a", "us-west-2b"]
        }
      }
    ]
  };

  describe('should filter the list of ASGs to those with security groups that do not match', function () {

    it('should return empty result for empty diff', function () {
      var securityGroups = ["helloworld", "default"];

      var result = diffService.diffSecurityGroups(securityGroups, {});

      expect(result).toEqual([]);
    });

    it('should return empty result for undefined diff', function () {
      var securityGroups = ["helloworld", "default"];

      var result = diffService.diffSecurityGroups(securityGroups, undefined);

      expect(result).toEqual([]);
    });

    it('should return empty result when all security groups match', function () {
      var securityGroups = ["helloworld", "default"];

      var result = diffService.diffSecurityGroups(securityGroups, justOneDiff);

      expect(result).toEqual([]);
    });

    it('should return info for security groups that do not match', function () {
      var securityGroups = ["helloworld"];

      var result = diffService.diffSecurityGroups(securityGroups, justOneDiff);

      expect(result).toEqual([
        {
          "commonSecurityGroups": ["default", "helloworld"],
          "serverGroups": [
            {"account": "test", "region": "us-east-1", "autoScalingGroupName": "helloclay--test-v003"}
          ]
        }
      ]);
    });

    it('should compare empty securityGroups when unmatched', function () {
      var securityGroups = [];

      var result = diffService.diffSecurityGroups(securityGroups, justOneDiff);

      expect(result).toEqual([
        {
          "commonSecurityGroups": ["default", "helloworld"],
          "serverGroups": [
            {"account": "test", "region": "us-east-1", "autoScalingGroupName": "helloclay--test-v003"}
          ]
        }
      ]);
    });


    it('should filter multi diff for matching security groups', function () {
      var securityGroups = ["helloworld", "default"];

      var result = diffService.diffSecurityGroups(securityGroups, severalSecurityGroupPermutationsDiff);

      expect(result).toEqual([
        {
          "commonSecurityGroups": ["helloworld"],
          "serverGroups": [
            {"account": "test", "region": "us-west-1", "autoScalingGroupName": "helloclay--test-v005"}
          ]
        },
        {
          "commonSecurityGroups": [],
          "serverGroups": [
            {"account": "test", "region": "us-east-1", "autoScalingGroupName": "helloclay--test-v003"}
          ]
        }
      ]);
    });

    it('should filter multi diff for matching security groups in any order', function () {
      var securityGroups = ["default", "helloworld"];

      var result = diffService.diffSecurityGroups(securityGroups, severalSecurityGroupPermutationsDiff);

      expect(result).toEqual([
        {
          "commonSecurityGroups": ["helloworld"],
          "serverGroups": [
            {"account": "test", "region": "us-west-1", "autoScalingGroupName": "helloclay--test-v005"}
          ]
        },
        {
          "commonSecurityGroups": [],
          "serverGroups": [
            {"account": "test", "region": "us-east-1", "autoScalingGroupName": "helloclay--test-v003"}
          ]
        }
      ]);
    });

    it('should filter multi diff for partially matching security groups', function () {
      var securityGroups = ["helloworld"];

      var result = diffService.diffSecurityGroups(securityGroups, severalSecurityGroupPermutationsDiff);

      expect(result).toEqual([
        {
          "commonSecurityGroups": ["default", "helloworld"],
          "serverGroups": [
            {"account": "test", "region": "us-west-1", "autoScalingGroupName": "helloclay--test-v003"},
            {"account": "test", "region": "us-west-2", "autoScalingGroupName": "helloclay--test-v010"}
          ]
        },
        {
          "commonSecurityGroups": [],
          "serverGroups": [
            {"account": "test", "region": "us-east-1", "autoScalingGroupName": "helloclay--test-v003"}
          ]
        }
      ]);
    });

    it('should filter multi diff for non matching security groups', function () {
      var securityGroups = ["wasupEarth"];

      var result = diffService.diffSecurityGroups(securityGroups, severalSecurityGroupPermutationsDiff);

      expect(result).toEqual([
        {
          "commonSecurityGroups": ["default", "helloworld"],
          "serverGroups": [
            {"account": "test", "region": "us-west-1", "autoScalingGroupName": "helloclay--test-v003"},
            {"account": "test", "region": "us-west-2", "autoScalingGroupName": "helloclay--test-v010"}
          ]
        },
        {
          "commonSecurityGroups": ["helloworld"],
          "serverGroups": [
            {"account": "test", "region": "us-west-1", "autoScalingGroupName": "helloclay--test-v005"}
          ]
        },
        {
          "commonSecurityGroups": [],
          "serverGroups": [
            {"account": "test", "region": "us-east-1", "autoScalingGroupName": "helloclay--test-v003"}
          ]
        }
      ]);
    });

    it('should filter multi diff for empty security groups', function () {
      var securityGroups = [];

      var result = diffService.diffSecurityGroups(securityGroups, severalSecurityGroupPermutationsDiff);

      expect(result).toEqual([
        {
          "commonSecurityGroups": ["default", "helloworld"],
          "serverGroups": [
            {"account": "test", "region": "us-west-1", "autoScalingGroupName": "helloclay--test-v003"},
            {"account": "test", "region": "us-west-2", "autoScalingGroupName": "helloclay--test-v010"}
          ]
        },
        {
          "commonSecurityGroups": ["helloworld"],
          "serverGroups": [
            {"account": "test", "region": "us-west-1", "autoScalingGroupName": "helloclay--test-v005"}
          ]
        }
      ]);
    });

    it('should not remove source from server group list if not found', function () {
      var securityGroups = [];
      var source = {
        account: 'test',
        region: 'us-west-1',
        asgName: 'helloclay--test-v010',
      };

      var result = diffService.diffSecurityGroups(securityGroups, severalSecurityGroupPermutationsDiff, source);

      expect(result).toEqual([
        {
          "commonSecurityGroups": ["default", "helloworld"],
          "serverGroups": [
            {"account": "test", "region": "us-west-1", "autoScalingGroupName": "helloclay--test-v003"},
            {"account": "test", "region": "us-west-2", "autoScalingGroupName": "helloclay--test-v010"}
          ]
        },
        {
          "commonSecurityGroups": ["helloworld"],
          "serverGroups": [
            {"account": "test", "region": "us-west-1", "autoScalingGroupName": "helloclay--test-v005"}
          ]
        }
      ]);
    });

    it('should remove source from server group list if found', function () {
      var securityGroups = [];
      var source = {
        account: 'test',
        region: 'us-west-1',
        asgName: 'helloclay--test-v003',
      };

      var result = diffService.diffSecurityGroups(securityGroups, severalSecurityGroupPermutationsDiff, source);

      expect(result).toEqual([
        {
          "commonSecurityGroups": ["default", "helloworld"],
          "serverGroups": [
            {"account": "test", "region": "us-west-2", "autoScalingGroupName": "helloclay--test-v010"}
          ]
        },
        {
          "commonSecurityGroups": ["helloworld"],
          "serverGroups": [
            {"account": "test", "region": "us-west-1", "autoScalingGroupName": "helloclay--test-v005"}
          ]
        }
      ]);
    });

    it('should remove attribute group if source is only server group in list', function () {
      var securityGroups = [];
      var source = {
        account: 'test',
        region: 'us-west-1',
        asgName: 'helloclay--test-v005',
      };

      var result = diffService.diffSecurityGroups(securityGroups, severalSecurityGroupPermutationsDiff, source);

      expect(result).toEqual([
        {
          "commonSecurityGroups": ["default", "helloworld"],
          "serverGroups": [
            {"account": "test", "region": "us-west-1", "autoScalingGroupName": "helloclay--test-v003"},
            {"account": "test", "region": "us-west-2", "autoScalingGroupName": "helloclay--test-v010"}
          ]
        }
      ]);
    });

  });

});


