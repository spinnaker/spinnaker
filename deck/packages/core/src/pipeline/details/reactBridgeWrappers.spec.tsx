import { shallow } from 'enzyme';
import React from 'react';

import { AngularJSAdapter } from '../../reactShims';
import { TargetSelect } from '../config/targetSelect.component';
import { InstanceArchetypeSelector } from '../../serverGroup/configure/common/InstanceArchetypeSelector';
import { InstanceTypeSelector } from '../../serverGroup/configure/common/InstanceTypeSelector';
import { AccountRegionClusterSelector } from '../../widgets/AccountRegionClusterSelector';
import { StageSummary } from './StageSummary';
import { StageSummaryWrapper } from './StageSummaryWrapper';
import { StepDetails } from './StepDetails';
import { StepExecutionDetailsWrapper } from './StepExecutionDetailsWrapper';

describe('pipeline details bridge wrappers', () => {
  it('renders the direct StageSummaryWrapper for Angular summary templates', () => {
    const props = {
      application: {} as any,
      config: {},
      execution: {} as any,
      stage: {} as any,
      stageSummary: {} as any,
    };

    const component = shallow(<StageSummary {...props} />);

    expect(component.find(StageSummaryWrapper).length).toBe(1);
    expect(component.find(StageSummaryWrapper).prop('sourceUrl')).toBeDefined();
  });

  it('renders StageSummaryWrapper through the Angular component adapter', () => {
    const component = shallow(
      <StageSummaryWrapper
        application={{} as any}
        execution={{} as any}
        sourceUrl="template.html"
        stage={{} as any}
        stageSummary={{} as any}
      />,
    );

    expect(component.find(AngularJSAdapter).prop('template')).toContain('stage-summary');
  });

  it('renders the direct StepExecutionDetailsWrapper for Angular detail templates', () => {
    const props = {
      application: {} as any,
      config: { cloudProvider: 'aws' },
      execution: {} as any,
      stage: {} as any,
    };

    const component = shallow(<StepDetails {...props} />);

    expect(component.find(StepExecutionDetailsWrapper).length).toBe(1);
    expect(component.find(StepExecutionDetailsWrapper).prop('sourceUrl')).toBeDefined();
  });

  it('renders StepExecutionDetailsWrapper through the Angular component adapter', () => {
    const component = shallow(
      <StepExecutionDetailsWrapper
        application={{} as any}
        config={{ cloudProvider: 'aws' }}
        configSections={[]}
        execution={{} as any}
        sourceUrl="template.html"
        stage={{} as any}
      />,
    );

    expect(component.find(AngularJSAdapter).prop('template')).toContain('step-execution-details');
    expect(component.find(AngularJSAdapter).prop('template')).toContain('config="props.config"');
  });

  it('renders selector wrappers through explicit Angular component adapters', () => {
    const command = { viewState: {} } as any;

    const accountRegionClusterTemplate = shallow(
      <AccountRegionClusterSelector
        accounts={[]}
        application={{} as any}
        clusterField="cluster"
        component={{} as any}
      />,
    )
      .find(AngularJSAdapter)
      .prop('template');

    expect(accountRegionClusterTemplate).toContain('account-region-cluster-selector-wrapper');
    expect(accountRegionClusterTemplate).toContain('cluster-field="props.clusterField"');
    expect(accountRegionClusterTemplate).not.toContain('cluster-field="{{::props.clusterField}}"');
    expect(
      shallow(<InstanceArchetypeSelector command={command} onProfileChanged={noop} onTypeChanged={noop} />)
        .find(AngularJSAdapter)
        .prop('template'),
    ).toContain('v2-instance-archetype-selector');
    expect(
      shallow(<InstanceTypeSelector command={command} onTypeChanged={noop} />)
        .find(AngularJSAdapter)
        .prop('template'),
    ).toContain('v2-instance-type-selector');
    expect(
      shallow(<TargetSelect model={{ target: 'current_asg_dynamic' }} options={[]} onChange={noop} />)
        .find(AngularJSAdapter)
        .prop('template'),
    ).toContain('target-select');
  });
});

function noop() {}
