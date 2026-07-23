import { isInsightDetailUrl, shouldShowDetailsView } from './InsightLayout';

describe('InsightLayout', () => {
  it('shows the detail outlet when the active state targets an insight detail view', () => {
    expect(shouldShowDetailsView({ views: { 'detail@../insight': {} } })).toBe(true);
  });

  it('shows the detail outlet for nested insight detail states without a retained detail view key', () => {
    expect(
      shouldShowDetailsView({ name: 'home.applications.application.insight.clusters.instanceDetails', views: {} }),
    ).toBe(true);
  });

  it('does not show the detail outlet for master-only insight states', () => {
    expect(
      shouldShowDetailsView({ name: 'home.applications.application.insight.clusters', views: { nav: {}, master: {} } }),
    ).toBe(false);
  });

  it('recognizes hash routes that target insight detail panels', () => {
    expect(
      isInsightDetailUrl(
        'http://localhost:5173/#/applications/kubernetesapp/clusters/instanceDetails/kubernetes/pod-1',
      ),
    ).toBe(true);
    expect(isInsightDetailUrl('http://localhost:5173/#/applications/kubernetesapp/clusters')).toBe(false);
  });
});
