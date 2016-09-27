'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.tagManager.service', [
    require('../../../../../core/utils/lodash.js'),
  ])
  .factory('gceTagManager', function (_) {
    let resetKeys = ['command', 'securityGroups', 'securityGroupObjectsKeyedByTag', 'securityGroupObjectsKeyedById'];

    this.reset = () => {
      resetKeys.forEach((key) => delete this[key]);
    };

    this.register = (command) => {
      this.command = command;
      let { credentials, backingData } = command;
      this.securityGroupObjects = _.cloneDeep(backingData.securityGroups[credentials].gce.global);

      initializeSecurityGroupObjects(this.securityGroupObjects, command.tags);

      let { byTag, bySecurityGroupId } = groupSecurityGroupObjects(this.securityGroupObjects);
      this.securityGroupObjectsKeyedByTag = byTag;
      this.securityGroupObjectsKeyedById = bySecurityGroupId;

      if (!command.securityGroups) {
        command.securityGroups = this.inferSecurityGroupIdsFromTags(command.tags);
      }
    };

    this.inferSecurityGroupIdsFromTags = (commandTags = []) => {
      return _(commandTags)
        .map(t => this.securityGroupObjectsKeyedByTag[t.value])
        .flatten()
        .map('id')
        .uniq()
        .valueOf();
    };

    let initializeSecurityGroupObjects = (securityGroupObjects, commandTags = []) => {
      securityGroupObjects
        .forEach((sg) => {
          // Raw target tags look like '[tag-a, tag-b]'.
          let rawTags = sg.targetTags;
          sg.tagsArray = rawTags
            ? rawTags.substring(1, rawTags.length - 1).split(', ')
            : [];

          sg.selectedTags = commandTags
            ? _.intersection(commandTags.map(t => t.value), sg.tagsArray)
            : [];
        });
    };

    let groupSecurityGroupObjects = (securityGroupObjects) => {
      return securityGroupObjects.reduce((groupings, sg) => {
        let { bySecurityGroupId } = groupings;
        bySecurityGroupId[sg.id] = sg;

        return sg.tagsArray.reduce((groupings, tag) => {
          let { byTag } = groupings;
          if (!byTag[tag]) {
            byTag[tag] = [];
          }
          byTag[tag].push(sg);

          return groupings;
        }, groupings);
      }, { byTag: {}, bySecurityGroupId: {} });
    };

    this.inferSelectedSecurityGroupFromTag = _.debounce((tagName) => {
      let securityGroupObjectsWithTag = this.securityGroupObjectsKeyedByTag[tagName],
        c = this.command;

      if (securityGroupObjectsWithTag) {
        updateSelectedTagsOnSecurityGroupObjects(securityGroupObjectsWithTag, tagName);
        c.securityGroups = _.pluck(
          updateSelectedSecurityGroups(getSecurityGroupObjectsFromIds(c.securityGroups), securityGroupObjectsWithTag),
          'id');
      }

      // If you pause while typing "tag-a," but you really want to add "tag-abc,"
      // make sure you remove "tag-a".
      this.updateSelectedTags();
    }, 100);

    this.updateSelectedTags = () => {
      let c = this.command,
        tags = c.tags.map(t => t.value);

      getSecurityGroupObjectsFromIds(c.securityGroups)
        .forEach((sg) => {
          if (sg.selectedTags) {
            sg.selectedTags = _.intersection(sg.selectedTags, tags);
          }

          if (!_.get(sg, 'selectedTags.length')) {
            c.securityGroups = _.without(c.securityGroups, sg.id);
          }
        });
    };

    this.addTag = (tagName) => {
      let c = this.command,
        tags = c.tags,
        securityGroupObjectsWithTag = this.securityGroupObjectsKeyedByTag[tagName];

      if (!_.contains(tags.map(t => t.value), tagName)) {
        tags.push({ value: tagName });
      }

      if (securityGroupObjectsWithTag) {
        updateSelectedTagsOnSecurityGroupObjects(securityGroupObjectsWithTag, tagName);
        c.securityGroups = _.pluck(
          updateSelectedSecurityGroups(getSecurityGroupObjectsFromIds(c.securityGroups), securityGroupObjectsWithTag),
          'id');
      }
    };

    let getSecurityGroupObjectsFromIds = (ids) => ids.map((id) => this.securityGroupObjectsKeyedById[id]);

    let updateSelectedTagsOnSecurityGroupObjects = (securityGroupObjects, newTag) => {
      securityGroupObjects.forEach((sg) => {
        sg.selectedTags = _(sg.selectedTags || [])
          .concat([newTag])
          .uniq()
          .valueOf();
      });
    };

    let updateSelectedSecurityGroups = (oldGroups, newGroups) => {
      return _(oldGroups)
        .concat(newGroups)
        .uniq()
        .valueOf();
    };

    this.removeTag = (tagName) => {
      let c = this.command,
        securityGroupIds = c.securityGroups || [],
        securityGroupObjects = getSecurityGroupObjectsFromIds(securityGroupIds);

      securityGroupObjects.forEach((sg) => {
        if (sg.selectedTags) {
          sg.selectedTags = sg.selectedTags.filter(tag => tag !== tagName);
        }
      });

      c.tags = c.tags.filter(tag => tag.value !== tagName);
    };

    this.removeSecurityGroup = (securityGroupId) => {
      let securityGroupObject = this.securityGroupObjectsKeyedById[securityGroupId],
        tagsToRemove = securityGroupObject.selectedTags,
        c = this.command;

      getSecurityGroupObjectsFromIds(c.securityGroups)
        .forEach((sg) => {
          if (sg.selectedTags) {
            sg.selectedTags = _.difference(sg.selectedTags, tagsToRemove);
          }
        });

      securityGroupObject.selectedTags = [];
      c.tags = _(c.tags)
        .map(t => t.value)
        .difference(tagsToRemove)
        .map(t => ({ value: t }))
        .valueOf();
    };

    this.getToolTipContent = (tagName) => {
      let groups = _.get(this, ['securityGroupObjectsKeyedByTag', tagName]),
        groupIds = groups ? groups.map((sg) => sg.id) : [];

      return `This tag associates this server group with security group${groupIds.length > 1 ? 's' : ''}
              <em>${groupIds.join(', ')}</em>.`;
    };

    this.showToolTip = (tagName) => {
      return !!_.get(this,['securityGroupObjectsKeyedByTag', tagName]);
    };

    return this;
  });
