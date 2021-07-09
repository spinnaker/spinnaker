import classNames from 'classnames';
import { Field, FieldProps, FormikErrors, FormikProps } from 'formik';
import { chain, groupBy, isNil, uniq } from 'lodash';
import React from 'react';
import { combineLatest as observableCombineLatest, from as observableFrom, Observable, Subject } from 'rxjs';
import { distinctUntilChanged, map, shareReplay, switchMap, takeUntil, withLatestFrom } from 'rxjs/operators';

import {
  AccountSelectInput,
  AccountService,
  Application,
  HelpField,
  IAccount,
  IMoniker,
  IRegion,
  ISubnet,
  IWizardPageComponent,
  NameUtils,
  RegionSelectField,
  Spinner,
  SubnetReader,
  ValidationMessage,
} from '@spinnaker/core';
import { AWSProviderSettings } from '../../../aws.settings';
import { IAmazonLoadBalancer, IAmazonLoadBalancerUpsertCommand } from '../../../domain';
import { AvailabilityZoneSelector } from '../../../serverGroup/AvailabilityZoneSelector';
import { SubnetSelectField } from '../../../subnet';

export interface ISubnetOption {
  availabilityZones: string[];
  deprecated?: boolean;
  label: string;
  purpose: string;
  vpcIds: string[];
}

export interface ILoadBalancerLocationProps {
  app: Application;
  formik: FormikProps<IAmazonLoadBalancerUpsertCommand>;
  forPipelineConfig?: boolean;
  isNew?: boolean;
  loadBalancer?: IAmazonLoadBalancer;
}

export interface ILoadBalancerLocationState {
  accounts: IAccount[];
  availabilityZones: string[];
  existingLoadBalancerNames: string[];
  hideInternalFlag: boolean;
  internalFlagToggled: boolean;
  regions: IRegion[];
  subnets: ISubnetOption[];
}

