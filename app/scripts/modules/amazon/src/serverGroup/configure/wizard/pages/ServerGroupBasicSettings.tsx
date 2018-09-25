import * as React from 'react';
import { Option } from 'react-select';
import { Observable, Subject } from 'rxjs';
import * as DOMPurify from 'dompurify';

import {
  NgReact,
  HelpField,
  IWizardPageProps,
  wizardPage,
  NameUtils,
  RegionSelectField,
  Application,
  ReactInjector,
  TetheredSelect,
  IServerGroup,
  TaskReason,
  Spinner,
} from '@spinnaker/core';

import { AwsNgReact } from 'amazon/reactShims';

import { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';

const isNotExpressionLanguage = (field: string) => field && !field.includes('${');
const isStackPattern = (stack: string) =>
  isNotExpressionLanguage(stack) ? /^([a-zA-Z_0-9._${}]*(\${.+})*)*$/.test(stack) : true;
const isDetailPattern = (detail: string) =>
  isNotExpressionLanguage(detail) ? /^([a-zA-Z_0-9._${}-]*(\${.+})*)*$/.test(detail) : true;

export interface IServerGroupBasicSettingsProps extends IWizardPageProps<IAmazonServerGroupCommand> {
  app: Application;
}

export interface IServerGroupBasicSettingsState {
  isLoadingImages: boolean;
  images: any[];
  namePreview: string;
  createsNewCluster: boolean;
  latestServerGroup: IServerGroup;
  showPreviewAsWarning: boolean;
}

class ServerGroupBasicSettingsImpl extends React.Component<
  IServerGroupBasicSettingsProps,
  IServerGroupBasicSettingsState
> {
  public static LABEL = 'Basic Settings';

  private imageSearchResultsStream = new Subject();

  // TODO: Extract the image selector into another component
  constructor(props: IServerGroupBasicSettingsProps) {
    super(props);
    const { values } = props.formik;
    const { disableImageSelection } = values.viewState;
    this.state = {
      images: disableImageSelection
        ? []
        : values.backingData.filtered.images.map(i => {
            i.label = this.getImageLabel(i);
            return i;
          }),
      isLoadingImages: false,
      ...this.getStateFromProps(props),
    };
  }

  private getImageLabel(image: any) {
    if (image.label) {
      return image.label;
    }
    return `${image.message || ''}${image.imageName || ''} ${image.ami ? `(${image.ami})` : ''}`;
  }

  private getStateFromProps(props: IServerGroupBasicSettingsProps) {
    const { app } = props;
    const { values } = props.formik;
    const { mode } = values.viewState;

    const namePreview = NameUtils.getClusterName(app.name, values.stack, values.freeFormDetails);
    const createsNewCluster = !app.clusters.find(c => c.name === namePreview);
    const showPreviewAsWarning = (mode === 'create' && !createsNewCluster) || (mode !== 'create' && createsNewCluster);

    const inCluster = (app.serverGroups.data as IServerGroup[])
      .filter(serverGroup => {
        return (
          serverGroup.cluster === namePreview &&
          serverGroup.account === values.credentials &&
          serverGroup.region === values.region
        );
      })
      .sort((a, b) => a.createdTime - b.createdTime);
    const latestServerGroup = inCluster.length ? inCluster.pop() : null;

    return { namePreview, createsNewCluster, latestServerGroup, showPreviewAsWarning };
  }

  private showLoadingSpinner = () => {
    this.setState({
      isLoadingImages: true,
      images: [
        {
          disabled: true,
          message: (
            <div className="horizontal center">
              <Spinner size="small" />
            </div>
          ),
        },
      ],
    });
  };

  public componentDidMount() {
    const { values } = this.props.formik;

    this.imageSearchResultsStream
      .do(this.showLoadingSpinner)
      .debounceTime(250)
      .switchMap(this.searchImagesImpl)
      .subscribe(data => {
        const images = data.map((image: any) => {
          if (image.message && !image.imageName) {
            return image;
          }

          const i = {
            imageName: image.imageName,
            ami: image.amis && image.amis[values.region] ? image.amis[values.region][0] : null,
            virtualizationType: image.attributes ? image.attributes.virtualizationType : null,
            label: '',
          };
          i.label = this.getImageLabel(i);
          return i;
        });

        values.backingData.filtered.images = images;
        values.backingData.packageImages = values.backingData.filtered.images;
        this.setState({ images, isLoadingImages: false });
      });
  }

  private searchImagesImpl = (q: string) => {
    const { selectedProvider, region } = this.props.formik.values;

    const findImagesPromise = ReactInjector.imageReader.findImages({ provider: selectedProvider, q, region });
    return Observable.fromPromise<any[]>(findImagesPromise).map(result => {
      if (result.length === 0 && q.startsWith('ami-') && q.length === 12) {
        // allow 'advanced' users to continue with just an ami id (backing image may not have been indexed yet)
        const record = {
          imageName: q,
          amis: {},
          attributes: {
            virtualizationType: '*',
          },
        } as any;

        // trust that the specific image exists in the selected region
        record.amis[region] = [q];
        result = [record];
      }
      return result;
    });
  };

  private searchImages = (q: string) => {
    this.imageSearchResultsStream.next(q);
  };

  private enableAllImageSearch = () => {
    this.props.formik.values.viewState.useAllImageSelection = true;
    this.searchImages('');
  };

  private imageChanged = (image: any) => {
    const { setFieldValue, values } = this.props.formik;
    values.virtualizationType = image.virtualizationType;
    values.amiName = image.amiName;
    setFieldValue('virtualizationType', image.virtualizationType);
    setFieldValue('amiName', image.imageName);
    values.imageChanged(values);
  };

  private accountUpdated = (account: string): void => {
    const { setFieldValue, values } = this.props.formik;
    values.credentials = account;
    values.credentialsChanged(values);
    values.subnetChanged(values);
    setFieldValue('credentials', account);
  };

  private regionUpdated = (region: string): void => {
    const { values, setFieldValue } = this.props.formik;
    values.region = region;
    values.regionChanged(values);
    setFieldValue('region', region);
  };

  private subnetUpdated = (): void => {
    const { setFieldValue, values } = this.props.formik;
    values.subnetChanged(values);
    setFieldValue('subnetType', values.subnetType);
  };

  public validate(values: IAmazonServerGroupCommand): { [key: string]: string } {
    const errors: { [key: string]: string } = {};

    if (!isStackPattern(values.stack)) {
      errors.stack = 'Only dot(.) and underscore(_) special characters are allowed in the Stack field.';
    }

    if (!isDetailPattern(values.freeFormDetails)) {
      errors.freeFormDetails =
        'Only dot(.), underscore(_), and dash(-) special characters are allowed in the Detail field.';
    }

    if (!values.viewState.disableImageSelection && !values.amiName) {
      errors.amiName = 'Image required.';
    }

    return errors;
  }

  private clientRequestsChanged = () => {
    const { values } = this.props.formik;
    values.toggleSuspendedProcess(values, 'AddToLoadBalancer');
    this.setState({});
  };

  private navigateToLatestServerGroup = () => {
    const { values } = this.props.formik;
    const { latestServerGroup } = this.state;

    const params = {
      provider: values.selectedProvider,
      accountId: latestServerGroup.account,
      region: latestServerGroup.region,
      serverGroup: latestServerGroup.name,
    };

    const { $state } = ReactInjector;
    if ($state.is('home.applications.application.insight.clusters')) {
      $state.go('.serverGroup', params);
    } else {
      $state.go('^.serverGroup', params);
    }
  };

  private stackChanged = (stack: string) => {
    const { setFieldValue, values } = this.props.formik;
    values.stack = stack; // have to do it here to make sure it's done before calling values.clusterChanged
    setFieldValue('stack', stack);
    values.clusterChanged(values);
  };

  private freeFormDetailsChanged = (freeFormDetails: string) => {
    const { setFieldValue, values } = this.props.formik;
    values.freeFormDetails = freeFormDetails; // have to do it here to make sure it's done before calling values.clusterChanged
    setFieldValue('freeFormDetails', freeFormDetails);
    values.clusterChanged(values);
  };

  public componentWillReceiveProps(nextProps: IServerGroupBasicSettingsProps) {
    this.setState(this.getStateFromProps(nextProps));
  }

  private handleReasonChanged = (reason: string) => {
    this.props.formik.setFieldValue('reason', reason);
  };

  private strategyChanged = (values: IAmazonServerGroupCommand, strategy: any) => {
    values.onStrategyChange(values, strategy);
    this.props.formik.setFieldValue('strategy', strategy.key);
  };

  public render() {
    const { app } = this.props;
    const { errors, values } = this.props.formik;
    const {
      createsNewCluster,
      isLoadingImages,
      images,
      latestServerGroup,
      namePreview,
      showPreviewAsWarning,
    } = this.state;
    const { AccountSelectField, DeploymentStrategySelector } = NgReact;
    const { SubnetSelectField } = AwsNgReact;

    const accounts = values.backingData.accounts;
    const readOnlyFields = values.viewState.readOnlyFields || {};

    let selectedImage;
    if (values.amiName && !values.amiName.includes('${')) {
      selectedImage = values.amiName;
    } else if (values.backingData.filtered.images) {
      selectedImage = values.backingData.filtered.images.find(image => image.imageName === values.amiName);
    }

    return (
      <div className="container-fluid form-horizontal">
        {values.regionIsDeprecated(values) && (
          <div className="form-group row">
            <div className="col-md-12 error-message">
              <div className="alert alert-danger">
                You are deploying into a deprecated region within the {values.credentials} account!
              </div>
            </div>
          </div>
        )}
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Account</div>
          <div className="col-md-7">
            <AccountSelectField
              readOnly={readOnlyFields.credentials}
              component={values}
              field="credentials"
              accounts={accounts}
              provider="aws"
              onChange={this.accountUpdated}
            />
          </div>
        </div>
        <RegionSelectField
          readOnly={readOnlyFields.region}
          labelColumns={3}
          component={values}
          field="region"
          account={values.credentials}
          regions={values.backingData.filtered.regions}
          onChange={this.regionUpdated}
        />
        <SubnetSelectField
          readOnly={readOnlyFields.subnet}
          labelColumns={3}
          helpKey="aws.serverGroup.subnet"
          component={values}
          field="subnetType"
          region={values.region}
          application={app}
          subnets={values.backingData.filtered.subnetPurposes}
          onChange={this.subnetUpdated}
        />
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Stack <HelpField id="aws.serverGroup.stack" />
          </div>
          <div className="col-md-7">
            <input
              type="text"
              className="form-control input-sm no-spel"
              value={values.stack}
              onChange={e => this.stackChanged(e.target.value)}
            />
          </div>
        </div>
        {errors.stack && (
          <div className="form-group row slide-in">
            <div className="col-sm-9 col-sm-offset-2 error-message">
              <span>{errors.stack}</span>
            </div>
          </div>
        )}
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Detail <HelpField id="aws.serverGroup.detail" />
          </div>
          <div className="col-md-7">
            <input
              type="text"
              className="form-control input-sm no-spel"
              value={values.freeFormDetails}
              onChange={e => this.freeFormDetailsChanged(e.target.value)}
            />
          </div>
        </div>
        {errors.freeFormDetails && (
          <div className="form-group row slide-in">
            <div className="col-sm-9 col-sm-offset-2 error-message">
              <span>{errors.freeFormDetails}</span>
            </div>
          </div>
        )}
        {values.viewState.imageSourceText && (
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Image Source</div>
            <div className="col-md-7" style={{ marginTop: '5px' }}>
              <span dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(values.viewState.imageSourceText) }} />
            </div>
          </div>
        )}
        {!values.viewState.disableImageSelection && (
          <div className="form-group">
            <div className="col-md-3 sm-label-right">
              Image <HelpField id="aws.serverGroup.imageName" />
            </div>
            {values.viewState.useAllImageSelection && (
              <div className="col-md-9">
                <TetheredSelect
                  clearable={false}
                  placeholder="Search for an image..."
                  required={true}
                  valueKey="imageName"
                  filterOptions={isLoadingImages ? (false as any) : undefined}
                  options={images}
                  optionRenderer={this.imageOptionRenderer}
                  onInputChange={value => this.searchImages(value)}
                  onChange={(value: Option<string>) => this.imageChanged(value)}
                  onSelectResetsInput={false}
                  onBlurResetsInput={false}
                  onCloseResetsInput={false}
                  value={selectedImage}
                  valueRenderer={this.imageOptionRenderer}
                />
              </div>
            )}
            {!values.viewState.useAllImageSelection && (
              <div className="col-md-9">
                <TetheredSelect
                  clearable={false}
                  placeholder="Pick an image"
                  required={true}
                  valueKey="imageName"
                  options={images}
                  optionRenderer={this.imageOptionRenderer}
                  onChange={(value: Option<string>) => this.imageChanged(value)}
                  onSelectResetsInput={false}
                  onBlurResetsInput={false}
                  onCloseResetsInput={false}
                  value={selectedImage}
                  valueRenderer={this.imageOptionRenderer}
                />
                <a className="clickable" onClick={this.enableAllImageSearch}>
                  Search All Images
                </a>{' '}
                <HelpField id="aws.serverGroup.allImages" />
              </div>
            )}
          </div>
        )}
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Traffic <HelpField id="aws.serverGroup.traffic" />
          </div>
          <div className="col-md-9 checkbox">
            <label>
              <input
                type="checkbox"
                onChange={this.clientRequestsChanged}
                checked={!values.processIsSuspended(values, 'AddToLoadBalancer')}
                disabled={values.strategy !== '' && values.strategy !== 'custom'}
              />
              Send client requests to new instances
            </label>
          </div>
        </div>
        {!values.viewState.disableStrategySelection &&
          values.selectedProvider && (
            <DeploymentStrategySelector command={values} onStrategyChange={this.strategyChanged} />
          )}
        {!values.viewState.hideClusterNamePreview && (
          <div className="form-group">
            <div className="col-md-12">
              <div className={`well-compact ${showPreviewAsWarning ? 'alert alert-warning' : 'well'}`}>
                <h5 className="text-center">
                  <p>Your server group will be in the cluster:</p>
                  <p>
                    <strong>
                      {namePreview}
                      {createsNewCluster && <span> (new cluster)</span>}
                    </strong>
                  </p>
                  {!createsNewCluster &&
                    values.viewState.mode === 'create' &&
                    latestServerGroup && (
                      <div className="text-left">
                        <p>There is already a server group in this cluster. Do you want to clone it?</p>
                        <p>
                          Cloning copies the entire configuration from the selected server group, allowing you to modify
                          whichever fields (e.g. image) you need to change in the new server group.
                        </p>
                        <p>
                          To clone a server group, select "Clone" from the "Server Group Actions" menu in the details
                          view of the server group.
                        </p>
                        <p>
                          <a className="clickable" onClick={this.navigateToLatestServerGroup}>
                            Go to details for {latestServerGroup.name}
                          </a>
                        </p>
                      </div>
                    )}
                </h5>
              </div>
            </div>
          </div>
        )}
        <TaskReason reason={values.reason} onChange={this.handleReasonChanged} />
      </div>
    );
  }

  private imageOptionRenderer = (option: Option) => {
    return (
      <>
        <span>{option.message}</span>
        <span>{option.imageName}</span>
        {option.ami && (
          <span>
            {' '}
            (<span>{option.ami}</span>)
          </span>
        )}
      </>
    );
  };
}

export const ServerGroupBasicSettings = wizardPage<IServerGroupBasicSettingsProps>(ServerGroupBasicSettingsImpl);
