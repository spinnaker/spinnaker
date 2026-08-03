import { shallow } from 'enzyme';
import React from 'react';

import { AllClusters, ClusterControls } from './AllClusters';
import { AllClustersGroupings } from './AllClustersGroupings';
import { OnDemandClusterPicker } from './onDemand/OnDemandClusterPicker';
import { BannerContainer } from '../banner';
import { FilterTags } from '../filterModel';
import { initialize } from '../state';
import { Spinner } from '../widgets';

const makeApplication = (fetchOnDemand: boolean) =>
  ({
    getDataSource: (key: string) => (key === 'serverGroups' ? { fetchOnDemand } : undefined),
  } as any);

describe('AllClusters views', () => {
  beforeEach(() => initialize());

  it('renders one on-demand picker and suppresses filter tags without changing initialized content', () => {
    const app = makeApplication(true);
    const wrapper = shallow(<AllClusters app={app} initialized={true} loadError={false} />);

    expect(wrapper.find(OnDemandClusterPicker)).toHaveSize(1);
    expect(wrapper.find(OnDemandClusterPicker).prop('application')).toBe(app);
    expect(wrapper.find(FilterTags)).toHaveSize(0);
    expect(wrapper.find(ClusterControls)).toHaveSize(1);
    expect(wrapper.find(BannerContainer)).toHaveSize(1);
    expect(wrapper.find(AllClustersGroupings)).toHaveSize(1);
    expect(wrapper.find(Spinner)).toHaveSize(0);
  });

  it('keeps filter tags and omits the picker during normal fetching', () => {
    const wrapper = shallow(<AllClusters app={makeApplication(false)} initialized={true} loadError={false} />);

    expect(wrapper.find(OnDemandClusterPicker)).toHaveSize(0);
    expect(wrapper.find(FilterTags)).toHaveSize(1);
    expect(wrapper.find(ClusterControls)).toHaveSize(1);
    expect(wrapper.find(BannerContainer)).toHaveSize(1);
    expect(wrapper.find(AllClustersGroupings)).toHaveSize(1);
  });

  it('keeps the standard loading view and groupings unchanged', () => {
    const wrapper = shallow(<AllClusters app={makeApplication(false)} initialized={false} loadError={false} />);

    expect(wrapper.find(Spinner)).toHaveSize(1);
    expect(wrapper.find(BannerContainer)).toHaveSize(0);
    expect(wrapper.find(AllClustersGroupings)).toHaveSize(1);
    expect(wrapper.find(OnDemandClusterPicker)).toHaveSize(0);
  });
});
