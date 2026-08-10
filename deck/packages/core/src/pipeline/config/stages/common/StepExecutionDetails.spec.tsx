import { StepExecutionDetailsComponent } from './StepExecutionDetails';
import { setDirectRouter } from '../../../../navigation/directRouter';

describe('StepExecutionDetails router context', () => {
  beforeEach(() => {
    const params = { details: 'facade-details' };
    setDirectRouter({ globals: { params }, stateService: { params } } as any);
  });

  afterEach(() => setDirectRouter(null));

  it('synchronizes the current section from injected route params', () => {
    const component = new StepExecutionDetailsComponent({
      detailsSections: [],
      stateParams: { details: 'injected-details' },
    } as any);
    spyOn(component, 'setState');

    component.updateCurrentSection();

    expect(component.setState).toHaveBeenCalledWith({ currentSection: 'injected-details' });
  });

  it('synchronizes the current section from the next routed props', () => {
    const component = new StepExecutionDetailsComponent({
      detailsSections: [],
      stateParams: { details: 'first-details' },
    } as any);
    spyOn(component, 'setState');

    component.componentWillReceiveProps({
      detailsSections: [],
      stateParams: { details: 'second-details' },
    } as any);

    expect(component.setState).toHaveBeenCalledWith({ currentSection: 'second-details' });
  });
});
