import { HelpContentsRegistry } from '../../help/helpContents.registry';

const helpContents: { [key: string]: string } = {
  'application.platformHealthOnly': `
        When this option is enabled, instance status as reported by the cloud provider will be considered sufficient to
        determine task completion. When this option is disabled, tasks will normally need health status reported by some other health provider (e.g. a
        load balancer or discovery service) to determine task completion.`,
  'application.showPlatformHealthOverride':
    'When this option is enabled, users will be able to toggle the option above on a task-by-task basis.',
  'application.permissions': `
      <ul>
        <li>To read from this application, a user must be a member of at least one group with read access.</li>
        <li>To write to this application, a user must be a member of at least one group with write access.</li>
        <li>If no permissions are specified, any user can read from or write to this application.</li>
        <li>These permissions will only be enforced if Fiat is enabled.</li>
      </ul>
      <p class="small"><strong>Note:</strong> Due to caching, data may be delayed up to 10 minutes</p>
    `,
  'application.enableRestartRunningExecutions': `
        When this option is enabled, users will be able to restart pipeline stages while a pipeline is still running.
        This behavior can have varying unexpected results and is <b>not recommended</b> to enable.`,
  'application.enableRerunActiveExecutions': `
        When this option is enabled, the re-run option also appears on active executions. This is usually not needed
        but may sometimes be useful for submitting multiple executions with identical parameters.`,
  'application.instance.port': `
     <p>This field is only used to generate links within Spinnaker to a running instance when viewing an
     instance's details.</p> The instance port can be used or overridden for specific links configured for your
     application (via the Config screen).
  `,
};

Object.keys(helpContents).forEach((key) => HelpContentsRegistry.register(key, helpContents[key]));
