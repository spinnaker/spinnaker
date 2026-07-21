import { AngularJSAdapter } from './AngularJSAdapter';

describe('AngularJSAdapter', () => {
  it('exposes locals as scope props and schedules an Angular digest when React props change', () => {
    const adapter = new AngularJSAdapter({ template: '<div />', locals: { value: 'initial' } });
    const $scope = {
      $evalAsync: jasmine.createSpy('$evalAsync'),
      props: undefined as unknown,
    };
    (adapter as any).$scope = $scope;

    adapter.componentWillReceiveProps({ template: '<div />', locals: { value: 'updated' } });

    expect(($scope.props as any).value).toBe('updated');
    expect($scope.$evalAsync).toHaveBeenCalledTimes(1);
  });
});