export class LoadBalancerLocation
  extends React.Component<ILoadBalancerLocationProps, ILoadBalancerLocationState>
  implements IWizardPageComponent<IAmazonLoadBalancerUpsertCommand> {
  public state: ILoadBalancerLocationState = {
    accounts: undefined,
    availabilityZones: [],
    existingLoadBalancerNames: [],
    hideInternalFlag: false,
    internalFlagToggled: false,
    regions: [],
    subnets: [],
  };

  private props$ = new Subject<ILoadBalancerLocationProps>();
  private destroy$ = new Subject<void>();

  public validate(values: IAmazonLoadBalancerUpsertCommand) {
    const errors = {} as FormikErrors<IAmazonLoadBalancerUpsertCommand>;

    if (this.state.existingLoadBalancerNames.includes(values.name)) {
      errors.name = `There is already a load balancer in ${values.credentials}:${values.region} with that name.`;
    }

    if (values.name && values.name.length > 32) {
      errors.name = 'Load balancer names cannot exceed 32 characters in length';
    }

    if (values.stack && !values.stack.match(/^[a-zA-Z0-9]*$/)) {
      errors.stack = 'Stack can only contain letters and numbers.';
    }

    if (values.detail && !values.detail.match(/^[a-zA-Z0-9-]*$/)) {
      errors.detail = 'Detail can only contain letters, numbers, and dashes.';
    }

    return errors;
  }

  protected buildName(): void {
    const { values } = this.props.formik;
    if (isNil(values.moniker)) {
      const nameParts = NameUtils.parseLoadBalancerName(values.name);
      values.stack = nameParts.stack;
      values.detail = nameParts.freeFormDetails;
    } else {
      values.stack = values.moniker.stack;
      values.detail = values.moniker.detail;
    }
    delete values.name;
  }

  private shouldHideInternalFlag(): boolean {
    if (AWSProviderSettings) {
      if (AWSProviderSettings.loadBalancers && AWSProviderSettings.loadBalancers.inferInternalFlagFromSubnet) {
        // clouddriver will check the subnet if isInternal is competely omitted
        delete this.props.formik.values.isInternal;
        return true;
      }
    }
    return false;
  }

  public componentDidMount(): void {
    this.setState({ hideInternalFlag: this.shouldHideInternalFlag() });
    if (this.props.loadBalancer && this.props.isNew) {
      this.buildName();
    }

    const formValues$ = this.props$.pipe(map((props) => props.formik.values));
    const appName$ = this.props$.pipe(
      map((props) => props.app.name),
      distinctUntilChanged(),
    );

    const form = {
      account$: formValues$.pipe(
        map((x) => x.credentials),
        distinctUntilChanged(),
      ),
      region$: formValues$.pipe(
        map((x) => x.region),
        distinctUntilChanged(),
      ),
      subnetPurpose$: formValues$.pipe(
        map((x) => x.subnetType),
        distinctUntilChanged(),
      ),
      stack$: formValues$.pipe(
        map((x) => x.stack),
        distinctUntilChanged(),
      ),
      detail$: formValues$.pipe(
        map((x) => x.detail),
        distinctUntilChanged(),
      ),
    };

    const allAccounts$ = observableFrom(AccountService.listAccounts('aws')).pipe(shareReplay(1));

    // combineLatest with allAccounts to wait for accounts to load and be cached
    const accountRegions$ = observableCombineLatest([form.account$, allAccounts$]).pipe(
      switchMap(([currentAccount, _allAccounts]) => AccountService.getRegionsForAccount(currentAccount)),
      shareReplay(1),
    );

    const allLoadBalancers$ = this.props.app.getDataSource('loadBalancers').data$ as Observable<IAmazonLoadBalancer[]>;
    const regionLoadBalancers$ = observableCombineLatest([allLoadBalancers$, form.account$, form.region$]).pipe(
      map(([allLoadBalancers, currentAccount, currentRegion]) => {
        return allLoadBalancers
          .filter((lb) => lb.account === currentAccount && lb.region === currentRegion)
          .map((lb) => lb.name);
      }),
      shareReplay(1),
    );

    const regionSubnets$ = observableCombineLatest([form.account$, form.region$]).pipe(
      switchMap(([currentAccount, currentRegion]) => this.getAvailableSubnets(currentAccount, currentRegion)),
      map((availableSubnets) => this.makeSubnetOptions(availableSubnets)),
      shareReplay(1),
    );

    const subnet$ = observableCombineLatest([regionSubnets$, form.subnetPurpose$]).pipe(
      map(([allSubnets, subnetPurpose]) => allSubnets && allSubnets.find((subnet) => subnet.purpose === subnetPurpose)),
    );

    // I don't understand why we use subnet.availabilityZones here, but region.availabilityZones below.
    const availabilityZones$ = subnet$.pipe(map((subnet) => (subnet ? uniq(subnet.availabilityZones).sort() : [])));

    // Update selected zones when the selected region changes
    const regionZones$ = form.region$.pipe(
      withLatestFrom(accountRegions$),
      map(([currentRegion, accountRegions]) => accountRegions.find((region) => region.name === currentRegion)),
      map((region) => (region ? region.availabilityZones : [])),
    );

    const moniker$ = observableCombineLatest([appName$, form.stack$, form.detail$]).pipe(
      map(([app, stack, detail]) => {
        return { app, stack, detail, cluster: NameUtils.getClusterName(app, stack, detail) } as IMoniker;
      }),
    );

    accountRegions$
      .pipe(withLatestFrom(form.region$), takeUntil(this.destroy$))
      .subscribe(([accountRegions, selectedRegion]) => {
        // If the selected region doesn't exist in the new list of regions (for a new acct), select the first region.
        if (!accountRegions.some((x) => x.name === selectedRegion)) {
          this.props.formik.setFieldValue('region', accountRegions[0] && accountRegions[0].name);
        }
      });

    regionZones$.pipe(takeUntil(this.destroy$)).subscribe((regionZones) => {
      this.props.formik.setFieldValue('regionZones', regionZones);
    });

    subnet$.pipe(takeUntil(this.destroy$)).subscribe((subnet) => {
      this.props.formik.setFieldValue('vpcId', subnet && subnet.vpcIds[0]);
      this.props.formik.setFieldValue('subnetType', subnet && subnet.purpose);
      if (!this.state.hideInternalFlag && !this.state.internalFlagToggled && subnet && subnet.purpose) {
        // Even if inferInternalFlagFromSubnet is false, deck will still try to guess which the user wants unless explicitly toggled
        this.props.formik.setFieldValue('isInternal', subnet.purpose.includes('internal'));
      }
    });

    moniker$.pipe(takeUntil(this.destroy$)).subscribe((moniker) => {
      this.props.formik.setFieldValue('moniker', moniker);
      this.props.formik.setFieldValue('name', moniker.cluster);
    });

    observableCombineLatest([allAccounts$, accountRegions$, availabilityZones$, regionLoadBalancers$, regionSubnets$])
      .pipe(takeUntil(this.destroy$))
      .subscribe(([accounts, regions, availabilityZones, existingLoadBalancerNames, subnets]) => {
        return this.setState({ accounts, regions, availabilityZones, existingLoadBalancerNames, subnets });
      });
  }

  public componentDidUpdate() {
    this.props$.next(this.props);
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  private internalFlagChanged = (event: React.ChangeEvent<any>): void => {
    this.setState({ internalFlagToggled: true });
    this.props.formik.handleChange(event);
  };

  private getAvailableSubnets(credentials: string, region: string): PromiseLike<ISubnet[]> {
    return SubnetReader.listSubnets().then((subnets) => {
      return chain(subnets)
        .filter({ account: credentials, region })
        .reject({ target: 'ec2' })
        .reject({ purpose: null })
        .value();
    });
  }

  private handleSubnetUpdated = (subnetType: string): void => {
    this.props.formik.setFieldValue('subnetType', subnetType);
  };

  private makeSubnetOptions(availableSubnets: ISubnet[]): ISubnetOption[] {
    const makeSubnetOption = (subnets: ISubnet[]) => {
      const { purpose, label, deprecated } = subnets[0];
      const vpcIds = uniq(subnets.map((x) => x.vpcId));
      const availabilityZones = uniq(subnets.map((x) => x.availabilityZone));
      return { purpose, label, deprecated, vpcIds, availabilityZones } as ISubnetOption;
    };

    const grouped = groupBy(availableSubnets, (sn) => sn.purpose);
    return Object.keys(grouped)
      .map((k) => grouped[k])
      .map((subnets) => makeSubnetOption(subnets));
  }

  private accountUpdated = (account: string): void => {
    this.props.formik.setFieldValue('credentials', account);
  };

  private regionUpdated = (region: string): void => {
    this.props.formik.setFieldValue('region', region);
  };

  private stackChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
    this.props.formik.setFieldValue('stack', event.target.value);
  };

  private detailChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
    this.props.formik.setFieldValue('detail', event.target.value);
  };

  private handleAvailabilityZonesChanged = (zones: string[]): void => {
    this.props.formik.setFieldValue('regionZones', zones);
  };

  public render() {
    const { app } = this.props;
    const { errors, values } = this.props.formik;
    const { accounts, availabilityZones, hideInternalFlag, regions, subnets } = this.state;

    const className = classNames({
      'col-md-12': true,
      well: true,
      'alert-danger': !!errors.name,
      'alert-info': !errors.name,
    });

    return (
      <div className="container-fluid form-horizontal">
        {!accounts && (
          <div style={{ height: '200px' }}>
            <Spinner size="medium" />
          </div>
        )}
        {accounts && (
          <div className="modal-body">
            <div className="form-group">
              <div className={className}>
                <strong>Your load balancer will be named: </strong>
                <span>{values.name}</span>
                <HelpField id="aws.loadBalancer.name" />
                <Field type="text" style={{ display: 'none' }} className="form-control input-sm no-spel" name="name" />
                {errors.name && <ValidationMessage type="error" message={errors.name} />}
              </div>
            </div>
            <div className="form-group">
              <div className="col-md-3 sm-label-right">Account</div>
              <div className="col-md-7">
                <AccountSelectInput
                  value={values.credentials}
                  onChange={(evt: any) => this.accountUpdated(evt.target.value)}
                  accounts={accounts}
                  provider="aws"
                />
              </div>
            </div>
            <RegionSelectField
              labelColumns={3}
              component={values}
              field="region"
              account={values.credentials}
              onChange={this.regionUpdated}
              regions={regions}
            />
            <div className="form-group">
              <div className="col-md-3 sm-label-right">
                Stack <HelpField id="aws.loadBalancer.stack" />
              </div>
              <div className="col-md-3">
                <input
                  type="text"
                  className={`form-control input-sm no-spel ${errors.stack ? 'invalid' : ''}`}
                  value={values.stack}
                  name="stack"
                  onChange={this.stackChanged}
                />
              </div>
              <div className="col-md-6 form-inline">
                <label className="sm-label-right">
                  <span>
                    Detail <HelpField id="aws.loadBalancer.detail" />{' '}
                  </span>
                </label>
                <input
                  type="text"
                  className={`form-control input-sm no-spel ${errors.detail ? 'invalid' : ''}`}
                  value={values.detail}
                  name="detail"
                  onChange={this.detailChanged}
                />
              </div>
              {errors.stack && (
                <div className="col-md-7 col-md-offset-3">
                  <ValidationMessage type="error" message={errors.stack} />
                </div>
              )}
              {errors.detail && (
                <div className="col-md-7 col-md-offset-3">
                  <ValidationMessage type="error" message={errors.detail} />
                </div>
              )}
            </div>

            <AvailabilityZoneSelector
              credentials={values.credentials}
              region={values.region}
              onChange={this.handleAvailabilityZonesChanged}
              selectedZones={values.regionZones}
              allZones={availabilityZones}
            />
            <SubnetSelectField
              labelColumns={3}
              helpKey="aws.loadBalancer.subnet"
              component={values}
              field="subnetType"
              region={values.region}
              subnets={subnets as any}
              application={app}
              onChange={() => this.handleSubnetUpdated(values.subnetType)}
            />
            {values.vpcId && !hideInternalFlag && (
              <div className="form-group">
                <div className="col-md-3 sm-label-right">
                  <b>Internal</b> <HelpField id="aws.loadBalancer.internal" />
                </div>
                <div className="col-md-7 checkbox">
                  <label>
                    <Field
                      name="isInternal"
                      onChange={this.internalFlagChanged}
                      render={({ field: { value, ...field } }: FieldProps) => (
                        <input type="checkbox" {...field} checked={!!value} />
                      )}
                    />
                    Create an internal load balancer
                  </label>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    );
  }
}
