import { CloneServerGroupExecutionDetailsComponent } from './CloneServerGroupExecutionDetails';
import { setDirectRouter } from '../../../../navigation/directRouter';

describe('CloneServerGroupExecutionDetails', () => {
  beforeEach(() => {
    const params = { project: 'facade-project' };
    setDirectRouter({ globals: { params }, stateService: { params, href: () => '' } } as any);
  });

  afterEach(() => setDirectRouter(null));

  it('uses the injected project route param for deployed server group links', () => {
    const component = new CloneServerGroupExecutionDetailsComponent({
      stage: {
        context: {
          application: 'application',
          cloudProvider: 'aws',
          credentials: 'account',
          'kato.tasks': [
            {
              resultObjects: {
                deploy: { serverGroupNames: ['us-east-1:server-group'] },
              },
            },
          ],
        },
      },
      stateParams: { project: 'injected-project' },
    } as any);
    spyOn(component, 'setState');

    (component as any).addDeployedArtifacts(component.props);

    const deployResults = (component.setState as jasmine.Spy).calls.mostRecent().args[0].deployResults;
    expect(deployResults[0].project).toBe('injected-project');
  });
});
