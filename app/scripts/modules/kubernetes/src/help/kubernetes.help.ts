import { HelpContentsRegistry } from '@spinnaker/core';

const helpContents: { [key: string]: string } = {
  'kubernetes.serverGroup.stack':
    '(Optional) One of the core naming components of a cluster, used to create vertical stacks of dependent services for integration testing.',
  'kubernetes.serverGroup.detail':
    '(Optional) A string of free-form alphanumeric characters and hyphens to describe any other variables.',
  'kubernetes.serverGroup.containers': `
      (Required) Select at least one image to run in this server group (pod).
      If multiple images are selected, they will be colocated and replicated equally.`,
  'kubernetes.serverGroup.initContainers': `
      (Optional) Select at least one image to run in this server group as an init container (pod).`,
  'kubernetes.serverGroup.terminationGracePeriodSeconds':
    '(Required) Termination grace period in <b>seconds</b>. You can customize the termination grace period setting (terminationGracePeriodSeconds). Because server groups (pods) represent running processes on nodes in the cluster, it is important to allow those processes to gracefully terminate when they are no longer needed (vs. being violently killed and having no chance to clean up). Default is <b>30</b> seconds.',
  'kubernetes.serverGroup.autoscaling.enabled':
    'If selected, a horizontal autoscaler will be attached to this replica set.',
  'kubernetes.serverGroup.autoscaling.min': 'The smallest number of pods to be deployed.',
  'kubernetes.serverGroup.autoscaling.max': 'The largest number of pods to be deployed.',
  'kubernetes.serverGroup.autoscaling.desired': 'The initial number of pods to be deployed.',
  'kubernetes.serverGroup.autoscaling.cpuTarget': 'The target CPU utilization to be achieved by the autoscaler.',
  'kubernetes.serverGroup.useSourceCapacity.enabled': 'Use same number of pods as current.',
  'kubernetes.serverGroup.deployment.enabled': 'Check this box if you want to spawn a deployment for this replica set.',
  'kubernetes.serverGroup.deployment.strategy': `
      <p><b>RollingUpdate</b> Deploy new pods in groups while killing old ones off.</p>
      <p><b>Recreate</b> Recreate the entire replica set.</p>`,
  'kubernetes.serverGroup.deployment.minReadySeconds':
    'The minimum time the deployment object will wait after a pod becomes healthy.',
  'kubernetes.serverGroup.deployment.revisionHistoryLimit':
    "(Optional) How many old replica sets to keep around. Leave empty if you don't want any to be deleted.",
  'kubernetes.serverGroup.deployment.maxSurge':
    '(Optional) Either a number or a percentage (1 vs. 10%) representing the maximum number of new pods to deploy during a rollout.',
  'kubernetes.serverGroup.deployment.maxUnavailable':
    '(Optional) Either a number or a percentage (1 vs. 10%) representing the maximum number of pods to destroy during a rollout.',
  'kubernetes.serverGroup.tolerations.key':
    'Key is the taint key that the toleration applies to. Empty means match all taint keys. If the key is empty, operator must be Exists; this combination means to match all values and all keys.',
  'kubernetes.serverGroup.tolerations.value':
    'Value is the taint value the toleration matches to. If the operator is Exists, the value should be empty, otherwise just a regular string.',
  'kubernetes.serverGroup.tolerations.operator':
    "Operator represents a key's relationship to the value. Valid operators are Exists and Equal. Defaults to Equal. Exists is equivalent to wildcard for value, so that a pod can tolerate all taints of a particular category.",
  'kubernetes.serverGroup.tolerations.effect':
    'Effect indicates the taint effect to match. Empty means match all taint effects. When specified, allowed values are NoSchedule, PreferNoSchedule and NoExecute.',
  'kubernetes.serverGroup.tolerations.tolerationSeconds':
    'TolerationSeconds represents the period of time the toleration (which must be of effect NoExecute, otherwise this field is ignored) tolerates the taint. By default, it is not set, which means tolerate the taint forever (do not evict). Zero and negative values will be treated as 0 (evict immediately) by the system.',
  'kubernetes.job.parallelism': '(Required) The number of concurrent pods to run.',
  'kubernetes.job.completions':
    '(Required) The number of sucessful completions required for the job to be considered a success.',
  'kubernetes.job.deadlineSeconds': '(Optional) The number of seconds until the job is considered a failure.',
  'kubernetes.containers.image': 'The image selected under Basic Settings whose container is to be configured.',
  'kubernetes.containers.registry': 'The registry the selected image will be pulled from.',
  'kubernetes.containers.command': 'The list of commands which to overwrite the docker ENTRYPOINT array.',
  'kubernetes.containers.name':
    '(Required) The name of the container associated with the above image. Used for resource identification',
  'kubernetes.containers.pullpolicy': `
      <p>Sets the policy used to determine when to pull (download) the selected container image.</p>
      <p><i><b>Warning</b> - This shouldn't be modified for most production pipelines as it encourages aliasing tags,
          preventing proper rollbacks and determining an image's source.</i></p>
      <p><b>IFNOTPRESENT</b>: (Default) Pulls the image only when it is not present on the host machine.</p>
      <p><b>ALWAYS</b>: Always pulls the image, regardless of if it is present or not.</p>
      <p><b>NEVER</b>: Never pulls the image, regardless of if it is present or not.</p>`,
  'kubernetes.containers.cpu': `
      (Optional) The relative CPU shares to allocate this container. If set, it is multiplied by 1024, then
      passed to Docker as the --cpu-shares flag. Otherwise the default of 1 (1024) is used`,
  'kubernetes.containers.lifecycle.postStart': `
      Called immediately after a container is created. If the handler fails, the container is terminated
      and restarted according to its restart policy.`,
  'kubernetes.containers.lifecycle.preStop': `
      Called immediately before a container is terminated. The container is terminated after the handler completes.
      The reason for termination is passed to the handler. Regardless of the outcome of the handler, the container is eventually terminated.`,
  'kubernetes.containers.execAction.command': `
      Command to execute inside the container. The working directory for the command is root within the container's filesystem.
      An exit status of 0 is treated as live/healthy; non-zero is unhealthy.`,
  'kubernetes.containers.httpGetAction.path': 'Path to access on the HTTP server',
  'kubernetes.containers.httpGetAction.port': 'Number of the port to access on the container.',
  'kubernetes.containers.httpGetAction.host': 'Host name to connect to, defaults to the pod IP.',
  'kubernetes.containers.httpGetAction.uriScheme': 'Scheme to use for connecting to the host.',
  'kubernetes.containers.httpGetAction.httpHeaders': 'Custom headers to set in the request.',
  'kubernetes.containers.memory': `
      (Optional) The relative memory in megabytes to allocate this container. If set, it is converted to an integer
      and passed to Docker as the --memory flag`,
  'kubernetes.containers.requests': `
      (Optional) This is used for scheduling. It assures that this container will always be scheduled on a machine ' +
      with at least this much of the resource available.`,
  'kubernetes.containers.ports.name': '(Optional) A name for this port. Can be found using DNS lookup if specified.',
  'kubernetes.containers.ports.containerPort': '(Required) The port to expose on this container.',
  'kubernetes.containers.ports.hostPort':
    '(Optional) The port to expose on <b>Host IP</b>. Most containers do not need this',
  'kubernetes.containers.ports.hostIp':
    '(Optional) The IP to bind the external port to. Most containers do not need this.',
  'kubernetes.containers.ports.protocol': '(Required) The protocol for this port.',
  'kubernetes.containers.limits': '(Optional) This provides a hard limit on this resource for the given container.',
  'kubernetes.containers.probes.type': `
      <p><b>HTTP</b> Hit the probe at the specified port and path.</p>
      <p><b>EXEC</b> Execute the specified commands on the container.</p>
      <p><b>TCP</b> Connect to the container at the specified port.</p>`,
  'kubernetes.containers.probes.initialDelay': 'How long to wait after startup before running this probe.',
  'kubernetes.containers.probes.timeout': 'How long to wait on the result of this probe.',
  'kubernetes.containers.probes.period': 'How long between probe executions.',
  'kubernetes.containers.probes.successThreshold':
    'How many executions need to succeed before the probe is declared healthy.',
  'kubernetes.containers.probes.failureThreshold':
    'How many executions need to fail before the probe is declared unhealthy.',
  'kubernetes.containers.volumemounts.name': 'The <b>Volume Source</b> configured above to claim.',
  'kubernetes.containers.volumemounts.mountPath': 'The directory to mount the specified <b>Volume Source</b> to.',
  'kubernetes.containers.volumemounts.subPath':
    'This will mount the sub path in the referenced volume rather than the root path.',
  'kubernetes.namespace':
    'The namespace you have configured with the above selected account. This will often be referred to as <b>Region</b> in Spinnaker.',
  'kubernetes.loadBalancer.detail':
    '(Optional) A string of free-form alphanumeric characters; by convention, we recommend using "frontend".',
  'kubernetes.loadBalancer.stack':
    '(Optional) One of the core naming components of a cluster, used to create vertical stacks of dependent services for integration testing.',
  'kubernetes.service.ports.name': '(Optional) A name for this port. Can be found using DNS lookup if specified.',
  'kubernetes.service.ports.port': 'The port this service will expose to resources internal to the cluster.',
  'kubernetes.service.ports.nodePort': `
      (Optional) A port to open on every node in the cluster. This allows you to receive external traffic without
      having to provision a cloud load balancer. <b>Type</b> in <b>Advanced Settings</b> cannot be set to <b>ClusterIP</b> for this to work.`,
  'kubernetes.service.ports.targetPort':
    '(Optional) The port to forward incoming traffic to for pods associated with this load balancer.',
  'kubernetes.service.ports.protocol': 'The protocol this port listens to.',
  'kubernetes.service.type': `
      <b>ClusterIP</b> means this is an internal load balancer only. <b>LoadBalancer</b> provisions a cloud load balancer if possible
      at address <b>Load Balancer IP</b>. <b>NodePort</b> means this load balancer forwards traffic from ports with <b>Node Port</b> specified.`,
  'kubernetes.service.sessionAffinity': `
      <b>None</b> means incoming connections are not associated with the pods they are routed to. <b>ClientIP</b>
      associates connections with pods by incoming IP address.`,
  'kubernetes.service.clusterIp': `
      (Optional) If specified, and available, this internal IP address will be the internal endpoint for this load balancer.
      If not specified, one will be assigned.`,
  'kubernetes.service.loadBalancerIp': `
      If specified, and available, this external IP address will be the external endpoint for this load balancer
      when <b>Type</b> is set to <b>LoadBalancer</b>.`,
  'kubernetes.service.externalIps': `
      IP addresses for which nodes in the cluster also accept traffic. This is not managed by Kubernetes and the
      responsibility of the user to configure.`,
  'kubernetes.pod.dnsPolicy': `
      <p>Set DNS policy for containers within the pod.</p>
      <p>Defaults to <b>ClusterFirst</b>.</p>
      <p>To have DNS options set along with hostNetwork, you have to specify DNS policy explicitly to <b>ClusterFirstWithHostNet</b>.</p>`,
  'kubernetes.pod.volume': `
      <p>A storage volume to be mounted and shared by containers in this pod. The lifecycle depends on the volume type selected.</p>
      <p><b>CONFIGMAP</b>: Intended to act as a reference to multiple properties files. Similar to the /etc directory, and the files within, on a Linux computer.</p>
      <p><b>EMPTYDIR</b>: A transient volume tied to the lifecycle of this pod.</p>
      <p><b>HOSTPATH</b>: A directory on the host node. Most pods do not need this.</p>
      <p><b>PERSISTENTVOLUMECLAIM</b>: An already created persistent volume claim to be bound by this pod.</p>
      <p><b>SECRET</b>: An already created kubernetes secret to be mounted in this pod.</p>`,
  'kubernetes.pod.emptydir.medium': `
      The type of storage medium used by this volume type.
      <p><b>DEFAULT</b>: Depends on the storage mechanism backing this pod's Kubernetes installation.</p>
      <p><b>MEMORY</b>: A tmpfs (RAM-backed filesystem). Very fast, but usage counts against the memory resource limit, and contents are lost on reboot.</p>`,
  'kubernetes.pod.volume.persistentvolumeclaim.claim': 'The name of the underlying persistent volume claim to request.',
  'kubernetes.pod.volume.hostpath.path': "The path on the host node's filesystem to mount.",
  'kubernetes.pod.volume.secret.secretName': 'The name of the secret to mount.',
  'kubernetes.pod.volume.awsElasticBlockStore.volumeId':
    'Unique ID of the persistent disk resource in AWS (Amazon EBS volume).',
  'kubernetes.pod.volume.awsElasticBlockStore.fsType':
    'Filesystem type of the volume that you want to mount. Tip: Ensure that the filesystem type is supported by the host operating system. Examples: "ext4", "xfs", "ntfs". Implicitly inferred to be "ext4" if unspecified.',
  'kubernetes.pod.volume.awsElasticBlockStore.partition':
    'The partition in the volume that you want to mount. If omitted, the default is to mount by volume name. Examples: For volume /dev/sda1, you specify the partition as "1". Similarly, the volume partition for /dev/sda is "0" (or you can leave the property empty).',
  'kubernetes.pod.volume.nfs.server': 'NFS (Network File System) server to be mount',
  'kubernetes.pod.volume.nfs.path': 'The path in the NFS to mount',
  'kubernetes.ingress.backend.port': 'The port for the specified load balancer.',
  'kubernetes.ingress.backend.service':
    'The load balancer (service) traffic not matching the below rules will be routed to.',
  'kubernetes.ingress.rules.service': 'The load balancer (service) traffic matching this rule will be routed to.',
  'kubernetes.ingress.rules.host':
    'The fully qualified domain name of a network host. Any traffic routed to this host matches this rule. May not be an IP address, or contain port information.',
  'kubernetes.ingress.rules.path': 'POSIX regex (IEE Std 1003.1) matched against the path of an incoming request.',
  'kubernetes.ingress.rules.port': 'The port on the specifed load balancer to route traffic to.',
  'kubernetes.ingress.tls.host':
    'The fully qualified domain name of a network host. Any traffic routed to this host can be secured with TLS. May not be an IP address, or contain port information.',
  'kubernetes.ingress.tls.secret':
    '(Optional) Name of the Kubernetes secret that will be used to secure TLS connections to the {{firewall}}. Note that Spinnaker will not create any secrets, they are assumed to exist.',
  'kubernetes.manifest.account': `
      <p>A Spinnaker account corresponds to a physical Kubernetes cluster. If you are unsure which account to use, talk to your Spinnaker admin.</p>
  `,
  'kubernetes.manifest.cluster': `
      <p>A cluster is a grouping of resources (often across multiple versions of a resource) within an application.</p>
  `,
  'kubernetes.manifest.stackdetail': `
      <p>
      <b>Stack:</b> <i>(Optional)</i> A naming component used to optionally group manifests within Spinnaker. Typically used to create vertical stacks of resources that can be aggregated/filtered later.
      </p>
      <p>
      <b>Detail:</b> <i>(Optional)</i> A naming component used to store free-form text about your manifest, and can be used to aggregate/filter resources later.
      </p>
  `,
  'kubernetes.manifest.delete.gracePeriod': `
      <p><i>(Optional)</i>The number of seconds given to the resource to terminate gracefully. Overrides what's set on the manifest if applicable.</p>
  `,
  'kubernetes.manifest.delete.cascading': `
      <p>When set, delete all resources managed by this resource as well (all pods owned by a replica set). When unset, this may orphan resources.</p>
  `,
  'kubernetes.manifest.source': `
      <p>Where the manifest file content is read from.</p>
      <p>
        <b>text:</b> The manifest is supplied statically to the pipeline from the below text-box.
      </p>
      <p>
        <b>artifact:</b> The manifest is read from an artifact supplied/created upstream. The expected artifact must be referenced here, and will be bound at runtime.
      </p>
  `,
  'kubernetes.manifest.rolloutStrategyOptions': `
    <p>Allow Spinnaker to associate each ReplicaSet deployed in this stage with one or more Services and manage traffic based on your selected rollout strategy options.</p>
  `,
  'kubernetes.manifest.rolloutStrategy': `
    <p>The rollout strategy tells Spinnaker what to do with the previous version(s) of the ReplicaSet in the cluster.</p>
  `,
  'kubernetes.manifest.expectedArtifact':
    'The artifact that is to be applied to the Kubernetes account for this stage.  The artifact should represent a valid Kubernetes manifest.',
  'kubernetes.manifest.requiredArtifactsToBind':
    'These artifacts must be present in the context for this stage to successfully complete. Artifacts specified will be <a href="https://www.spinnaker.io/reference/artifacts/in-kubernetes-v2/#binding-artifacts-in-manifests" target="_blank">bound to the deployed manifest.</a>',
  'kubernetes.manifest.skipExpressionEvaluation':
    '<p>Skip SpEL expression evaluation of the manifest artifact in this stage. Can be paired with the "Evaluate SpEL expressions in overrides at bake time" option in the Bake Manifest stage when baking a third-party manifest artifact with expressions not meant for Spinnaker to evaluate as SpEL.</p>',
  'kubernetes.manifest.undoRollout.revisionsBack': `
      <p>How many revisions to rollback from the current active revision. This is not a hard-coded revision to rollout.</p>
      <p>For example: If you specify "1", and this stage executes, the prior revision will be active upon success.</p>
  `,
  'kubernetes.manifest.patch.record': `Record the applied patch in the <i>kubernetes.io/change-cause</i> annotation. If the annotation already exists, the contents are replaced.`,
  'kubernetes.manifest.patch.mergeStrategy': `
    <p>The merge strategy to use when patching Kubernetes objects:</p>
    <p><b>strategic</b>: <i>(Default)</i> <a href="https://github.com/kubernetes/community/blob/master/contributors/devel/sig-api-machinery/strategic-merge-patch.md" target="_blank">Kubernetes Strategic merge patch.</a></p>
    <p><b>json</b>: <a href="https://tools.ietf.org/html/rfc6902" target="_blank">JSON Patch, RFC 6902</a></p>
    <p><b>merge</b>: <a href="https://tools.ietf.org/html/rfc7386" target="_blank">JSON Merge Patch, RFC 7386</a></p>
  `,
  'kubernetes.runJob.captureSource':
    'Source from which to capture Job output. Captured output will be available in the pipeline context for use in downstream stages.',
  'kubernetes.runJob.captureSource.containerName': `Use logs from this container to capture output data.`,
};

Object.keys(helpContents).forEach((key) => HelpContentsRegistry.register(key, helpContents[key]));
