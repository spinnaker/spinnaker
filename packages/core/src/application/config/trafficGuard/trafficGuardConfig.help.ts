import { HelpContentsRegistry } from '../../../help/helpContents.registry';

const helpContents: any[] = [
  {
    key: 'trafficGuard.region',
    contents: '<p>Required; you can select the wildcard (*) to include all regions.</p>',
  },
  {
    key: 'trafficGuard.stack',
    contents: `<p>Optional; you can use the wildcard (*) to include all stacks (including no stack).
               To apply the guard <em>only</em> to a cluster without a stack, leave this field blank.</p>`,
  },
  {
    key: 'trafficGuard.detail',
    contents: `<p>Optional; you can use the wildcard (*) to include all stacks (including no detail).
               To apply the guard <em>only</em> to a cluster without a detail, leave this field blank.</p>`,
  },
];

helpContents.forEach((entry: any) => HelpContentsRegistry.register(entry.key, entry.contents));
