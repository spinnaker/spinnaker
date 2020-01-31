package com.netflix.spinnaker.keel.clouddriver.model

import com.netflix.spinnaker.keel.api.Capacity
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.retrofit.model.ModelParsingTestSupport
import java.util.UUID.randomUUID
import kotlin.random.Random.Default.nextInt
import org.apache.commons.lang3.RandomStringUtils.random
import org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric
import org.apache.commons.lang3.RandomStringUtils.randomNumeric

object ActiveServerGroupTest : ModelParsingTestSupport<CloudDriverService, ActiveServerGroup>(CloudDriverService::class.java) {

  private val app = "fnord"
  private val seq = nextInt(0, 1000).toString().padStart(3, '0')
  private val region = "ap-south-1"
  private val instance = "i-${randomHex(12)}"
  private val subnets = (0..2).map { "subnet-${randomNumeric(8)}" }
  private val securityGroups = mapOf(
    app to "sg-${randomHex(16)}",
    "nf-infrastructure" to "sg-${randomHex(8)}",
    "nf-datacenter" to "sg-${randomHex(8)}"
  )
  private val ami = "ami-${randomHex(16)}"
  private val vpc = "vpc-${randomHex(8)}"
  private val ip = randomIp()
  private val owner = randomHex(12)
  private val launch = randomNumeric(12)

  fun randomHex(count: Int = 8): String = random(count, "0123456789abcdef")
  fun randomIp() = (0..3).map { nextInt(0, 1000) }.joinToString(".")

