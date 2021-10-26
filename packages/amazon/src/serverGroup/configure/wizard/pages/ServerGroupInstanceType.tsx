import type { FormikErrors, FormikProps } from 'formik';
import { some } from 'lodash';
import React from 'react';
import { Subject } from 'rxjs';

import type { IWizardPageComponent } from '@spinnaker/core';

import type { IAmazonInstanceTypeCategory } from '../../../../instance/awsInstanceType.service';
import { InstanceTypeSelector } from '../instanceType/InstanceTypeSelector';
import { AwsReactInjector } from '../../../../reactShims';
import type { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';

export interface IServerGroupInstanceTypeProps {
  formik: FormikProps<IAmazonServerGroupCommand>;
}

export interface IServerGroupInstanceTypeState {
  instanceTypeDetails: IAmazonInstanceTypeCategory[];
}

export class ServerGroupInstanceType
  extends React.Component<IServerGroupInstanceTypeProps, IServerGroupInstanceTypeState>
  implements IWizardPageComponent<IAmazonServerGroupCommand> {
  public state: IServerGroupInstanceTypeState = {
    instanceTypeDetails: [],
  };
  private props$ = new Subject<IServerGroupInstanceTypeProps>();
  private destroy$ = new Subject<void>();

  public validate(values: IAmazonServerGroupCommand): FormikErrors<IAmazonServerGroupCommand> {
    const errors: FormikErrors<IAmazonServerGroupCommand> = {};

    if (values.viewState.useSimpleInstanceTypeSelector) {
      if (!values.instanceType) {
        errors.instanceType = 'Instance Type required.';
      }
    } else {
      const advancedModeErrors = this.validateAdvancedModeFields(values, errors);
      Object.assign(errors, { ...advancedModeErrors });
    }

    return errors;
  }

  private validateAdvancedModeFields(
    values: IAmazonServerGroupCommand,
    errors: FormikErrors<IAmazonServerGroupCommand>,
  ): FormikErrors<IAmazonServerGroupCommand> {
    if (!values.launchTemplateOverridesForInstanceType.length) {
      errors.instanceType = 'At least one instance type required.';
    }
    if (values.launchTemplateOverridesForInstanceType.length > 40) {
      errors.instanceType = 'Maximum of 40 instance types are allowed.';
    }
    const weightsSpecified = values.launchTemplateOverridesForInstanceType.filter(
      (it) => it.weightedCapacity !== undefined,
    );
    if (
      !(
        weightsSpecified.length === values.launchTemplateOverridesForInstanceType.length ||
        weightsSpecified.length === 0
      )
    ) {
      errors.instanceType = 'Weighted capacity must be specified for all instance types selected or none.';
    }
    if (
      some(weightsSpecified, function (instanceType) {
        const weightedCapacity = Number(instanceType.weightedCapacity);
        return weightedCapacity < 1 || weightedCapacity > 999;
      })
    ) {
      errors.instanceType = 'Weighted capacity must be a number between 1 and 999.';
    }

    if (values.onDemandBaseCapacity < 0) {
      errors.onDemandBaseCapacity = 'On-Demand base capacity must be non-negative.';
    }

    if (values.onDemandBaseCapacity > values.capacity.max) {
      errors.onDemandBaseCapacity = 'On-Demand base capacity must be less than or equal to max capacity.';
    }

    if (values.onDemandPercentageAboveBaseCapacity < 0 || values.onDemandPercentageAboveBaseCapacity > 100) {
      errors.onDemandPercentageAboveBaseCapacity =
        'On-Demand percentage above base capacity must be a number between 0 and 100.';
    }

    if (values.spotPrice) {
      const spotPriceNum = Number(values.spotPrice);
      if (Number.isNaN(spotPriceNum)) {
        errors.spotPrice = 'Spot Max Price must be a number or empty string, used to unset previous max price.';
      }
      if (spotPriceNum <= 0) {
        errors.spotPrice = 'Spot Max Price must be greater than 0, if specified.';
      }
    }

    if (values.spotInstancePools && values.spotAllocationStrategy !== 'lowest-price') {
      errors.spotInstancePools = "Spot Instance Pools is only supported for 'lowest-price' Spot Allocation Strategy.";
    }
    if (values.spotInstancePools <= 0 || values.spotInstancePools > 20) {
      errors.spotInstancePools = 'Spot Instance Pools count must be a number between 1 and 20, when applicable.';
    }

    return errors;
  }

  public componentDidMount(): void {
    Promise.resolve(AwsReactInjector.awsInstanceTypeService.getCategories()).then(
      (categories: IAmazonInstanceTypeCategory[]) => {
        this.setState({ instanceTypeDetails: categories });
      },
    );
  }

  public componentDidUpdate() {
    this.props$.next(this.props);
  }

  public componentWillUnmount() {
    this.destroy$.next();
  }

  public render() {
    const { values } = this.props.formik;
    const showTypeSelector = !!(values.viewState.disableImageSelection || values.amiName);

    // mark unavailable instance types for all profiles
    const availableInstanceTypesForConfig: string[] = values.backingData?.filtered?.instanceTypes ?? [];
    const markedInstanceTypeDetails: IAmazonInstanceTypeCategory[] = Array.from(this.state.instanceTypeDetails);
    if (!values.viewState.disableImageSelection && availableInstanceTypesForConfig.length) {
      markedInstanceTypeDetails.forEach((profile) => {
        profile.families.forEach((family) => {
          family.instanceTypes.forEach((instanceType) => {
            instanceType.unavailable = !availableInstanceTypesForConfig.includes(instanceType.name);
          });
        });
      });
    }

    if (showTypeSelector && values) {
      return <InstanceTypeSelector formik={this.props.formik} instanceTypeDetails={markedInstanceTypeDetails} />;
    }

    return <h5 className="text-center">Please select an image.</h5>;
  }
}
