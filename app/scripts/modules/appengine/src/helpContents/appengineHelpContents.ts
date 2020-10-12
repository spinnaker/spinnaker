import { HelpContentsRegistry } from '@spinnaker/core';

const helpContents = [
  {
    key: 'appengine.serverGroup.gcs.repositoryUrl',
    value: `The full URL to the GCS bucket or TAR file containing the source files for this deployment,
              including 'gs://'. For example, <b>gs://my-bucket/my-app</b> or <b>gs://my-bucket/app.tar</b>.`,
  },
  {
    key: 'appengine.serverGroup.git.repositoryUrl',
    value: `The full URL to the git repository containing the source files for this deployment,
              including protocol. For example, <b>https://github.com/spinnaker/deck.git</b>`,
  },
  {
    key: 'appengine.serverGroup.gitCredentialType',
    value: `The credential type that will be used to access the git repository for this deployment.
              You can configure these credentials alongside your App Engine credentials.`,
  },
  {
    key: 'appengine.serverGroup.branch',
    value: 'The name of the branch in the above git repository to be used for this deployment.',
  },
  {
    key: 'appengine.serverGroup.applicationDirectoryRoot',
    value: `(Optional) Path to the directory root of the application to be deployed,
              starting from the root of the git repository. This is the directory from which
              <code>gcloud app deploy</code> will be run.`,
  },
  {
    key: 'appengine.serverGroup.configFilepaths',
    value: `Paths to App Engine application configuration files, starting from the application directory root,
              specified above. In most cases, this will be <code>app.yaml</code> or <code>cron.yaml</code>,
              but could be <code>path/to/app.yaml</code> or <code>path/to/cron.yaml</code>.`,
  },
  {
    key: 'appengine.serverGroup.configFiles',
    value: `<p>(Optional) The contents of an App Engine config file (e.g., an <code>app.yaml</code> or
              <code>cron.yaml</code> file). These files should not conflict with the config filepaths above:
              if you include, for example, the contents of an <code>app.yaml</code>
              file here, you should <b>not</b> specify the file path to an <code>app.yaml</code> above.<br></p>
              <p>If this is a pipeline stage, you can use Spinnaker Pipeline Expressions here.</p>`,
  },
  {
    key: 'appengine.serverGroup.configFilesRequired',
    value: `<p>The contents of an App Engine config file (e.g., an <code>app.yaml</code> or
              <code>cron.yaml</code> file).</p>
              <p>An <code>app.yaml</code> file is required and must have <code>runtime: custom</code> in
              order to deploy successfully.</p>
              <p>If this is a pipeline stage, you can use Spinnaker Pipeline Expressions here.</p>`,
  },
  {
    key: 'appengine.serverGroup.matchBranchOnRegex',
    value: `(Optional) A Jenkins trigger may produce details from multiple repositories and branches.
              Spinnaker will use the regex specified here to help resolve a branch for the deployment.
              If Spinnaker cannot resolve exactly one branch from the trigger, this pipeline will fail.`,
  },
  {
    key: 'appengine.serverGroup.promote',
    value: 'If selected, the newly deployed server group will receive all traffic.',
  },
  {
    key: 'appengine.serverGroup.stopPreviousVersion',
    value: `If selected, the previously running server group in this server group's <b>service</b>
              (Spinnaker load balancer) will be stopped. This option will be respected only if this server group will
              be receiving all traffic and the previous server group is using manual scaling.`,
  },
  {
    key: 'appengine.serverGroup.containerImageUrl',
    value: `The full URL to the container image to use for deployment. The URL must be one of the valid GCR hostnames,
              for example <b>gcr.io/my-project/image:tag</b>`,
  },
  {
    key: 'appengine.serverGroup.suppress-version-string',
    value: `Spinnaker automatically versions your server groups. This means deployments through Spinnaker receive a
              short version string at the end of their name, like "v001". In most cases you will want to keep this
              version as part of the name. Preventing this string from being added to your server group will stop
              it from being considered when rolling back, promoting new versions or executing deployment strategies.
              If you are certain that you do not want the version string applied to this server group then check
              this box.`,
  },
  {
    key: 'appengine.loadBalancer.shardBy.cookie',
    value:
      'Diversion based on a specially named cookie, "GOOGAPPUID." The cookie must be set by the application itself or no diversion will occur.',
  },
  {
    key: 'appengine.loadBalancer.shardBy.ip',
    value: 'Diversion based on applying the modulus operation to a fingerprint of the IP address.',
  },
  {
    key: 'appengine.loadBalancer.migrateTraffic',
    value: `If selected, traffic will be gradually shifted to a single version. For gradual traffic migration,
              the target version must be located within instances that are configured for
              both warmup requests and automatic scaling.
              Gradual traffic migration is not supported in the App Engine flexible environment.`,
  },
  {
    key: 'appengine.loadBalancer.allocations',
    value: 'An allocation is the percent of traffic directed to a server group.',
  },
  {
    key: 'appengine.loadBalancer.textLocator',
    value: `Either the name of a server group, or a Spinnaker Pipeline Expression
              that resolves to the name of a server group.`,
  },
  {
    key: 'appengine.instance.availability',
    value: `
        An instance's <b>availability</b> is determined by its version (Spinnaker server group).
        <ul>
          <li>Manual scaling versions use resident instances</li>
          <li>Basic scaling versions use dynamic instances</li>
          <li>Auto scaling versions use dynamic instances - but if you specify a number, N,
              of minimum idle instances, the first N instances will be resident,
              and additional dynamic instances will be created as necessary.
          </li>
        </ul>`,
  },
  {
    key: 'appengine.instance.averageLatency',
    value: 'Average latency over the last minute in milliseconds.',
  },
  {
    key: 'appengine.instance.vmStatus',
    value: 'Status of the virtual machine where this instance lives.',
  },
  {
    key: 'appengine.instance.qps',
    value: 'Average queries per second over the last minute.',
  },
  {
    key: 'appengine.instance.errors',
    value: 'Number of errors since this instance was started.',
  },
  {
    key: 'appengine.instance.requests',
    value: 'Number of requests since this instance was started.',
  },
];

helpContents.forEach((entry) => HelpContentsRegistry.register(entry.key, entry.value));