  override val json = """
    |{
    |  "name": "$app-v$seq",
    |  "region": "$region",
    |  "zones": [
    |    "${region}b",
    |    "${region}c",
    |    "${region}a"
    |  ],
    |  "instances": [
    |    {
    |      "name": "$instance",
    |      "launchTime": 1548881579000,
    |      "health": [
    |        {
    |          "type": "Amazon",
    |          "healthClass": "platform",
    |          "state": "Unknown"
    |        }
    |      ],
    |      "providerType": "aws",
    |      "cloudProvider": "aws",
    |      "healthState": "Unknown",
    |      "zone": "${region}c",
    |      "humanReadableName": "$instance",
    |      "labels": {},
    |      "subnetId": "${subnets.first()}",
    |      "virtualizationType": "hvm",
    |      "amiLaunchIndex": 0,
    |      "enaSupport": true,
    |      "elasticInferenceAcceleratorAssociations": [],
    |      "sourceDestCheck": true,
    |      "hibernationOptions": {
    |        "configured": false
    |      },
    |      "isHealthy": false,
    |      "instanceId": "$instance",
    |      "vpcId": "$vpc",
    |      "hypervisor": "xen",
    |      "rootDeviceName": "/dev/sda1",
    |      "productCodes": [],
    |      "state": {
    |        "code": 16,
    |        "name": "running"
    |      },
    |      "architecture": "x86_64",
    |      "ebsOptimized": false,
    |      "imageId": "$ami",
    |      "blockDeviceMappings": [
    |        {
    |          "deviceName": "/dev/sda1",
    |          "ebs": {
    |            "attachTime": 1548881580000,
    |            "deleteOnTermination": true,
    |            "status": "attached",
    |            "volumeId": "vol-${randomHex(16)}"
    |          }
    |        }
    |      ],
    |      "stateTransitionReason": "",
    |      "clientToken": "${randomUUID()}_${subnets.first()}_1",
    |      "instanceType": "t2.nano",
    |      "keyName": "nf-test-keypair-a",
    |      "cpuOptions": {
    |        "coreCount": 1,
    |        "threadsPerCore": 1
    |      },
    |      "monitoring": {
    |        "state": "disabled"
    |      },
    |      "publicDnsName": "",
    |      "iamInstanceProfile": {
    |        "arn": "arn:aws:iam::$owner:instance-profile/${app}InstanceProfile",
    |        "id": "${randomAlphanumeric(20)}"
    |      },
    |      "privateIpAddress": "$ip",
    |      "rootDeviceType": "ebs",
    |      "tags": [
    |        {
    |          "key": "spinnaker:application",
    |          "value": "$app"
    |        },
    |        {
    |          "key": "aws:autoscaling:groupName",
    |          "value": "$app-v$seq"
    |        }
    |      ],
    |      "elasticGpuAssociations": [],
    |      "licenses": [],
    |      "networkInterfaces": [
    |        {
    |          "attachment": {
    |            "attachTime": 1548881579000,
    |            "attachmentId": "eni-attach-${randomHex(16)}",
    |            "deleteOnTermination": true,
    |            "deviceIndex": 0,
    |            "status": "attached"
    |          },
    |          "description": "",
    |          "groups": [
    |            ${securityGroups.map { (name, id) -> """{"groupName": "$name", "groupId": "$id"}""" }.joinToString()}
    |          ],
    |          "ipv6Addresses": [],
    |          "macAddress": "0a:78:8f:f4:43:b0",
    |          "networkInterfaceId": "eni-${randomHex(16)}",
    |          "ownerId": "$owner",
    |          "privateDnsName": "ip-${ip.replace(".", "-")}.$region.compute.internal",
    |          "privateIpAddress": "$ip",
    |          "privateIpAddresses": [
    |            {
    |              "primary": true,
    |              "privateDnsName": "ip-${ip.replace(".", "-")}.$region.compute.internal",
    |              "privateIpAddress": "$ip"
    |            }
    |          ],
    |          "sourceDestCheck": true,
    |          "status": "in-use",
    |          "subnetId": "${subnets.first()}",
    |          "vpcId": "$vpc"
    |        }
    |      ],
    |      "privateDnsName": "ip-${ip.replace(".", "-")}.$region.compute.internal",
    |      "securityGroups": [
    |        ${securityGroups.map { (name, id) -> """{"groupName": "$name", "groupId": "$id"}""" }.joinToString()}
    |      ],
    |      "placement": {
    |        "availabilityZone": "${region}c",
    |        "groupName": "",
    |        "tenancy": "default"
    |      }
    |    }
    |  ],
    |  "image": {
    |    "virtualizationType": "hvm",
    |    "imageId": "$ami",
    |    "blockDeviceMappings": [
    |      {
    |        "deviceName": "/dev/sda1",
    |        "ebs": {
    |          "deleteOnTermination": true,
    |          "snapshotId": "snap-${randomHex(16)}",
    |          "volumeSize": 10,
    |          "volumeType": "standard",
    |          "encrypted": false
    |        }
    |      },
    |      {
    |        "deviceName": "/dev/sdb",
    |        "virtualName": "ephemeral0"
    |      },
    |      {
    |        "deviceName": "/dev/sdc",
    |        "virtualName": "ephemeral1"
    |      },
    |      {
    |        "deviceName": "/dev/sdd",
    |        "virtualName": "ephemeral2"
    |      },
    |      {
    |        "deviceName": "/dev/sde",
    |        "virtualName": "ephemeral3"
    |      }
    |    ],
    |    "description": "name=$app, arch=x86_64, ancestor_name=xenialbase-x86_64-201811142132-ebs, ancestor_id=ami-0fe383ecea7f75eac, ancestor_version=nflx-base-5.308.0-h1044.b4b3f78",
    |    "enaSupport": true,
    |    "creationDate": "2018-11-15T23:13:30.000Z",
    |    "ownerId": "$owner",
    |    "imageLocation": "$owner/$app-3.16.0-h205.121d4ac-x86_64-20181115184054-xenial-hvm-sriov-ebs-ebs-ebs",
    |    "rootDeviceType": "ebs",
    |    "tags": [
    |      {
    |        "key": "appversion",
    |        "value": "$app-3.16.0-h205.121d4ac/ZZ-WAPP-$app/205"
    |      },
    |      {
    |        "key": "creator",
    |        "value": "fzlem@netflix.com"
    |      },
    |      {
    |        "key": "base_ami_version",
    |        "value": "nflx-base-5.308.0-h1044.b4b3f78"
    |      },
    |      {
    |        "key": "creation_time",
    |        "value": "2018-11-15 23:13:31 UTC"
    |      },
    |      {
    |        "key": "build_host",
    |        "value": "https://my.jenkins.server/"
    |      },
    |      {
    |        "key": "stack",
    |        "value": "test"
    |      }
    |    ],
    |    "public": false,
    |    "sriovNetSupport": "simple",
    |    "hypervisor": "xen",
    |    "name": "$app-3.16.0-h205.121d4ac-x86_64-20181115184054-xenial-hvm-sriov-ebs-ebs-ebs",
    |    "rootDeviceName": "/dev/sda1",
    |    "productCodes": [],
    |    "state": "available",
    |    "imageType": "machine",
    |    "architecture": "x86_64"
    |  },
    |  "launchConfig": {
    |    "kernelId": "",
    |    "ramdiskId": "",
    |    "ebsOptimized": false,
    |    "imageId": "$ami",
    |    "userData": "${randomAlphanumeric(255)}==",
    |    "blockDeviceMappings": [],
    |    "classicLinkVPCSecurityGroups": [],
    |    "instanceType": "t2.nano",
    |    "keyName": "nf-test-keypair-a",
    |    "launchConfigurationARN": "arn:aws:autoscaling:$region:$owner:launchConfiguration:${randomUUID()}:launchConfigurationName/$app-$seq-$launch",
    |    "iamInstanceProfile": "${app}InstanceProfile",
    |    "launchConfigurationName": "$app-$seq-$launch",
    |    "createdTime": 1544656134371,
    |    "securityGroups": [
    |      ${securityGroups.values.joinToString(prefix = "\"", postfix = "\"")}
    |    ],
    |    "instanceMonitoring": {
    |      "enabled": false
    |    }
    |  },
    |  "asg": {
    |    "autoScalingGroupName": "$app-v$seq",
    |    "autoScalingGroupARN": "arn:aws:autoscaling:$region:$owner:autoScalingGroup:${randomUUID()}:autoScalingGroupName/$app-$seq",
    |    "launchConfigurationName": "$app-$seq-$launch",
    |    "minSize": 1,
    |    "maxSize": 1,
    |    "desiredCapacity": 1,
    |    "defaultCooldown": 10,
    |    "availabilityZones": [
    |      "${region}b",
    |      "${region}c",
    |      "${region}a"
    |    ],
    |    "loadBalancerNames": [],
    |    "targetGroupARNs": [],
    |    "healthCheckType": "EC2",
    |    "healthCheckGracePeriod": 600,
    |    "instances": [
    |      {
    |        "instanceId": "$instance",
    |        "availabilityZone": "${region}c",
    |        "lifecycleState": "InService",
    |        "healthStatus": "Healthy",
    |        "launchConfigurationName": "$app-$seq-$launch",
    |        "protectedFromScaleIn": false
    |      }
    |    ],
    |    "createdTime": 1544656135184,
    |    "suspendedProcesses": [],
    |    "enabledMetrics": [],
    |    "tags": [
    |      {
    |        "resourceId": "$app-v$seq",
    |        "resourceType": "auto-scaling-group",
    |        "key": "spinnaker:application",
    |        "value": "$app",
    |        "propagateAtLaunch": true
    |      }
    |    ],
    |    "terminationPolicies": [
    |      "Default"
    |    ],
    |    "newInstancesProtectedFromScaleIn": false,
    |    "serviceLinkedRoleARN": "arn:aws:iam::$owner:role/aws-service-role/autoscaling.amazonaws.com/AWSServiceRoleForAutoScaling",
    |    "vpczoneIdentifier": "${subnets.joinToString(",")}"
    |  },
    |  "scalingPolicies": [
    |   {
    |     "autoScalingGroupName": "$app-v$seq",
    |     "policyName": "$app-v$seq/ZZZ-RPS per instance-GreaterThanThreshold-560.0-3-60-1576009239950",
    |     "policyARN": "arn:aws:autoscaling:$region:$owner:scalingPolicy:8d1f1cc2-a28a-481e-b3b0-6e9c4cfc8712:autoScalingGroupName/$app-v$seq:policyName/$app-v$seq/ZZZ-RPS per instance-GreaterThanThreshold-560.0-3-60-1576009239950",
    |     "policyType": "TargetTrackingScaling",
    |     "stepAdjustments": [],
    |     "estimatedInstanceWarmup": 300,
    |     "alarms": [
    |       {
    |         "alarmName": "TargetTracking-$app-v$seq-AlarmHigh-3d97edfc-dc29-4497-9203-20e2c0859db6",
    |         "alarmArn": "arn:aws:cloudwatch:$region:$owner:alarm:TargetTracking-$app-v$seq-AlarmHigh-3d97edfc-dc29-4497-9203-20e2c0859db6",
    |         "alarmDescription": "DO NOT EDIT OR DELETE. For TargetTrackingScaling policy...",
    |         "actionsEnabled": true,
    |         "alarmActions": [
    |           "arn:aws:autoscaling:$region:$owner:scalingPolicy:8d1f1cc2-a28a-481e-b3b0-6e9c4cfc8712:autoScalingGroupName/$app-v$seq:policyName/$app-v$seq/ZZZ-RPS per instance-GreaterThanThreshold-560.0-3-60-1576009239950"
    |         ],
    |         "metricName": "RPS per instance",
    |         "namespace": "ZZZ/EPIC",
    |         "statistic": "Average",
    |         "dimensions": [
    |           {
    |             "name": "AutoScalingGroupName",
    |             "value": "$app-v$seq"
    |           }
    |         ],
    |         "period": 60,
    |         "evaluationPeriods": 3,
    |         "threshold": 560.0,
    |         "comparisonOperator": "GreaterThanThreshold",
    |         "metrics": [],
    |         "okactions": []
    |       }
    |     ],
    |     "targetTrackingConfiguration": {
    |       "customizedMetricSpecification": {
    |         "metricName": "RPS per instance",
    |         "namespace": "ZZZ/EPIC",
    |         "statistic": "Average",
    |         "dimensions": [
    |           {
    |             "name": "AutoScalingGroupName",
    |             "value": "$app-v$seq"
    |           }
    |         ]
    |       },
    |       "targetValue": 560.0,
    |       "disableScaleIn": true
    |     }
    |   }
    |  ],
    |  "scheduledActions": [],
    |  "buildInfo": {
    |    "package_name": "$app",
    |    "version": "3.16.0",
    |    "commit": "121d4ac",
    |    "jenkins": {
    |      "name": "ZZ-WAPP-$app",
    |      "number": "205",
    |      "host": "https://my.jenkins.server/"
    |    }
    |  },
    |  "vpcId": "$vpc",
    |  "type": "aws",
    |  "cloudProvider": "aws",
    |  "targetGroups": [],
    |  "createdTime": 1544656135184,
    |  "disabled": false,
    |  "loadBalancers": [],
    |  "capacity": {
    |    "min": 1,
    |    "max": 1,
    |    "desired": 1
    |  },
    |  "securityGroups": [
    |    ${securityGroups.values.joinToString { "\"$it\"" }}
    |  ],
    |  "instanceCounts": {
    |    "total": 1,
    |    "up": 0,
    |    "down": 0,
    |    "unknown": 1,
    |    "outOfService": 0,
    |    "starting": 0
    |  },
    |  "serverGroupManagers": [],
    |  "labels": {},
    |  "moniker": {
    |    "app": "$app",
    |    "cluster": "$app",
    |    "sequence": 0
    |  },
    |  "launchConfigName": "$app-$seq-$launch",
    |  "accountName": "test"
    |}
  """.trimMargin()

