import { element, IScope } from 'angular';
import { $compile, $controller, $templateRequest } from 'ngimport';
import React from 'react';

export interface IRenderAngularJSBaseProps extends React.HTMLProps<HTMLDivElement> {
  controller?: string;
  controllerAs?: string;
  locals?: { [key: string]: any };
}
export type IRenderAngularJSTemplateProps = IRenderAngularJSBaseProps & { template: string };
export type IRenderAngularJSTemplateUrlProps = IRenderAngularJSBaseProps & { templateUrl: string };
export type IRenderAngularJSProps = IRenderAngularJSTemplateProps | IRenderAngularJSTemplateUrlProps;

export class AngularJSAdapter extends React.Component<IRenderAngularJSProps> {
  public static defaultProps: Partial<IRenderAngularJSProps> = { locals: {} };
  private $scope: IScope;

  constructor(props: any) {
    super(props);
  }

  public componentWillReceiveProps(nextProps: IRenderAngularJSProps) {
    if (this.$scope) {
      this.$scope.props = nextProps;
    }
  }

  private refCallback(ref: Element) {
    if (!ref) {
      return;
    }

    const { controller, controllerAs, locals } = this.props;
    const template = (this.props as IRenderAngularJSTemplateProps).template;
    const templateUrl = (this.props as IRenderAngularJSTemplateUrlProps).templateUrl;

    const _locals = { ...this.props, ...locals };
    if (templateUrl) {
      $templateRequest(templateUrl).then((templateString) => {
        this.renderAngularTemplateAndController(ref, templateString, controller, controllerAs, _locals);
      });
    } else {
      this.renderAngularTemplateAndController(ref, template, controller, controllerAs, _locals);
    }
  }

  private renderAngularTemplateAndController(
    ref: Element,
    templateString: string,
    controller?: string,
    controllerAs?: string,
    locals?: object,
  ) {
    const $element = element(ref);
    const parentScope = $element.scope();
    const $scope = (this.$scope = parentScope.$new());
    $scope.props = this.props;

    $element.html(templateString);
    $compile(ref)($scope);

    if (controller) {
      const controllerStr = controllerAs ? `${controller} as ${controllerAs}` : controller;
      const controllerInstance = $controller(controllerStr, { $scope, $element, ...locals });
      $element.data('$ngControllerController', controllerInstance);
    }
  }

  public componentWillUnmount() {
    this.$scope.$destroy();
  }

  public render() {
    return <div className="AngularJSAdapter" ref={(ref) => this.refCallback(ref)} />;
  }
}
