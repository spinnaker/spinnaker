import { HelpContentsRegistry } from '@spinnaker/core';
import Utility from '../utility';

const helpContents: { [key: string]: string } = {
  'azure.securityGroup.ingress.description': 'Friendly description of the rule you want to enable (limit 80 chars.)',
  'azure.securityGroup.ingress.priority':
    "Rules are processed in priority order; the lower the number, the higher the priority.  We recommend leaving gaps between rules - 100, 200, 300, etc. - so that it's easier to add new rules without having to edit existing rules.  There are several default rules that can be overridden with priority (65000, 65001 and 65500).  For more information visit http://portal.azure.com.",
  'azure.securityGroup.ingress.source':
    "The source filter can be Any, an IP address range or a default tag('Internet', 'VirtualNetwork', AzureLoadBalancer').  It specifies the incoming traffic from a specific source IP address range (CIDR format) that will be allowed or denied by this rule.",
  'azure.securityGroup.ingress.sourcePortRange':
    'The source port range can be a single port, such as 80, or a port range, such as 1024-65535.  This specifies from which ports incoming traffic will be allowed or denied by this rule.  Provide an asterisk (*) to allow traffic from clients connecting from any port.',
  'azure.securityGroup.ingress.destination':
    "The destination filter can be Any, an IP address range or a default tag('Internet', 'VirtualNetwork', AzureLoadBalancer').  It specifies the outgoing traffic from a specific destination IP address range (CIDR format) that will be allowed or denied by this rule.",
  'azure.securityGroup.ingress.destinationPortRange':
    'The destination port range can be a single port, such as 80, or a port range, such as 1024-65535.  This specifies from which destination ports traffic will be allowed or denied by this rule.  Provide an asterisk (*) to allow traffic from clients connecting from any port.',
  'azure.securityGroup.ingress.direction': 'Specifies whether the rule is for inbound or outbound traffic.',
  'azure.securityGroup.ingress.actions':
    'To adjust the priority of a rule, move it up or down in the list of rules.  Rules at the top of the list have the highest priority.',
  'azure.securityGroup.ingress.destPortRanges':
    'Provide a single port, such as 80; a port range, such as 1024-65535; or a comma-separated list of single ports and/or port ranges, such as 80,1024-65535. Provide an asterisk (*) to allow traffic on any port.',
  'azure.securityGroup.ingress.sourceIPCIDRRanges':
    'Provide an address range using CIDR notation, such as 192.168.99.0/24; an IP address, such as 192.168.99.0; or a comma-separated list of address ranges or IP addresses, such as 10.0.0.0/24,44.66.0.0/24',
  'azure.serverGroup.imageName': '(Required) <b>Image</b> is the deployable Azure Machine Image.',
  'azure.serverGroup.stack':
    '(Required) <b>Stack</b> is one of the core naming components of a cluster, used to create vertical stacks of dependent services for integration testing.',
  'azure.serverGroup.detail':
    '(Required) <b>Detail</b> is a naming component to help distinguish specifics of the server group.',
  'azure.serverGroup.scriptLocation':
    'The location of custom scripts separated by comma or semicolon to be downloaded on to each instance. A single script should be like: fileUri. Multiple scripts should be like fileUri1,fileUri2 or fileUri1;fileUri2',
  'azure.serverGroup.commandToExecute':
    'Command(s) to execute custom scripts provided during provisioning of an instance.',
  'azure.serverGroup.customData': 'Script or metadata to be injected into each instances.',
  'azure.serverGroup.customTags': `Custom tags on Virtual Machine Scale Set. Allow ${Utility.TAG_LIMITATION} tags at most.`,
  'azure.serverGroup.enableInboundNAT':
    'An Azure load balancer of the basic sku will be created with adding inbound NAT port-forwarding rules to facilitate loggin on VM instances. There is no charge for creating an Azure load balancer of the basic sku. This option is disabled if Availability Zones are set which require Standard Azure Load Balancer and an extra Network Security Group with correct inbound and outbound rules configured.',
  'azure.serverGroup.lun':
    'Specifies the logical unit number of the data disk. This value is used to identify data disks within the VM and therefore must be unique for each data disk attached to a VM.',
  'azure.serverGroup.diskSizeGB':
    'Specifies the size of an empty data disk in gigabytes. This value cannot be larger than 1023 GB',
  'azure.serverGroup.managedDisk.storageAccountType':
    'You can choose between Azure managed disks types to support your workload or scenario.',
  'azure.serverGroup.caching':
    'Changing the default host caching policy can adversely impact the performance of your application. You should run performance tests to measure its impact. To improve the total IOPS/throughput, we recommend striping across multiple disks and using premium (SSD) disks.',
  'azure.loadBalancer.dnsName':
    'If there is no custom DNS label specified, a default DNS name will be created. The default value will be "GeneratedText.cloudapp.net" for Azure Application Gateway or "GeneratedText.[region].cloudapp.azure.com" for Azure Load Balancer.',
  'azure.loadBalancer.probes.probeInterval':
    'Probe interval in seconds. This value is the time interval between two consecutive probes.',
  'azure.loadBalancer.probes.timeout':
    'Probe time-out in seconds. If a valid response is not received within this time-out period, the probe is marked as failed. Note that the time-out value should not be more than the Interval value.',
  'azure.loadBalancer.probes.unhealthyThreshold':
    'Probe retry count. The back-end server is marked down after the consecutive probe failure count reaches the unhealthy threshold.',
  'azure.loadBalancer.loadBalancingRules.idleTimeout':
    'Keep a TCP or HTTP connection open without relying on clients to send keep-alive messages.',
  'azure.loadBalancer.loadBalancingRules.sessionPersistence':
    'Session persistence specifies that traffic from a client should be handled by the same virtual machine in the backend pool for the duration of a session. "None" specifies that successive requests from the same client may be handled by any virtual machine. "Client IP" specifies that successive requests from the same client IP address will be handled by the same virtual machine. "Client IP and protocol" specifies that successive requests from the same client IP address and protocol combination will be handled by the same virtual machine.',
};

Object.keys(helpContents).forEach((key) => HelpContentsRegistry.register(key, helpContents[key]));
