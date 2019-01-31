package com.netflix.spinnaker.keel.clouddriver

import com.netflix.spinnaker.keel.clouddriver.model.AutoScalingGroup
import com.netflix.spinnaker.keel.clouddriver.model.BaseModelParsingTest
import com.netflix.spinnaker.keel.clouddriver.model.ClusterActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.InstanceMonitoring
import com.netflix.spinnaker.keel.clouddriver.model.LaunchConfig
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.ServerGroupCapacity
import com.netflix.spinnaker.keel.clouddriver.model.Tag

object ClusterActiveServerGroupTest : BaseModelParsingTest<ClusterActiveServerGroup>() {

  override val json = javaClass.getResource("/cluster.json")

  override val call: CloudDriverService.() -> ClusterActiveServerGroup? = {
    activeServerGroup("keel", "mgmttest", "keel-test", "eu-west-1", "aws")
  }

  override val expected = ClusterActiveServerGroup(
    name = "fletch_test-v000",
    accountName = "test",
    targetGroups = emptyList(),
    region = "eu-west-1",
    zones = listOf("eu-west-1b", "eu-west-1c", "eu-west-1a"),
    launchConfig = LaunchConfig(
      ramdiskId = "",
      ebsOptimized = false,
      imageId = "ami-05a878bfa321e03b4",
      userData = "TkVURkxJWF9BUFBfTUVUQURBVEE9ImFtaT1hbWktMDVhODc4YmZhMzIxZTAzYjQmYXNnPWZsZXRjaF90ZXN0LXYwMDAmdD0xNTQ0NjU2MTMzJnR5cGU9YXdzJnZlcnNpb249MSIKTkVURkxJWF9BUFBfTUVUQURBVEFfQjY0PSJZVzFwUFdGdGFTMHdOV0U0TnpoaVptRXpNakZsTUROaU5DWmhjMmM5Wm14bGRHTm9YM1JsYzNRdGRqQXdNQ1owUFRFMU5EUTJOVFl4TXpNbWRIbHdaVDFoZDNNbWRtVnljMmx2YmoweCIKTkVURkxJWF9BUFBfTUVUQURBVEFfU0lHPSJzaWc9VGhVRFhMa1VSRzhVODBoWmlyNl94ZHpmTmR0NzlmMTRwSUdUNUZMcHFYTmduYVhrelNOc2FWUFI3ZEJqamtHOUk4LU5ydUJZYmRqN3l3LUwtV251aGpWY2gwZ1A0T1ZQNktBd1pPUXRnNDMyTkE4Zzh4MlF1cDA2aVBKUERNUWlYNng0ekVWdjlkcERJemFLcXgxb0lUVjFQWGVvVTRjODYwNVZVUXY0TWFDZC1vdDhkRDZ1T3ZsYVppbXZEdXdERnFhZDF5NFZIWFNYQzJ6elZqX1RxT0M4d3dOU2hPRVZ5ZmhoT0dsblRJMmg2Zi1qSTFwa0s3alFGT2hJeVg4VnBQdGJUUm9Tb1BobU42X1hxdnk0cHhYOEdXaTNEV0t5U0VxZGpwWWJCUFJ0UXQxYnJPX1FSNVpIeVgxRGZ1MHdndjMtWmpOOGxVblUtZjJHTHRxQU9wbjBEc1VIcFhSTTRkWHQtYl9abFJmeEZDTFh5cG5KaWRjdHF1Vm82WWdQNDJDN2VqakNfM2NXN282SFctRWlENlVFQjg3THVDMnhsb3BXRkNSZFdXSVVDX2c4WjBfUzJaMWY4ckJ3Mmg4NHVIbmZQNDVYTnBoUjJTV2tKb0tWNjA5TmpCajkwSWwzZzBBR3ZmRjdxNFZadTlRc2VHUU5fVl9nRTZjZDBFQjAyRldVZmVFNFFhcmNjcHIyNXFaUDAwdW5GaU9USWRtUXJKY3FzcEtWM2VidXRxbnZmVzFDNDVqbGtkQzIxUE1uZE13cE5DRzVoa01GaGNrLTEwLWp2S2ZHejVSbFpITnVDcXR6djRMR3ZxVEpGcU1rTzZONDQySHkxUGdUaVd0ZkptWGpMdDZvbkR0N1YtMVJiYjNLdVpncWxQMU5SNHh3cWstUnlCQlFjZFElM0Qma2V5SUQ9MSZzQWxnPVNIQTUxMndpdGhSU0FhbmRNR0YxIgpORVRGTElYX0FDQ09VTlQ9InRlc3QiCk5FVEZMSVhfQUNDT1VOVF9UWVBFPSJtYWluIgpORVRGTElYX0VOVklST05NRU5UPSJ0ZXN0IgpORVRGTElYX0FQUD0iZmxldGNoX3Rlc3QiCk5FVEZMSVhfQVBQVVNFUj0iZmxldGNoX3Rlc3QiCk5FVEZMSVhfU1RBQ0s9IiIKTkVURkxJWF9DTFVTVEVSPSJmbGV0Y2hfdGVzdCIKTkVURkxJWF9ERVRBSUw9IiIKTkVURkxJWF9BVVRPX1NDQUxFX0dST1VQPSJmbGV0Y2hfdGVzdC12MDAwIgpORVRGTElYX0xBVU5DSF9DT05GSUc9ImZsZXRjaF90ZXN0LXYwMDAtMTIxMjIwMTgyMzA4NTIiCkVDMl9SRUdJT049ImV1LXdlc3QtMSIKCg==",
      instanceType = "t2.nano",
      keyName = "nf-test-keypair-a",
      iamInstanceProfile = "fletch_testInstanceProfile",
      instanceMonitoring = InstanceMonitoring(false)
    ),
    asg = AutoScalingGroup(
      autoScalingGroupName = "fletch_test-v000",
      defaultCooldown = 10,
      healthCheckType = "EC2",
      healthCheckGracePeriod = 600,
      suspendedProcesses = emptyList(),
      enabledMetrics = emptyList(),
      tags = listOf(Tag("spinnaker:application", "fletch_test")),
      terminationPolicies = listOf("Default")
    ),
    vpcId = "vpc-98b413fd",
    loadBalancers = emptyList(),
    capacity = ServerGroupCapacity(1, 1, 1),
    securityGroups = listOf("sg-01be6e67944355aef", "sg-3a0c495f", "sg-3b0c495e"),
    moniker = Moniker(
      app = "fletch_test",
      cluster = "fletch_test",
      sequence = "0"
    )
  )
}