  override suspend fun CloudDriverService.call(): ActiveServerGroup? =
    this.activeServerGroup("keel@spinnaker", "keel", "mgmttest", "keel-test", "$region", "aws")

  override val expected = ActiveServerGroup(
    name = "$app-v$seq",
    cloudProvider = "aws",
    accountName = "test",
    targetGroups = emptySet(),
    region = "$region",
    zones = setOf("a", "b", "c").map { "$region$it" }.toSet(),
    image = ActiveServerGroupImage(
      imageId = ami,
      appVersion = "$app-3.16.0-h205.121d4ac",
      baseImageVersion = "nflx-base-5.308.0-h1044.b4b3f78"
    ),
    launchConfig = LaunchConfig(
      ramdiskId = "",
      ebsOptimized = false,
      imageId = ami,
      instanceType = "t2.nano",
      keyName = "nf-test-keypair-a",
      iamInstanceProfile = "${app}InstanceProfile",
      instanceMonitoring = InstanceMonitoring(false)
    ),
    asg = AutoScalingGroup(
      autoScalingGroupName = "$app-v$seq",
      defaultCooldown = 10,
      healthCheckType = "EC2",
      healthCheckGracePeriod = 600,
      suspendedProcesses = emptySet(),
      enabledMetrics = emptySet(),
      tags = setOf(Tag("spinnaker:application", app)),
      terminationPolicies = setOf("Default"),
      vpczoneIdentifier = subnets.joinToString(",")
    ),
    scalingPolicies = listOf(
      ScalingPolicy(
        autoScalingGroupName = "$app-v$seq",
        policyName = "$app-v$seq/ZZZ-RPS per instance-GreaterThanThreshold-560.0-3-60-1576009239950",
        policyType = "TargetTrackingScaling",
        stepAdjustments = emptyList(),
        estimatedInstanceWarmup = 300,
        targetTrackingConfiguration = TargetTrackingConfiguration(
          targetValue = 560.0,
          disableScaleIn = true,
          customizedMetricSpecification = CustomizedMetricSpecificationModel(
            metricName = "RPS per instance",
            namespace = "ZZZ/EPIC",
            statistic = "Average",
            dimensions = listOf(MetricDimensionModel(name = "AutoScalingGroupName", value = "$app-v$seq"))
          )
        ),
        alarms = listOf(ScalingPolicyAlarm(
          comparisonOperator = "GreaterThanThreshold",
          dimensions = listOf(MetricDimensionModel(name = "AutoScalingGroupName", value = "$app-v$seq")),
          evaluationPeriods = 3,
          period = 60,
          threshold = 560,
          metricName = "RPS per instance",
          namespace = "ZZZ/EPIC",
          statistic = "Average"))
      )
    ),
    vpcId = vpc,
    loadBalancers = emptySet(),
    capacity = Capacity(1, 1, 1),
    securityGroups = securityGroups.values.toSet(),
    moniker = Moniker(
      app = app,
      sequence = 0
    ),
    buildInfo = BuildInfo(packageName = "fnord")
  )
}
