---
layout: toc-page
title: Developer's Guide
id: developer_guide
lang: en
---

* Table of contents. This line is required to start the list.
{:toc}

#Developer's Guide

##Spinnaker Components

Spinnaker is composed of the following micro services:

* [Clouddriver](https://www.github.com/spinnaker/clouddriver) - encapsulates all cloud operations 
* [Deck](https://www.github.com/spinnaker/deck) - User Interface
* [Echo](https://www.github.com/spinnaker/echo) - event service that forwards events from Spinnaker. Echo is responsible for triggering pipeline executions and forwarding pipeline events to listeners
* [Front50](https://www.github.com/spinnaker/front50) - data store for pipelines, notifications and applications
* [Gate](https://www.github.com/spinnaker/gate) - service gateway responsible for providing an API to end users and UI. 
* [Igor](https://www.github.com/spinnaker/igor) - interface to Jenkins, Stash and Github
* [Orca](https://www.github.com/spinnaker/orca) - orchestration engine responsible for running spinnaker pipelines and tasks
* [Rosco](https://www.github.com/spinnaker/rosco) - bakery responsible for creating images for deployment
