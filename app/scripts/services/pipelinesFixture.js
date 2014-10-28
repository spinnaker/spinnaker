'use strict';

angular.module('deckApp')
  .constant('pipelinesFixture', [{
    'id': '1',
    'name': 'Deploy To Main',
    'application': 'gate',
    'status': 'STARTED',
    'startTime': 1414434835755,
    'endTime': 1414435451418,
    'stages': [{
        'name': 'init',
        'status': 'COMPLETED',
        'context': {},
        'startTime': 1414434835755,
        'endTime': 1414434835793,
        'steps': [{
            'name': 'orca-init-step',
            'status': 'COMPLETED',
            'startTime': 1414434835755,
            'endTime': 1414434835773
        }, {
            'name': 'orca-init-step-2',
            'status': 'COMPLETED',
            'startTime': 1414434835785,
            'endTime': 1414434835793
        }]
    }, {
        'name': 'bake',
        'status': 'COMPLETED',
        'context': {
            'region': 'us-west-1',
            'user': 'orca',
            'package': 'gate',
            'baseOs': 'ubuntu',
            'baseLabel': 'release',
            'status': {
                'id': 's-37mfk0f19382rby7pkvedz8ppj',
                'state': 'COMPLETED',
                'resourceId': 'b-5zgcwpzw6p8ejbjwvj7y8ha85d'
            },
            'ami': 'ami-f90216bc'
        },
        'startTime': 1414434835806,
        'endTime': 1414434837552,
        'steps': [{
            'name': 'createBake',
            'status': 'COMPLETED',
            'startTime': 1414434835806,
            'endTime': 1414434837339
        }, {
            'name': 'monitorBake',
            'status': 'COMPLETED',
            'startTime': 1414434837349,
            'endTime': 1414434837507
        }, {
            'name': 'completedBake',
            'status': 'COMPLETED',
            'startTime': 1414434837518,
            'endTime': 1414434837552
        }]
    }, {
        'name': 'deploy',
        'status': 'RUNNING',
        'context': {
            'cluster': {
                'strategy': 'redblack',
                'application': 'gate',
                'stack': 'main',
                'instanceType': 'm3.medium',
                'securityGroups': [
                    'nf-infrastructure-vpc',
                    'nf-datacenter-vpc',
                    'nf-infrastructure-vpc',
                    'nf-datacenter-vpc'
                ],
                'subnetType': 'internal',
                'availabilityZones': {
                    'us-west-1': []
                },
                'capacity': {
                    'min': 1,
                    'max': 1,
                    'desired': 1
                },
                'loadBalancers': [
                    'gate-main-frontend'
                ]
            },
            'account': 'prod',
            'disableAsg': {
                'asgName': 'gate-main-v002',
                'regions': [
                    'us-east-1'
                ],
                'credentials': 'prod'
            },
            'notification.type': 'createdeploy',
            'kato.last.task.id': {
                'id': '2453'
            },
            'kato.task.id': {
                'id': '2453'
            },
            'deploy.account.name': 'prod',
            'deploy.server.groups': {
                'us-west-1': [
                    'gate-main-v000'
                ]
            },
            'kato.tasks': [{
                'id': '2453',
                'status': {
                    'completed': true,
                    'failed': false
                },
                'history': [{
                    'phase': 'ORCHESTRATION',
                    'status': 'Initializing Orchestration Task...'
                }, {
                    'phase': 'ORCHESTRATION',
                    'status': 'Processing op: AllowLaunchAtomicOperation'
                }, {
                    'phase': 'ALLOW_LAUNCH',
                    'status': 'Initializing Allow Launch Operation...'
                }, {
                    'phase': 'ALLOW_LAUNCH',
                    'status': 'Allowing launch of ami-f90216bc from prod'
                }, {
                    'phase': 'ALLOW_LAUNCH',
                    'status': 'Creating tags on target AMI (creation_time: 2014-10-25 02:16:13 UTC, base_ami_version: nflx-base-1.312-h265.c574454, creator: orca, build_host: http://builds.netflix.com/, appversion: gatekeeper-app-59.2-h9.2a82c33/DSC-beehive-suite-gatekeeper-test-build/9).'
                }, {
                    'phase': 'ALLOW_LAUNCH',
                    'status': 'Done allowing launch of ami-xxx from prod.'
                }, {
                    'phase': 'ORCHESTRATION',
                    'status': 'Orchestration completed.'
                }, {
                    'phase': 'ORCHESTRATION',
                    'status': 'Processing op: DeployAtomicOperation'
                }, {
                    'phase': 'DEPLOY',
                    'status': 'Initializing phase.'
                }, {
                    'phase': 'DEPLOY',
                    'status': 'Looking for BasicAmazonDeployDescription handler...'
                }, {
                    'phase': 'DEPLOY',
                    'status': 'Found handler: BasicAmazonDeployHandler'
                }, {
                    'phase': 'DEPLOY',
                    'status': 'Invoking Handler.'
                }, {
                    'phase': 'DEPLOY',
                    'status': 'Initializing handler...'
                }, {
                    'phase': 'DEPLOY',
                    'status': 'Preparing deployment to [us-west-1:[]]...'
                }, {
                    'phase': 'AWS_DEPLOY',
                    'status': 'Beginning Amazon deployment.'
                }, {
                    'phase': 'AWS_DEPLOY',
                    'status': 'Checking for security package.'
                }, {
                    'phase': 'AWS_DEPLOY',
                    'status': 'Looking up security groups...'
                }, {
                    'phase': 'AWS_DEPLOY',
                    'status': 'Beginning ASG deployment.'
                }, {
                    'phase': 'AWS_DEPLOY',
                    'status': 'Building launch configuration for new ASG.'
                }, {
                    'phase': 'AWS_DEPLOY',
                    'status': 'Deploying ASG.'
                }, {
                    'phase': 'AWS_DEPLOY',
                    'status': ' > Deploying to subnetIds: subnet-xxx,subnet-xxx'
                }, {
                    'phase': 'DEPLOY',
                    'status': 'Server Groups: [us-west-1:gate-main-v000] created.'
                }, {
                    'phase': 'ORCHESTRATION',
                    'status': 'Orchestration completed.'
                }, {
                    'phase': 'ORCHESTRATION',
                    'status': 'Orchestration completed.'
                }]
            }]
        },
        'startTime': 1414434837564,
        'endTime': null,
        'steps': [{
            'name': 'createDeploy',
            'status': 'COMPLETED',
            'startTime': 1414434837564,
            'endTime': 1414434838199
        }, {
            'name': 'monitorDeploy',
            'status': 'COMPLETED',
            'startTime': 1414434838212,
            'endTime': 1414434850280
        }, {
            'name': 'forceCacheRefresh',
            'status': 'COMPLETED',
            'startTime': 1414434850301,
            'endTime': 1414434850310
        }, {
            'name': 'waitForUpInstances',
            'status': 'FAILED',
            'startTime': 1414434850332,
            'endTime': 1414435451418
        }]
    }]
},{
    'id': '2',
    'name': 'Deploy To Staging',
    'application': 'gate',
    'status': 'COMPLETED',
    'startTime': 1214434835755,
    'endTime': 1214435451418,
    'stages': [{
        'name': 'init',
        'status': 'COMPLETED',
        'context': {},
        'startTime': 1414434835755,
        'endTime': 1414434835793,
        'steps': [{
            'name': 'orca-init-step',
            'status': 'COMPLETED',
            'startTime': 1414434835755,
            'endTime': 1414434835773
        }, {
            'name': 'orca-init-step-2',
            'status': 'COMPLETED',
            'startTime': 1414434835785,
            'endTime': 1414434835793
        }]
    }, {
        'name': 'bake',
        'status': 'COMPLETED',
        'context': {
            'region': 'us-west-1',
            'user': 'orca',
            'package': 'gate',
            'baseOs': 'ubuntu',
            'baseLabel': 'release',
            'status': {
                'id': 's-37mfk0f19382rby7pkvedz8ppj',
                'state': 'COMPLETED',
                'resourceId': 'b-5zgcwpzw6p8ejbjwvj7y8ha85d'
            },
            'ami': 'ami-f90216bc'
        },
        'startTime': 1414434835806,
        'endTime': 1414434837552,
        'steps': [{
            'name': 'createBake',
            'status': 'COMPLETED',
            'startTime': 1414434835806,
            'endTime': 1414434837339
        }, {
            'name': 'monitorBake',
            'status': 'COMPLETED',
            'startTime': 1414434837349,
            'endTime': 1414434837507
        }, {
            'name': 'completedBake',
            'status': 'COMPLETED',
            'startTime': 1414434837518,
            'endTime': 1414434837552
        }]
    }, {
        'name': 'deploy',
        'status': 'RUNNING',
        'context': {
            'cluster': {
                'strategy': 'redblack',
                'application': 'gate',
                'stack': 'main',
                'instanceType': 'm3.medium',
                'securityGroups': [
                    'nf-infrastructure-vpc',
                    'nf-datacenter-vpc',
                    'nf-infrastructure-vpc',
                    'nf-datacenter-vpc'
                ],
                'subnetType': 'internal',
                'availabilityZones': {
                    'us-west-1': []
                },
                'capacity': {
                    'min': 1,
                    'max': 1,
                    'desired': 1
                },
                'loadBalancers': [
                    'gate-main-frontend'
                ]
            },
            'account': 'prod',
            'disableAsg': {
                'asgName': 'gate-main-v002',
                'regions': [
                    'us-east-1'
                ],
                'credentials': 'prod'
            },
            'notification.type': 'createdeploy',
            'kato.last.task.id': {
                'id': '2453'
            },
            'kato.task.id': {
                'id': '2453'
            },
            'deploy.account.name': 'prod',
            'deploy.server.groups': {
                'us-west-1': [
                    'gate-main-v000'
                ]
            },
            'kato.tasks': [{
                'id': '2453',
                'status': {
                    'completed': true,
                    'failed': false
                },
                'history': [{
                    'phase': 'ORCHESTRATION',
                    'status': 'Initializing Orchestration Task...'
                }, {
                    'phase': 'ORCHESTRATION',
                    'status': 'Processing op: AllowLaunchAtomicOperation'
                }, {
                    'phase': 'ALLOW_LAUNCH',
                    'status': 'Initializing Allow Launch Operation...'
                }, {
                    'phase': 'ALLOW_LAUNCH',
                    'status': 'Allowing launch of ami-f90216bc from prod'
                }, {
                    'phase': 'ALLOW_LAUNCH',
                    'status': 'Creating tags on target AMI (creation_time: 2014-10-25 02:16:13 UTC, base_ami_version: nflx-base-1.312-h265.c574454, creator: orca, build_host: http://builds.netflix.com/, appversion: gatekeeper-app-59.2-h9.2a82c33/DSC-beehive-suite-gatekeeper-test-build/9).'
                }, {
                    'phase': 'ALLOW_LAUNCH',
                    'status': 'Done allowing launch of ami-xxx from prod.'
                }, {
                    'phase': 'ORCHESTRATION',
                    'status': 'Orchestration completed.'
                }, {
                    'phase': 'ORCHESTRATION',
                    'status': 'Processing op: DeployAtomicOperation'
                }, {
                    'phase': 'DEPLOY',
                    'status': 'Initializing phase.'
                }, {
                    'phase': 'DEPLOY',
                    'status': 'Looking for BasicAmazonDeployDescription handler...'
                }, {
                    'phase': 'DEPLOY',
                    'status': 'Found handler: BasicAmazonDeployHandler'
                }, {
                    'phase': 'DEPLOY',
                    'status': 'Invoking Handler.'
                }, {
                    'phase': 'DEPLOY',
                    'status': 'Initializing handler...'
                }, {
                    'phase': 'DEPLOY',
                    'status': 'Preparing deployment to [us-west-1:[]]...'
                }, {
                    'phase': 'AWS_DEPLOY',
                    'status': 'Beginning Amazon deployment.'
                }, {
                    'phase': 'AWS_DEPLOY',
                    'status': 'Checking for security package.'
                }, {
                    'phase': 'AWS_DEPLOY',
                    'status': 'Looking up security groups...'
                }, {
                    'phase': 'AWS_DEPLOY',
                    'status': 'Beginning ASG deployment.'
                }, {
                    'phase': 'AWS_DEPLOY',
                    'status': 'Building launch configuration for new ASG.'
                }, {
                    'phase': 'AWS_DEPLOY',
                    'status': 'Deploying ASG.'
                }, {
                    'phase': 'AWS_DEPLOY',
                    'status': ' > Deploying to subnetIds: subnet-xxx,subnet-xxx'
                }, {
                    'phase': 'DEPLOY',
                    'status': 'Server Groups: [us-west-1:gate-main-v000] created.'
                }, {
                    'phase': 'ORCHESTRATION',
                    'status': 'Orchestration completed.'
                }, {
                    'phase': 'ORCHESTRATION',
                    'status': 'Orchestration completed.'
                }]
            }]
        },
        'startTime': 1414434837564,
        'endTime': null,
        'steps': [{
            'name': 'createDeploy',
            'status': 'COMPLETED',
            'startTime': 1414434837564,
            'endTime': 1414434838199
        }, {
            'name': 'monitorDeploy',
            'status': 'COMPLETED',
            'startTime': 1414434838212,
            'endTime': 1414434850280
        }, {
            'name': 'forceCacheRefresh',
            'status': 'COMPLETED',
            'startTime': 1414434850301,
            'endTime': 1414434850310
        }, {
            'name': 'waitForUpInstances',
            'status': 'FAILED',
            'startTime': 1414434850332,
            'endTime': 1414435451418
        }]
    }]
},
{
    'id': '3',
    'name': 'Deploy To Main',
    'application': 'gate',
    'status': 'FAILED',
    'startTime': 1414984019849, 
    'endTime': 1414984019849,
    'stages': [{
        'name': 'init',
        'status': 'FAILED',
        'context': {},
        'startTime': 1414984019849, 
        'endTime': 1414984019849,
    }],
},
{
    'id': '4',
    'name': 'Deploy To Main',
    'application': 'gate',
    'status': 'FAILED',
    'startTime': 1414897619849, 
    'endTime': 1414984019849,
    'stages': [{
        'name': 'init',
        'status': 'FAILED',
        'context': {},
        'startTime': 1414897619849, 
        'endTime': 1414984019849,
    }],
},

  ]);
