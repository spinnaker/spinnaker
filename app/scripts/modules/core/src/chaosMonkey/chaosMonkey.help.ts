import { HelpContentsRegistry } from '../help/helpContents.registry';

const helpContents: any[] = [
  {
    key: 'application.chaos.enabled',
    contents:
      '<p>Chaos Monkey periodically terminates instances in your server groups to ensure resiliency.</p>' +
      '<p>If you do <b>not</b> want your application to participate in Chaos Monkey, unselect this option.</p>',
  },
  {
    key: 'chaos.meanTime',
    contents: '<p>The average number of days between terminations for each group</p>',
  },
  {
    key: 'chaos.minTime',
    contents: '<p>The minimum number of days Chaos Monkey will leave the groups alone</p>',
  },
  {
    key: 'chaos.grouping',
    contents:
      '<p>Tells Chaos Monkey how to decide which instances to terminate:</p>' +
      '<ul>' +
      '<li><b>App:</b> Only terminate one instance in the entire application, across stacks and clusters</li>' +
      '<li><b>Stack:</b> Only terminate one instance in each stack</li>' +
      '<li><b>Cluster:</b> Terminate an instance in every cluster</li>' +
      '</ul>',
  },
  {
    key: 'chaos.regions',
    contents:
      '<p>If selected, Chaos Monkey will treat each region in each group separately, e.g. if your cluster ' +
      'is deployed in three regions, an instance in each region would be terminated.</p>',
  },
  {
    key: 'chaos.exceptions',
    contents:
      '<p>When Chaos Monkey is enabled, exceptions tell Chaos Monkey to leave certain clusters alone. ' +
      'You can use wildcards (*) to include all matching fields.</p>',
  },
  {
    key: 'chaos.documentation',
    contents: `<p>Chaos Monkey documentation can be found
                     <a target="_blank" href="https://github.com/Netflix/chaosmonkey/blob/master/README.md">
                       here
                     </a>.
                   </p>`,
  },
];

helpContents.forEach((entry: any) => HelpContentsRegistry.register(entry.key, entry.contents));
