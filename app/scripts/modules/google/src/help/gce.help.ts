import { HelpContentsRegistry } from '@spinnaker/core';

const helpContents: { [key: string]: string } = {
  'gce.httpLoadBalancer.certificate':
    'The name of an SSL certificate. If specified, Spinnaker will create an HTTPS load balancer.',
  'gce.httpLoadBalancer.defaultService':
    'A default service handles any requests that do not match a specified host rule or path matching rule.',
  'gce.httpLoadBalancer.externalIP':
    'The IP address for this listener. If you do not specify an IP, your listener will be assigned an ephemeral IP.',
  'gce.httpLoadBalancer.hostRule.hostPattern': 'For example, <b>example.com</b>.',
  'gce.httpLoadBalancer.namedPort': `
      For HTTP(S) and SSL/TCP load balancers,
      incoming traffic is directed through a named port (for Spinnaker, the named port is <b>http</b>).
      The mapping from named port to port number is specified per server group
      and can be configured within the server group creation dialogue under <b>Port Name Mapping</b>.`,
  'gce.httpLoadBalancer.pathRule.paths': 'For example, <b>/path</b> in <b>example.com/path</b>',
  'gce.httpLoadBalancer.port':
    'HTTP requests can be load balanced based on port 80 or port 8080. HTTPS requests can be load balanced on port 443.',
  'gce.image.artifact':
    'The artifact that is to be deployed to this cluster.  The artifact should represent a deployable image.',
  'gce.image.source': `
      <p>Where the image to deploy is read from.</p>
      <p>
        <b>Artifact:</b> Deploy an artifact that was supplied/created upstream. The expected artifact must be referenced here, and will be bound at runtime.
      </p>
      <p>
        <b>Prior Stage:</b> Deploy the result of the most recent Bake or Find Image stage.
      </p>
  `,
  'gce.instance.customInstance.cores':
    '<ul><li>Above 1, vCPU count must be even.</li><li>Zones that support Haswell and Ivy Bridge processors can support custom machine types up to 32 vCPUs.</li><li>Zones that support Sandy Bridge processors can support up to 16 vCPUs.</li></ul>',
  'gce.instance.customInstance.memory':
    '<ul><li>Memory per vCPU must be between .9 GB and 6.5 GB.</li><li>Total memory must be a multiple of 256 MB.</li></ul>',
  'gce.instance.customMetadata.instance-template': 'The instance template used to configure this instance.',
  'gce.instance.customMetadata.load-balancer-names':
    'This field is used to "remember" what load balancers this instance is associated with, even if it is deregistered.',
  'gce.instance.customMetadata.global-load-balancer-names':
    'This field is used to "remember" what global load balancers this instance is associated with, even if it is deregistered.',
  'gce.instance.customMetadata.backend-service-names':
    'This field is used to "remember" what backend services this instance is associated with, even if it is deregistered.',
  'gce.instance.customMetadata.load-balancing-policy':
    'This field is used to "remember" the load balancing policy this instance is associated with, even if it is deregistered.',
  'gce.instance.customMetadata.startup-script': 'This script will run automatically on every boot.',
  'gce.instance.labels.spinnaker-region': 'This label can be used to group instances when querying for metrics.',
  'gce.instance.labels.spinnaker-server-group': 'This label can be used to group instances when querying for metrics.',
  'gce.instance.storage':
    '<p>Storage options can be fully-configured on the <b>Advanced Settings</b> tab. These are just default settings. Please be aware of your Local SSD quotas as you provision VMs.</p>',
  'gce.instance.storage.localSSD':
    '<p>Local SSD disks are always 375GB. All non shared-core instance types support attaching up to 8 Local SSD disks. Please be aware of your Local SSD quotas as you provision VMs.</p>',
  'gce.instance.serviceAccount':
    '<p>Service accounts authenticate applications running on your virtual machine instances to other Google Cloud Platform services. Valid values are either "default" or the full email address of a custom service account.</p>',
  'gce.instance.authScopes':
    '<p>Service account scopes specify which Google Cloud Platform APIs your instances can authenticate with, and define the level of access that your instances have with those services. You can enter custom auth scopes by typing into the blank field.</p>',
  'gce.instance.authScopes.cloud-platform':
    '<p>The instances in this server group have full API access to all Google Cloud services.</p>',
  'gce.instanceType.32core':
    '<p>32-core machine types are in Beta and are available only in Ivy Bridge and Haswell zones.</p>',
  'gce.internalLoadBalancer.ports': 'Use a comma to separate up to five TCP ports.',
  'gce.internalHttpLoadBalancer.network':
    "Network must have a subnet whose 'purpose' is 'INTERNAL_HTTPS_LOAD_BALANCER'",
  'gce.loadBalancer.connectionDraining':
    '(Optional) If set, enables connection draining for this backend service. This field defines the number of seconds to wait before instances that belong to this backend service are terminated in order to drain in-flight connections.',
  'gce.loadBalancer.detail':
    '<p>(Optional) <b>Detail</b> is a string of free-form alphanumeric characters and hyphens to describe any other variables.</p>',
  'gce.loadBalancer.advancedSettings.healthInterval':
    '<p>Configures the interval, in seconds, between load balancer health checks.</p><p>Default: <b>10</b></p>',
  'gce.loadBalancer.advancedSettings.healthyThreshold':
    '<p>Configures the number of healthy observations before reinstituting an instance into the load balancer’s traffic rotation.</p><p>Default: <b>10</b></p>',
  'gce.loadBalancer.advancedSettings.unhealthyThreshold':
    '<p>Configures the number of unhealthy observations before deservicing an instance from the load balancer.</p><p>Default: <b>2</b></p>',
  'gce.loadBalancer.healthCheck':
    '(Optional) <b>Health Checks</b> use HTTP requests to determine if a VM instance is healthy.',
  'gce.loadBalancer.portName':
    '(Required) The <b>Port Name</b> this backend service will forward traffic to. Load balancers in GCP specify a <b>Port Name</b>, and each server group added to a load balancer needs to specify a mapping from that <b>Port Name</b> to a port to actually receive traffic.',
  'gce.loadBalancer.portRange':
    '(Optional) Only packets addressed to ports in the specified <b>Port Range</b> will be forwarded. If left empty, all ports are forwarded. Must be a single port number or two port numbers separated by a dash. Each port number must be between 1 and 65535, inclusive. For example: 5000-5999.',
  'gce.securityGroup.sourceCIDRs':
    'Traffic is only allowed from sources that match one of these CIDR ranges, or one of the source tags above.',
  'gce.securityGroup.sourceTags':
    'Traffic is only allowed from sources that match one of these tags, or one of the source CIDR ranges below.',
  'gce.securityGroup.targetTags': 'Traffic is only allowed if the target instance has one of these tags.',
  'gce.securityGroup.targetServiceAccounts':
    'Traffic is allowed if the target instance has one of these service accounts.',
  'gce.securityGroup.sourceServiceAccounts':
    'Traffic is allowed if the source instance has one of these service accounts.',
  'gce.serverGroup.associatePublicIpAddress.providerField':
    'Check if new GCE server groups in this application should be assigned a public IP address by default.',
  'gce.serverGroup.resizeWithAutoscalingPolicy': `
      Setting the desired instance count for a server group with an autoscaler is not supported by Spinnaker;
      if the desired instance count differs from the instance count that the autoscaler wants to maintain for its configured metrics,
      it will quickly override the desired instance count.`,
  'gce.serverGroup.scalingPolicy.coolDownPeriodSec':
    'How long to wait before collecting information from a new instance. This should be at least the time it takes to initialize the instance.',
  'gce.serverGroup.scalingPolicy.cpuUtilization':
    'Autoscaler adds or removes instances to maintain this CPU usage on each instance.',
  'gce.serverGroup.scalingPolicy.predictiveAutoscaling':
    'Autoscaler adds or removes instances based on forecasted load. You must set a CPU utilization target to enable predictive autoscaling.',
  'gce.serverGroup.scalingPolicy.loadBalancingUtilization':
    'Autoscaler adds or removes instances to maintain this usage of load-balancing capacity.',
  'gce.serverGroup.scalingPolicy.customMetricUtilizations':
    'Autoscaler adds or removes instances to maintain this usage for custom metric.',
  'gce.serverGroup.imageName':
    '(Required) <b>Image</b> is the Google Compute Engine image. Images are restricted to the account selected.',
  'gce.serverGroup.capacity':
    'The number of instances that the instance group manager will attempt to maintain. Deleting or abandoning instances will affect this number, as will resizing the group.',
  'gce.serverGroup.minCpuPlatform':
    'The baseline minimum CPU platform to use for your instances, whenever available. Select "Automatic" unless you have a specific need.',
  'gce.serverGroup.customMetadata':
    '<b>Custom Metadata</b> will be propagated to the instances in this server group. This is useful for passing in arbitrary values that can be queried by your code on the instance.',
  'gce.serverGroup.userData':
    '<p>Custom user data will be propagated to the instances in this server group. Key/value pairs can either be newline or comma delimited.</p><strong>Example:</strong><br/> <pre>key=value<br>key2=value2</pre>',
  'gce.serverGroup.customMetadata.load-balancer-names':
    'This field is used to "remember" what load balancers this server group is associated with, even if the instances are deregistered.',
  'gce.serverGroup.customMetadata.global-load-balancer-names':
    'This field is used to "remember" what global load balancers this server group is associated with, even if the instances are deregistered.',
  'gce.serverGroup.customMetadata.backend-service-names':
    'This field is used to "remember" what backend services this server group is associated with, even if the instances are deregistered.',
  'gce.serverGroup.customMetadata.load-balancing-policy':
    'This field is used to "remember" the load balancing policy this server group is configured with, even if the server group is deregistered from the load balancer. This allows us to re-enable the server group with the same load balancing policy specified on creation.',
  'gce.serverGroup.customMetadata.select-zones':
    'This regional server group will be deployed only to specific zones within the region.',
  'gce.serverGroup.customMetadata.startup-script': 'This script will run automatically on every boot.',
  'gce.serverGroup.labels.spinnaker-region': 'This label can be used to group instances when querying for metrics.',
  'gce.serverGroup.labels.spinnaker-server-group':
    'This label can be used to group instances when querying for metrics.',
  'gce.serverGroup.shieldedVmConfig':
    'Shielded VM features include trusted UEFI firmware and come with options for Secure Boot, Virtual Trusted Platform Module (vTPM), and Integrity Monitoring.',
  'gce.serverGroup.shieldedVmSecureBoot':
    'Secure boot helps protect your VM instances against boot-level and kernel-level malware and rootkits.',
  'gce.serverGroup.shieldedVmVtpm':
    'Virtual Trusted Platform Module (vTPM) validates your guest VM pre-boot and boot integrity, and offers key generation and protection.',
  'gce.serverGroup.shieldedVmIntegrityMonitoring':
    'Integrity monitoring lets you monitor and verify the runtime boot integrity of your shielded VM instances using Stackdriver reports. Note: requires vTPM to be enabled.',
  'gce.serverGroup.preemptibility':
    'A preemptible VM costs much less, but lasts only 24 hours. It can be terminated sooner due to system demands.',
  'gce.serverGroup.automaticRestart':
    'Compute Engine can automatically restart VM instances if they are terminated for non-user-initiated reasons (maintenance event, hardware failure, software failure, etc.).',
  'gce.serverGroup.onHostMaintenance':
    'When Compute Engine performs periodic infrastructure maintenance it can migrate your VM instances to other hardware without downtime.',
  'gce.serverGroup.canIpForward':
    'Forwarding allows the instance to help route packets. See <a target="_blank" href="https://cloud.google.com/compute/docs/networking?hl=en_US#canipforward">here</a> for more information.',
  'gce.serverGroup.securityGroups.implicit':
    'Firewall rules with no target tags defined will permit incoming connections that match the ingress rules to all instances in the network.',
  'gce.serverGroup.securityGroups.targetTags':
    'This {{firewall}} rule will be associated with this server group only if a target tag is selected.',
  'gce.serverGroup.autoscaling.targetCPUUsage':
    'Autoscaling adds or removes VMs in the group to maintain this level of CPU usage on each VM.',
  'gce.serverGroup.autoscaling.targetHTTPLoadBalancingUsage':
    "Autoscaling adds or removes VMs in the group to maintain this usage of load-balancing capacity. This value is a percentage of the 'Maximum rate' setting of the load balancer this group is used by.",
  'gce.serverGroup.autoscaling.targetMetric':
    'Autoscaling adds or removes VMs in the group to maintain these target levels.',
  'gce.serverGroup.autoscaling.minVMs':
    'The least number of VM instances the group will contain, even if the target is not met.',
  'gce.serverGroup.autoscaling.maxVMs': 'The largest number of VM instances allowed, even if the target is exceeded.',
  'gce.serverGroup.autoscaling.cooldown':
    'How long to wait before collecting information from a new instance. This should be at least the time it takes to initialize the instance. To find the minimum, create an instance from the same image and note how long it takes to start.',
  'gce.serverGroup.autoscaling.mode':
    'Mode of operation of the autoscaling policy. This guides the autoscaler by defining the types of scaling operations it can perform. Options are ON, ONLY_SCALE_OUT, and OFF.',
  'gce.serverGroup.autoHealing':
    'VMs in the group are recreated as needed. You can use a health check to recreate a VM if the health check finds the VM unresponsive. If you do not select a health check, VMs are recreated only when stopped.',
  'gce.serverGroup.initialDelaySec':
    'The time to allow an instance to boot and applications to fully start before the first health check.',
  'gce.serverGroup.maxUnavailable': `
      Maximum number of instances that can be unavailable when auto-healing. The instance is considered available if all of the following conditions are satisfied:
      <ul>
        <li>1. Instance's status is RUNNING.</li>
        <li>2. Instance's liveness health check result was observed to be HEALTHY at least once.</li>
      </ul>`,
  'gce.serverGroup.subnet': `
      Subnetworks allow you to regionally segment the network IP space into prefixes (subnets) and control which prefix a VM instance's internal IP address is allocated from. There are several types of GCE networks:
      <ul>
        <li><b>Legacy (non-subnet) Network</b>: IP address allocation occurs at the global network level. This means the network address space spans across all regions.</li>
        <li><b>Auto Subnet Network</b>: Server groups will be automatically assigned to the specified region's subnet.</li>
        <li><b>Custom Subnet Network</b>: A subnet must be selected for the server group. If no subnets have been created for the specified region, you will not be able to provision the server group.</li>
      </ul>`,
  'gce.serverGroup.loadBalancingPolicy.balancingMode':
    'Tells the load balancer when the backend is at capacity. If a backend is at capacity, new requests are routed to the nearest region that can handle requests. The balancing mode can be based on CPU utilization or requests per second (RPS).',
  'gce.serverGroup.loadBalancingPolicy.maxRatePerInstance':
    'The maximum number of requests per second that can be sent to the backend instance group. Input must be a number greater than zero.',
  'gce.serverGroup.loadBalancingPolicy.maxUtilization':
    'The maximum CPU utilization allowed for the backend. CPU utilization is calculated by averaging CPU use across all instances in the backend instance group. Input must be a number between 0 and 100.',
  'gce.serverGroup.loadBalancingPolicy.maxConnectionsPerInstance':
    'The target connections per second for individual instances. When this number is exceeded, requests are directed to another backend.',
  'gce.serverGroup.loadBalancingPolicy.capacityScaler': `
      An additional control to manage your maximum CPU utilization or RPS.
      If you want your instances to operate at a max 80% CPU utilization, set your balancing mode to 80% max CPU utilization and your capacity to 100%.
      If you want to cut instance utilization by half, set your balancing mode to 80% max CPU utilization and your capacity to 50%. Input must be a number between 0 and 100.`,
  'gce.serverGroup.loadBalancingPolicy.portName':
    'A load balancer sends traffic to an instance group through a named port. Input must be a port name.',
  'gce.serverGroup.loadBalancingPolicy.listeningPort':
    'A load balancer sends traffic to an instance group through a named port. Input must be a port number (i.e., between 1 and 65535).',
  'gce.serverGroup.traffic':
    'Registers the server group with any associated load balancers. These registrations are used by Spinnaker to determine if the server group is enabled.',
  'gce.serverGroup.accelerator':
    'Attaches GPUs to instances in this server group. The set of available GPUs is dictated by the selected zone. You cannot attach GPUs to instances with shared-core machine types.',
  'gce.tagImage.consideredStages':
    'Limit which previous stages will be considered when locating images to tag. If left unchecked, images generated by any upstream stage will be tagged.',
  'pipeline.config.gce.bake.accountName':
    '<p>(Optional) The name of a Google account configured within Rosco. If left blank, the first configured account will be used.</p>',
  'pipeline.config.gce.bake.baseImage':
    '<p>(Optional) A GCE image name. For example: ubuntu-1204-precise-v20150910.</p>',
  'gce.loadBalancerType.Network': `
    <p>Use Network Load Balancing to balance the load on your systems based on incoming IP protocol data, such as address, port, and protocol type.</p>
    <p>Network Load Balancing is a regional, non-proxied load balancer. You can use it to load balance UDP traffic, and TCP and SSL traffic on ports that are not supported by the SSL proxy and TCP proxy load balancers.</p>`,
  'gce.loadBalancerType.HTTP(S)':
    '<p>Google Cloud Platform (GCP) HTTP(S) Load Balancing provides global load balancing for HTTP(S) requests destined for your instances.</p>',
  'gce.loadBalancerType.Internal':
    '<p>Internal TCP/UDP Load Balancing is a regional load balancer that enables you to run and scale your services behind a private load balancing IP address that is accessible only to your internal virtual machine instances.</p>',
  'gce.loadBalancerType.SSL':
    '<p>Google Cloud SSL Proxy Load Balancing terminates user SSL (TLS) connections at the load balancing layer, then balances the connections across your instances using the SSL or TCP protocols. This supports both IPv4 and IPv6 addresses for client traffic.</p>',
  'gce.loadBalancerType.TCP':
    '<p>Google Cloud Platform (GCP) TCP Proxy Load Balancing allows you to use a single IP address for all users around the world. GCP TCP proxy load balancing automatically routes traffic to the instances that are closest to the user.</p>',
};

Object.keys(helpContents).forEach((key) => HelpContentsRegistry.register(key, helpContents[key]));
