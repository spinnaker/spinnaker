---
layout: toc-page
title: User's Guide
id: user_guide
lang: en
---

* Table of contents. This line is required to start the list.
{:toc}

#User Guide

##Starting Spinnaker

##Understanding the Clusters View

##Working with Pipelines

##Deployment Strategies

Deployment strategies are specialized pipelines that are bound to the deploy stage of an execution. They can be selected as strategies when defining the deploy stage of a standard pipeline or via clone server group action in the clusters view.

To use a deployment strategy, select 'custom' in the strategy dropdown. Then, select the strategy by name and fill in the appropiate parameters if needed.

To define a new deployment strategy, go to the Pipelines tab and click on the Configure... dropdown. Select 'create new' and set the type to be 'strategy'. Click on save to edit your strategy. 

Strategies are like regular pipelines, with a few key differences:

1. The deploy stage of a strategy will not let you add clusters, since these will be inherited from the deploy operation when the strategy is ran.
2. In a normal pipeline, you usually need to set the account, region and cluster for some stages for them to work. These fields are suppressed when setting up a strategy and are inherited from the deploy operation.
3. You can't set triggers on a strategy.
4. Strategies don't allow for the Canary, Find Image and Bake stages. 

When a deployment strategy is ran, it will inject the following parameters into the pipeline: application, region, credentials and amiName. These values would be derived from the deploy stage that calls the strategy and available to the templated pipelines mechanism in Spinnaker. If a strategy parameter exists with the same name as the automatically calculated strategy, the user-supplied parameter will take precedence over the derived value.