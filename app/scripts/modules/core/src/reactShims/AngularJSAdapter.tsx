import * as React from 'react';
import * as angular from 'angular';
import { IScope } from 'angular';
import { $compile, $controller } from 'ngimport';

export interface IRenderAngularJSProps extends React.HTMLProps<HTMLDivElement> {
  template: string;
  controller: string;
  locals?: { [key: string]: any };
}

export class AngularJSAdapter extends React.Component<IRenderAngularJSProps> {
  public static defaultProps: Partial<IRenderAngularJSProps> = { locals: {} };
  private $scope: IScope;

  constructor(props: any) {
    super(props);
  }

  public componentWillReceiveProps(nextProps: IRenderAngularJSProps) {
    this.$scope.props = nextProps;
  }

  private refCallback(ref: Element) {
    if (!ref) {
      return;
    }

    const { template, controller, locals } = this.props;
    const $element = angular.element(ref);

    const parentScope = $element.scope();
    const $scope = this.$scope = parentScope.$new();
    $scope.props = this.props;

    $element.html(template);
    $compile(ref)($scope);

    if (controller) {
      const controllerInstance = $controller(controller, { $scope, $element, ...locals });
      $element.data('$ngControllerController', controllerInstance);
    }
  }

  public componentWillUnmount() {
    this.$scope.$destroy();
  }

  public render() {
    const { template, controller, locals, ...rest } = this.props;
    return <div ref={(ref) => this.refCallback(ref)} {...rest} />
  }
}
