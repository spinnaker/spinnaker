import { HelpContentsRegistry } from '@spinnaker/core';

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
};

Object.keys(helpContents).forEach(key => HelpContentsRegistry.register(key, helpContents[key]));
