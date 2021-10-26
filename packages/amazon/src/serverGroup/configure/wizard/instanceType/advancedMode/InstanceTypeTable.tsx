import React from 'react';
import type { SortEnd } from 'react-sortable-hoc';
import { arrayMove } from 'react-sortable-hoc';

import { CpuCreditsToggle } from '../CpuCreditsToggle';
import { InstanceTypeTableBody } from './InstanceTypeTableBody';
import { Header, Heading } from './InstanceTypeTableParts';
import { Footer } from './InstanceTypeTableParts';
import { AWSProviderSettings } from '../../../../../aws.settings';
import type { IAmazonInstanceTypeCategory } from '../../../../../instance/awsInstanceType.service';
import type { IAmazonInstanceTypeOverride } from '../../../serverGroupConfiguration.service';

import './advancedMode.less';

export interface IInstanceTypeTableProps {
  currentProfile: string;
  selectedInstanceTypesMap: Map<string, IAmazonInstanceTypeOverride>;
  unlimitedCpuCreditsInCmd: boolean;
  profileDetails: IAmazonInstanceTypeCategory;
  availableInstanceTypesList: string[];
  handleInstanceTypesChange: (instanceTypes: IAmazonInstanceTypeOverride[]) => void;
  setUnlimitedCpuCredits: (unlimitedCpuCredits: boolean | undefined) => void;
}

export function InstanceTypeTable(props: IInstanceTypeTableProps) {
  const { currentProfile, selectedInstanceTypesMap } = props;

  const handleSortEnd = (sortEnd: SortEnd): void => {
    const sortedInstanceTypes: string[] = Array.from(selectedInstanceTypesMap.values())
      .sort((i1, i2) => i1.priority - i2.priority)
      .map((it) => it.instanceType);
    const instanceTypesInNewOrder = arrayMove(sortedInstanceTypes, sortEnd.oldIndex, sortEnd.newIndex);

    updatePriorityForSelectedTypes(instanceTypesInNewOrder);
  };

  const updatePriorityForSelectedTypes = (instanceTypesInNewOrder: string[]): void => {
    selectedInstanceTypesMap.forEach((value, key) => {
      const newPriority = 1 + instanceTypesInNewOrder.indexOf(key);
      if (value.priority !== newPriority) {
        value.priority = newPriority;
      }
    });
    props.handleInstanceTypesChange(Array.from(selectedInstanceTypesMap.values()));
  };

  const removeInstanceType = (instanceType: string): void => {
    const selectedInstanceTypesMapNew = new Map(selectedInstanceTypesMap);
    selectedInstanceTypesMapNew.delete(instanceType);
    props.handleInstanceTypesChange(Array.from(selectedInstanceTypesMapNew.values()));
  };

  const addOrUpdateInstanceType = (type: string, weight: string) => {
    const weightNum = Number(weight);
    const weightedCapacity = isNaN(weightNum) || weightNum === 0 ? undefined : weightNum.toString();
    const itemToUpdate = selectedInstanceTypesMap.has(type)
      ? {
          ...selectedInstanceTypesMap.get(type), // update existing item
          weightedCapacity,
        }
      : {
          instanceType: type, // new item
          weightedCapacity,
          priority:
            1 +
            Array.from(selectedInstanceTypesMap.values()).reduce(
              (max, it) => (it.priority > max ? it.priority : max),
              0,
            ),
        };
    selectedInstanceTypesMap.set(type, itemToUpdate);
    props.handleInstanceTypesChange(Array.from(selectedInstanceTypesMap.values()));
  };

  const isCpuCreditsEnabled: boolean = AWSProviderSettings.serverGroups?.enableCpuCredits;
  const selectedInstanceTypeNames = Array.from(selectedInstanceTypesMap.keys());
  const cpuCreditsToggle = (
    <div>
      <CpuCreditsToggle
        unlimitedCpuCredits={props.unlimitedCpuCreditsInCmd}
        currentProfile={currentProfile}
        selectedInstanceTypes={selectedInstanceTypeNames}
        setUnlimitedCpuCredits={props.setUnlimitedCpuCredits}
      />
    </div>
  );

  if (currentProfile && currentProfile !== 'custom') {
    const { label, descriptionListOverride, families, showCpuCredits } = props.profileDetails;
    const isCustom = false;
    return (
      <div className={'row sub-section'}>
        <Heading
          isCustom={isCustom}
          profileLabel={label}
          profileDescriptionArr={descriptionListOverride ? descriptionListOverride : families.map((f) => f.description)}
        />
        {isCpuCreditsEnabled && cpuCreditsToggle}
        <table className="table table-hover">
          <Header isCustom={isCustom} showCpuCredits={showCpuCredits} />
          <InstanceTypeTableBody
            isCustom={isCustom}
            profileFamiliesDetails={families}
            selectedInstanceTypesMap={selectedInstanceTypesMap}
            addOrUpdateInstanceType={addOrUpdateInstanceType}
            removeInstanceType={removeInstanceType}
            handleSortEnd={handleSortEnd}
          />
        </table>
      </div>
    );
  } else {
    const isCustom = true;
    return (
      <div className={'row sub-section'}>
        <Heading isCustom={isCustom} />
        {isCpuCreditsEnabled && cpuCreditsToggle}
        <table className="table table-hover">
          <Header isCustom={isCustom} />
          <InstanceTypeTableBody
            isCustom={isCustom}
            selectedInstanceTypesMap={selectedInstanceTypesMap}
            addOrUpdateInstanceType={addOrUpdateInstanceType}
            removeInstanceType={removeInstanceType}
            handleSortEnd={handleSortEnd}
          />
          <Footer
            isCustom={isCustom}
            availableInstanceTypesList={props.availableInstanceTypesList.filter(
              (it) => !selectedInstanceTypeNames.includes(it),
            )}
            addOrUpdateInstanceType={addOrUpdateInstanceType}
          />
        </table>
      </div>
    );
  }
}
