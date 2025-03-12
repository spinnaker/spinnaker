# Change Log

All notable changes to this project will be documented in this file.
See [Conventional Commits](https://conventionalcommits.org) for commit guidelines.

## [0.29.1](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.29.0...@spinnaker/core@0.29.1) (2024-06-10)


### Bug Fixes

* **redblack:** fixing redblack onchange values ([#10107](https://github.com/spinnaker/deck/issues/10107)) ([443408e](https://github.com/spinnaker/deck/commit/443408e7f6404b10d195b146f2f68fe926c2413a))





# [0.29.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.28.0...@spinnaker/core@0.29.0) (2024-05-10)


### Bug Fixes

* **lambdaStages:** Exporting Lambda stages based on the feature flag settings ([#10085](https://github.com/spinnaker/deck/issues/10085)) ([93bab65](https://github.com/spinnaker/deck/commit/93bab656555fabd539e186587a40dd8a0358dbd9))
* **pipelineGraph:** Handling exception when requisiteStageRefIds is not defined ([#10086](https://github.com/spinnaker/deck/issues/10086)) ([4e1635d](https://github.com/spinnaker/deck/commit/4e1635d6026c6fbcb5912de1859c45038fd1258a))
* **pipeline:** Handle render/validation when stageTimeoutMs is a Spel expression ([#10103](https://github.com/spinnaker/deck/issues/10103)) ([9237f78](https://github.com/spinnaker/deck/commit/9237f7890e5f02f5369bc91984de98b18591ef9e))
* **runJobs:** Persist External Log links after the deletion of the pods ([#10081](https://github.com/spinnaker/deck/issues/10081)) ([fd55cf1](https://github.com/spinnaker/deck/commit/fd55cf1fb3bca08b931a10bb4c10d65393a20c73))


### Features

* **cdevents-notification:** CDEvents notification to produce CDEvents ([#9997](https://github.com/spinnaker/deck/issues/9997)) ([fc96adb](https://github.com/spinnaker/deck/commit/fc96adb17b5051f655e1d651b28c8eb0efd7e11e))
* **taskView:** Implement opt-in paginated request for TaskView ([#10093](https://github.com/spinnaker/deck/issues/10093)) ([5fa1e96](https://github.com/spinnaker/deck/commit/5fa1e96b90c7398338d67ef7a7337ee3628591bd))
* **wait-stage:** Make Wait Stage restartable. ([#10073](https://github.com/spinnaker/deck/issues/10073)) ([f3df056](https://github.com/spinnaker/deck/commit/f3df0561d891e928a14c32a3544fc331ebf1d981))





# [0.28.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.27.0...@spinnaker/core@0.28.0) (2023-12-07)


### Features

* Add feature flag for multi block failure messages. ([#10061](https://github.com/spinnaker/deck/issues/10061)) ([374f724](https://github.com/spinnaker/deck/commit/374f724de221d68030a86e1f6452e3303390339a))
* Expose spinnaker/kayenta to the plugin framework to allow us to create kayenta plugins in Deck ([#10072](https://github.com/spinnaker/deck/issues/10072)) ([dbf0574](https://github.com/spinnaker/deck/commit/dbf0574176cbbca781d970c64dfe49f6911ef8b8))
* Split deployment failure messages. ([#10060](https://github.com/spinnaker/deck/issues/10060)) ([73dda48](https://github.com/spinnaker/deck/commit/73dda48caccd969ef562af3f86bc1f17efbdad7f))





# [0.27.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.26.0...@spinnaker/core@0.27.0) (2023-10-16)


### Bug Fixes

* **publish:** set access config in deck libraries ([#10049](https://github.com/spinnaker/deck/issues/10049)) ([2a5ebe2](https://github.com/spinnaker/deck/commit/2a5ebe25662eeb9d41b5071749266bf9d6d51104))


### Features

* **helm/bake:** Add additional input fields where we can fill in details of the APIs versions ([#10036](https://github.com/spinnaker/deck/issues/10036)) ([d968183](https://github.com/spinnaker/deck/commit/d9681830244ecd1c70cc02459f148d0822b7187e))





# [0.26.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.25.0...@spinnaker/core@0.26.0) (2023-09-06)


### Features

* **core:** Add ability to set Default Tag filters for an application in application config ([#10020](https://github.com/spinnaker/deck/issues/10020)) ([c768e88](https://github.com/spinnaker/deck/commit/c768e88fbc893d0bd5dc86959320a7b7d67443e5))


### Reverts

* Revert "fix(core): conditionally hide expression evaluation warning messages (#9771)" (#10021) ([62033d0](https://github.com/spinnaker/deck/commit/62033d0fc6f0a953bd3f01e4452664b92fd02dfb)), closes [#9771](https://github.com/spinnaker/deck/issues/9771) [#10021](https://github.com/spinnaker/deck/issues/10021)



# 3.15.0 (2023-07-27)


### Features

* **core:** set Cancellation Reason to be expanded by default ([#10018](https://github.com/spinnaker/deck/issues/10018)) ([db06e88](https://github.com/spinnaker/deck/commit/db06e88bada70fa4065f56fc33af7207943415c5))





# [0.25.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.24.1...@spinnaker/core@0.25.0) (2023-07-20)


### Bug Fixes

* **core/pipeline:** Resolved issue getting during pipeline save with spaces in pipeline name. ([#10009](https://github.com/spinnaker/deck/issues/10009)) ([ec8d2bb](https://github.com/spinnaker/deck/commit/ec8d2bbada0192673cfede4401e5c18d884dec59))


### Features

* **artifacts:** Add support for artifact store views and calls ([#10011](https://github.com/spinnaker/deck/issues/10011)) ([b520bae](https://github.com/spinnaker/deck/commit/b520bae8296c85ed096ea6aaee022e114bb6a52f))
* **lambda:** Migrate Lambda plugin to OSS ([#9988](https://github.com/spinnaker/deck/issues/9988)) ([11f1cab](https://github.com/spinnaker/deck/commit/11f1cabb8efe8d7e034faf06ae3cb455eef6369a)), closes [#9984](https://github.com/spinnaker/deck/issues/9984)
* **stages/bakeManifests:** add helmfile support ([#9998](https://github.com/spinnaker/deck/issues/9998)) ([a4a0f33](https://github.com/spinnaker/deck/commit/a4a0f331d181b74d7c3a8c1b46724757be17a9f0))





## [0.24.1](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.24.0...@spinnaker/core@0.24.1) (2023-06-02)


### Bug Fixes

* **core/pipeline:** Pipeline builder-pipeline action dropdown closing not properly ([#9999](https://github.com/spinnaker/deck/issues/9999)) ([a672a20](https://github.com/spinnaker/deck/commit/a672a208625d2551ec38f5179cf519fec4a40280))





# [0.24.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.23.2...@spinnaker/core@0.24.0) (2023-05-11)


### Features

* **deck:** make StageFailureMessage component overridable ([#9994](https://github.com/spinnaker/deck/issues/9994)) ([39f70cc](https://github.com/spinnaker/deck/commit/39f70ccae0ce2027a63da60a7e6f2f08fe8f7240))





## [0.23.2](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.23.1...@spinnaker/core@0.23.2) (2023-05-03)


### Bug Fixes

* **angular:** fix missed AngularJS bindings ([#9989](https://github.com/spinnaker/deck/issues/9989)) ([f947bf9](https://github.com/spinnaker/deck/commit/f947bf997a03dee2f600fc72415bf141320978e4))





## [0.23.1](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.23.0...@spinnaker/core@0.23.1) (2023-04-03)


### Bug Fixes

* **6755:** Resolved issue regarding warning reporting when cloning a server group in AWS ([#9948](https://github.com/spinnaker/deck/issues/9948)) ([6b36cb6](https://github.com/spinnaker/deck/commit/6b36cb68112361587bfb8e3fb5c31024d10d7072))
* UI crashes when running pipeline(s) with many stages. ([#9960](https://github.com/spinnaker/deck/issues/9960)) ([8d84d27](https://github.com/spinnaker/deck/commit/8d84d2737729d364a74c34468c874f4613d68801))





# [0.23.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.22.2...@spinnaker/core@0.23.0) (2023-02-01)


### Bug Fixes

* **core:** Missing config elements after Angular 1.8 update ([#9936](https://github.com/spinnaker/deck/issues/9936)) ([884665a](https://github.com/spinnaker/deck/commit/884665a78cb91bb01e533fdd8be65a3a5de19019))
* **helm:** update tooltip to not include Chart.yaml ([#9934](https://github.com/spinnaker/deck/issues/9934)) ([a55f6f3](https://github.com/spinnaker/deck/commit/a55f6f335b51f76f3a8a79ac698884253d7b1076))


### Features

* **Azure:** Update UI to handle custom and managed images. ([#9910](https://github.com/spinnaker/deck/issues/9910)) ([a9057b4](https://github.com/spinnaker/deck/commit/a9057b44f035fc76a7eb461cd2c28c420791457c))
* **core/bake:** support include crds flag in Helm3 ([#9903](https://github.com/spinnaker/deck/issues/9903)) ([a10f11d](https://github.com/spinnaker/deck/commit/a10f11d33f3135963786083003e33db40f3e0b18))
* **core/pipeline:** Add missing flag `skipDownstreamOutput` in pipeline stage ([#9930](https://github.com/spinnaker/deck/issues/9930)) ([deba01e](https://github.com/spinnaker/deck/commit/deba01ed7ba275194f51bcc0cbf414bbf3266562))
* **pipeline:** added feature flag for pipeline when mj stage child ([#9914](https://github.com/spinnaker/deck/issues/9914)) ([4b6fd53](https://github.com/spinnaker/deck/commit/4b6fd53c4674b37c1c9742b9a9fdedb8e1fda5ca))





## [0.22.2](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.22.1...@spinnaker/core@0.22.2) (2022-10-21)


### Bug Fixes

* **links:** update link to spinnaker release changelog ([#9897](https://github.com/spinnaker/deck/issues/9897)) ([1591513](https://github.com/spinnaker/deck/commit/159151368e99da0e990d607039268e20b8b1a8b2))





## [0.22.1](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.22.0...@spinnaker/core@0.22.1) (2022-10-06)


### Bug Fixes

* **core:** Do not set static document base URL ([#9890](https://github.com/spinnaker/deck/issues/9890)) ([5ac7516](https://github.com/spinnaker/deck/commit/5ac75160cf8b6099aaea8b31874ebbbb13409b2a)), closes [#9802](https://github.com/spinnaker/deck/issues/9802) [spinnaker/spinnaker#6723](https://github.com/spinnaker/spinnaker/issues/6723)
* **search:** Error thrown when search version 2 is enabled ([#9888](https://github.com/spinnaker/deck/issues/9888)) ([af0b585](https://github.com/spinnaker/deck/commit/af0b5854dc2a35d643c06baa25a07bfc425fc154))



# 3.12.0 (2022-09-09)


### Bug Fixes

* **notifications:** Added space in Google Chat notification. ([#9884](https://github.com/spinnaker/deck/issues/9884)) ([ed1d5f8](https://github.com/spinnaker/deck/commit/ed1d5f8691f7155c5c346916ad871c9993ea8363))





# [0.22.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.21.0...@spinnaker/core@0.22.0) (2022-08-03)


### Features

* **dependencies:** Update vulnerable dependencies ([#9875](https://github.com/spinnaker/deck/issues/9875)) ([bf92932](https://github.com/spinnaker/deck/commit/bf92932c9396a88fb902050b52f504e4ac01aaa0))


### Performance Improvements

* **pipelinegraph:** Improve the performance of the pipeline graph rendering ([#9871](https://github.com/spinnaker/deck/issues/9871)) ([55fa5d0](https://github.com/spinnaker/deck/commit/55fa5d00beb576d9d9215923bcc4b7ead64a4fba))





# [0.21.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.20.1...@spinnaker/core@0.21.0) (2022-07-11)


### Features

* **rosco:** add Kustomize 4 support ([#9868](https://github.com/spinnaker/deck/issues/9868)) ([53a05ca](https://github.com/spinnaker/deck/commit/53a05ca5aed8d1d9d99e1420a0b1a0408b331c75))





## [0.20.1](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.20.0...@spinnaker/core@0.20.1) (2022-07-01)

**Note:** Version bump only for package @spinnaker/core





# [0.20.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.19.4...@spinnaker/core@0.20.0) (2022-06-22)


### Features

* **core:** Synchronize the verticalNavExpandedAtom using an atom effect ([#9859](https://github.com/spinnaker/deck/issues/9859)) ([a81f4ab](https://github.com/spinnaker/deck/commit/a81f4ab9bbcf86d31f31f52390740db62a8e7e5d))





## [0.19.4](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.19.3...@spinnaker/core@0.19.4) (2022-05-05)


### Bug Fixes

* **core:** apps always display as unconfigured ([#9853](https://github.com/spinnaker/deck/issues/9853)) ([7d45ac7](https://github.com/spinnaker/deck/commit/7d45ac79ee4c68ae1d55584a789acf74616f4d0b)), closes [/github.com/spinnaker/deck/blob/master/packages/core/src/application/config/applicationAttributes.directive.js#L31](https://github.com//github.com/spinnaker/deck/blob/master/packages/core/src/application/config/applicationAttributes.directive.js/issues/L31)





## [0.19.3](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.19.2...@spinnaker/core@0.19.3) (2022-04-21)

**Note:** Version bump only for package @spinnaker/core





## [0.19.2](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.19.1...@spinnaker/core@0.19.2) (2022-04-09)

**Note:** Version bump only for package @spinnaker/core





## [0.19.1](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.19.0...@spinnaker/core@0.19.1) (2022-03-08)

**Note:** Version bump only for package @spinnaker/core





# [0.19.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.18.0...@spinnaker/core@0.19.0) (2022-01-22)


### Features

* **core:** HTML5 Routing ([#9802](https://github.com/spinnaker/deck/issues/9802)) ([d5a077a](https://github.com/spinnaker/deck/commit/d5a077a4b3765ba3e45ec53242bfac187cc96712))





# [0.18.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.17.0...@spinnaker/core@0.18.0) (2022-01-12)


### Bug Fixes

* **core:** ensure that users are not warned of their success ([#9794](https://github.com/spinnaker/deck/issues/9794)) ([333677b](https://github.com/spinnaker/deck/commit/333677b9c9508ae16e91a02e5b0416c7bf217431))


### Features

* **core/pipeline:** Load quiet period configuration from Gate, not from SETTINGS ([#9788](https://github.com/spinnaker/deck/issues/9788)) ([75694bd](https://github.com/spinnaker/deck/commit/75694bdd55c9eaf3af8d9b531ee0ac4a471733c5))





# [0.17.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.16.1...@spinnaker/core@0.17.0) (2021-12-11)


### Features

* **pipeline executions/deck:** Add support for max concurrent pipeli… ([#9777](https://github.com/spinnaker/deck/issues/9777)) ([525dc78](https://github.com/spinnaker/deck/commit/525dc788f8fb6508a404023d816df4d01e4de0c2))





## [0.16.1](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.16.0...@spinnaker/core@0.16.1) (2021-12-08)


### Bug Fixes

* **core:** conditionally hide expression evaluation warning messages ([#9771](https://github.com/spinnaker/deck/issues/9771)) ([7e3dd50](https://github.com/spinnaker/deck/commit/7e3dd5053ccdb06ce067303062f90ae82b56bfc8))





# [0.16.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.15.1...@spinnaker/core@0.16.0) (2021-12-01)


### Features

* **core:** Prepopulate name for app creation ([#9718](https://github.com/spinnaker/deck/issues/9718)) ([7460fe6](https://github.com/spinnaker/deck/commit/7460fe67c3a5caa755f19d0c674efdf0bcd28141))





## [0.15.1](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.15.0...@spinnaker/core@0.15.1) (2021-11-12)


### Bug Fixes

* **bake:** make helm chart path visible for git/repo artifact ([#9768](https://github.com/spinnaker/deck/issues/9768)) ([9c4e438](https://github.com/spinnaker/deck/commit/9c4e438d4f8fd6c9b72f6648347bb68b93dd8139))





# [0.15.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.14.2...@spinnaker/core@0.15.0) (2021-11-03)


### Bug Fixes

* **core/pipeline:** Make wait time readable if over 1 hour ([#9749](https://github.com/spinnaker/deck/issues/9749)) ([5848b4d](https://github.com/spinnaker/deck/commit/5848b4dcc1797865cb93b90090284be10497329f))
* **core:** Auto-open edit modal for inferred app ([#9722](https://github.com/spinnaker/deck/issues/9722)) ([f539145](https://github.com/spinnaker/deck/commit/f5391457aca7741999398648c70b347c41e85e55))
* **md:** hide deployment status if currently deploying ([#9757](https://github.com/spinnaker/deck/issues/9757)) ([3ca2d55](https://github.com/spinnaker/deck/commit/3ca2d551c511bd47709670e140af696d57cde0b0))
* **md:** short preview env intro text ([#9754](https://github.com/spinnaker/deck/issues/9754)) ([a4a361b](https://github.com/spinnaker/deck/commit/a4a361b44b801b8e6ed9e80ed7625e9b1bfb84b6))


### Features

* **amazon/instance:** Add typing to instance types ([35be23d](https://github.com/spinnaker/deck/commit/35be23d3c6112aab148b53e09983f37ed03b3b1e))
* **aws:** Adding support for multiple instance types and EC2 ASG MixedInstancePolicy - part 3 - parent components for Instance Type wizard and related ([44ab1ab](https://github.com/spinnaker/deck/commit/44ab1abdb1dd25f05b2305b65eb97eed09c8bac7))
* **core:** Configurable account tag limit for executions ([#9750](https://github.com/spinnaker/deck/issues/9750)) ([55cd7fc](https://github.com/spinnaker/deck/commit/55cd7fc3f65c3fdef2f22bba0a192143af87d7e5))
* **md:** added a section for "Now deploying" ([#9752](https://github.com/spinnaker/deck/issues/9752)) ([39aa27d](https://github.com/spinnaker/deck/commit/39aa27d6d525acb80d9cfeb8927f654198ca7c2a))
* **md:** added deployed infrastructure section to artifacts + bunch of style updates ([#9759](https://github.com/spinnaker/deck/issues/9759)) ([a8ce521](https://github.com/spinnaker/deck/commit/a8ce521981e70fe046aa72ef43fbb10d09903498))
* **md:** allow users to reset a constraint if it failed ([#9755](https://github.com/spinnaker/deck/issues/9755)) ([c1ac25a](https://github.com/spinnaker/deck/commit/c1ac25a5aba6ffbebe822a4e13f291013092833d))
* **md:** Handle rolling forward properly ([#9753](https://github.com/spinnaker/deck/issues/9753)) ([4a6dd5c](https://github.com/spinnaker/deck/commit/4a6dd5c8876babc28570a8cda281dd57ee886292))
* **md:** redesign of artifacts - metadata + rollback and compare to UX ([#9738](https://github.com/spinnaker/deck/issues/9738)) ([ae940a0](https://github.com/spinnaker/deck/commit/ae940a0b098eb22cd270df33729c5c75564ece76))





## [0.14.2](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.14.1...@spinnaker/core@0.14.2) (2021-10-05)


### Bug Fixes

* **md:** build links ([#9743](https://github.com/spinnaker/deck/issues/9743)) ([b7397f1](https://github.com/spinnaker/deck/commit/b7397f1b5a7e7b175fc601ba6a8512bfd9fd9361))





## [0.14.1](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.14.0...@spinnaker/core@0.14.1) (2021-10-01)

**Note:** Version bump only for package @spinnaker/core





# [0.14.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.12.0...@spinnaker/core@0.14.0) (2021-09-30)


### Bug Fixes

* bump @types/react to 16.14.10 ([bb62b99](https://github.com/spinnaker/deck/commit/bb62b991514c2a81fbdf467c01f3ce7467f71718))
* **core/presentation:** Remove return value from useEffect in useMountStatusRef ([b238b9c](https://github.com/spinnaker/deck/commit/b238b9c2357ed33e44bbcd48cffe6255c923dd5c))


### Features

* **aws/infrastructure:** Hide aws ad hoc infrastructure action buttons ([#9712](https://github.com/spinnaker/deck/issues/9712)) ([7202efd](https://github.com/spinnaker/deck/commit/7202efd54ad0b048d5c1f45c24162619b25be844))
* **md:** clean artifact version details by moving the bake and build details to a popover ([#9715](https://github.com/spinnaker/deck/issues/9715)) ([0f2480e](https://github.com/spinnaker/deck/commit/0f2480ec6718730ae58c592f894e6b78dcb67a5d))





# [0.13.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.12.0...@spinnaker/core@0.13.0) (2021-09-30)


### Bug Fixes

* bump @types/react to 16.14.10 ([bb62b99](https://github.com/spinnaker/deck/commit/bb62b991514c2a81fbdf467c01f3ce7467f71718))
* **core/presentation:** Remove return value from useEffect in useMountStatusRef ([b238b9c](https://github.com/spinnaker/deck/commit/b238b9c2357ed33e44bbcd48cffe6255c923dd5c))


### Features

* **aws/infrastructure:** Hide aws ad hoc infrastructure action buttons ([#9712](https://github.com/spinnaker/deck/issues/9712)) ([7202efd](https://github.com/spinnaker/deck/commit/7202efd54ad0b048d5c1f45c24162619b25be844))
* **md:** clean artifact version details by moving the bake and build details to a popover ([#9715](https://github.com/spinnaker/deck/issues/9715)) ([0f2480e](https://github.com/spinnaker/deck/commit/0f2480ec6718730ae58c592f894e6b78dcb67a5d))





# [0.12.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.11.7...@spinnaker/core@0.12.0) (2021-09-29)


### Bug Fixes

* **core:** Ensure text is stringin CopyToClipboard ([#9704](https://github.com/spinnaker/deck/issues/9704)) ([78da1d8](https://github.com/spinnaker/deck/commit/78da1d8fd2a98533fd202506369d320207e45fc8))
* **kubernetes:** hide ad-hoc infrastructure action buttons when spinnaker has multiple accounts ([#9707](https://github.com/spinnaker/deck/issues/9707)) ([337ec29](https://github.com/spinnaker/deck/commit/337ec291494bc8d7ff863cd2a44ce38ab98229c1))


### Features

* **md:** added a default component if preview environments configured but don't exist ([#9713](https://github.com/spinnaker/deck/issues/9713)) ([9432fd0](https://github.com/spinnaker/deck/commit/9432fd0e5419b0c2015f1b968b95c305acb928e1))





## [0.11.7](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.11.6...@spinnaker/core@0.11.7) (2021-09-24)


### Bug Fixes

* **core/cloudProvider:** remove unused ICloudProviderLogoState interface ([57d606c](https://github.com/spinnaker/deck/commit/57d606cb2442b5eb1e465b3cd5bc9f5235ae9d92))
* **core:** previous commit updated the font to sans serif 3, but not the styleguide. ([#9700](https://github.com/spinnaker/deck/issues/9700)) ([b01afa9](https://github.com/spinnaker/deck/commit/b01afa95ca19455e7a316844101f080adef99ca9))
* **md:** allow users to enable resource management ([#9690](https://github.com/spinnaker/deck/issues/9690)) ([455be38](https://github.com/spinnaker/deck/commit/455be38a5fee8fbcb2345b6026844e00923418fb))





## [0.11.6](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.11.5...@spinnaker/core@0.11.6) (2021-09-23)


### Bug Fixes

* **md:** notify users on error in mutation ([#9691](https://github.com/spinnaker/deck/issues/9691)) ([ad99afb](https://github.com/spinnaker/deck/commit/ad99afbef6fa55bb570d8c2530667b24b2d02c79))





## [0.11.5](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.11.4...@spinnaker/core@0.11.5) (2021-09-23)


### Bug Fixes

* **aws/lambda:** Fix functions icon on menu ([#9686](https://github.com/spinnaker/deck/issues/9686)) ([9e6aae3](https://github.com/spinnaker/deck/commit/9e6aae34af206ea6d280fac1b892d7aba7f38abc))
* **pipelines:** Reduce pipelineConfig fetches ([#9677](https://github.com/spinnaker/deck/issues/9677)) ([106d00b](https://github.com/spinnaker/deck/commit/106d00b942ce19c5a316a130963c411171893108))





## [0.11.4](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.11.3...@spinnaker/core@0.11.4) (2021-09-21)


### Bug Fixes

* **md:** render resource status as markdown ([#9674](https://github.com/spinnaker/deck/issues/9674)) ([9f3d12f](https://github.com/spinnaker/deck/commit/9f3d12f3df97f33723b5287ccac5202cd4e2f8c4))





## [0.11.3](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.11.2...@spinnaker/core@0.11.3) (2021-09-18)


### Bug Fixes

* **md:** fix version message style ([#9671](https://github.com/spinnaker/deck/issues/9671)) ([c5ff99a](https://github.com/spinnaker/deck/commit/c5ff99ac472645fdb5f931e221bc4d475335c086))





## [0.11.2](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.11.1...@spinnaker/core@0.11.2) (2021-09-18)


### Bug Fixes

* **md:** replaced status with statusSummary ([#9669](https://github.com/spinnaker/deck/issues/9669)) ([4defe7f](https://github.com/spinnaker/deck/commit/4defe7f3df71c201533bda4f103e63b116859a96))





## [0.11.1](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.11.0...@spinnaker/core@0.11.1) (2021-09-16)


### Bug Fixes

* **core:** break words in notifications to prevent overflow ([#9665](https://github.com/spinnaker/deck/issues/9665)) ([9e15cc7](https://github.com/spinnaker/deck/commit/9e15cc7aaba7eb88bff64a1d618758de4e6476dd))
* **md:** Added DELETING state to resource states.  ([#9659](https://github.com/spinnaker/deck/issues/9659)) ([dc36ccd](https://github.com/spinnaker/deck/commit/dc36ccdebdb1c2e20ac1f594f6b8418f470f3076))





# [0.11.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.10.0...@spinnaker/core@0.11.0) (2021-09-15)


### Features

* **md:** display manifest path and make it configurable ([#9657](https://github.com/spinnaker/deck/issues/9657)) ([c7faaef](https://github.com/spinnaker/deck/commit/c7faaef01ae89b6103f37fa0087a94e33c658d8c))





# [0.10.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.9.1...@spinnaker/core@0.10.0) (2021-09-14)


### Bug Fixes

* **core:** do not show "0" when no scaling activities detected ([da063fb](https://github.com/spinnaker/deck/commit/da063fbe1f15638b17c0903703ee9b608f92ea40))
* **style:** keep submit button status indicator inline with label ([3d2b4cb](https://github.com/spinnaker/deck/commit/3d2b4cbaf3bf35ae170034d3843e75d994a642a3))


### Features

* **core:** Allow notification email SpEL ([#9633](https://github.com/spinnaker/deck/issues/9633)) ([05bd71f](https://github.com/spinnaker/deck/commit/05bd71f9870a5a234fe6a4302be0952b305fc2e4))
* **core:** Expand Image Provider validation to find image stages ([#9647](https://github.com/spinnaker/deck/issues/9647)) ([c6cd62f](https://github.com/spinnaker/deck/commit/c6cd62f5dea233343037e29fdebb978728ff8341))
* **md:** extended and simplified constraint title rendering ([#9640](https://github.com/spinnaker/deck/issues/9640)) ([b96078e](https://github.com/spinnaker/deck/commit/b96078e304aad10f147bfeb65b91299c9033a6d1))
* **travis:** Remove Property File field from Travis stages and triggers ([#8990](https://github.com/spinnaker/deck/issues/8990)) ([6aa8e39](https://github.com/spinnaker/deck/commit/6aa8e39cae511bbdf3152be23e12f48a19a25abd))





## [0.9.1](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.9.0...@spinnaker/core@0.9.1) (2021-09-06)


### Bug Fixes

* **core/application:** Fix for `requiredGroupMembership` breaking UI ([#9610](https://github.com/spinnaker/deck/issues/9610)) ([0a1f6ab](https://github.com/spinnaker/deck/commit/0a1f6abc4c912068beee3bb55b4500ef827bd98c))





# [0.9.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.8.0...@spinnaker/core@0.9.0) (2021-09-02)


### Features

* **md:** added "import now" to delivery config ([#9634](https://github.com/spinnaker/deck/issues/9634)) ([9777658](https://github.com/spinnaker/deck/commit/9777658f2d5b090228da8d339f5385f535d4c4b8))





# [0.8.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.7.0...@spinnaker/core@0.8.0) (2021-08-30)


### Features

* **core/pipeline:** pipeline graph minimize based on number of stages ([#9562](https://github.com/spinnaker/deck/issues/9562)) ([86c5c7c](https://github.com/spinnaker/deck/commit/86c5c7cde993749384a062e24102675ccb290e92))





# [0.7.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.6.0...@spinnaker/core@0.7.0) (2021-08-25)


### Bug Fixes

* **md:** always show the config component ([#9613](https://github.com/spinnaker/deck/issues/9613)) ([ed35f1c](https://github.com/spinnaker/deck/commit/ed35f1c2ee17636f3090b3269cdb9ef80bd719ff))


### Features

* **managed-delivery:** Expose resource definition on the UI ([#9611](https://github.com/spinnaker/deck/issues/9611)) ([d264840](https://github.com/spinnaker/deck/commit/d264840ee86beb3dd5581556d957f468ebb4e4cd))





# [0.6.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.5.0...@spinnaker/core@0.6.0) (2021-08-20)


### Bug Fixes

* **md:** increased pinned icon size ([#9600](https://github.com/spinnaker/deck/issues/9600)) ([e5e2de1](https://github.com/spinnaker/deck/commit/e5e2de1659db230e047e0b48b8812ec40ec4c593))


* remove postcss nested (#9602) ([a20faf1](https://github.com/spinnaker/deck/commit/a20faf1b6020cf7f079b9486e6662530024a4336)), closes [#9602](https://github.com/spinnaker/deck/issues/9602)


### BREAKING CHANGES

* removed postcss-nested plugin.  Unlikely that this will break anything, but it's possible.





# [0.5.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.4.0...@spinnaker/core@0.5.0) (2021-08-18)


### Bug Fixes

* **core:** Group execution popover too wide ([#9585](https://github.com/spinnaker/deck/issues/9585)) ([3121ffa](https://github.com/spinnaker/deck/commit/3121ffa89295dbb252bf4b1f81ba6f1abc0c7e25))


### Features

* **md:** use isCurrent to identify the current version + show secondary status when needed (deploying/vetoed) ([#9594](https://github.com/spinnaker/deck/issues/9594)) ([847905b](https://github.com/spinnaker/deck/commit/847905b44c44fba72009c2f31b288cd0760e5f4b))





# [0.4.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.3.1...@spinnaker/core@0.4.0) (2021-08-17)


### Features

* **md:** support max and actual deploys in allowed times constraint ([#9589](https://github.com/spinnaker/deck/issues/9589)) ([9f8aa7d](https://github.com/spinnaker/deck/commit/9f8aa7d253c62bf60d2157c901387dddecf9f1cb))
* **md:** updated preview environment titles to use the branch name, link to the PR, and show basedOn ([#9587](https://github.com/spinnaker/deck/issues/9587)) ([13e3f8f](https://github.com/spinnaker/deck/commit/13e3f8fadcb51d06dcc0f4bccfeeabd7127feb17))





## [0.3.1](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.3.0...@spinnaker/core@0.3.1) (2021-08-13)


### Bug Fixes

* **core/pipeline:** explicitly type DateTimeFormatOptions ([fcdd803](https://github.com/spinnaker/deck/commit/fcdd803492a50c02876c027480f60d8aa1144e72))
* **md:** git integration improvements ([#9583](https://github.com/spinnaker/deck/issues/9583)) ([516eb01](https://github.com/spinnaker/deck/commit/516eb018622849a636d16063f5d2ee8a232b1f42))





# [0.3.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.2.1...@spinnaker/core@0.3.0) (2021-08-10)


### Bug Fixes

* **md:** hide completed task status and progress bar if completed ([#9560](https://github.com/spinnaker/deck/issues/9560)) ([6b441b0](https://github.com/spinnaker/deck/commit/6b441b03a45d10930711ab50d86f851afe8cd534))


### Features

* **core/pipeline:** Check if deploy stage has a trigger that provide… ([#9538](https://github.com/spinnaker/deck/issues/9538)) ([287cecb](https://github.com/spinnaker/deck/commit/287cecb5abf0871c1a2429d8c370470d54417cad))
* **md:** an option to enable the auto import delivery config from git  ([#9558](https://github.com/spinnaker/deck/issues/9558)) ([fbcb21a](https://github.com/spinnaker/deck/commit/fbcb21a8ee7e2e431383d5d8b4fa79cf324a6f68))





## [0.2.1](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.2.0...@spinnaker/core@0.2.1) (2021-08-05)


### Bug Fixes

* **core:** fix layout, click-to-scroll on diff view ([#9555](https://github.com/spinnaker/deck/issues/9555)) ([bc6ccca](https://github.com/spinnaker/deck/commit/bc6ccca86c6506cfcd243823147c9c9ea95df185))
* **core:** Format changes timestamps when they are numbers or strings ([#9552](https://github.com/spinnaker/deck/issues/9552)) ([e780285](https://github.com/spinnaker/deck/commit/e780285d8fbdbbef3eba181368c0fe9ac4a4b866))





# [0.2.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.1.0...@spinnaker/core@0.2.0) (2021-08-02)


### Bug Fixes

* **core/filterModel:** Do not add browser histroy events when changing infrastructure filter queries ([56cc429](https://github.com/spinnaker/deck/commit/56cc429a93d6a980cdce4e52dfb8adbe1d045832))
* **core:** fixed a bunch of circular deps ([29f2b39](https://github.com/spinnaker/deck/commit/29f2b39fd44b43265124e824cb311ff3f8abbf0a))
* **core:** Remove circular dependencies - 2 ([ee5f783](https://github.com/spinnaker/deck/commit/ee5f783e213bb175f6f9bd9c85bd42e3d6850d47))
* **core:** Remove circular dependency of modules ([5c942b1](https://github.com/spinnaker/deck/commit/5c942b15b5f6a257d737a87fc8552dc5e40c8762))
* **core:** trying to avoid importing ReactInjector in presentation ([4d464ce](https://github.com/spinnaker/deck/commit/4d464cea3a98be1c31484c7ec4123d3ca74c7c21))
* **md:** text for BLOCKED status in allowed times constraint ([#9550](https://github.com/spinnaker/deck/issues/9550)) ([811e806](https://github.com/spinnaker/deck/commit/811e8065d12d36d114ea9e3af0049d368563a1ff))
* **md:** the raw delivery config is not showing up properly. Hiding it for now ([#9548](https://github.com/spinnaker/deck/issues/9548)) ([8f7c6ef](https://github.com/spinnaker/deck/commit/8f7c6eff565a7915fbb8fe2607eae742ffc516b1))


### Features

* **core/cluster:** Remove unused react2angular <filter-search> component ([7514b0b](https://github.com/spinnaker/deck/commit/7514b0b06e687e3f92548517297365ed4d8137ce))





# [0.1.0](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.0.620...@spinnaker/core@0.1.0) (2021-07-30)


### Bug Fixes

* **all:** Fix lodash global usage ([d048432](https://github.com/spinnaker/deck/commit/d048432978f0aa0bceb2b58f80ea7301de153072))
* **build:** Upgrade uirouter/react version ([cc5004b](https://github.com/spinnaker/deck/commit/cc5004bfded32642553077346c19e34820d24ae7))
* **core/pipeline:** Disable pipeline sorting when filtering by name (because some pipelines are hidden) ([1e06186](https://github.com/spinnaker/deck/commit/1e06186dec9fa9b38806f02db17e38c9d89125da))
* **md:** fetch raw delivery config via graphql ([#9537](https://github.com/spinnaker/deck/issues/9537)) ([1ca3549](https://github.com/spinnaker/deck/commit/1ca354966c1ad7ab07d614db1df56aea67ffef6a))
* **vite:** Add vite fixes ([8e0840a](https://github.com/spinnaker/deck/commit/8e0840a647944d9f90ad51c6568c320b096730d6))


### Features

* **core/pipeline:** Add some SpinErrorBoundaries to pipeline execution details ([14fa364](https://github.com/spinnaker/deck/commit/14fa364905a8bc532e6e28529acacf8a7d0d9e83))
* **core/presentation:** Add a retry button to SpinErrorBoundary ([9883704](https://github.com/spinnaker/deck/commit/9883704db8f5b770c0793347b85d93bd87626e18))
* introduce a separate timeout config property for api timeouts ([#9498](https://github.com/spinnaker/deck/issues/9498)) ([678a78a](https://github.com/spinnaker/deck/commit/678a78afa4cb762df01d8c6ed19311fdeeeb86a7))





## [0.0.620](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.0.619...@spinnaker/core@0.0.620) (2021-07-26)

**Note:** Version bump only for package @spinnaker/core





## [0.0.619](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.0.618...@spinnaker/core@0.0.619) (2021-07-22)


### Bug Fixes

* sample commit to test publishing scripts ([#9509](https://github.com/spinnaker/deck/issues/9509)) ([7438c84](https://github.com/spinnaker/deck/commit/7438c84d75c18cf5c1e7d17d39eef046f8f644dd))





## [0.0.618](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.0.616...@spinnaker/core@0.0.618) (2021-07-22)


### Bug Fixes

* **core/loadBalancer:** Pushed modal overlay to the background ([#9466](https://github.com/spinnaker/deck/issues/9466)) ([799250b](https://github.com/spinnaker/deck/commit/799250bde2fdf9ba7d88b1a5adb0c937601b6ae0))





## [0.0.617](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.0.616...@spinnaker/core@0.0.617) (2021-07-22)


### Bug Fixes

* **core/loadBalancer:** Pushed modal overlay to the background ([#9466](https://github.com/spinnaker/deck/issues/9466)) ([799250b](https://github.com/spinnaker/deck/commit/799250bde2fdf9ba7d88b1a5adb0c937601b6ae0))





## [0.0.616](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.0.611...@spinnaker/core@0.0.616) (2021-07-19)

**Note:** Version bump only for package @spinnaker/core





## [0.0.615](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.0.611...@spinnaker/core@0.0.615) (2021-07-19)

**Note:** Version bump only for package @spinnaker/core





## [0.0.614](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.0.611...@spinnaker/core@0.0.614) (2021-07-19)

**Note:** Version bump only for package @spinnaker/core





## [0.0.613](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.0.611...@spinnaker/core@0.0.613) (2021-07-19)

**Note:** Version bump only for package @spinnaker/core





## [0.0.612](https://github.com/spinnaker/deck/compare/@spinnaker/core@0.0.611...@spinnaker/core@0.0.612) (2021-07-19)

**Note:** Version bump only for package @spinnaker/core





## 0.0.611 (2021-07-17)


### Bug Fixes

* **publishing:** Auto approve instead of adding "ready to merge" label ([51f536c](https://github.com/spinnaker/deck/commit/51f536c275e77854d8f173aeec86412ffbd66b6d))






## [0.0.608](https://www.github.com/spinnaker/deck/compare/ec8327948590884983967e99c6d6df40e1f76f9c...760d2f3fe4594554803ae28b333dabd379f6623e) (2021-07-09)


### Changes

chore(core): publish core@0.0.608 ([760d2f3f](https://github.com/spinnaker/deck/commit/760d2f3fe4594554803ae28b333dabd379f6623e))  
fix(managed-delivery): Fix resource deleting icon [#9416](https://github.com/spinnaker/deck/pull/9416) ([84b21758](https://github.com/spinnaker/deck/commit/84b21758d9ffd50924e191eb7fa2f742626f4957))  
chore(core): React CopyToClipboard is the source of truth [#9405](https://github.com/spinnaker/deck/pull/9405) ([0f15bb57](https://github.com/spinnaker/deck/commit/0f15bb5726b8d6b6d113c27b4d70e49cf29de0d9))  



## [0.0.607](https://www.github.com/spinnaker/deck/compare/05245508779f4ed8953e9651266392350bf6ba64...ec8327948590884983967e99c6d6df40e1f76f9c) (2021-07-08)


### Changes

chore(core): publish core@0.0.607 ([ec832794](https://github.com/spinnaker/deck/commit/ec8327948590884983967e99c6d6df40e1f76f9c))  
chore(refactor): Convert NumberList to react [#9389](https://github.com/spinnaker/deck/pull/9389) ([19f97afb](https://github.com/spinnaker/deck/commit/19f97afb41e926b0abdea8c3e025121850ee3d77))  
feat(managed-delivery): Add resource deleting status and icon [#9413](https://github.com/spinnaker/deck/pull/9413) ([fa8bf205](https://github.com/spinnaker/deck/commit/fa8bf2055dfaf73f4cc0c587f9e464f3e934448c))  
chore(*): Import TaskMonitorWrapper from core instead of NgReact [#9406](https://github.com/spinnaker/deck/pull/9406) ([cce5473b](https://github.com/spinnaker/deck/commit/cce5473b600f173f9df41c7dabe6e2fceb29999f))  
refactor(fonts): Move fonts to core ([5dc1deac](https://github.com/spinnaker/deck/commit/5dc1deac6843ea118751681c7d205856882a6091))  
fix(tasks): Handle tasks with no stages [#9403](https://github.com/spinnaker/deck/pull/9403) ([15dc9387](https://github.com/spinnaker/deck/commit/15dc93875651315e0707237ddc76ada21ab14e28))  
feat(md): MD debug mode [#9401](https://github.com/spinnaker/deck/pull/9401) ([09c275d1](https://github.com/spinnaker/deck/commit/09c275d1542de156306f67cccd3341cd2617b67a))  



## [0.0.606](https://www.github.com/spinnaker/deck/compare/6accdc57040ee0715a1dbe679c7ccbc4e2c6689f...05245508779f4ed8953e9651266392350bf6ba64) (2021-07-03)


### Changes

chore(core): publish core@0.0.606 ([05245508](https://github.com/spinnaker/deck/commit/05245508779f4ed8953e9651266392350bf6ba64))  
feat(md): Display the raw delivery config instead of the processed one [#9399](https://github.com/spinnaker/deck/pull/9399) ([5d5185d7](https://github.com/spinnaker/deck/commit/5d5185d7827d53d0f9a91e4b6f0143c1802aa615))  
fix(md): convert error message to simple text [#9393](https://github.com/spinnaker/deck/pull/9393) ([55c97d9c](https://github.com/spinnaker/deck/commit/55c97d9c556de3847a975511b35d8bf130ba16d2))  



## [0.0.605](https://www.github.com/spinnaker/deck/compare/ad2e01da0bd78bc9679eda66862be144c68420b2...6accdc57040ee0715a1dbe679c7ccbc4e2c6689f) (2021-07-01)


### Changes

chore(core): publish core@0.0.605 ([6accdc57](https://github.com/spinnaker/deck/commit/6accdc57040ee0715a1dbe679c7ccbc4e2c6689f))  
fix(core): make ignored errors public [#9390](https://github.com/spinnaker/deck/pull/9390) ([98429c08](https://github.com/spinnaker/deck/commit/98429c08eecaa771b41171e9d794b53007e3a75f))  
fix(core): properly load reactGA [#9385](https://github.com/spinnaker/deck/pull/9385) ([b56aafe1](https://github.com/spinnaker/deck/commit/b56aafe1836065869367624171292a867aa76d52))  
fix(md): remove the built-in search box [#9386](https://github.com/spinnaker/deck/pull/9386) ([4b5c0b67](https://github.com/spinnaker/deck/commit/4b5c0b674409c1202e592553b87759f1f713d490))  
chore(all): Remove ng template cache for webpack ([be6df680](https://github.com/spinnaker/deck/commit/be6df680689e0624b27635bc875d0b4390a3bc4a))  
chore(build): Integrate with yarn workspaces ([e30e631b](https://github.com/spinnaker/deck/commit/e30e631b128bd1c8bfef3a48643ce0b4f9935f1d))  
chore(core): Remove EntitySource from NgReact [#9387](https://github.com/spinnaker/deck/pull/9387) ([b6ac0b7c](https://github.com/spinnaker/deck/commit/b6ac0b7c615291b42e91c7b3f07bb6ec7c2d6f18))  
feat(tasks): Use stage context over variables [#9361](https://github.com/spinnaker/deck/pull/9361) ([ca80a2ea](https://github.com/spinnaker/deck/commit/ca80a2ea48ef1a6946de8e3842b914727e2ea495))  
fix(md): only show messages section if not empty [#9380](https://github.com/spinnaker/deck/pull/9380) ([94e87011](https://github.com/spinnaker/deck/commit/94e87011adf104396597f02b83b45b3a404501bd))  
chore(core): Remove deps on ngReact for EntitySource and ViewChangesLink [#9379](https://github.com/spinnaker/deck/pull/9379) ([0a64e176](https://github.com/spinnaker/deck/commit/0a64e176819c989e26a40a3512753799f42e6680))  



## [0.0.604](https://www.github.com/spinnaker/deck/compare/4ea225e2c06a4236f50d0a281fa86c6b3907f589...ad2e01da0bd78bc9679eda66862be144c68420b2) (2021-06-25)


### Changes

chore(core): publish core@0.0.604 ([ad2e01da](https://github.com/spinnaker/deck/commit/ad2e01da0bd78bc9679eda66862be144c68420b2))  
fix(core/diffs): NPE on ViewChangesLink, minor style tweak [#9375](https://github.com/spinnaker/deck/pull/9375) ([2a9e13ef](https://github.com/spinnaker/deck/commit/2a9e13ef1faf047db165cbce2736310cd9bcf616))  



## [0.0.603](https://www.github.com/spinnaker/deck/compare/8fa0348b0bb717a8c834695852203ab5e659ca8f...4ea225e2c06a4236f50d0a281fa86c6b3907f589) (2021-06-25)


### Changes

chore(core): publish core@0.0.603 ([4ea225e2](https://github.com/spinnaker/deck/commit/4ea225e2c06a4236f50d0a281fa86c6b3907f589))  
fix(core/help): Do not render conditionally for angular compatibility [#9373](https://github.com/spinnaker/deck/pull/9373) ([1406c2f2](https://github.com/spinnaker/deck/commit/1406c2f28070ad33b3538dfe7e5dc93b3dbfe4f5))  
refactor(core): Reactify ViewChangesLink, ChangesModal, CommitHistory and JarDiff [#9359](https://github.com/spinnaker/deck/pull/9359) ([d83f6002](https://github.com/spinnaker/deck/commit/d83f60025a40c12dde11158948bdfb15e2eb5f0e))  
feat(md): backend messages  [#9366](https://github.com/spinnaker/deck/pull/9366) ([57ce3d30](https://github.com/spinnaker/deck/commit/57ce3d30e26125fed60bb33259137cd95197cde0))  
feat(md): added links to resources [#9356](https://github.com/spinnaker/deck/pull/9356) ([4469f0bf](https://github.com/spinnaker/deck/commit/4469f0bf0621e0195dfa46341b600b8f39009ffb))  
fix(core/modal): Make SubmitButton compatible with flex display [#9368](https://github.com/spinnaker/deck/pull/9368) ([8180812d](https://github.com/spinnaker/deck/commit/8180812dabd6c54d5ea271f79836fad4351745a4))  
fix(md): css fix for no artifacts [#9370](https://github.com/spinnaker/deck/pull/9370) ([c18b86a7](https://github.com/spinnaker/deck/commit/c18b86a7f80f1601a6acb62a3c32a726d89dcbb0))  



## [0.0.602](https://www.github.com/spinnaker/deck/compare/3274330cf36dbcff896ef23db3e129421d46bece...8fa0348b0bb717a8c834695852203ab5e659ca8f) (2021-06-22)


### Changes

chore(core): publish core@0.0.602 ([8fa0348b](https://github.com/spinnaker/deck/commit/8fa0348b0bb717a8c834695852203ab5e659ca8f))  
fix(md): make attributes nullable [#9365](https://github.com/spinnaker/deck/pull/9365) ([1d21f80b](https://github.com/spinnaker/deck/commit/1d21f80b7b443e6e9e3af368acd5e578e835d1f4))  



## [0.0.601](https://www.github.com/spinnaker/deck/compare/d31668d4b1d03100b81e3aaa2b17f64f7502d387...3274330cf36dbcff896ef23db3e129421d46bece) (2021-06-20)


### Changes

chore(core): publish core@0.0.601 ([3274330c](https://github.com/spinnaker/deck/commit/3274330cf36dbcff896ef23db3e129421d46bece))  
fix(core): expose user menu [#9360](https://github.com/spinnaker/deck/pull/9360) ([a3025b41](https://github.com/spinnaker/deck/commit/a3025b41b5c26b8acee36e9891f27449b27ebe56))  
refactor(core/entityTag): Convert entity source components to React [#9340](https://github.com/spinnaker/deck/pull/9340) ([f14885d1](https://github.com/spinnaker/deck/commit/f14885d15d17417ca2ba50e0151c79cb01ad6cfd))  
refactor(core): Convert UserMenu to React [#9298](https://github.com/spinnaker/deck/pull/9298) ([233279ed](https://github.com/spinnaker/deck/commit/233279ed4a1c51f3e7e14c3fb318394e03a47ddc))  
chore(core): Export TaskMonitorWrapper [#9296](https://github.com/spinnaker/deck/pull/9296) ([ff6f32ee](https://github.com/spinnaker/deck/commit/ff6f32ee317a8f534ca5c33f3c1f385827d23dcb))  
feat(md): ignore allowed-time window [#9358](https://github.com/spinnaker/deck/pull/9358) ([2ce86a34](https://github.com/spinnaker/deck/commit/2ce86a34e9e3c04cb5563b24d8ea2cd3b260e3ad))  
chore(*): Remove references to ButtonBusyInidicator [#9283](https://github.com/spinnaker/deck/pull/9283) ([ee2ab2f2](https://github.com/spinnaker/deck/commit/ee2ab2f2a8a4b35210f8da40318bbca857bae1aa))  
fix(core/serverGroup): Add space in scaling policy details [#9357](https://github.com/spinnaker/deck/pull/9357) ([10afe110](https://github.com/spinnaker/deck/commit/10afe1103e891613df3361b3b7fd949fc9800ce9))  
feat(logging): ignore noisy errors [#9345](https://github.com/spinnaker/deck/pull/9345) ([a2c7cab7](https://github.com/spinnaker/deck/commit/a2c7cab75cf68e683ccce50e6497006795d37ff8))  
feat(md): change allowed times fail message [#9355](https://github.com/spinnaker/deck/pull/9355) ([d4d9de0d](https://github.com/spinnaker/deck/commit/d4d9de0d9cd1f03e48e0bc59565bdd7f3764b440))  



## [0.0.600](https://www.github.com/spinnaker/deck/compare/bcbe23d30dcb52f2f0121c790bd3550f3132b180...d31668d4b1d03100b81e3aaa2b17f64f7502d387) (2021-06-14)


### Changes

chore(core): publish core@0.0.600 ([d31668d4](https://github.com/spinnaker/deck/commit/d31668d4b1d03100b81e3aaa2b17f64f7502d387))  
fix(md): fix pin icon from cropping due to overflow-x: auto [#9352](https://github.com/spinnaker/deck/pull/9352) ([308c931d](https://github.com/spinnaker/deck/commit/308c931d91f63e558bd794225cb7c3aa91431101))  
fix(md): improve error logging [#9343](https://github.com/spinnaker/deck/pull/9343) ([e8e555d0](https://github.com/spinnaker/deck/commit/e8e555d01410af3e914a75709d25dffaa863b8be))  



## [0.0.599](https://www.github.com/spinnaker/deck/compare/513810594a994a66d2496c91696d5c8142439396...bcbe23d30dcb52f2f0121c790bd3550f3132b180) (2021-06-11)


### Changes

chore(core): publish core@0.0.599 ([bcbe23d3](https://github.com/spinnaker/deck/commit/bcbe23d30dcb52f2f0121c790bd3550f3132b180))  
feat(md): retry version actions (verifications and post deploy) [#9339](https://github.com/spinnaker/deck/pull/9339) ([e209d696](https://github.com/spinnaker/deck/commit/e209d696b4f4fb3f1c49649ba201fb769d5c01bf))  
chore(bump): Upgrade @spinnaker/scripts ([db9f47df](https://github.com/spinnaker/deck/commit/db9f47df6eae4e87319586721c1dc95cc86290a9))  
feat(md): remove loadingIndicator.svg and use Spinner instead [#9338](https://github.com/spinnaker/deck/pull/9338) ([78497fd7](https://github.com/spinnaker/deck/commit/78497fd7e1f948e2310e3588aa7e5b606c337677))  



## [0.0.598](https://www.github.com/spinnaker/deck/compare/dd483d23092bb31b255b644ad56bd0c08b1d1269...513810594a994a66d2496c91696d5c8142439396) (2021-06-10)


### Changes

chore(core): publish core@0.0.598 ([51381059](https://github.com/spinnaker/deck/commit/513810594a994a66d2496c91696d5c8142439396))  
fix(md): constraint time position [#9332](https://github.com/spinnaker/deck/pull/9332) ([1adb2245](https://github.com/spinnaker/deck/commit/1adb2245eee324957f9debc82b849517df215fa8))  
feat(md): set grid view as default and improve styles [#9306](https://github.com/spinnaker/deck/pull/9306) ([ce4bfa25](https://github.com/spinnaker/deck/commit/ce4bfa2513fe2ec6ac3c38ed1a1190e2b357b0a6))  
chore(core/amazon): Remove ViewScalingPolicies link from NgReact [#9271](https://github.com/spinnaker/deck/pull/9271) ([0a3f56b9](https://github.com/spinnaker/deck/commit/0a3f56b94821a5d7d80bfc722ec6b2b98e5e5e85))  
refactor(core/help): Point angular and ngReact to HelpField [#9258](https://github.com/spinnaker/deck/pull/9258) ([d45b7ea2](https://github.com/spinnaker/deck/commit/d45b7ea2894bf0186ca3ba1237dc2b41b12a83e1))  
fix(md): grid icon [#9331](https://github.com/spinnaker/deck/pull/9331) ([0d5d4c49](https://github.com/spinnaker/deck/commit/0d5d4c492907a1fba151ef0cefaba3f37e460380))  
feat(md): improve error handling for non-managed applications [#9293](https://github.com/spinnaker/deck/pull/9293) ([8a0f4607](https://github.com/spinnaker/deck/commit/8a0f46076eea19db5d29384671cec9617e1a758e))  
fix(core): Clearer execution details in ServerGroupStageContext [#9329](https://github.com/spinnaker/deck/pull/9329) ([915274df](https://github.com/spinnaker/deck/commit/915274dffd7497d20bfc86787df1676fd3f9079c))  
fix(containerlogs): add ability to view multiple pod logs [#9107](https://github.com/spinnaker/deck/pull/9107) ([2bb6960a](https://github.com/spinnaker/deck/commit/2bb6960af31695b594727a99bb3e379909b58e42))  



## [0.0.597](https://www.github.com/spinnaker/deck/compare/7aab127a99b0b10f1d9eef99b828dc8db7a5a53e...dd483d23092bb31b255b644ad56bd0c08b1d1269) (2021-06-10)


### Changes

chore(core): publish core@0.0.597 ([dd483d23](https://github.com/spinnaker/deck/commit/dd483d23092bb31b255b644ad56bd0c08b1d1269))  
feat(md): log envs direction [#9325](https://github.com/spinnaker/deck/pull/9325) ([0946d19b](https://github.com/spinnaker/deck/commit/0946d19b5943ca0522531ceb659a8e7791bb9c74))  
feat(titus/pipeline): Add IPv6 toggle with defaults to Run Job Stage [#9292](https://github.com/spinnaker/deck/pull/9292) ([14cbd97e](https://github.com/spinnaker/deck/commit/14cbd97e533d1ed04800092e7ab0ba5f5101eb98))  



## [0.0.596](https://www.github.com/spinnaker/deck/compare/323dd8193428e3e117b14c57e9901a9576cc885c...7aab127a99b0b10f1d9eef99b828dc8db7a5a53e) (2021-06-09)


### Changes

chore(core): publish core@0.0.596 ([7aab127a](https://github.com/spinnaker/deck/commit/7aab127a99b0b10f1d9eef99b828dc8db7a5a53e))  
fix(core): Remove storybook to fix corejs imports ([3566e2e0](https://github.com/spinnaker/deck/commit/3566e2e00f1ecedd3313e7e36b1ce1f3573a2837))  



## [0.0.595](https://www.github.com/spinnaker/deck/compare/2a0e156c50990c0d0e5c339158c2da04369d4388...323dd8193428e3e117b14c57e9901a9576cc885c) (2021-06-09)


### Changes

chore(core): publish core@0.0.595 ([323dd819](https://github.com/spinnaker/deck/commit/323dd8193428e3e117b14c57e9901a9576cc885c))  
refactor(packages): Migrate packages to make them independent ([9da3751a](https://github.com/spinnaker/deck/commit/9da3751a3b7420eb83ee6b589c1f73b12faed572))  



## [0.0.594](https://www.github.com/spinnaker/deck/compare/4004181d2697e67a4c0a0f22689b2f39f2b2f2ec...2a0e156c50990c0d0e5c339158c2da04369d4388) (2021-06-08)


### Changes

chore(core): publish core@0.0.594 ([2a0e156c](https://github.com/spinnaker/deck/commit/2a0e156c50990c0d0e5c339158c2da04369d4388))  
feat(core): added navbar logging [#9309](https://github.com/spinnaker/deck/pull/9309) ([b75fb8c8](https://github.com/spinnaker/deck/commit/b75fb8c8fe09cae13541bdbe11366018ff7b22aa))  
feat(md): change url when expanding a version [#9307](https://github.com/spinnaker/deck/pull/9307) ([537bc9de](https://github.com/spinnaker/deck/commit/537bc9dede2342b42d8b27f7cb601ff5d1d7e254))  



## [0.0.593](https://www.github.com/spinnaker/deck/compare/2240867bf30ebad57f8bbe408b1b3a2974edcc06...4004181d2697e67a4c0a0f22689b2f39f2b2f2ec) (2021-06-07)


### Changes

chore(core): publish core@0.0.593 ([4004181d](https://github.com/spinnaker/deck/commit/4004181d2697e67a4c0a0f22689b2f39f2b2f2ec))  
feat(md): added branch details to the history branch [#9305](https://github.com/spinnaker/deck/pull/9305) ([f926f9b0](https://github.com/spinnaker/deck/commit/f926f9b0d2c300cb2d589f41bedeee31f680c3db))  
feat(md): Preview environments v0 [#9302](https://github.com/spinnaker/deck/pull/9302) ([14588473](https://github.com/spinnaker/deck/commit/14588473dfbb8921a78f8e3ad4b1f9662e4e8132))  
feat(core): Add type definition for png files ([5292c11c](https://github.com/spinnaker/deck/commit/5292c11c415ed091e88edcb98908f62eb99cb3a9))  
feat(build): Add custom rollup config for core ([e5a953b0](https://github.com/spinnaker/deck/commit/e5a953b0bc53bdffb6546be970102116eb48c13e))  



## [0.0.592](https://www.github.com/spinnaker/deck/compare/c72702b8f5884f1db4d5ce1ece45366c3594e316...2240867bf30ebad57f8bbe408b1b3a2974edcc06) (2021-06-04)


### Changes

chore(core): publish core@0.0.592 ([2240867b](https://github.com/spinnaker/deck/commit/2240867bf30ebad57f8bbe408b1b3a2974edcc06))  
feat(md): change the deploying state blinking green instead of teal [#9291](https://github.com/spinnaker/deck/pull/9291) ([33708ee9](https://github.com/spinnaker/deck/commit/33708ee93ef675d7a074e12691e811847bbed2c7))  
feat(md): set the new UI as the default one, and allow users to opt out [#9290](https://github.com/spinnaker/deck/pull/9290) ([995d622a](https://github.com/spinnaker/deck/commit/995d622a4bfe30704f7f938a44bbae4368ab5f91))  
feat(md): show delivery config yml in the configuration tab [#9286](https://github.com/spinnaker/deck/pull/9286) ([3bb4ffea](https://github.com/spinnaker/deck/commit/3bb4ffea15aa62efb74c4f14891bf0e59b188084))  
fix(md): minor improvements based on feedback from #9269 [#9289](https://github.com/spinnaker/deck/pull/9289) ([75a3a251](https://github.com/spinnaker/deck/commit/75a3a251f7226b9e896baa8101dced957099d7cc))  



## [0.0.591](https://www.github.com/spinnaker/deck/compare/0e201c274c1b4c198d99050acd8690f79c4813de...c72702b8f5884f1db4d5ce1ece45366c3594e316) (2021-06-04)


### Changes

chore(core): publish core@0.0.591 ([c72702b8](https://github.com/spinnaker/deck/commit/c72702b8f5884f1db4d5ce1ece45366c3594e316))  
feat(md): New option to display environments side by side [#9269](https://github.com/spinnaker/deck/pull/9269) ([4d689b5b](https://github.com/spinnaker/deck/commit/4d689b5b85efa73c2ec90476ffd2e3135a129ab7))  
fix(nav): fix unsubscribe on unmount [#9285](https://github.com/spinnaker/deck/pull/9285) ([ae43ad01](https://github.com/spinnaker/deck/commit/ae43ad0102faaf0a35dc4a2984c513f22bb22e2f))  
fix(md): properly redirect the old ui links to the new ui [#9287](https://github.com/spinnaker/deck/pull/9287) ([d998362c](https://github.com/spinnaker/deck/commit/d998362c672522c01d7ad31dddba61c7f08914bf))  



## [0.0.590](https://www.github.com/spinnaker/deck/compare/e8e53dcaade518ff188593760f8fd591c2b00cce...0e201c274c1b4c198d99050acd8690f79c4813de) (2021-06-03)


### Changes

chore(core): publish core@0.0.590 ([0e201c27](https://github.com/spinnaker/deck/commit/0e201c274c1b4c198d99050acd8690f79c4813de))  
feat(md): moved createdAt to the metadata instead of on the left [#9279](https://github.com/spinnaker/deck/pull/9279) ([bf5e954e](https://github.com/spinnaker/deck/commit/bf5e954eb4aba329b50a1a85894a1df26763a86f))  
refactor(build): Fix paths for rollup ([9a9468a2](https://github.com/spinnaker/deck/commit/9a9468a2e1da465d5b95ae996395f4e59b116a09))  
feat(md): added logs to the important actions and events [#9281](https://github.com/spinnaker/deck/pull/9281) ([915b3421](https://github.com/spinnaker/deck/commit/915b3421b9a7070cad57ac8612406df5b71ad38c))  
fix(md): allowed time default timezone [#9280](https://github.com/spinnaker/deck/pull/9280) ([2917d7b5](https://github.com/spinnaker/deck/commit/2917d7b5de10488bc8df20d2f1db71dc60413316))  
fix(md): only show git link if the commit message is not a link [#9278](https://github.com/spinnaker/deck/pull/9278) ([8524b0ea](https://github.com/spinnaker/deck/commit/8524b0eafee5a4fe238ae6a233752de89ab399ec))  
feat(md): show resource account in metadata [#9277](https://github.com/spinnaker/deck/pull/9277) ([8b1e1f8e](https://github.com/spinnaker/deck/commit/8b1e1f8e9724ed8fd726e8bd1687c109d5e495c9))  
style(md): add spacing above the actions if there is a description [#9276](https://github.com/spinnaker/deck/pull/9276) ([23103b0d](https://github.com/spinnaker/deck/commit/23103b0d66333ffc2cedecd38a249d16fabf34af))  



## [0.0.589](https://www.github.com/spinnaker/deck/compare/7223865f7ec31429c342dad04f0a7384f5a21734...e8e53dcaade518ff188593760f8fd591c2b00cce) (2021-06-02)


### Changes

chore(core): publish core@0.0.589 ([e8e53dca](https://github.com/spinnaker/deck/commit/e8e53dcaade518ff188593760f8fd591c2b00cce))  
fix(style): shrink padding on stage details tabs [#9274](https://github.com/spinnaker/deck/pull/9274) ([acc1243f](https://github.com/spinnaker/deck/commit/acc1243f41e08333cfc7728d24ba9d83e90ddcbe))  
fix(core): Re-order imports and remove requires ([e6d020cc](https://github.com/spinnaker/deck/commit/e6d020ccf028ee4e40d870a8dc685efa81fd8137))  
feat(md): version history collapsible icon - switch to arrow and cross icon [#9268](https://github.com/spinnaker/deck/pull/9268) ([18039b90](https://github.com/spinnaker/deck/commit/18039b9000160406b2b9c85a17e695078df22c10))  
feat(core/spinner): Consolidate spinners [#9255](https://github.com/spinnaker/deck/pull/9255) ([e5375f0d](https://github.com/spinnaker/deck/commit/e5375f0d4122e081bc764cdd79c62b352761d81d))  



## [0.0.588](https://www.github.com/spinnaker/deck/compare/405fc1fbaca2f309950ac263dd66530571dc74b7...7223865f7ec31429c342dad04f0a7384f5a21734) (2021-06-01)


### Changes

chore(core): publish core@0.0.588 ([7223865f](https://github.com/spinnaker/deck/commit/7223865f7ec31429c342dad04f0a7384f5a21734))  
feat(md): improve styles on mobile + large screen [#9260](https://github.com/spinnaker/deck/pull/9260) ([0647665d](https://github.com/spinnaker/deck/commit/0647665d47a38a3430cac431dcf67360a7a55199))  
fix(core/stage): Remove string interpolation token from ng template for rollup ([7cfaca12](https://github.com/spinnaker/deck/commit/7cfaca1271517302b70164d4ddb47e312867d375))  
fix(core): Fix imports in core for rollup ([8d8e8a59](https://github.com/spinnaker/deck/commit/8d8e8a5997fd6b0f81907b05d8606167817ba350))  
refactor(core): Convert alias imports to relative imports in less ([8b6478f1](https://github.com/spinnaker/deck/commit/8b6478f18088e31531377b1baf606f81a1817fec))  
feat(md): log ui toggling [#9254](https://github.com/spinnaker/deck/pull/9254) ([6ed36898](https://github.com/spinnaker/deck/commit/6ed36898e83c3620b7622e0cc79599dbaa99aabe))  
fix(md): constraint icon size [#9253](https://github.com/spinnaker/deck/pull/9253) ([a2b79f24](https://github.com/spinnaker/deck/commit/a2b79f240b46e726c795c6d96d5ea1e731e1adae))  



## [0.0.587](https://www.github.com/spinnaker/deck/compare/5956d64344e039989f044f361cf4d6aa5acb0710...405fc1fbaca2f309950ac263dd66530571dc74b7) (2021-05-27)


### Changes

chore(core): publish core@0.0.587 ([405fc1fb](https://github.com/spinnaker/deck/commit/405fc1fbaca2f309950ac263dd66530571dc74b7))  
feat(core): add google analytics to logger [#9246](https://github.com/spinnaker/deck/pull/9246) ([50e06cb4](https://github.com/spinnaker/deck/commit/50e06cb421402b4c36fbb0810c4704061aefcd71))  



## [0.0.586](https://www.github.com/spinnaker/deck/compare/8c83e6ef71cb2c90f4a52034bf4b1bed47361b22...5956d64344e039989f044f361cf4d6aa5acb0710) (2021-05-26)


### Changes

chore(core): publish core@0.0.586 ([5956d643](https://github.com/spinnaker/deck/commit/5956d64344e039989f044f361cf4d6aa5acb0710))  
feat(md): History - scroll to version + share link [#9222](https://github.com/spinnaker/deck/pull/9222) ([f78f141f](https://github.com/spinnaker/deck/commit/f78f141f229153be81cf45056258a0dda665fcd0))  
refactor(core): Reactify scaling activities modal [#9248](https://github.com/spinnaker/deck/pull/9248) ([9437dcc3](https://github.com/spinnaker/deck/commit/9437dcc3eedd705e33a7a4fdff0643148a8315d0))  
feat(core): Link to Build and Image in server group header [#9236](https://github.com/spinnaker/deck/pull/9236) ([34764ed6](https://github.com/spinnaker/deck/commit/34764ed64fc738ab997bfb6b6850e37e5c3f5f40))  
feat(md): Added mark as good + refactor actions to mutations and hooks [#9245](https://github.com/spinnaker/deck/pull/9245) ([0dd557c0](https://github.com/spinnaker/deck/commit/0dd557c0232428b1b89ab49777361da29fddf47c))  



## [0.0.585](https://www.github.com/spinnaker/deck/compare/d09f556d534a7ffda1d49201fbd8209811054fc0...8c83e6ef71cb2c90f4a52034bf4b1bed47361b22) (2021-05-26)


### Changes

chore(core): publish core@0.0.585 ([8c83e6ef](https://github.com/spinnaker/deck/commit/8c83e6ef71cb2c90f4a52034bf4b1bed47361b22))  
fix(md): wrap tooltip content with span [#9241](https://github.com/spinnaker/deck/pull/9241) ([1bb91d2e](https://github.com/spinnaker/deck/commit/1bb91d2e5f7b20615e980b8e998f6d89bf5eea3a))  
refactor(core): Reactify scaling activities modal [#9214](https://github.com/spinnaker/deck/pull/9214) ([0ecc87de](https://github.com/spinnaker/deck/commit/0ecc87de2690dc9b5f0d0f3f94571c76cb52e4bf))  



## [0.0.584](https://www.github.com/spinnaker/deck/compare/6cb6484860e9e14342f3e9b235b4e3f79cbbda00...d09f556d534a7ffda1d49201fbd8209811054fc0) (2021-05-25)


### Changes

chore(core): publish core@0.0.584 ([d09f556d](https://github.com/spinnaker/deck/commit/d09f556d534a7ffda1d49201fbd8209811054fc0))  
feat(md): version veto info [#9234](https://github.com/spinnaker/deck/pull/9234) ([12ed317c](https://github.com/spinnaker/deck/commit/12ed317c16f1c59f87fc770cea23f0a50ebf8918))  
fix(md): lifecycle data was missing in the query [#9233](https://github.com/spinnaker/deck/pull/9233) ([28f35bcf](https://github.com/spinnaker/deck/commit/28f35bcf08fec2069575a730183230073bb5db53))  
feat(md): limit history versions to 100 [#9232](https://github.com/spinnaker/deck/pull/9232) ([5a177192](https://github.com/spinnaker/deck/commit/5a177192f40b1c317b003565aa2165d7e44a54eb))  
feat(md): build version tooltip [#9231](https://github.com/spinnaker/deck/pull/9231) ([78730b1f](https://github.com/spinnaker/deck/commit/78730b1f907bed31e904b473a636aff3718cd4c6))  
feat(md): pinned data tooltip info [#9208](https://github.com/spinnaker/deck/pull/9208) ([f0235b3d](https://github.com/spinnaker/deck/commit/f0235b3d076fe5b55b56bea3aeb11cc44f0f19be))  



## [0.0.583](https://www.github.com/spinnaker/deck/compare/cc275e3faa0981fe8564a74c8ad128802c02ba4d...6cb6484860e9e14342f3e9b235b4e3f79cbbda00) (2021-05-24)


### Changes

chore(core): publish core@0.0.583 ([6cb64848](https://github.com/spinnaker/deck/commit/6cb6484860e9e14342f3e9b235b4e3f79cbbda00))  
style(md): fix constraints style - adding margin to chevron + add padding below the actions properly [#9227](https://github.com/spinnaker/deck/pull/9227) ([2c547cdb](https://github.com/spinnaker/deck/commit/2c547cdb5ed69ef241e3617eeb956065874c9be3))  
fix(md): environments are now sorted on the backend. We only need to reverse the order in the history heading [#9228](https://github.com/spinnaker/deck/pull/9228) ([7845147c](https://github.com/spinnaker/deck/commit/7845147c536c0b1eea63e7668a63e043d0d7c4d4))  
feat(md): added judgedAt time to constraints + switched to CollapsibleSection [#9215](https://github.com/spinnaker/deck/pull/9215) ([c40745d0](https://github.com/spinnaker/deck/commit/c40745d0979a2bee32d948b8a9e59fdd8c76d0bc))  
fix(md): refetch version on constraint (works in all cases) [#9225](https://github.com/spinnaker/deck/pull/9225) ([cca5ad60](https://github.com/spinnaker/deck/commit/cca5ad602ee9f9bae74ce3084eb7c2877ea0bc10))  
feat(md): show management warning in history [#9226](https://github.com/spinnaker/deck/pull/9226) ([844ee30b](https://github.com/spinnaker/deck/commit/844ee30b899ec27eb8b4c755aafad4d7251797a9))  
feat(md): only show first two pending versions [#9224](https://github.com/spinnaker/deck/pull/9224) ([98f0238a](https://github.com/spinnaker/deck/commit/98f0238a23ac896fe451ad074a09177f0d48a778))  
fix(md): sort versions even when no current exists [#9223](https://github.com/spinnaker/deck/pull/9223) ([8e59bcb2](https://github.com/spinnaker/deck/commit/8e59bcb28d5814d0244a5ccd92d964be26a52c4b))  



## [0.0.582](https://www.github.com/spinnaker/deck/compare/7a690e3b7d4da344a41fb20cf4e52f1bd3e5cffe...cc275e3faa0981fe8564a74c8ad128802c02ba4d) (2021-05-21)


### Changes

chore(core): publish core@0.0.582 ([cc275e3f](https://github.com/spinnaker/deck/commit/cc275e3faa0981fe8564a74c8ad128802c02ba4d))  
feat(md): added commit sha to text and a link the tooltip [#9213](https://github.com/spinnaker/deck/pull/9213) ([47b520dc](https://github.com/spinnaker/deck/commit/47b520dc04465b9b37aea76d470d49d90f23568a))  



## [0.0.581](https://www.github.com/spinnaker/deck/compare/20dd06a1bcbbd34eb22f4710ea3da0c43c1ca3b4...7a690e3b7d4da344a41fb20cf4e52f1bd3e5cffe) (2021-05-21)


### Changes

chore(core): publish core@0.0.581 ([7a690e3b](https://github.com/spinnaker/deck/commit/7a690e3b7d4da344a41fb20cf4e52f1bd3e5cffe))  
fix(md): fix order of environments [#9207](https://github.com/spinnaker/deck/pull/9207) ([d62bd8cb](https://github.com/spinnaker/deck/commit/d62bd8cb141035ea3b8590625cd8cf8d92c45700))  
feat(md): show error message if there are no artifacts or resources [#9205](https://github.com/spinnaker/deck/pull/9205) ([7791003a](https://github.com/spinnaker/deck/commit/7791003a487a2f261db0c3f9af32e2081773f3dc))  
fix(core/pipeline): Warn if concurrency disabled on stage restart [#9143](https://github.com/spinnaker/deck/pull/9143) ([edec92b4](https://github.com/spinnaker/deck/commit/edec92b4860e53e43b9dc4fd613af98fc5450eac))  
fix(lint): Fix lint errors ([5561db04](https://github.com/spinnaker/deck/commit/5561db048f69d671225521a00fd2aeb5442fc6b6))  



## [0.0.580](https://www.github.com/spinnaker/deck/compare/0f9e4041c95e8c05e9428b8c4b94efc91baa439c...20dd06a1bcbbd34eb22f4710ea3da0c43c1ca3b4) (2021-05-20)


### Changes

chore(core): publish core@0.0.580 ([20dd06a1](https://github.com/spinnaker/deck/commit/20dd06a1bcbbd34eb22f4710ea3da0c43c1ca3b4))  
feat(md): versions history page [#9191](https://github.com/spinnaker/deck/pull/9191) ([cf49088a](https://github.com/spinnaker/deck/commit/cf49088aba8ca899d5bd76cd3affa8cd70103a5f))  
fix(core/pipeline): Resolved pipeline header alignment issue by ellip… [#9110](https://github.com/spinnaker/deck/pull/9110) ([fb07390a](https://github.com/spinnaker/deck/commit/fb07390a3bf7364288dbe2c269c937df258ef191))  
feat(pipeline): Change back button to breadcrumbs [#9020](https://github.com/spinnaker/deck/pull/9020) ([2ae83451](https://github.com/spinnaker/deck/commit/2ae834515af2f2a1128b820e61af21d68a9bd1e2))  
fix(core/task): Render "submitting task" instead of throwing an error in MultiTaskMonitor.tsx [#9177](https://github.com/spinnaker/deck/pull/9177) ([00818f7a](https://github.com/spinnaker/deck/commit/00818f7ab716e54367a30f20f51b63f9019b2c03))  



## [0.0.579](https://www.github.com/spinnaker/deck/compare/00fc8d9f7f2dee1515167fe6880305f574494e14...0f9e4041c95e8c05e9428b8c4b94efc91baa439c) (2021-05-15)


### Changes

chore(core): publish core@0.0.579 ([0f9e4041](https://github.com/spinnaker/deck/commit/0f9e4041c95e8c05e9428b8c4b94efc91baa439c))  
fix(md): renamed feature flag [#9181](https://github.com/spinnaker/deck/pull/9181) ([7f6cbad0](https://github.com/spinnaker/deck/commit/7f6cbad0d9c38c06701ddd3a72878ec21b917992))  



## [0.0.578](https://www.github.com/spinnaker/deck/compare/2daaa57095382a66bc284011ff243e19b2741f8d...00fc8d9f7f2dee1515167fe6880305f574494e14) (2021-05-14)


### Changes

chore(core): publish core@0.0.578 ([00fc8d9f](https://github.com/spinnaker/deck/commit/00fc8d9f7f2dee1515167fe6880305f574494e14))  
fix(md): check if should use the new ui [#9179](https://github.com/spinnaker/deck/pull/9179) ([834af1ed](https://github.com/spinnaker/deck/commit/834af1ed2b5af9dd417347d1e21717ec2e14792d))  
feat(core/ManualJudgment): Enhanced ManualJudgment [#8818](https://github.com/spinnaker/deck/pull/8818) ([91e864c1](https://github.com/spinnaker/deck/commit/91e864c19e6e24260bda386f4939d67a08302ce3))  
fix(core): classnames package cleanup [#9173](https://github.com/spinnaker/deck/pull/9173) ([4940c482](https://github.com/spinnaker/deck/commit/4940c482732b95030164e23abd72e91d3a781bca))  



## [0.0.577](https://www.github.com/spinnaker/deck/compare/7c53c8b5570b9e0e319fcfa46d34ac479a086af2...2daaa57095382a66bc284011ff243e19b2741f8d) (2021-05-13)


### Changes

chore(core): publish core@0.0.577 ([2daaa570](https://github.com/spinnaker/deck/commit/2daaa57095382a66bc284011ff243e19b2741f8d))  
feat(core): logger component [#9172](https://github.com/spinnaker/deck/pull/9172) ([16cd17e5](https://github.com/spinnaker/deck/commit/16cd17e52869a53f30a064f5284ee2e6f09cee1d))  
fix(md): ignore the status event if it's identical to the reason [#9174](https://github.com/spinnaker/deck/pull/9174) ([5b2b797a](https://github.com/spinnaker/deck/commit/5b2b797a4f0fb534f3a3da5c02215d7a291496d5))  
fix(core/popover): Scope css back to running tasks popover [#9175](https://github.com/spinnaker/deck/pull/9175) ([c751dba6](https://github.com/spinnaker/deck/commit/c751dba6600c586db4526865926d94a8d733d0df))  



## [0.0.576](https://www.github.com/spinnaker/deck/compare/0473b0e5c6c20b05af3ea657ba6f805e5b14d554...7c53c8b5570b9e0e319fcfa46d34ac479a086af2) (2021-05-13)


### Changes

chore(core): publish core@0.0.576 ([7c53c8b5](https://github.com/spinnaker/deck/commit/7c53c8b5570b9e0e319fcfa46d34ac479a086af2))  
feat(md): handle BLOCKED state for constraints [#9168](https://github.com/spinnaker/deck/pull/9168) ([9a33c575](https://github.com/spinnaker/deck/commit/9a33c5751e60b510de7b1e9becce394fc01851ef))  
feat(md): show post deploy tasks [#9166](https://github.com/spinnaker/deck/pull/9166) ([f47cc5bf](https://github.com/spinnaker/deck/commit/f47cc5bf5539592d8ae3fca7c0583ffeb10cfba5))  



## [0.0.575](https://www.github.com/spinnaker/deck/compare/52ea9b6b790409a8b94a5d0a6c80a236befd4acd...0473b0e5c6c20b05af3ea657ba6f805e5b14d554) (2021-05-13)


### Changes

chore(core): publish core@0.0.575 ([0473b0e5](https://github.com/spinnaker/deck/commit/0473b0e5c6c20b05af3ea657ba6f805e5b14d554))  
fix(deck): Attempt to fix `Cannot read property 'length' of undefined` [#9153](https://github.com/spinnaker/deck/pull/9153) ([94bfcf4e](https://github.com/spinnaker/deck/commit/94bfcf4e0dee133deed6b216239be8ac4e5a678f))  
fix(core): fix state update for pipeline tags [#9163](https://github.com/spinnaker/deck/pull/9163) ([31e83036](https://github.com/spinnaker/deck/commit/31e8303623b11505e075ae589b3a7e9a4c3f2f1d))  
feat(md): application management [#9154](https://github.com/spinnaker/deck/pull/9154) ([f0086f6d](https://github.com/spinnaker/deck/commit/f0086f6d3e04b1bc95df93225bf56651ed1e7528))  



## [0.0.574](https://www.github.com/spinnaker/deck/compare/68a6192ec0157eb98ef14e7cd0506c24ade4cb94...52ea9b6b790409a8b94a5d0a6c80a236befd4acd) (2021-05-12)


### Changes

chore(core): publish core@0.0.574 ([52ea9b6b](https://github.com/spinnaker/deck/commit/52ea9b6b790409a8b94a5d0a6c80a236befd4acd))  
refactor(core/serverGroup): Convert running tasks popover to react [#9152](https://github.com/spinnaker/deck/pull/9152) ([70a2b998](https://github.com/spinnaker/deck/commit/70a2b9985d947216ac3c2559232eff9eb5b49ded))  
refactor(core): Convert AddEntityTagLinks to React [#9147](https://github.com/spinnaker/deck/pull/9147) ([39fa7730](https://github.com/spinnaker/deck/commit/39fa77303b75ca6e5b0af96d1e011bcff452cede))  
feat(aws/lb): Internal ALBs can be dualstacked [#9144](https://github.com/spinnaker/deck/pull/9144) ([7499271c](https://github.com/spinnaker/deck/commit/7499271c1eb45f88aae3265a48f7e9148bf5d3ea))  
feat(aws/titus): Add help text to IPv6 field [#9139](https://github.com/spinnaker/deck/pull/9139) ([01aef56e](https://github.com/spinnaker/deck/commit/01aef56e4d9d645caa7f82a814b4b9be5d5d5ae1))  



## [0.0.573](https://www.github.com/spinnaker/deck/compare/c2e67da810fef9e3b9972daa7f4d454bae8221d2...68a6192ec0157eb98ef14e7cd0506c24ade4cb94) (2021-05-06)


### Changes

chore(core): publish core@0.0.573 ([68a6192e](https://github.com/spinnaker/deck/commit/68a6192ec0157eb98ef14e7cd0506c24ade4cb94))  
fix(core): fix issue in console log modal [#9134](https://github.com/spinnaker/deck/pull/9134) ([8aeeaf7a](https://github.com/spinnaker/deck/commit/8aeeaf7a470f5704a04c9b54728e5548402cd705))  
fix(style): this ensure that the main div won't overflow the page [#9130](https://github.com/spinnaker/deck/pull/9130) ([716f7b78](https://github.com/spinnaker/deck/commit/716f7b78cf5300dbdddcbb981c622b8b0d9437dc))  
feat(pipeline): Show completed time in stage details [#9126](https://github.com/spinnaker/deck/pull/9126) ([408f0eba](https://github.com/spinnaker/deck/commit/408f0ebaa7ebbd942ac369b4cde98cf5887cb08c))  
chore(prettier): Just Update Prettier™️ ([9aeb398b](https://github.com/spinnaker/deck/commit/9aeb398ba3a6a49ad9677f2d8b5ba6f8840f2b21))  
fix(core): pushed modal overlay to the background [#9111](https://github.com/spinnaker/deck/pull/9111) ([22859bea](https://github.com/spinnaker/deck/commit/22859bea9a4a1f2ecbcb7d131f204e9131b756cc))  



## [0.0.572](https://www.github.com/spinnaker/deck/compare/5dc996a4cf188e05b14795294dedaef4d783b585...c2e67da810fef9e3b9972daa7f4d454bae8221d2) (2021-04-29)


### Changes

chore(core): publish core@0.0.572 ([c2e67da8](https://github.com/spinnaker/deck/commit/c2e67da810fef9e3b9972daa7f4d454bae8221d2))  
feat(codebuild): Secondary Sources Version [#8885](https://github.com/spinnaker/deck/pull/8885) ([75d0dcf5](https://github.com/spinnaker/deck/commit/75d0dcf5add0fe4d24ee6c9be1a4bf507fb4b6a6))  
chore(rxjs): Migrate to static combineLatest and fix typing errors ([496e44af](https://github.com/spinnaker/deck/commit/496e44af5fb9abf304d9f4e1b08a2f4f0d2a634e))  
chore(rxjs): Remove now unused imports of Observable from 'rxjs' ([a4fc97f3](https://github.com/spinnaker/deck/commit/a4fc97f3abfa4395079e70c52e856dad4d0ecc68))  
chore(rxjs): Manually migrate rxjs code in .js files ([30c3ee8d](https://github.com/spinnaker/deck/commit/30c3ee8d593973b0c6a87fd0a2dd9a15b10470f0))  
chore(rxjs): Fix combineLatest deprecated calls ([2a9e2a4b](https://github.com/spinnaker/deck/commit/2a9e2a4bbfbc07f2d5fb8cca4e67276aa3e2c8ea))  
chore(rxjs): Run rxjs 5-to-6 migration tooling ([c11835cf](https://github.com/spinnaker/deck/commit/c11835cfef079d5d6af8dcfbafa4fe416a059a3e))  
fix(redblack): Update redblack fields for angular DeploymentStrategyS… [#9105](https://github.com/spinnaker/deck/pull/9105) ([b9a8b58e](https://github.com/spinnaker/deck/commit/b9a8b58e3499702dad7381d5f19a3cab17dd3e1c))  



## [0.0.571](https://www.github.com/spinnaker/deck/compare/be04cc294c90613fb78695d85088dcebf83f8c5b...5dc996a4cf188e05b14795294dedaef4d783b585) (2021-04-23)


### Changes

chore(core): publish core@0.0.571 ([5dc996a4](https://github.com/spinnaker/deck/commit/5dc996a4cf188e05b14795294dedaef4d783b585))  
feat(core): updated notifications to react-toastify [#9103](https://github.com/spinnaker/deck/pull/9103) ([1a6ae753](https://github.com/spinnaker/deck/commit/1a6ae753bacc92f32c4d66ff98314a667713ce67))  
chore(core): bumped date-fns version [#9101](https://github.com/spinnaker/deck/pull/9101) ([a0056e2e](https://github.com/spinnaker/deck/commit/a0056e2e7cebc1ba71295a4bbd2db9659722049a))  



## [0.0.570](https://www.github.com/spinnaker/deck/compare/ac2b29ef0d9b1e4b84e1ef3cec3f08aaf8a972f5...be04cc294c90613fb78695d85088dcebf83f8c5b) (2021-04-21)


### Changes

chore(core): publish core@0.0.570 ([be04cc29](https://github.com/spinnaker/deck/commit/be04cc294c90613fb78695d85088dcebf83f8c5b))  



## [0.0.569](https://www.github.com/spinnaker/deck/compare/97efd25bc7f0001e2dc3a8ec12f172f698f35c92...ac2b29ef0d9b1e4b84e1ef3cec3f08aaf8a972f5) (2021-04-21)


### Changes

chore(core): publish core@0.0.569 ([ac2b29ef](https://github.com/spinnaker/deck/commit/ac2b29ef0d9b1e4b84e1ef3cec3f08aaf8a972f5))  
Remove webpack modules + webpack consolidation [#9097](https://github.com/spinnaker/deck/pull/9097) ([00145566](https://github.com/spinnaker/deck/commit/001455667f2afb5c728737863f7365fc4fcbb76b))  
fix(pipeline): Do not infer user from parent [#9095](https://github.com/spinnaker/deck/pull/9095) ([9b08e8d1](https://github.com/spinnaker/deck/commit/9b08e8d192e29c7a34aa9330f819043a439d2904))  
feat(core/pipeline): filter by stages awaiting judgement [#9096](https://github.com/spinnaker/deck/pull/9096) ([ac1213b1](https://github.com/spinnaker/deck/commit/ac1213b16487b2e4fc0fe63d931160a53e2f9555))  
fix(changelog-link): refactor changelog logic [#9090](https://github.com/spinnaker/deck/pull/9090) ([f2801092](https://github.com/spinnaker/deck/commit/f280109273f95cc582870594ca99078cccfedafa))  
feat(core): subscribe apollo client to app refereshs ([5647c4d7](https://github.com/spinnaker/deck/commit/5647c4d7fcaf11fbacb80ccce703067637e048dc))  
feat(core): added graphql framework (no actual functionality) ([23893c02](https://github.com/spinnaker/deck/commit/23893c02bda07cb4b4ffa7153e5354971d0c50af))  



## [0.0.568](https://www.github.com/spinnaker/deck/compare/55837bb0d7f8229e587063381271f3f24f4d2662...97efd25bc7f0001e2dc3a8ec12f172f698f35c92) (2021-04-14)


### Changes

chore(core): publish core@0.0.568 ([97efd25b](https://github.com/spinnaker/deck/commit/97efd25bc7f0001e2dc3a8ec12f172f698f35c92))  
fix(core/pipeline): Avoids checkStale error when saving a pipeline after renaming ([a465f7e3](https://github.com/spinnaker/deck/commit/a465f7e38bc57d40f45dc436d7347151aac49704))  
fix(core/pipeline): Fixes "stale check" failures when saving pipelines twice, also fixes revert behavior of pipeline tags/description ([30151b53](https://github.com/spinnaker/deck/commit/30151b53d35a4f4ad5c81fcc0264ce009eb9a970))  
feat(trigger): Add an optional link to configure a stash trigger [#9035](https://github.com/spinnaker/deck/pull/9035) ([61f10377](https://github.com/spinnaker/deck/commit/61f10377d21a67826d97f78fb6265f6f091dae71))  
fix(core): Fix lint error ([cb939961](https://github.com/spinnaker/deck/commit/cb93996170ccd3438f5deedc8770ae1c2196086b))  
feat(core/taskExecutor): Adds failure message to error [#9083](https://github.com/spinnaker/deck/pull/9083) ([e5aa34f8](https://github.com/spinnaker/deck/commit/e5aa34f89fad4af9c60ed5a6c020163cc1bd1765))  
feat(aws): Adding support to view details of an ASG with MixedInstancesPolicy(MIP) [#8960](https://github.com/spinnaker/deck/pull/8960) ([fb0ad572](https://github.com/spinnaker/deck/commit/fb0ad5726b1321bbc9cae378f4a50f388fe5c611))  



## [0.0.567](https://www.github.com/spinnaker/deck/compare/64ac711998e12aa80f8651c8cbb5540ffeb10d0c...55837bb0d7f8229e587063381271f3f24f4d2662) (2021-04-08)


### Changes

chore(core): publish core@0.0.567 ([55837bb0](https://github.com/spinnaker/deck/commit/55837bb0d7f8229e587063381271f3f24f4d2662))  
fix(core): unique pipeline graph ids ([eacb254f](https://github.com/spinnaker/deck/commit/eacb254fbef47a1dfd8916f1da03a43094c4106a))  
feat(md): allowed times constraints - add times group and fixed timezone [#9072](https://github.com/spinnaker/deck/pull/9072) ([c3d46014](https://github.com/spinnaker/deck/commit/c3d4601400cb481404707f47d97967ad768348ee))  
fix(md): Manual judgement failed text [#9075](https://github.com/spinnaker/deck/pull/9075) ([c9211031](https://github.com/spinnaker/deck/commit/c92110315030f555358817278123fd6804ec1721))  



## [0.0.566](https://www.github.com/spinnaker/deck/compare/b3845a5d2b466d8f4397eb614a63a8b602156a76...64ac711998e12aa80f8651c8cbb5540ffeb10d0c) (2021-04-06)


### Changes

chore(core): publish core@0.0.566 ([64ac7119](https://github.com/spinnaker/deck/commit/64ac711998e12aa80f8651c8cbb5540ffeb10d0c))  
feat(titus): Finish migrating instance details to react [#9034](https://github.com/spinnaker/deck/pull/9034) ([3ae687e5](https://github.com/spinnaker/deck/commit/3ae687e51992d1294672f78bbb1a041481044fc0))  
feat(infrastructure/buttons): Add a new property configurable on sett… [#7822](https://github.com/spinnaker/deck/pull/7822) ([0bb92635](https://github.com/spinnaker/deck/commit/0bb9263502188fcbadd4b1d1218f345229e0d184))  
feat(md): sort environments based on constraints on the main page [#9071](https://github.com/spinnaker/deck/pull/9071) ([b311fbdf](https://github.com/spinnaker/deck/commit/b311fbdfa1f7560a83325f1001dc50391bdf047a))  



## [0.0.565](https://www.github.com/spinnaker/deck/compare/009ec323bf178039a86d462240b48f0423327635...b3845a5d2b466d8f4397eb614a63a8b602156a76) (2021-04-02)


### Changes

chore(core): publish core@0.0.565 ([b3845a5d](https://github.com/spinnaker/deck/commit/b3845a5d2b466d8f4397eb614a63a8b602156a76))  
fix(md): fix order of environments based on the constraints [#9069](https://github.com/spinnaker/deck/pull/9069) ([7785f840](https://github.com/spinnaker/deck/commit/7785f840fd5bd31279047efc8c660aa7760ba80b))  



## [0.0.564](https://www.github.com/spinnaker/deck/compare/7c5ee9f78c3c900e1440e855a3d7314214121361...009ec323bf178039a86d462240b48f0423327635) (2021-04-02)


### Changes

chore(core): publish core@0.0.564 ([009ec323](https://github.com/spinnaker/deck/commit/009ec323bf178039a86d462240b48f0423327635))  
feat(md): Show resource history link on hover [#9063](https://github.com/spinnaker/deck/pull/9063) ([80454d37](https://github.com/spinnaker/deck/commit/80454d37e024bb8b37d1a8d46ad475c2ff6fab65))  
feat(pipeline): Pipeline Tags config UI [#9056](https://github.com/spinnaker/deck/pull/9056) ([9de0098b](https://github.com/spinnaker/deck/commit/9de0098b7f23083baab31fb2695781bdbd60e92c))  



## [0.0.563](https://www.github.com/spinnaker/deck/compare/2c384c9853ddd6859019d739034b0e02542d8667...7c5ee9f78c3c900e1440e855a3d7314214121361) (2021-03-30)


### Changes

chore(core): publish core@0.0.563 ([7c5ee9f7](https://github.com/spinnaker/deck/commit/7c5ee9f78c3c900e1440e855a3d7314214121361))  
test: remove async/done combination which is now deprecated ([ecbd4e2e](https://github.com/spinnaker/deck/commit/ecbd4e2e96066a00dc85ac2cc7302dcc6f16b7e2))  
test: fix typing errors introduced by stricter types in updated @types/jasmine package ([0ff5c18c](https://github.com/spinnaker/deck/commit/0ff5c18c3fb947800460d32241e8b31d6720c760))  
test: fix uncaught rejection detected by updated jasmine/karma libs ([ee5bcfc9](https://github.com/spinnaker/deck/commit/ee5bcfc933c6a2ef49c78b0c7ded11e0ecdc1bc8))  
test: switch some imports to `import type` to eliminate certain warnings during karma runs ([84e4fe6b](https://github.com/spinnaker/deck/commit/84e4fe6b24592c9bbb65fe45339ccf2a930b2c50))  
chore(ci): replace hex colors with closes css variables [#9060](https://github.com/spinnaker/deck/pull/9060) ([f8f72b7d](https://github.com/spinnaker/deck/commit/f8f72b7d53c55f635b2c96490ac1f8c9d565093a))  
fix(core/pipeline): use cloudprovider from context when generating links [#9061](https://github.com/spinnaker/deck/pull/9061) ([6917f572](https://github.com/spinnaker/deck/commit/6917f5728f656ae39236328e0e152f1167a15cc7))  



## [0.0.562](https://www.github.com/spinnaker/deck/compare/36250ab84bf80c3e232a8b7b4fb111fb760f6d3d...2c384c9853ddd6859019d739034b0e02542d8667) (2021-03-30)


### Changes

chore(core): publish core@0.0.562 ([2c384c98](https://github.com/spinnaker/deck/commit/2c384c9853ddd6859019d739034b0e02542d8667))  
feat(amazon/serverGroup): Introduce MetricAlarmChart.tsx using Chart.js ([802cb2ee](https://github.com/spinnaker/deck/commit/802cb2eea59c858fc4cb216325b6ae78a0cd9aef))  
fix(core/pipeline): After saving a pipeline, use the value from the server as the new "original" [#9050](https://github.com/spinnaker/deck/pull/9050) ([1965bc01](https://github.com/spinnaker/deck/commit/1965bc015f8439814732fdeab25abd7788b1d1fb))  
feat(core/confirmationModal): Close dialog when  esc is pressed [#9041](https://github.com/spinnaker/deck/pull/9041) ([08cba4d8](https://github.com/spinnaker/deck/commit/08cba4d88051958c078367602ed8c547191c863c))  
fix(core/presentation): allow null observable in useObservable.hook [#9046](https://github.com/spinnaker/deck/pull/9046) ([834bdeb7](https://github.com/spinnaker/deck/commit/834bdeb79dbf80dc378fec4b8ec3327fbc15795d))  
feat(nav): new overridable section at the bottom of the left navigation bar [#9044](https://github.com/spinnaker/deck/pull/9044) ([09d5cf8a](https://github.com/spinnaker/deck/commit/09d5cf8af27d0b736f57142963a27082f6f07a5a))  
fix(md): hide depends-on icon from the artifacts list [#9037](https://github.com/spinnaker/deck/pull/9037) ([2129cde3](https://github.com/spinnaker/deck/commit/2129cde36fb25ad3f5f543dde544992fe2d7468f))  
feat(md): change pending verification icon color to blue [#9036](https://github.com/spinnaker/deck/pull/9036) ([e78f0c4a](https://github.com/spinnaker/deck/commit/e78f0c4a011422f69c3e21634901ac8f40592656))  
fix(core/presentation): make hidePopover prop optional in HoverablePopover Component type [#9043](https://github.com/spinnaker/deck/pull/9043) ([a7ec358f](https://github.com/spinnaker/deck/commit/a7ec358f165c5f7bb59eb88255601713ed4f7012))  



## [0.0.561](https://www.github.com/spinnaker/deck/compare/616ad335b1f37833521da85f96c7015d599f796d...36250ab84bf80c3e232a8b7b4fb111fb760f6d3d) (2021-03-25)


### Changes

chore(core): publish core@0.0.561 ([36250ab8](https://github.com/spinnaker/deck/commit/36250ab84bf80c3e232a8b7b4fb111fb760f6d3d))  
feat(pipelines): Enable filtering on dynamic categories [#8849](https://github.com/spinnaker/deck/pull/8849) ([9fe0ed5e](https://github.com/spinnaker/deck/commit/9fe0ed5ece095084812c9f88c98b83445cce2ff2))  
feat(core/Executions): Make component overridable [#9002](https://github.com/spinnaker/deck/pull/9002) ([7e5c836e](https://github.com/spinnaker/deck/commit/7e5c836e0cc95de27b1376ef8ab42508902cf43d))  



## [0.0.560](https://www.github.com/spinnaker/deck/compare/e2efed2e6a69a76002851dcae9d88b49ca08f721...616ad335b1f37833521da85f96c7015d599f796d) (2021-03-24)


### Changes

chore(core): publish core@0.0.560 ([616ad335](https://github.com/spinnaker/deck/commit/616ad335b1f37833521da85f96c7015d599f796d))  
fix(deck): Handles an undefined reference on `TaskMonitorWrapper`. [#9031](https://github.com/spinnaker/deck/pull/9031) ([9f3d454c](https://github.com/spinnaker/deck/commit/9f3d454c1ae6891f7aa554ebf2a485e7d4d2c248))  



## [0.0.559](https://www.github.com/spinnaker/deck/compare/ae562e303a1afd93455c6341d298bcef788c955a...e2efed2e6a69a76002851dcae9d88b49ca08f721) (2021-03-23)


### Changes

chore(core): publish core@0.0.559 ([e2efed2e](https://github.com/spinnaker/deck/commit/e2efed2e6a69a76002851dcae9d88b49ca08f721))  
fix(pr): added a warning ([2ebdb9b2](https://github.com/spinnaker/deck/commit/2ebdb9b2930f2991cb526dca2c5473f0743d34a3))  
fix(pr): only register MD plugin if necessary ([8bd72b53](https://github.com/spinnaker/deck/commit/8bd72b530333e3242deb03d21ad4eeba817772bf))  
refactor(pr): Updated render functions type and moved files around ([c3139a73](https://github.com/spinnaker/deck/commit/c3139a731866d9402c95604544d9f27e04c2d935))  
feat(pr): add a separate function for rendering the description ([301b0022](https://github.com/spinnaker/deck/commit/301b00224c29b0a4d1c0bb336146de46ea65ea42))  
fix(pr): missing docs ([6236351d](https://github.com/spinnaker/deck/commit/6236351df5b4dc696e160739ac31f27b72d40e37))  
fix(pr): renaming ([0a7f5285](https://github.com/spinnaker/deck/commit/0a7f5285aeda7e76a7ed3959384598373da970ee))  
fix(pr): simplified types ([931c8858](https://github.com/spinnaker/deck/commit/931c8858b347e967c207b2c0a67cd6e38fcb744d))  
fix(pr): missing optional ([84185ef9](https://github.com/spinnaker/deck/commit/84185ef98b2f85b3258526ac5f25ffe8f7390a9f))  
feat(md): new constraint plugin registry ([421a7f8a](https://github.com/spinnaker/deck/commit/421a7f8a90af7edb1dea10275e2c8654f470b8ea))  
refactor(md): move constraintRegistry to another folder ([0d83fb3b](https://github.com/spinnaker/deck/commit/0d83fb3b30b1097ee9444025727ed77cf4adc30e))  
feat(core/pipeline): Validate that submitted pipeline is not stale (hasn't been changed by another user) [#9027](https://github.com/spinnaker/deck/pull/9027) ([c6fd509c](https://github.com/spinnaker/deck/commit/c6fd509cef8b04aee81426336d9ce5875ce3675e))  
fix(core/projects): Recent projects: only save the link to project dashboard [#9024](https://github.com/spinnaker/deck/pull/9024) ([00615870](https://github.com/spinnaker/deck/commit/00615870baed44fe88ace32b09981bcfcac789d3))  
feat(core/ExecutionGroup): make component overridable [#8999](https://github.com/spinnaker/deck/pull/8999) ([ba9835a3](https://github.com/spinnaker/deck/commit/ba9835a3c9c7783408e95056e76fb7e6944f66aa))  
feat(core/Execution): make component overridable [#9000](https://github.com/spinnaker/deck/pull/9000) ([7e0bfd22](https://github.com/spinnaker/deck/commit/7e0bfd22e98e082638ecbad44c5a499ad0fee58f))  
feat(core/ApplicationDataSourceRegistry): Remove data source based on key [#9003](https://github.com/spinnaker/deck/pull/9003) ([b4d3da39](https://github.com/spinnaker/deck/commit/b4d3da391b9545f23f2ec3e1ebad02c236f8475c))  
feat(core/ProjectHeader): Make component overridable [#8997](https://github.com/spinnaker/deck/pull/8997) ([a1aae991](https://github.com/spinnaker/deck/commit/a1aae991999505b0e3768922fff0805d9f48f07c))  
fix(core/presentation): Add deps to useCallback [#9021](https://github.com/spinnaker/deck/pull/9021) ([22c76b12](https://github.com/spinnaker/deck/commit/22c76b1266b6354fbeef08c0bb7075a253d33b68))  
fix(pipeline): Collapse parameters in ancestry [#9017](https://github.com/spinnaker/deck/pull/9017) ([76754d28](https://github.com/spinnaker/deck/commit/76754d288008ce1ad23fcbb6a8c72fcb0e42fa8b))  
fix(pipeline): Move configure into execution [#9013](https://github.com/spinnaker/deck/pull/9013) ([8fb7b704](https://github.com/spinnaker/deck/commit/8fb7b704ea312e8ea53422d661efcb9571a2a8f2))  



## [0.0.558](https://www.github.com/spinnaker/deck/compare/117a6e5b492a9ae27f96b6162163bbea2fc5d4cf...ae562e303a1afd93455c6341d298bcef788c955a) (2021-03-19)


### Changes

chore(core): publish core@0.0.558 ([ae562e30](https://github.com/spinnaker/deck/commit/ae562e303a1afd93455c6341d298bcef788c955a))  
chore: eslint --fix [#9015](https://github.com/spinnaker/deck/pull/9015) ([9a5ea31a](https://github.com/spinnaker/deck/commit/9a5ea31abf82e014c4a603fbfa12a50a1f2dd117))  



## [0.0.557](https://www.github.com/spinnaker/deck/compare/1683630a024101273317f6ba01b57f66c2702e3f...117a6e5b492a9ae27f96b6162163bbea2fc5d4cf) (2021-03-18)


### Changes

chore(core): publish core@0.0.557 ([117a6e5b](https://github.com/spinnaker/deck/commit/117a6e5b492a9ae27f96b6162163bbea2fc5d4cf))  
fix(md): constraint status was using the global status var [#9010](https://github.com/spinnaker/deck/pull/9010) ([8186440f](https://github.com/spinnaker/deck/commit/8186440f92bb7af1e35d8ba2fa132a7b021b7fa4))  
fix(core): Trim cloud providers after splitting [#9009](https://github.com/spinnaker/deck/pull/9009) ([680eddc7](https://github.com/spinnaker/deck/commit/680eddc786064733750941fcf7deb7dbdbb05d53))  



## [0.0.556](https://www.github.com/spinnaker/deck/compare/86a87d2022d3925214fc65bbe8ef7213b81051ed...1683630a024101273317f6ba01b57f66c2702e3f) (2021-03-18)


### Changes

chore(core): publish core@0.0.556 ([1683630a](https://github.com/spinnaker/deck/commit/1683630a024101273317f6ba01b57f66c2702e3f))  
fix(core/presentation): Change standard field layout flex to  justify-content: space-between; to align formfield actions on the right hand side [#8995](https://github.com/spinnaker/deck/pull/8995) ([867a0aeb](https://github.com/spinnaker/deck/commit/867a0aeb06ef3c9df6bc20ccaf76a9b5f8315f04))  
fix(core): Update IInstance with some commonly used attributes [#9005](https://github.com/spinnaker/deck/pull/9005) ([3a287e23](https://github.com/spinnaker/deck/commit/3a287e23d32ca48c0b6f14f19d69b9706254329e))  
fix(core): Export ConsoleOutputLink [#8998](https://github.com/spinnaker/deck/pull/8998) ([da315f59](https://github.com/spinnaker/deck/commit/da315f59ecec9bddf9e31e5af11bda349fd202c0))  
fix(core): Running tasks popover surpassing screen dimensions [#8986](https://github.com/spinnaker/deck/pull/8986) ([2451d83f](https://github.com/spinnaker/deck/commit/2451d83f6f0532cb5be76183e8b66a9c5c6796ce))  
feat(pipeline): Render lineage in permalink view ([4862fd3e](https://github.com/spinnaker/deck/commit/4862fd3e51da6111d9f4b6224f2cd63969b66f31))  
feat(pipeline): Refactored SingleExecutionDetails ([c506d975](https://github.com/spinnaker/deck/commit/c506d97579e5ea2df35c01437940b0dcdca9f66b))  
feat(pipeline): Lineage - removing unused params ([245bb921](https://github.com/spinnaker/deck/commit/245bb921dd66036f2f1d46099644afd1e05b748e))  
feat(md): Added allowed time constraint + code cleanup ([216b0a15](https://github.com/spinnaker/deck/commit/216b0a155b74ab4b7ebeee3b12b436b7619cdfb1))  
feat(core/presentation): Add a react hook useFormInputValueMapper allowing FormInput values to be mapped/transformed/converted if the caller model requires [#8974](https://github.com/spinnaker/deck/pull/8974) ([abbce18f](https://github.com/spinnaker/deck/commit/abbce18f6221146d13889df3e3edc5f0c96b6c4e))  
feat(core/presentation): add `mode="CREATABLE"` to the ReactSelectInput [#8993](https://github.com/spinnaker/deck/pull/8993) ([b3f3bfa7](https://github.com/spinnaker/deck/commit/b3f3bfa773856e3f46cac0baef5fe2ce05f04d52))  
feat(core/history): Always link to /applications/foo from the recent applications list Do not try to be fancy and link to the most recent deepest route, such as an instance or load balancer ([c123cb62](https://github.com/spinnaker/deck/commit/c123cb62c12e62c77e37547df565714093fa8cf4))  
refactor(core/search): migrate RecentlyViewedItems.tsx to functional component ([f8e5600c](https://github.com/spinnaker/deck/commit/f8e5600cbaf17f0b79a54898f3d55221a3de785d))  
fix(core/presentation): Avoid remounting CheckListInput on every render [#8971](https://github.com/spinnaker/deck/pull/8971) ([e03cf672](https://github.com/spinnaker/deck/commit/e03cf672fec668d9b3b36a74cb23f64bc7f73d46))  
feat(core/InsightMenu): Make component overridable [#8989](https://github.com/spinnaker/deck/pull/8989) ([4778bc08](https://github.com/spinnaker/deck/commit/4778bc08e1e8d2d511e7a23e2efb2ea20bfdba1f))  
fix(core/presentation): CheckboxInput.tsx: render a &nbsp; if no text is present to align on baseline in flexbox containers [#8972](https://github.com/spinnaker/deck/pull/8972) ([a924df2a](https://github.com/spinnaker/deck/commit/a924df2a1d6c742981ab72462b519274a1d0901b))  
feat(core/application): Add a PlatformHeatlhOverrideInput wrapper component that adheres to the FormInput api [#8973](https://github.com/spinnaker/deck/pull/8973) ([87ef81d9](https://github.com/spinnaker/deck/commit/87ef81d995ffdf760a6b92ff9a35b1063a8db393))  



## [0.0.555](https://www.github.com/spinnaker/deck/compare/e4e0029935f082c7389b96d53585a020f81870f6...86a87d2022d3925214fc65bbe8ef7213b81051ed) (2021-03-09)


### Changes

chore(core): publish core@0.0.555 ([86a87d20](https://github.com/spinnaker/deck/commit/86a87d2022d3925214fc65bbe8ef7213b81051ed))  
feat(core): Show slack channel on app search if feature enabled [#8984](https://github.com/spinnaker/deck/pull/8984) ([ddfc7566](https://github.com/spinnaker/deck/commit/ddfc7566af125003889c4f43456b2be6495a5eec))  
fix(pr): remove config call ([b1757737](https://github.com/spinnaker/deck/commit/b175773778f1777aba1f05bdaed5b054e3de8fcf))  
feat(md): store resource kinds without version number, and fallback to it if version is missing ([ebe89373](https://github.com/spinnaker/deck/commit/ebe8937382423bafd5720434f57c57730d0f42fd))  
fix(md): missing elb version ([55444b84](https://github.com/spinnaker/deck/commit/55444b84cadc1a2207d232bd05c62381d3290e86))  



## [0.0.554](https://www.github.com/spinnaker/deck/compare/cb4e7c68214edecf956b24fad45852c9e20649ca...e4e0029935f082c7389b96d53585a020f81870f6) (2021-03-05)


### Changes

chore(core): publish core@0.0.554 ([e4e00299](https://github.com/spinnaker/deck/commit/e4e0029935f082c7389b96d53585a020f81870f6))  
feat(core/application): Export the ApplicationContext values [#8977](https://github.com/spinnaker/deck/pull/8977) ([c3af1842](https://github.com/spinnaker/deck/commit/c3af1842fbef73d3075a281520d2b832fe69b2ef))  
feat(core): Allow function components to be overridden [#8970](https://github.com/spinnaker/deck/pull/8970) ([f1efe824](https://github.com/spinnaker/deck/commit/f1efe8246d4ca1435f5a1e21682bf8975a64cab7))  



## [0.0.553](https://www.github.com/spinnaker/deck/compare/686ee44d849cbe63c5f7044972fae83b9d93599e...cb4e7c68214edecf956b24fad45852c9e20649ca) (2021-03-04)


### Changes

chore(core): publish core@0.0.553 ([cb4e7c68](https://github.com/spinnaker/deck/commit/cb4e7c68214edecf956b24fad45852c9e20649ca))  
fix(md): remove beta tag [#8968](https://github.com/spinnaker/deck/pull/8968) ([3d0ddbd9](https://github.com/spinnaker/deck/commit/3d0ddbd9b97be84d87b8e1439e38383ccb116f93))  
fix(pipelines): Add deps to breadcrumbs useMemo [#8967](https://github.com/spinnaker/deck/pull/8967) ([765499fd](https://github.com/spinnaker/deck/commit/765499fdadec89131359bc089e4a9c090368eb14))  
fix(pr): added explicit log level [#8966](https://github.com/spinnaker/deck/pull/8966) ([4c02d1c5](https://github.com/spinnaker/deck/commit/4c02d1c5bdc051e20c6a8e5a0f785b82c637822a))  
fix(core/forms): MapEditor: make trash icon position: relative to avoid scrollbars [#8964](https://github.com/spinnaker/deck/pull/8964) ([273966d8](https://github.com/spinnaker/deck/commit/273966d86d1cefe35378c842a3a334e03f5fd606))  
feat(md): resource history title - show resource display name [#8963](https://github.com/spinnaker/deck/pull/8963) ([3f77dc72](https://github.com/spinnaker/deck/commit/3f77dc7293455f8c073bb579381d73a91cd23255))  
fix(pr): remove console.log ([b2a6db1c](https://github.com/spinnaker/deck/commit/b2a6db1c4944811319f2c21cb0d2d5d003aa7912))  
feat(md): redesigned history page ([9a5ee2f5](https://github.com/spinnaker/deck/commit/9a5ee2f5023500db3c9253dce052be181f98efab))  
fix(core): updated type ([1faa3163](https://github.com/spinnaker/deck/commit/1faa3163da6f611beb9ed9ae39241e85521af256))  
feat(md): remove events white list and get info from the backend ([378890ba](https://github.com/spinnaker/deck/commit/378890ba5ba22838b2e8f7207e291371d2f5aa07))  
refactor(md): refactor managed resource history + fix memoization ([e5213a8c](https://github.com/spinnaker/deck/commit/e5213a8c79301f1c6891625dd9648e701c2cc204))  
fix(amazon/serverGroup): Move spelLoadBalancers and spelTargetGroups to viewState [#8957](https://github.com/spinnaker/deck/pull/8957) ([eb4394f8](https://github.com/spinnaker/deck/commit/eb4394f8f97bb7958a23c75f3f40cd00111317b6))  



## [0.0.552](https://www.github.com/spinnaker/deck/compare/71ca49ba2df9432aaaba9d9d5caea8d58bb458ef...686ee44d849cbe63c5f7044972fae83b9d93599e) (2021-03-02)


### Changes

chore(core): publish core@0.0.552 ([686ee44d](https://github.com/spinnaker/deck/commit/686ee44d849cbe63c5f7044972fae83b9d93599e))  
feat(core): global search box - search on enter [#8948](https://github.com/spinnaker/deck/pull/8948) ([eb38aba4](https://github.com/spinnaker/deck/commit/eb38aba4293c2f67ee7c44a37e28725dacdad03a))  
Merge branch 'master' into save-app-notifications ([14ec907c](https://github.com/spinnaker/deck/commit/14ec907c276c04f4d804f4a1e16a04f008e6ae77))  
chore(md): enable strict null check on the editor level (it won't be enforced during compilation) [#8949](https://github.com/spinnaker/deck/pull/8949) ([fa96f320](https://github.com/spinnaker/deck/commit/fa96f320ba616511a40ddd95de831740663655bd))  
fix(core/presentation): Apply `flex: 1` to responsive table cell so the cell takes up the available width [#8942](https://github.com/spinnaker/deck/pull/8942) ([f53787e8](https://github.com/spinnaker/deck/commit/f53787e86e674953558a88425ab69c9a3c70140b))  
feat(core/notifications): Save app notifications when modal is submitted ([6b1eb677](https://github.com/spinnaker/deck/commit/6b1eb67758d5c13befb08c899193fcdd338562f6))  
fix(core): avoid fetching security groups twice [#8938](https://github.com/spinnaker/deck/pull/8938) ([3adbdf3a](https://github.com/spinnaker/deck/commit/3adbdf3a7021c641ebb7d1e48cf66c61b8baabe3))  
Merge branch 'master' into titus-ipv6-enable ([a784e1ef](https://github.com/spinnaker/deck/commit/a784e1efe464f950b994f981a195eaf2ecf4c9d6))  
feat(titus/serverGroup): Enable ipv6 in test environment ([33976190](https://github.com/spinnaker/deck/commit/3397619092306364289eebaa60a912dcfb654aab))  



## [0.0.551](https://www.github.com/spinnaker/deck/compare/03302d5a2ea8049720560426335ed3821abec748...71ca49ba2df9432aaaba9d9d5caea8d58bb458ef) (2021-02-22)


### Changes

chore(core): publish core@0.0.551 ([71ca49ba](https://github.com/spinnaker/deck/commit/71ca49ba2df9432aaaba9d9d5caea8d58bb458ef))  
fix(UI): Fixes hamburger menu icon alignment [#8927](https://github.com/spinnaker/deck/pull/8927) ([f2c2b64b](https://github.com/spinnaker/deck/commit/f2c2b64baeee8bd71cbfe8635990ff7a116c95d9))  
fix(core): Update import order ([7af62d49](https://github.com/spinnaker/deck/commit/7af62d491d65ba734c291e85f3da3d379ffb7d66))  
Revert "Merge pull request #8909 from caseyhebebrand/save-notifications" ([f0fb49e0](https://github.com/spinnaker/deck/commit/f0fb49e061090c67e83b0b3d397088a7827ee4e5))  
chore(lint): Update import statement ordering ([5a9768bc](https://github.com/spinnaker/deck/commit/5a9768bc6db2f527a73d6b1f5fb3120c101e094b))  
chore(eslint): Bumping @spinnaker/eslint-plugin in deck ([281b1f78](https://github.com/spinnaker/deck/commit/281b1f78bca3e9fbb8e21bd1d81c4e423922528f))  
chore(lint): Format import statements in core ([b2556cde](https://github.com/spinnaker/deck/commit/b2556cde9f7d101def8fd6711ea152b96ec02655))  
chore(lint): Enable import-sort rule for core ([2ae0d866](https://github.com/spinnaker/deck/commit/2ae0d8662997e2484c8c6705b9048af9e7b75a28))  
Merge branch 'master' into save-notifications ([3bbfaab2](https://github.com/spinnaker/deck/commit/3bbfaab2391dafd3f47577a6832e44492b53b489))  
chore(lint): Disable import-sort rule for `core` ([a9a36592](https://github.com/spinnaker/deck/commit/a9a36592c00eeeef07e17073416a7025c22e8149))  
Refactor to useData ([e1c7ae23](https://github.com/spinnaker/deck/commit/e1c7ae2322c4fbcf75d7bf14f0f5c7c0d5279b03))  
fix(core): Update naming and export react2angular converter ([4ea3e887](https://github.com/spinnaker/deck/commit/4ea3e887ff370eac1b295bbc90e3032183685dfc))  
refactor(core): Remove obsolete files ([63b465ab](https://github.com/spinnaker/deck/commit/63b465ab28f9f9314274f75154cfec4e7fa85265))  
refactor(core): Transition to new react component ([c4b5d0a4](https://github.com/spinnaker/deck/commit/c4b5d0a42b590c9bf2f8b34ce05018e4af87d205))  
refactor(core): ConsoleOutput link and modal ([2093d6ca](https://github.com/spinnaker/deck/commit/2093d6ca22af8411a60ee84765f525b2e2096d7e))  
fix(core): add dotted underline visual indicator to hover-able popovers [#8749](https://github.com/spinnaker/deck/pull/8749) ([e0db63f2](https://github.com/spinnaker/deck/commit/e0db63f20f52a04ebc59537e549810e1f9a99f21))  
chore(prettier): Format code using prettier ([b6364c82](https://github.com/spinnaker/deck/commit/b6364c820c106ee54e5bd5770e44c81fa3af06e9))  
fix(core): Only forward to permalink on initial load [#8928](https://github.com/spinnaker/deck/pull/8928) ([6b233904](https://github.com/spinnaker/deck/commit/6b233904869c687177e1e695583f99be1d2a0ee1))  
feat(core): Refactor and close modal after saving ([8872461e](https://github.com/spinnaker/deck/commit/8872461e6a64de07176b62e48464176c7817fe63))  
feat(core): Automatically save notifications when closing modal ([fdd9c5a4](https://github.com/spinnaker/deck/commit/fdd9c5a4a42d5a8d116c4ad4547f6e123fcd3c4c))  



## [0.0.550](https://www.github.com/spinnaker/deck/compare/88f69027f7c6ff75b40e8aed12c714cd559fd097...03302d5a2ea8049720560426335ed3821abec748) (2021-02-17)


### Changes

chore(core): publish core@0.0.550 ([03302d5a](https://github.com/spinnaker/deck/commit/03302d5a2ea8049720560426335ed3821abec748))  
Merge branch 'master' into bastion-host-type ([8a432acf](https://github.com/spinnaker/deck/commit/8a432acf4582879d918c4e9e615f20f0338c4288))  
refactor(md): split artifactDetail to smaller files [#8922](https://github.com/spinnaker/deck/pull/8922) ([acac332e](https://github.com/spinnaker/deck/commit/acac332e9578cc227d47f60bf206cdea2f65275d))  
fix(core/account): Update AccountDetails interface ([d2056eb6](https://github.com/spinnaker/deck/commit/d2056eb60b6bfea26f22d5a7d000d3abbc324f79))  
Merge branch 'master' into titus-type-updates ([474d03da](https://github.com/spinnaker/deck/commit/474d03da6f24b14dff3620ea31e2d8c49ef0b5a9))  
feat(core/md): verification cards ([2e6a75ac](https://github.com/spinnaker/deck/commit/2e6a75ac99ee642808ae433fa0f6d01415c9d972))  
refactor(titus): Create component wrapper with generic functionality ([06585c58](https://github.com/spinnaker/deck/commit/06585c586e33f8c88cd191f03eefde54d7180858))  
fix(core/serverGroup): Ensure failsafe SpEL expressions are provided ([c0089131](https://github.com/spinnaker/deck/commit/c0089131e43a34dcbf5babb7a471d9cf3c36e76c))  



## [0.0.549](https://www.github.com/spinnaker/deck/compare/8156f126a1099075709cc02ac46a844386361df9...88f69027f7c6ff75b40e8aed12c714cd559fd097) (2021-02-11)


### Changes

chore(core): publish core@0.0.549 ([88f69027](https://github.com/spinnaker/deck/commit/88f69027f7c6ff75b40e8aed12c714cd559fd097))  
fix(core/ci): Fix endpoint url typo in getJobConfig() [#8904](https://github.com/spinnaker/deck/pull/8904) ([205e164e](https://github.com/spinnaker/deck/commit/205e164eeee964b6ede0f56d840215078760da10))  
fix(CiBuild): Allow for empty SCM information when pulling builds [#8890](https://github.com/spinnaker/deck/pull/8890) ([e63b2abe](https://github.com/spinnaker/deck/commit/e63b2abe48e65d343a369a30b66995be6c23ce03))  



## [0.0.548](https://www.github.com/spinnaker/deck/compare/9e333553b52ed406afbf922bb864bd454fb9725e...8156f126a1099075709cc02ac46a844386361df9) (2021-02-10)


### Changes

chore(core): publish core@0.0.548 ([8156f126](https://github.com/spinnaker/deck/commit/8156f126a1099075709cc02ac46a844386361df9))  
refactor(core/ci): Migrate to builds api v3 [#8892](https://github.com/spinnaker/deck/pull/8892) ([f8d3d432](https://github.com/spinnaker/deck/commit/f8d3d432203782f1946db8bc4c1843eb9eea14ef))  
refactor(presentation): Refer Icon, Illustration members from @spinnaker/presentation ([8f136eb8](https://github.com/spinnaker/deck/commit/8f136eb88790d66ae42cf597f55b88cd2cd2d0e0))  
refactor(presentation): Update references to illustration and icon modules ([78a655c0](https://github.com/spinnaker/deck/commit/78a655c03aa1c7d09e64440ce6a586fd906577ec))  
refactor(presentation): Move illustrations and icons modules to presentation ([c086f91a](https://github.com/spinnaker/deck/commit/c086f91a10896627d595458f4f4bdb07857d632f))  
refactor(md): organized files in folders [#8897](https://github.com/spinnaker/deck/pull/8897) ([26e60677](https://github.com/spinnaker/deck/commit/26e6067795b65a8ffdc93c8add920c69f50be283))  



## [0.0.547](https://www.github.com/spinnaker/deck/compare/1c6139a22e31cd7345ef4ed9122b7960bcc5d9d6...9e333553b52ed406afbf922bb864bd454fb9725e) (2021-02-09)


### Changes

chore(core): publish core@0.0.547 ([9e333553](https://github.com/spinnaker/deck/commit/9e333553b52ed406afbf922bb864bd454fb9725e))  
fix(nav): fix tooltip vertical alignment ([d1ef25ce](https://github.com/spinnaker/deck/commit/d1ef25ce280d003938eb451c4311c5a30e77310c))  



## [0.0.546](https://www.github.com/spinnaker/deck/compare/3b763a0e6934e268ba5d27d84356cdf154457a11...1c6139a22e31cd7345ef4ed9122b7960bcc5d9d6) (2021-02-08)


### Changes

chore(core): publish core@0.0.546 ([1c6139a2](https://github.com/spinnaker/deck/commit/1c6139a22e31cd7345ef4ed9122b7960bcc5d9d6))  
refactor(aws/titus): Convert instance DNS to react [#8884](https://github.com/spinnaker/deck/pull/8884) ([745e1bf5](https://github.com/spinnaker/deck/commit/745e1bf5592b768e733f32b6fe6d6852b955c227))  
feat(core): Toggle to show or hide many filter tags [#8869](https://github.com/spinnaker/deck/pull/8869) ([b4584689](https://github.com/spinnaker/deck/commit/b4584689dd15f66d03fed420fb62a175724b1989))  
feat(amazon/loadBalancer): Enable dualstacking in ALBs/NLBs with constraints [#8859](https://github.com/spinnaker/deck/pull/8859) ([4c661749](https://github.com/spinnaker/deck/commit/4c6617492ad0e70e913c1608241cb0c0870e4f4e))  



## [0.0.545](https://www.github.com/spinnaker/deck/compare/fa632a0a82d630b7486fd3a6756e87daa63ba69b...3b763a0e6934e268ba5d27d84356cdf154457a11) (2021-02-03)


### Changes

chore(core): publish core@0.0.545 ([3b763a0e](https://github.com/spinnaker/deck/commit/3b763a0e6934e268ba5d27d84356cdf154457a11))  
feat(md): Collasible metadata elements  [#8880](https://github.com/spinnaker/deck/pull/8880) ([274944fb](https://github.com/spinnaker/deck/commit/274944fb9bd0f04b69a6b634e332a0eea73ed75f))  
fix(core/cluster): Clicking an instance breaks rendering of cluster pods [#8866](https://github.com/spinnaker/deck/pull/8866) ([c7a58cd4](https://github.com/spinnaker/deck/commit/c7a58cd4657054b2b1333057333f6aed0151741f))  
feat(core): Add a new variable that is interpolated in instance links [#8868](https://github.com/spinnaker/deck/pull/8868) ([76cd8cdf](https://github.com/spinnaker/deck/commit/76cd8cdfd6d381fb0c4ad15181026e27b923cff1))  
fix(core): add vertical padding on page owner nav link [#8876](https://github.com/spinnaker/deck/pull/8876) ([b3ae753a](https://github.com/spinnaker/deck/commit/b3ae753aab84e0c471355d1ee5ac69f572b81808))  
feat(core): Expose raw subnet ID [#8877](https://github.com/spinnaker/deck/pull/8877) ([7d94b690](https://github.com/spinnaker/deck/commit/7d94b690fef08aee08ed724f4d99ee300e9670c1))  



## [0.0.544](https://www.github.com/spinnaker/deck/compare/da72de1fc6f9576cf899b9cdfbd30199b6438d47...fa632a0a82d630b7486fd3a6756e87daa63ba69b) (2021-01-28)


### Changes

chore(core): publish core@0.0.544 ([fa632a0a](https://github.com/spinnaker/deck/commit/fa632a0a82d630b7486fd3a6756e87daa63ba69b))  
feat(core): export StageSummary props in core module [#8873](https://github.com/spinnaker/deck/pull/8873) ([7972a2e5](https://github.com/spinnaker/deck/commit/7972a2e5e6d11bdc8249ad87adf325b683e4e679))  
fix(core): handle errors on confirmation modal submission ([17bdf716](https://github.com/spinnaker/deck/commit/17bdf716fb22bfe44979656073d7f1f823601aed))  
feat(core/serverGroups): Intelligently enable/disable server groups in multiselect [#8865](https://github.com/spinnaker/deck/pull/8865) ([509eb4db](https://github.com/spinnaker/deck/commit/509eb4db136d81460588ee89e2e4914613a259a1))  



## [0.0.543](https://www.github.com/spinnaker/deck/compare/35e0131b7e6669b47a05648260ad9f4e3fc477cc...da72de1fc6f9576cf899b9cdfbd30199b6438d47) (2021-01-27)


### Changes

chore(core): publish core@0.0.543 ([da72de1f](https://github.com/spinnaker/deck/commit/da72de1fc6f9576cf899b9cdfbd30199b6438d47))  
feat(core): allow custom stage summary as React components [#8870](https://github.com/spinnaker/deck/pull/8870) ([ab2873a2](https://github.com/spinnaker/deck/commit/ab2873a2c6cf6787d1e8c4945c895d5c88ebaddc))  



## [0.0.542](https://www.github.com/spinnaker/deck/compare/61611fcf76147f3c58bc8eb8d372df5bfc91d4f0...35e0131b7e6669b47a05648260ad9f4e3fc477cc) (2021-01-21)


### Changes

chore(core): publish core@0.0.542 ([35e0131b](https://github.com/spinnaker/deck/commit/35e0131b7e6669b47a05648260ad9f4e3fc477cc))  
feat(core/loadBalancer): Filter by load balancer type [#8850](https://github.com/spinnaker/deck/pull/8850) ([b0139a61](https://github.com/spinnaker/deck/commit/b0139a61faef57e2274c3263a476cccc912328e8))  
feat(core/pipeline): Add the ability to disable base os options [#8851](https://github.com/spinnaker/deck/pull/8851) ([5e45b852](https://github.com/spinnaker/deck/commit/5e45b8525ad9f248572acd5550dbe1a57b7e69bb))  
fix(core/pipelines): break and wrap long comments text [#8748](https://github.com/spinnaker/deck/pull/8748) ([dde85130](https://github.com/spinnaker/deck/commit/dde8513022e45dd593e48b717c92631260509655))  
fix(API): encodeURIComponent for each path() element [#8646](https://github.com/spinnaker/deck/pull/8646) ([e78115ef](https://github.com/spinnaker/deck/commit/e78115ef849fd10f5d2086095f3f9d9e79c5ff66))  



## [0.0.541](https://www.github.com/spinnaker/deck/compare/d4547fb6cf8c2c9ffc02d131dd139b255a194994...61611fcf76147f3c58bc8eb8d372df5bfc91d4f0) (2021-01-19)


### Changes

chore(core): publish core@0.0.541 ([61611fcf](https://github.com/spinnaker/deck/commit/61611fcf76147f3c58bc8eb8d372df5bfc91d4f0))  
fix(pipeline/overlay): fix overlay placement with long desc [#8848](https://github.com/spinnaker/deck/pull/8848) ([5b7d9557](https://github.com/spinnaker/deck/commit/5b7d95576976076cdb20561591f1ef77503e03f4))  
fix(core/projects): Align 404 error message to center if project not found [#8820](https://github.com/spinnaker/deck/pull/8820) ([45ecf855](https://github.com/spinnaker/deck/commit/45ecf855167d04baf872438d980d4e4b60303e4e))  
fix(core/search): Exclude duplicate results in search views [#8821](https://github.com/spinnaker/deck/pull/8821) ([3ca7365b](https://github.com/spinnaker/deck/commit/3ca7365b8f08becf4ded9e8f02a717a02f77b59f))  
fix(core): remove save pipeline error message if success [#8822](https://github.com/spinnaker/deck/pull/8822) ([4b8b469c](https://github.com/spinnaker/deck/commit/4b8b469cb85b3c11d34e2c871875a27162a52b10))  
Deangularize instance writer [#8834](https://github.com/spinnaker/deck/pull/8834) ([f16b0775](https://github.com/spinnaker/deck/commit/f16b0775917242a39ae70e86c5541020c898b872))  
fix(core): Fix incorrect removal when merging serverGroups response [#8853](https://github.com/spinnaker/deck/pull/8853) ([7401fcee](https://github.com/spinnaker/deck/commit/7401fceee3e747e13bbaa0b954f5da664ec09d64))  
fix(core/executions): Protect migrationStatus sorting if config is null [#8847](https://github.com/spinnaker/deck/pull/8847) ([6e5e4796](https://github.com/spinnaker/deck/commit/6e5e4796084cde46ca861d781c42f8107e76c42f))  



## [0.0.540](https://www.github.com/spinnaker/deck/compare/2d0c55bf21f747b3ab0ea2040759f3106db6903f...d4547fb6cf8c2c9ffc02d131dd139b255a194994) (2021-01-13)


### Changes

chore(core): publish core@0.0.540 ([d4547fb6](https://github.com/spinnaker/deck/commit/d4547fb6cf8c2c9ffc02d131dd139b255a194994))  
refactor(core): Extract server group name previewer into its own component [#8842](https://github.com/spinnaker/deck/pull/8842) ([a35e8bf2](https://github.com/spinnaker/deck/commit/a35e8bf29007e928374e79eb95eaf91d0f8d5f25))  
fix(pipeline): fix error message handling [#8843](https://github.com/spinnaker/deck/pull/8843) ([625ba6fc](https://github.com/spinnaker/deck/commit/625ba6fc5613d4d28f4da701a99b6f690cd72e08))  
fix(core/pipeline): Remove note that pipelines are only paused for 72 hours ([ccf8c002](https://github.com/spinnaker/deck/commit/ccf8c0021b712cb76d9936b3d751044b1744c35b))  



## [0.0.539](https://www.github.com/spinnaker/deck/compare/576ffb6120f1c278b7740ab8187047117bcbed6f...2d0c55bf21f747b3ab0ea2040759f3106db6903f) (2021-01-13)


### Changes

chore(core): publish core@0.0.539 ([2d0c55bf](https://github.com/spinnaker/deck/commit/2d0c55bf21f747b3ab0ea2040759f3106db6903f))  
refactor(core/deployment): Update redblack fields without force updating [#8840](https://github.com/spinnaker/deck/pull/8840) ([95eacfb6](https://github.com/spinnaker/deck/commit/95eacfb6f5cea460677aa0907b18555dedae4135))  
fix(gitlab): fix help text for gitlab artifacts ([a027d62e](https://github.com/spinnaker/deck/commit/a027d62e40ab8366cb42f2512aef9692024d9b3a))  
feat(md): waiting status [#8836](https://github.com/spinnaker/deck/pull/8836) ([88587466](https://github.com/spinnaker/deck/commit/885874660148bd526e0a3172390f812a81ed31c6))  
feat(kubernetes): Raw resources UI MVP [#8800](https://github.com/spinnaker/deck/pull/8800) ([c7eb9f4e](https://github.com/spinnaker/deck/commit/c7eb9f4eca327360aa419d910d4d1aade094603a))  
fix(core/executions): Update migrated status to match API [#8831](https://github.com/spinnaker/deck/pull/8831) ([ef87a9e1](https://github.com/spinnaker/deck/commit/ef87a9e1b5ffd044452c6bab1bfaebbca1b83b29))  
fix(core/deploymentStrategy): do not show highlander preview in deploy stage config (only show in clone dialog) ([3a51af21](https://github.com/spinnaker/deck/commit/3a51af21b7954949b3f462ed633ce2280e3e517b))  
feat(core/deploymentStrategy): Add a preview for Highlander deploys ([8be06a03](https://github.com/spinnaker/deck/commit/8be06a036fec152eeddb075bf9968be29944c7ac))  
fix(serverGroup): Increase the timeout for api request [#8812](https://github.com/spinnaker/deck/pull/8812) ([c32c91ac](https://github.com/spinnaker/deck/commit/c32c91accf42b7fe67220ceb545a3d5037adfdc4))  
feat(core/executions): Render newly migrated execution groups  [#8807](https://github.com/spinnaker/deck/pull/8807) ([29a85a0d](https://github.com/spinnaker/deck/commit/29a85a0d610fe01846855044a2bc77b323e1fc5f))  
fix(core/projects): Fix duplicate Projects appearing in recent history (on search screen) [#8806](https://github.com/spinnaker/deck/pull/8806) ([8f291c0d](https://github.com/spinnaker/deck/commit/8f291c0d2bc41c6e88653588dbdbde62d568af25))  



## [0.0.538](https://www.github.com/spinnaker/deck/compare/c82acb2792278e0612ecf21eaff63f995b974c5c...576ffb6120f1c278b7740ab8187047117bcbed6f) (2020-12-16)


### Changes

chore(core): publish core@0.0.538 ([576ffb61](https://github.com/spinnaker/deck/commit/576ffb6120f1c278b7740ab8187047117bcbed6f))  
feat(core/api): Add 'deleteData' argument to REST().delete(deleteData); [#8804](https://github.com/spinnaker/deck/pull/8804) ([4e6f9e3e](https://github.com/spinnaker/deck/commit/4e6f9e3ef800091dd4b8702699f33628a87aa375))  
chore: Migrate ApplicationReader.spec.ts and serverGroupWriter.service.spec to MockHttpClient ([afec9f8a](https://github.com/spinnaker/deck/commit/afec9f8a49c500e8a200bc029d8405186c62f577))  
chore(rest): Migrate missed API usage to REST ([ecb1dbc3](https://github.com/spinnaker/deck/commit/ecb1dbc3b385295c436216312cd47970eef9dca0))  
refactor(REST): Find all empty args REST() calls and pass in the endpoint variable, i.e., REST(endpoint) ([d66c6241](https://github.com/spinnaker/deck/commit/d66c62419602d427cb6ab7ed06e06eae68c65b46))  
fix(core/search): Switch back to deprecated API because search uses the ICache interface which REST() does not support ([1cb2c30f](https://github.com/spinnaker/deck/commit/1cb2c30f5aa6067cd5f5fde83ff167097a029ee3))  
refactor(REST): Prefer REST('/foo/bar') over REST().path('foo', 'bar') ([1d4320a0](https://github.com/spinnaker/deck/commit/1d4320a08f73093483cbb93784e9115c236b1f8a))  
refactor(api-deprecation): API is deprecated, switch to REST() ([97bfbf67](https://github.com/spinnaker/deck/commit/97bfbf67b5d359cc540918b62c99088ad82dfb1b))  
refactor(api-deprecation): Prefer API.path('foo', 'bar') over API.path('foo').path('bar') ([39b08e72](https://github.com/spinnaker/deck/commit/39b08e72b4baef1063a3ab9b65584e6e4e73d3e2))  
refactor(api-deprecation): Migrate from API.get(queryparams) to .query(queryparams).get() ([46db35b0](https://github.com/spinnaker/deck/commit/46db35b063b8c9b457f3f3675cd81a11a867c070))  
refactor(api-deprecation): Migrate from API.data({}).post() to .post({}) or.put({}) ([6a1c0814](https://github.com/spinnaker/deck/commit/6a1c0814cd5e0679f0e82fda4590bcdefbdf440b))  
refactor(api-deprecation): Migrate from API.one/all/withParams/getList() to path/query/get() ([587db3ab](https://github.com/spinnaker/deck/commit/587db3ab20040fb5c72fe48feb36eccd7d1f297a))  
test(mock-http-client): Remove unnecessary API.baseUrl prefix in expectGET/etc calls ([807de9ad](https://github.com/spinnaker/deck/commit/807de9ada97154e3e8b699dece0f8d5ec4493942))  
test(mock-http-client): Remove no longer needed references to $httpBackend in unit tests after migration to MockHttpClient ([924e80be](https://github.com/spinnaker/deck/commit/924e80be96fcd9e72cae7381cb50b577fa2ca709))  
test(mock-http-client): Manually fix tests which didn't pass after auto-migrating using the eslint rule migrate-to-mock-http-client --fix ([e1238187](https://github.com/spinnaker/deck/commit/e1238187ffb851e2b2a6402004bc6a42e780ec9a))  
test(mock-http-client): Run eslint rule migrate-to-mock-http-client --fix ([ef5d8ea0](https://github.com/spinnaker/deck/commit/ef5d8ea0661d360661831b57d3ee8457aae0ecfd))  
feat(core/api): Introduce a MockHttpClient.ts as a incremental replacement for AngularJS $httpBackend for unit tests. [#8775](https://github.com/spinnaker/deck/pull/8775) ([9fe939c2](https://github.com/spinnaker/deck/commit/9fe939c2fdf0a8ffe6efade125808253a1735a29))  
fix(pipeline): Fixing lost history record for React stages [#8801](https://github.com/spinnaker/deck/pull/8801) ([54d9f6cc](https://github.com/spinnaker/deck/commit/54d9f6cc07ac0e41a774a337b1e7c9ddf20f0e39))  
feat(core/ci): Integrating CI builds with Deck [#8798](https://github.com/spinnaker/deck/pull/8798) ([0ca92594](https://github.com/spinnaker/deck/commit/0ca9259451433536c83ccf4bed78ddeaf04b750e))  
Avoid raw "$http" usage [#8790](https://github.com/spinnaker/deck/pull/8790) ([969f4fe0](https://github.com/spinnaker/deck/commit/969f4fe0e9ab75eef2ceb0a2287643425293e209))  
fix: use 'import type {} from package' to fix the webpack warnings for missing interface imports [#8794](https://github.com/spinnaker/deck/pull/8794) ([6308695a](https://github.com/spinnaker/deck/commit/6308695ab430ea13c71cadf328f8d627cdffa070))  
fix(bake): make helm chart file path visible when the chart comes from a git/repo artifact [#8789](https://github.com/spinnaker/deck/pull/8789) ([b971b5a0](https://github.com/spinnaker/deck/commit/b971b5a00327ef6345995b4057e2594e2ed71233))  



## [0.0.537](https://www.github.com/spinnaker/deck/compare/2c3d0afdd963f9c3392850f310499e296500773a...c82acb2792278e0612ecf21eaff63f995b974c5c) (2020-12-11)


### Changes

chore(core): publish core@0.0.537 ([c82acb27](https://github.com/spinnaker/deck/commit/c82acb2792278e0612ecf21eaff63f995b974c5c))  
fix(core/managed): re-enable build events [#8786](https://github.com/spinnaker/deck/pull/8786) ([06aca3a9](https://github.com/spinnaker/deck/commit/06aca3a951b32c0ae6bad4aac3a760824b46202f))  
chore(core): Allow cloud provider logos in registry [#8782](https://github.com/spinnaker/deck/pull/8782) ([728939d7](https://github.com/spinnaker/deck/commit/728939d7957997b4756467123415024a18d4395b))  
feat(manual judgment/deck): Added ability to add roles to manual judgment stage [#8779](https://github.com/spinnaker/deck/pull/8779) ([38568a3f](https://github.com/spinnaker/deck/commit/38568a3f9c94af42e71fda0dd6229f458a71b443))  
fix(core/pipeline): Fix linking to pipeline execution [#8778](https://github.com/spinnaker/deck/pull/8778) ([aa201bba](https://github.com/spinnaker/deck/commit/aa201bba182ff009676913ac40422d057eb9f647))  
feat(bake): add UI element for helmChartFilePath to bake manifest stage, when using a git/repo artifact for the helm chart [#8774](https://github.com/spinnaker/deck/pull/8774) ([a53e32cf](https://github.com/spinnaker/deck/commit/a53e32cfcd045bfbabf9b5a46c46ead352f585ee))  



## [0.0.536](https://www.github.com/spinnaker/deck/compare/50491de15c381e0be0ae82d305d1237a41cea08f...2c3d0afdd963f9c3392850f310499e296500773a) (2020-12-09)


### Changes

chore(core): publish core@0.0.536 ([2c3d0afd](https://github.com/spinnaker/deck/commit/2c3d0afdd963f9c3392850f310499e296500773a))  
fix(core/managed): temporarily hide build pre-deployment events [#8776](https://github.com/spinnaker/deck/pull/8776) ([8a869814](https://github.com/spinnaker/deck/commit/8a8698145e64a5644045c2e622f214189663b105))  
Revert "feat(bake): add UI element for helmChartFilePath to bake manifest stage, when using a git/repo artifact for the helm chart (#8751)" [#8773](https://github.com/spinnaker/deck/pull/8773) ([7a723c98](https://github.com/spinnaker/deck/commit/7a723c980fabb5265dbb66251c1ae4c62b2ea01b))  
fix(core): Redirecting aged out executions to permalink [#8770](https://github.com/spinnaker/deck/pull/8770) ([54097588](https://github.com/spinnaker/deck/commit/54097588e543bc62981f35bd19d811be93b0f64b))  
feat(bake): add UI element for helmChartFilePath to bake manifest stage, when using a git/repo artifact for the helm chart [#8751](https://github.com/spinnaker/deck/pull/8751) ([38b31387](https://github.com/spinnaker/deck/commit/38b31387459b548d8b44caeee24bbcd251eeedc5))  



## [0.0.535](https://www.github.com/spinnaker/deck/compare/7d198145b6870e2b31d32436e19b697d35a25d1d...50491de15c381e0be0ae82d305d1237a41cea08f) (2020-12-04)


### Changes

chore(core): publish core@0.0.535 ([50491de1](https://github.com/spinnaker/deck/commit/50491de15c381e0be0ae82d305d1237a41cea08f))  
feat(core/managed): add pre-deployment events for builds [#8768](https://github.com/spinnaker/deck/pull/8768) ([cb683081](https://github.com/spinnaker/deck/commit/cb683081e9e85b313b1ead5936caa2d5950eaa1a))  
refactor(REST): Rename HttpClientBackend interface to HttpClientImplementation ([8038dd80](https://github.com/spinnaker/deck/commit/8038dd80d2374bc511524f80a9c02bce805c7b4a))  
chore(core/api): fix api-no-unused-chaining lint violations ([78dee2c1](https://github.com/spinnaker/deck/commit/78dee2c1cb852de73900141a4f649342c72ab44b))  
chore(core/api): Update plugin.registry.spec.ts and SpelService.spec.ts tests for new http backend ([d3327e5a](https://github.com/spinnaker/deck/commit/d3327e5ae8e046774e1a171cc395e34c6017df6c))  
feat(core/api): Create a new REST() client api to replace API.one() ([c4e775f5](https://github.com/spinnaker/deck/commit/c4e775f540ce031a59bfeab8f28dd87f5fa74ed9))  



## [0.0.534](https://www.github.com/spinnaker/deck/compare/fbb4e56d5483713b63b43d3a1642c640512f45ed...7d198145b6870e2b31d32436e19b697d35a25d1d) (2020-11-24)


### Changes

chore(core): publish core@0.0.534 ([7d198145](https://github.com/spinnaker/deck/commit/7d198145b6870e2b31d32436e19b697d35a25d1d))  
chore(core): Export all generic instance components [#8755](https://github.com/spinnaker/deck/pull/8755) ([f1029669](https://github.com/spinnaker/deck/commit/f1029669ac91c4f2b0c859a66debdf49d79b7cc5))  
fix(core): Fix details drawer positioning [#8754](https://github.com/spinnaker/deck/pull/8754) ([530f59c9](https://github.com/spinnaker/deck/commit/530f59c912bc9ef1293b0dfa44a80ad8cef94e08))  



## [0.0.533](https://www.github.com/spinnaker/deck/compare/41093c05e16b8109b22c1e2794df7334e673be77...fbb4e56d5483713b63b43d3a1642c640512f45ed) (2020-11-24)


### Changes

chore(core): publish core@0.0.533 ([fbb4e56d](https://github.com/spinnaker/deck/commit/fbb4e56d5483713b63b43d3a1642c640512f45ed))  
feat(core/managed): surface pre-deployment steps for versions [#8750](https://github.com/spinnaker/deck/pull/8750) ([f4d7d14e](https://github.com/spinnaker/deck/commit/f4d7d14e5bff77e381274f634d7134968e0f7293))  
chore(core): Upload new illustrations [#8744](https://github.com/spinnaker/deck/pull/8744) ([0cdf8369](https://github.com/spinnaker/deck/commit/0cdf8369f4156e09ebcab8f097ec76e2b713936b))  
feat(core): Add a container for migration banners [#8735](https://github.com/spinnaker/deck/pull/8735) ([e3c80c35](https://github.com/spinnaker/deck/commit/e3c80c353d2d10bdf82cafc89dded9fad9fb2125))  
feat(bake): include git/repo artifacts in the list of artifacts to choose when baking helm charts [#8743](https://github.com/spinnaker/deck/pull/8743) ([976e958b](https://github.com/spinnaker/deck/commit/976e958b25d44086b59108367ab038199ffcdc7e))  
feat(aws): added the ability to modify CPU credits [#8736](https://github.com/spinnaker/deck/pull/8736) ([7a8f9394](https://github.com/spinnaker/deck/commit/7a8f9394354c4c5bb75d0bfd98feb2a76bad3a94))  



## [0.0.532](https://www.github.com/spinnaker/deck/compare/91cceb3feeb3bb5e64b0ad4f87af77d504d05d23...41093c05e16b8109b22c1e2794df7334e673be77) (2020-11-18)


### Changes

chore(core): publish core@0.0.532 ([41093c05](https://github.com/spinnaker/deck/commit/41093c05e16b8109b22c1e2794df7334e673be77))  
fix(core/api): Fix type safety for API.one() builder [#8739](https://github.com/spinnaker/deck/pull/8739) ([b3536fd5](https://github.com/spinnaker/deck/commit/b3536fd5102d201356244efa2e8a5342231b0982))  
feat(core/managed): fill some usage logging gaps in Environments [#8738](https://github.com/spinnaker/deck/pull/8738) ([43d58e84](https://github.com/spinnaker/deck/commit/43d58e84767ce973bef8b7a348965019ed14e86a))  
fix(core): Initialize cache for executions permalink [#8737](https://github.com/spinnaker/deck/pull/8737) ([abd0da70](https://github.com/spinnaker/deck/commit/abd0da7017efb4313486dce89882b62e182d8035))  
fix(config): Make running executions limit configurable [#8713](https://github.com/spinnaker/deck/pull/8713) ([97148a44](https://github.com/spinnaker/deck/commit/97148a44ad3d6818657359acce91fc1baff3b9a1))  
feat(core/pipelines): Add execution breadcrumbs to the pipeline execution component [#8462](https://github.com/spinnaker/deck/pull/8462) ([7271167b](https://github.com/spinnaker/deck/commit/7271167b685e32972047eef22e47cbb37dd0f7f5))  



## [0.0.531](https://www.github.com/spinnaker/deck/compare/dc3646329e1224544078010a3608fab84c5d60f5...91cceb3feeb3bb5e64b0ad4f87af77d504d05d23) (2020-11-12)


### Changes

chore(core): publish core@0.0.531 ([91cceb3f](https://github.com/spinnaker/deck/commit/91cceb3feeb3bb5e64b0ad4f87af77d504d05d23))  
fix(core/loadBalancer): Handle null healthCheckProtocol [#8732](https://github.com/spinnaker/deck/pull/8732) ([645b99dc](https://github.com/spinnaker/deck/commit/645b99dc98b291a818c0108e1ec1b4a39f4cd7eb))  



## [0.0.530](https://www.github.com/spinnaker/deck/compare/0429a9767c0540f9ae17274234985289e23cd42f...dc3646329e1224544078010a3608fab84c5d60f5) (2020-11-12)


### Changes

chore(core): publish core@0.0.530 ([dc364632](https://github.com/spinnaker/deck/commit/dc3646329e1224544078010a3608fab84c5d60f5))  
feat(core/managed): support git compare link on versions [#8730](https://github.com/spinnaker/deck/pull/8730) ([f9b4a87c](https://github.com/spinnaker/deck/commit/f9b4a87cb57aa0f5ad8f2aa4b863f497e326c572))  



## [0.0.529](https://www.github.com/spinnaker/deck/compare/c457c3b5d6c5d41e25956294dfb052634c6c1bd0...0429a9767c0540f9ae17274234985289e23cd42f) (2020-11-12)


### Changes

chore(core): publish core@0.0.529 ([0429a976](https://github.com/spinnaker/deck/commit/0429a9767c0540f9ae17274234985289e23cd42f))  
fix(core/instance): Optional copyable sshLink to InstanceDetailsHeader [#8728](https://github.com/spinnaker/deck/pull/8728) ([f2c6b4ff](https://github.com/spinnaker/deck/commit/f2c6b4ff16f20603d0c58497d83166924a7c65ef))  
fix(amazon/targetGroups): Handle empty healthCheckPath [#8727](https://github.com/spinnaker/deck/pull/8727) ([5cd460e8](https://github.com/spinnaker/deck/commit/5cd460e824c477a8144c4cf6ddba53432f3a3a1e))  



## [0.0.528](https://www.github.com/spinnaker/deck/compare/99a637e6832f188512b5cbc2a4a00174998f389d...c457c3b5d6c5d41e25956294dfb052634c6c1bd0) (2020-11-11)


### Changes

chore(core): publish core@0.0.528 ([c457c3b5](https://github.com/spinnaker/deck/commit/c457c3b5d6c5d41e25956294dfb052634c6c1bd0))  
Pager feature in Spinnaker application navigation bar  as an optional feature [#8715](https://github.com/spinnaker/deck/pull/8715) ([c6b23a7a](https://github.com/spinnaker/deck/commit/c6b23a7ab276269f4338874edca24be75ad308f8))  



## [0.0.527](https://www.github.com/spinnaker/deck/compare/ad433462d5fc7bf25eb37dd653aa3c7ccf1211c7...99a637e6832f188512b5cbc2a4a00174998f389d) (2020-11-10)


### Changes

chore(core): publish core@0.0.527 ([99a637e6](https://github.com/spinnaker/deck/commit/99a637e6832f188512b5cbc2a4a00174998f389d))  
chore(managed): fix new resource versions not showing up in environment view [#8702](https://github.com/spinnaker/deck/pull/8702) ([cb01e00f](https://github.com/spinnaker/deck/commit/cb01e00f0eade9ec596ce3ae1b798cae84063d50))  
fix(core/pipeline): Handle null restrictionExecutionWindow [#8707](https://github.com/spinnaker/deck/pull/8707) ([f54db852](https://github.com/spinnaker/deck/commit/f54db8521c99c737693f58f82b781e92b47cc257))  
feat(core): Add Registry object to DebugWindow for easier debugging [#8708](https://github.com/spinnaker/deck/pull/8708) ([65a84e5b](https://github.com/spinnaker/deck/commit/65a84e5b91f53cb55614b69cc456b0b8042d1aa2))  
feat(core/search): Make the search results dropdown wider [#8709](https://github.com/spinnaker/deck/pull/8709) ([2d56329d](https://github.com/spinnaker/deck/commit/2d56329d8bff450517a5abf1c1570f0087e5a451))  



## [0.0.526](https://www.github.com/spinnaker/deck/compare/5fcb3bff1fe7a14deb992efeddfc79fb7f9f9697...ad433462d5fc7bf25eb37dd653aa3c7ccf1211c7) (2020-11-07)


### Changes

chore(core): publish core@0.0.526 ([ad433462](https://github.com/spinnaker/deck/commit/ad433462d5fc7bf25eb37dd653aa3c7ccf1211c7))  
refactor(core/instance): Create generic header for instance details panel [#8706](https://github.com/spinnaker/deck/pull/8706) ([37483823](https://github.com/spinnaker/deck/commit/37483823071dfbc76491b77004c5435479207cc6))  
fix(core/nav): Add shading to active route [#8705](https://github.com/spinnaker/deck/pull/8705) ([53490381](https://github.com/spinnaker/deck/commit/53490381accc124a467e7dfd6c4b8188920d2bff))  
refactor(core): Remove componentWillUpdate and componentWillMount methods ([d53136ad](https://github.com/spinnaker/deck/commit/d53136ad7b40fed858a17cbac878344ee10e904d))  
fix(core/securityGroup): Place alerts in a better spot [#8696](https://github.com/spinnaker/deck/pull/8696) ([586308cf](https://github.com/spinnaker/deck/commit/586308cf2001189a260199707d7f263da6e62f94))  



## [0.0.525](https://www.github.com/spinnaker/deck/compare/edfe6830b6e22b6eeba5b3a63cc6a91606ea0a29...5fcb3bff1fe7a14deb992efeddfc79fb7f9f9697) (2020-11-03)


### Changes

chore(core): publish core@0.0.525 ([5fcb3bff](https://github.com/spinnaker/deck/commit/5fcb3bff1fe7a14deb992efeddfc79fb7f9f9697))  
fix(core): Remove less override after react refactor [#8695](https://github.com/spinnaker/deck/pull/8695) ([ade00f79](https://github.com/spinnaker/deck/commit/ade00f79934e011f6a4c5afe3897e1dc19bb340c))  
refactor(aws/titus): Reactify instance insights [#8698](https://github.com/spinnaker/deck/pull/8698) ([deab22f3](https://github.com/spinnaker/deck/commit/deab22f34fe0cd82fc797a553732a148b72405b8))  
fix(core/clusterFilter): Use filterModel object to retrieve filter values [#8693](https://github.com/spinnaker/deck/pull/8693) ([62b81bf0](https://github.com/spinnaker/deck/commit/62b81bf027ea6fc6c3bb250abe79f2ec64004d71))  
fix(core/cronTrigger): Fix regex for weekly cron triggers [#8690](https://github.com/spinnaker/deck/pull/8690) ([4a4f4721](https://github.com/spinnaker/deck/commit/4a4f47218b21e79fca92217999e89e5f2a185a9c))  



## [0.0.524](https://www.github.com/spinnaker/deck/compare/9f015b14597cc8ac9e0836590c9d58dda40cbb3e...edfe6830b6e22b6eeba5b3a63cc6a91606ea0a29) (2020-10-28)


### Changes

chore(core): publish core@0.0.524 ([edfe6830](https://github.com/spinnaker/deck/commit/edfe6830b6e22b6eeba5b3a63cc6a91606ea0a29))  
fix(promiselike): Revert typeRoots tsconfig change, move types to src/types and add KLUDGE to expose them in the @spinnaker/core bundle ([a929d3fa](https://github.com/spinnaker/deck/commit/a929d3fa4db978aaf7b6d8ada12abc5b03403821))  
chore(PromiseLike): Migrate remaining IPromise typings to PromiseLike ([2c0d0f68](https://github.com/spinnaker/deck/commit/2c0d0f6814689d93820eab4e97e5d89f98a61cc5))  



## [0.0.523](https://www.github.com/spinnaker/deck/compare/47a8002877ce304ce51f928214e6b3b60820fc6c...9f015b14597cc8ac9e0836590c9d58dda40cbb3e) (2020-10-28)


### Changes

chore(core): publish core@0.0.523 ([9f015b14](https://github.com/spinnaker/deck/commit/9f015b14597cc8ac9e0836590c9d58dda40cbb3e))  
feat(core/clusterFilter): Sort cluster filter values alphabetically [#8689](https://github.com/spinnaker/deck/pull/8689) ([6da861a1](https://github.com/spinnaker/deck/commit/6da861a12aaeda744b253491cb67e2b141fe7362))  
chore(promiselike): Migrate more code away from angularjs IPromise to PromiseLike [#8687](https://github.com/spinnaker/deck/pull/8687) ([1df3daa8](https://github.com/spinnaker/deck/commit/1df3daa88209e885abb3d528edae4a942a060afb))  
refactor(storybook/forms): Rewrite stories for SpinFormik [#8660](https://github.com/spinnaker/deck/pull/8660) ([947cee14](https://github.com/spinnaker/deck/commit/947cee14d35f9137d96bc874a5169693470b5252))  
refactor(aws/instance): Reactify instance tags and security groups [#8686](https://github.com/spinnaker/deck/pull/8686) ([d15ca2d5](https://github.com/spinnaker/deck/commit/d15ca2d56ef4bf924b089c73bb612d302b10fdd2))  
feat(core/managed): add DIFF_NOT_ACTIONABLE resource status [#8681](https://github.com/spinnaker/deck/pull/8681) ([9b8795dc](https://github.com/spinnaker/deck/commit/9b8795dc07e188afd5bcf9b537e4b56f6926e71d))  
chore(eslint): pre-fix linter violations from upcoming eslint-plugin release [#8684](https://github.com/spinnaker/deck/pull/8684) ([2c8b8566](https://github.com/spinnaker/deck/commit/2c8b8566e467ce2cf24437a1acd5209f57c66175))  
chore(oracle/serverGroup): Migrate from $q.all({}) to $q.all([]) ([9de2547d](https://github.com/spinnaker/deck/commit/9de2547deb22111fb94ea33dde4ac1859c0767c0))  
chore(package): amazon@0.0.274 appengine@0.0.21 azure@0.0.259 cloudfoundry@0.0.105 core@0.0.522 docker@0.0.64 ecs@0.0.267 google@0.0.25 huaweicloud@0.0.7 kubernetes@0.0.53 oracle@0.0.14 tencentcloud@0.0.10 titus@0.0.149 [#8680](https://github.com/spinnaker/deck/pull/8680) ([47a80028](https://github.com/spinnaker/deck/commit/47a8002877ce304ce51f928214e6b3b60820fc6c))  
chore(modules): Reformat package.json with prettier [#8679](https://github.com/spinnaker/deck/pull/8679) ([0b1e2977](https://github.com/spinnaker/deck/commit/0b1e29778521da03673dc2aff083e490164ce616))  
Revert "chore(package): amazon@0.0.274 appengine@0.0.21 azure@0.0.259 cloudfoundry@0.0.105 core@0.0.522 docker@0.0.64 ecs@0.0.267 google@0.0.25 huaweicloud@0.0.7 kubernetes@0.0.53 oracle@0.0.14 tencentcloud@0.0.10 titus@0.0.149" [#8678](https://github.com/spinnaker/deck/pull/8678) ([a220af58](https://github.com/spinnaker/deck/commit/a220af588e194762757be534cce2d7ae9dc508d5))  



## [0.0.522](https://www.github.com/spinnaker/deck/compare/a220af588e194762757be534cce2d7ae9dc508d5...47a8002877ce304ce51f928214e6b3b60820fc6c) (2020-10-26)


### Changes

chore(package): amazon@0.0.274 appengine@0.0.21 azure@0.0.259 cloudfoundry@0.0.105 core@0.0.522 docker@0.0.64 ecs@0.0.267 google@0.0.25 huaweicloud@0.0.7 kubernetes@0.0.53 oracle@0.0.14 tencentcloud@0.0.10 titus@0.0.149 [#8680](https://github.com/spinnaker/deck/pull/8680) ([47a80028](https://github.com/spinnaker/deck/commit/47a8002877ce304ce51f928214e6b3b60820fc6c))  
chore(modules): Reformat package.json with prettier [#8679](https://github.com/spinnaker/deck/pull/8679) ([0b1e2977](https://github.com/spinnaker/deck/commit/0b1e29778521da03673dc2aff083e490164ce616))  
Revert "chore(package): amazon@0.0.274 appengine@0.0.21 azure@0.0.259 cloudfoundry@0.0.105 core@0.0.522 docker@0.0.64 ecs@0.0.267 google@0.0.25 huaweicloud@0.0.7 kubernetes@0.0.53 oracle@0.0.14 tencentcloud@0.0.10 titus@0.0.149" [#8678](https://github.com/spinnaker/deck/pull/8678) ([a220af58](https://github.com/spinnaker/deck/commit/a220af588e194762757be534cce2d7ae9dc508d5))  
chore(core): publish core@0.0.522 ([343296b1](https://github.com/spinnaker/deck/commit/343296b1a0404c142a2c6cf23f30d66a527a375a))  
chore(PromiseLike): Migrate code from IPromise types to PromiseLike ([1e2032bb](https://github.com/spinnaker/deck/commit/1e2032bb39d6b524a3dfae3df9c4bd6862c40057))  
chore(PromiseLike): Migrate code from IPromise types to PromiseLike ([553be66f](https://github.com/spinnaker/deck/commit/553be66f1c2757e0bb5ecfd595986697f245c041))  
feat(typescript): Add a new `app/types` typeRoot to all the tsconfig.json files providing `PromiseLike` and *.svg imports ([e622a534](https://github.com/spinnaker/deck/commit/e622a5348f614ee8615fab13082ac5f2fdd95960))  
feat($q): Adds PromiseLike compatibility to $q and $q promises ([c9c0b4dc](https://github.com/spinnaker/deck/commit/c9c0b4dc0d071e737090745f450fef0e84b08c74))  
chore(package): In packages, do not use webpack to typecheck [#8670](https://github.com/spinnaker/deck/pull/8670) ([8b3c134d](https://github.com/spinnaker/deck/commit/8b3c134d1ab82610611a194917cf5958047e1cc3))  
chore(package): use node_modules/.bin/* in module scripts, add 'build' script (the old 'lib' script) [#8668](https://github.com/spinnaker/deck/pull/8668) ([231f7818](https://github.com/spinnaker/deck/commit/231f7818895e7e2a12bb3591a2112559e07ee01d))  
fix(core): Check if manifest event is null from k8s. [#8666](https://github.com/spinnaker/deck/pull/8666) ([f3e1d6e5](https://github.com/spinnaker/deck/commit/f3e1d6e5f78b9149c5a89f5a84992357c289a1f0))  
fix(deck): Bugfix 6112: provide default settings for oracle provider in deck microservice to remove Halyard dependency. [#8665](https://github.com/spinnaker/deck/pull/8665) ([6022821f](https://github.com/spinnaker/deck/commit/6022821f895597e7bf8b9d62d02d7ba5c92ab70f))  
feat(core/managed): run commit messages through Markdown [#8664](https://github.com/spinnaker/deck/pull/8664) ([926b1bd8](https://github.com/spinnaker/deck/commit/926b1bd8943144648d503c011d2c0a68a6f0c17b))  



## [0.0.521](https://www.github.com/spinnaker/deck/compare/343296b1a0404c142a2c6cf23f30d66a527a375a...a220af588e194762757be534cce2d7ae9dc508d5) (2020-10-26)


### Changes

Revert "chore(package): amazon@0.0.274 appengine@0.0.21 azure@0.0.259 cloudfoundry@0.0.105 core@0.0.522 docker@0.0.64 ecs@0.0.267 google@0.0.25 huaweicloud@0.0.7 kubernetes@0.0.53 oracle@0.0.14 tencentcloud@0.0.10 titus@0.0.149" [#8678](https://github.com/spinnaker/deck/pull/8678) ([a220af58](https://github.com/spinnaker/deck/commit/a220af588e194762757be534cce2d7ae9dc508d5))  



## [0.0.522](https://www.github.com/spinnaker/deck/compare/b7528427da90e2307d7841511800c288eb1a6250...343296b1a0404c142a2c6cf23f30d66a527a375a) (2020-10-26)


### Changes

chore(package): amazon@0.0.274 appengine@0.0.21 azure@0.0.259 cloudfoundry@0.0.105 core@0.0.522 docker@0.0.64 ecs@0.0.267 google@0.0.25 huaweicloud@0.0.7 kubernetes@0.0.53 oracle@0.0.14 tencentcloud@0.0.10 titus@0.0.149 [#8680](https://github.com/spinnaker/deck/pull/8680) ([47a80028](https://github.com/spinnaker/deck/commit/47a8002877ce304ce51f928214e6b3b60820fc6c))  
chore(modules): Reformat package.json with prettier [#8679](https://github.com/spinnaker/deck/pull/8679) ([0b1e2977](https://github.com/spinnaker/deck/commit/0b1e29778521da03673dc2aff083e490164ce616))  
Revert "chore(package): amazon@0.0.274 appengine@0.0.21 azure@0.0.259 cloudfoundry@0.0.105 core@0.0.522 docker@0.0.64 ecs@0.0.267 google@0.0.25 huaweicloud@0.0.7 kubernetes@0.0.53 oracle@0.0.14 tencentcloud@0.0.10 titus@0.0.149" [#8678](https://github.com/spinnaker/deck/pull/8678) ([a220af58](https://github.com/spinnaker/deck/commit/a220af588e194762757be534cce2d7ae9dc508d5))  
chore(core): publish core@0.0.522 ([343296b1](https://github.com/spinnaker/deck/commit/343296b1a0404c142a2c6cf23f30d66a527a375a))  
chore(PromiseLike): Migrate code from IPromise types to PromiseLike ([1e2032bb](https://github.com/spinnaker/deck/commit/1e2032bb39d6b524a3dfae3df9c4bd6862c40057))  
chore(PromiseLike): Migrate code from IPromise types to PromiseLike ([553be66f](https://github.com/spinnaker/deck/commit/553be66f1c2757e0bb5ecfd595986697f245c041))  
feat(typescript): Add a new `app/types` typeRoot to all the tsconfig.json files providing `PromiseLike` and *.svg imports ([e622a534](https://github.com/spinnaker/deck/commit/e622a5348f614ee8615fab13082ac5f2fdd95960))  
feat($q): Adds PromiseLike compatibility to $q and $q promises ([c9c0b4dc](https://github.com/spinnaker/deck/commit/c9c0b4dc0d071e737090745f450fef0e84b08c74))  
chore(package): In packages, do not use webpack to typecheck [#8670](https://github.com/spinnaker/deck/pull/8670) ([8b3c134d](https://github.com/spinnaker/deck/commit/8b3c134d1ab82610611a194917cf5958047e1cc3))  
chore(package): use node_modules/.bin/* in module scripts, add 'build' script (the old 'lib' script) [#8668](https://github.com/spinnaker/deck/pull/8668) ([231f7818](https://github.com/spinnaker/deck/commit/231f7818895e7e2a12bb3591a2112559e07ee01d))  
fix(core): Check if manifest event is null from k8s. [#8666](https://github.com/spinnaker/deck/pull/8666) ([f3e1d6e5](https://github.com/spinnaker/deck/commit/f3e1d6e5f78b9149c5a89f5a84992357c289a1f0))  
fix(deck): Bugfix 6112: provide default settings for oracle provider in deck microservice to remove Halyard dependency. [#8665](https://github.com/spinnaker/deck/pull/8665) ([6022821f](https://github.com/spinnaker/deck/commit/6022821f895597e7bf8b9d62d02d7ba5c92ab70f))  
feat(core/managed): run commit messages through Markdown [#8664](https://github.com/spinnaker/deck/pull/8664) ([926b1bd8](https://github.com/spinnaker/deck/commit/926b1bd8943144648d503c011d2c0a68a6f0c17b))  



## [0.0.521](https://www.github.com/spinnaker/deck/compare/ae9292ed1f1774d779a3c8ed41c5d97f76991097...b7528427da90e2307d7841511800c288eb1a6250) (2020-10-15)


### Changes

Revert "chore(package): amazon@0.0.274 appengine@0.0.21 azure@0.0.259 cloudfoundry@0.0.105 core@0.0.522 docker@0.0.64 ecs@0.0.267 google@0.0.25 huaweicloud@0.0.7 kubernetes@0.0.53 oracle@0.0.14 tencentcloud@0.0.10 titus@0.0.149" [#8678](https://github.com/spinnaker/deck/pull/8678) ([a220af58](https://github.com/spinnaker/deck/commit/a220af588e194762757be534cce2d7ae9dc508d5))  
chore(core): publish core@0.0.522 ([343296b1](https://github.com/spinnaker/deck/commit/343296b1a0404c142a2c6cf23f30d66a527a375a))  
chore(PromiseLike): Migrate code from IPromise types to PromiseLike ([1e2032bb](https://github.com/spinnaker/deck/commit/1e2032bb39d6b524a3dfae3df9c4bd6862c40057))  
chore(PromiseLike): Migrate code from IPromise types to PromiseLike ([553be66f](https://github.com/spinnaker/deck/commit/553be66f1c2757e0bb5ecfd595986697f245c041))  
feat(typescript): Add a new `app/types` typeRoot to all the tsconfig.json files providing `PromiseLike` and *.svg imports ([e622a534](https://github.com/spinnaker/deck/commit/e622a5348f614ee8615fab13082ac5f2fdd95960))  
feat($q): Adds PromiseLike compatibility to $q and $q promises ([c9c0b4dc](https://github.com/spinnaker/deck/commit/c9c0b4dc0d071e737090745f450fef0e84b08c74))  
chore(package): In packages, do not use webpack to typecheck [#8670](https://github.com/spinnaker/deck/pull/8670) ([8b3c134d](https://github.com/spinnaker/deck/commit/8b3c134d1ab82610611a194917cf5958047e1cc3))  
chore(package): use node_modules/.bin/* in module scripts, add 'build' script (the old 'lib' script) [#8668](https://github.com/spinnaker/deck/pull/8668) ([231f7818](https://github.com/spinnaker/deck/commit/231f7818895e7e2a12bb3591a2112559e07ee01d))  
fix(core): Check if manifest event is null from k8s. [#8666](https://github.com/spinnaker/deck/pull/8666) ([f3e1d6e5](https://github.com/spinnaker/deck/commit/f3e1d6e5f78b9149c5a89f5a84992357c289a1f0))  
fix(deck): Bugfix 6112: provide default settings for oracle provider in deck microservice to remove Halyard dependency. [#8665](https://github.com/spinnaker/deck/pull/8665) ([6022821f](https://github.com/spinnaker/deck/commit/6022821f895597e7bf8b9d62d02d7ba5c92ab70f))  
feat(core/managed): run commit messages through Markdown [#8664](https://github.com/spinnaker/deck/pull/8664) ([926b1bd8](https://github.com/spinnaker/deck/commit/926b1bd8943144648d503c011d2c0a68a6f0c17b))  
chore(core): publish core@0.0.521 [#8659](https://github.com/spinnaker/deck/pull/8659) ([b7528427](https://github.com/spinnaker/deck/commit/b7528427da90e2307d7841511800c288eb1a6250))  
feat(core/pipeline): Rename "Source" link to "View as JSON" [#8658](https://github.com/spinnaker/deck/pull/8658) ([97bd615b](https://github.com/spinnaker/deck/commit/97bd615b565bf03623f64d92c3f124177670bc16))  
fix(core/managed): surface artifact reference on details pane [#8656](https://github.com/spinnaker/deck/pull/8656) ([d7e357e5](https://github.com/spinnaker/deck/commit/d7e357e5228089afa1de1bedb64930b3d00ae2ea))  



## [0.0.520](https://www.github.com/spinnaker/deck/compare/4b79b4d75628bc74b91b72980f0a5e8ba479335e...ae9292ed1f1774d779a3c8ed41c5d97f76991097) (2020-10-14)


### Changes

chore(package): amazon@0.0.273 core@0.0.520 oracle@0.0.13 [#8655](https://github.com/spinnaker/deck/pull/8655) ([ae9292ed](https://github.com/spinnaker/deck/commit/ae9292ed1f1774d779a3c8ed41c5d97f76991097))  
fix(core/managed): respect line breaks in commit messages [#8653](https://github.com/spinnaker/deck/pull/8653) ([068518d8](https://github.com/spinnaker/deck/commit/068518d8ac8917be9f1676067094e266888c450a))  
fix(aws): Support cross zone load balancing for NLB [#8557](https://github.com/spinnaker/deck/pull/8557) ([a7e8847d](https://github.com/spinnaker/deck/commit/a7e8847db53289a3dd8803523096ae4a91710184))  



## [0.0.519](https://www.github.com/spinnaker/deck/compare/f216cc6556bff90033b28bbe7e9f94517ddaa270...4b79b4d75628bc74b91b72980f0a5e8ba479335e) (2020-10-12)


### Changes

chore(package): amazon@0.0.272 appengine@0.0.20 azure@0.0.258 cloudfoundry@0.0.104 core@0.0.519 docker@0.0.63 ecs@0.0.266 google@0.0.24 huaweicloud@0.0.6 kubernetes@0.0.52 oracle@0.0.12 tencentcloud@0.0.9 titus@0.0.148 [#8647](https://github.com/spinnaker/deck/pull/8647) ([4b79b4d7](https://github.com/spinnaker/deck/commit/4b79b4d75628bc74b91b72980f0a5e8ba479335e))  
chore(prettier): Just Update Prettier™ [#8644](https://github.com/spinnaker/deck/pull/8644) ([8532bdd4](https://github.com/spinnaker/deck/commit/8532bdd4c08d59c38a0adde70ccac4f163c9dd97))  
feat(core): make Environments the default route if enabled [#8643](https://github.com/spinnaker/deck/pull/8643) ([4fc4c714](https://github.com/spinnaker/deck/commit/4fc4c71462f83fd0ebb6ffa9b1801b6d54be2367))  
 refactor(core): Component for instance actions dropdown [#8642](https://github.com/spinnaker/deck/pull/8642) ([24b1db6c](https://github.com/spinnaker/deck/commit/24b1db6ceb84d16afef74472c7e7bb6280e6e570))  
chore(lint): eslint --fix react2angular-with-error-boundary ([defaf19b](https://github.com/spinnaker/deck/commit/defaf19b5f11f8cce70e14fa1cdd52e88e6de0fd))  
feat(core/presentation): Add SpinErrorBoundary.tsx react error boundary component ([c60dc303](https://github.com/spinnaker/deck/commit/c60dc303f03268b4cbe1731f2b8c55d96d1db68a))  
feat(ecs): add cypress tests for the ecs provider [#8632](https://github.com/spinnaker/deck/pull/8632) ([382591bb](https://github.com/spinnaker/deck/commit/382591bb3768eb7fb598e6eb598e0baf5c26b150))  
feat(keel): set defaults for managed delivery config [#8634](https://github.com/spinnaker/deck/pull/8634) ([5848c28d](https://github.com/spinnaker/deck/commit/5848c28d86825272b3b8e48435f2d9f9ebeda324))  
feat(core/application): sort Environments first for users with it enabled [#8639](https://github.com/spinnaker/deck/pull/8639) ([8bfa687a](https://github.com/spinnaker/deck/commit/8bfa687af932db06579dbec1ed90bcf5e4cf4474))  



## [0.0.518](https://www.github.com/spinnaker/deck/compare/5cdc7fa4494ada88702155bb91f80a0a16f3782e...f216cc6556bff90033b28bbe7e9f94517ddaa270) (2020-10-09)


### Changes

Package bump amazon 0.0.271 azure 0.0.257 cloudfoundry 0.0.103 core 0.0.518 docker 0.0.62 google 0.0.23 oracle 0.0.11 tencentcloud 0.0.8 titus 0.0.147 [#8640](https://github.com/spinnaker/deck/pull/8640) ([f216cc65](https://github.com/spinnaker/deck/commit/f216cc6556bff90033b28bbe7e9f94517ddaa270))  
Drag n drop nav [#8626](https://github.com/spinnaker/deck/pull/8626) ([d565b000](https://github.com/spinnaker/deck/commit/d565b000721c8468c112876f8aad6e4a35bb46f5))  
chore(eslint): eslint --fix api-no-slashes [#8631](https://github.com/spinnaker/deck/pull/8631) ([fab1a0ad](https://github.com/spinnaker/deck/commit/fab1a0ad75200cca60dfb74455d99f332e3e376f))  



## [0.0.517](https://www.github.com/spinnaker/deck/compare/8aa1e3e514703fcf0b4bf7b06dffafe01e9c27ed...5cdc7fa4494ada88702155bb91f80a0a16f3782e) (2020-10-06)


### Changes

chore(package): amazon@0.0.270 azure@0.0.256 cloudfoundry@0.0.102 core@0.0.517 docker@0.0.61 google@0.0.22 oracle@0.0.10 tencentcloud@0.0.7 [#8630](https://github.com/spinnaker/deck/pull/8630) ([5cdc7fa4](https://github.com/spinnaker/deck/commit/5cdc7fa4494ada88702155bb91f80a0a16f3782e))  
Revert "fix(appname): encodeURIComponent for app name (#8586)" [#8627](https://github.com/spinnaker/deck/pull/8627) ([885cd169](https://github.com/spinnaker/deck/commit/885cd169ad0dca8e7e6683bc96d2c131af6b3a1e))  



## [0.0.516](https://www.github.com/spinnaker/deck/compare/682b3d1335d24eed782fc736de63893f1fab9f24...8aa1e3e514703fcf0b4bf7b06dffafe01e9c27ed) (2020-10-05)


### Changes

chore(package): amazon@0.0.269 azure@0.0.255 cloudfoundry@0.0.101 core@0.0.516 docker@0.0.60 google@0.0.21 oracle@0.0.9 tencentcloud@0.0.6 [#8624](https://github.com/spinnaker/deck/pull/8624) ([8aa1e3e5](https://github.com/spinnaker/deck/commit/8aa1e3e514703fcf0b4bf7b06dffafe01e9c27ed))  
feat(core/managed): limit artifact versions to 30 [#8623](https://github.com/spinnaker/deck/pull/8623) ([80858e77](https://github.com/spinnaker/deck/commit/80858e771cf041d4de4347c6e701606af152d09a))  
feat(core/managed): put constraints in a 'skipped' state on skipped versions [#8620](https://github.com/spinnaker/deck/pull/8620) ([90a2819a](https://github.com/spinnaker/deck/commit/90a2819a34d5f42acc6a5d60233282ef0d508dbc))  
feat(core/managed): scroll to selected version in sidebar, update scroll containers [#8618](https://github.com/spinnaker/deck/pull/8618) ([815742d4](https://github.com/spinnaker/deck/commit/815742d4a93c4eec0faf404bd5d086916c55abe5))  
feat(core/managed): apply consistent sorting to resources [#8622](https://github.com/spinnaker/deck/pull/8622) ([de24ef32](https://github.com/spinnaker/deck/commit/de24ef322c39f1199cad50fd9264f973c8ea99ac))  
fix(core/pipeline): Always enable "show revision history" in edit pipeline dialog [#8621](https://github.com/spinnaker/deck/pull/8621) ([37354834](https://github.com/spinnaker/deck/commit/3735483477f2a1ba6eaad437c617412221836aaa))  
fix(core/managed): fixup some details from new layout [#8619](https://github.com/spinnaker/deck/pull/8619) ([abffd841](https://github.com/spinnaker/deck/commit/abffd8412797a59d5f049efb258df010758669e9))  
fix(appname): encodeURIComponent for app name [#8586](https://github.com/spinnaker/deck/pull/8586) ([f1bb04e8](https://github.com/spinnaker/deck/commit/f1bb04e867e68e53f4e4edb22192afbcd9715d5d))  
feat(validation): Allow app name validators to be overridden [#8584](https://github.com/spinnaker/deck/pull/8584) ([60c0c7b3](https://github.com/spinnaker/deck/commit/60c0c7b3cfbc5aac732feb1a4be206c5509c1bc1))  



## [0.0.515](https://www.github.com/spinnaker/deck/compare/cccfdb37d0765f25e53998b1def0fd65739e5b44...682b3d1335d24eed782fc736de63893f1fab9f24) (2020-10-02)


### Changes

chore(package): cloudfoundry@0.0.100 core@0.0.515 titus@0.0.146 [#8616](https://github.com/spinnaker/deck/pull/8616) ([682b3d13](https://github.com/spinnaker/deck/commit/682b3d1335d24eed782fc736de63893f1fab9f24))  
fix(Oracle): Fixed Oracle object storage selection [#8588](https://github.com/spinnaker/deck/pull/8588) ([d38e7c54](https://github.com/spinnaker/deck/commit/d38e7c54e8aed27a52c1282ed55128bd445f859d))  



## [0.0.514](https://www.github.com/spinnaker/deck/compare/c16d3ff8683c724d90a78cbd2a963766e767d4ee...cccfdb37d0765f25e53998b1def0fd65739e5b44) (2020-10-01)


### Changes

chore(package): appengine@0.0.19 core@0.0.514 ecs@0.0.265 titus@0.0.145 [#8612](https://github.com/spinnaker/deck/pull/8612) ([cccfdb37](https://github.com/spinnaker/deck/commit/cccfdb37d0765f25e53998b1def0fd65739e5b44))  
feat(core/managed): switch to abbreviated timestamp format, add version timestamps [#8610](https://github.com/spinnaker/deck/pull/8610) ([34bc7e9f](https://github.com/spinnaker/deck/commit/34bc7e9f250e922a352a90c1b2ff417aff70ffb8))  
feat(core/managed): show failed, pending bubbles for any constraint type [#8611](https://github.com/spinnaker/deck/pull/8611) ([012b0048](https://github.com/spinnaker/deck/commit/012b004842c53cac4fe427ba1d4ae01026c25132))  
feat(webhooks): document stausJsonPath is now optional [#8608](https://github.com/spinnaker/deck/pull/8608) ([7604b3d4](https://github.com/spinnaker/deck/commit/7604b3d4bf7fb3ee795ea7720eb34b722f3a0f67))  
feat(core/presentation): add useInterval hook [#8609](https://github.com/spinnaker/deck/pull/8609) ([0463ecb5](https://github.com/spinnaker/deck/commit/0463ecb5c4f2146635378746bb49c3c9551a595d))  



## [0.0.513](https://www.github.com/spinnaker/deck/compare/34e523f3219b0db78f61a2d5723525bc60a676b9...c16d3ff8683c724d90a78cbd2a963766e767d4ee) (2020-09-28)


### Changes

chore(core): publish core@0.0.513 [#8606](https://github.com/spinnaker/deck/pull/8606) ([c16d3ff8](https://github.com/spinnaker/deck/commit/c16d3ff8683c724d90a78cbd2a963766e767d4ee))  
feat(core/managed): visual re-treatment of statuses, constraints, and more [#8600](https://github.com/spinnaker/deck/pull/8600) ([c71e950c](https://github.com/spinnaker/deck/commit/c71e950c33bc5b99f8fbc33edf8a2d6a6c61f831))  



## [0.0.512](https://www.github.com/spinnaker/deck/compare/3d8f6268351065390bf3a42e7bfcf7cb398b236d...34e523f3219b0db78f61a2d5723525bc60a676b9) (2020-09-28)


### Changes

chore(package): core@0.0.512 kubernetes@0.0.51 [#8605](https://github.com/spinnaker/deck/pull/8605) ([34e523f3](https://github.com/spinnaker/deck/commit/34e523f3219b0db78f61a2d5723525bc60a676b9))  
fix(core/spelNumberInput): Fix broken html escaping [#8604](https://github.com/spinnaker/deck/pull/8604) ([f3324c69](https://github.com/spinnaker/deck/commit/f3324c69f141d427d46708e9f264905fbd788de5))  
feat(core/managed): tighten up layout on environments header [#8601](https://github.com/spinnaker/deck/pull/8601) ([f38e674f](https://github.com/spinnaker/deck/commit/f38e674f5a115903d5ec2db022891b263ba436c3))  
feat(stages): Add monitorPipeline stage execution details [#8561](https://github.com/spinnaker/deck/pull/8561) ([caff6d20](https://github.com/spinnaker/deck/commit/caff6d2070ca96e770c7c013d7c935b2849d0d80))  
feat(storybook/formik): More formik stories ([e57714c0](https://github.com/spinnaker/deck/commit/e57714c08b41f65c6a214b7f82e917e71992c5a1))  



## [0.0.511](https://www.github.com/spinnaker/deck/compare/2273dee2b3668ba105a34a062da669cdf7c207c5...3d8f6268351065390bf3a42e7bfcf7cb398b236d) (2020-09-25)


### Changes

chore(package): amazon@0.0.268 core@0.0.511 [#8597](https://github.com/spinnaker/deck/pull/8597) ([3d8f6268](https://github.com/spinnaker/deck/commit/3d8f6268351065390bf3a42e7bfcf7cb398b236d))  
feat(helm): Add Helm Chart pipeline trigger [#8475](https://github.com/spinnaker/deck/pull/8475) ([65dd935c](https://github.com/spinnaker/deck/commit/65dd935cdffdcee16667e62cdcc5c727a844cc24))  
feat(storybook/SpinFormik): Create a story for SpinFormik ([519dfc01](https://github.com/spinnaker/deck/commit/519dfc010598de6428b833f1a63e2ee12baaf4f2))  



## [0.0.510](https://www.github.com/spinnaker/deck/compare/502da13306d416b140b82a52648cfbfb476f6277...2273dee2b3668ba105a34a062da669cdf7c207c5) (2020-09-23)


### Changes

chore(package): amazon@0.0.267 appengine@0.0.18 azure@0.0.254 cloudfoundry@0.0.99 core@0.0.510 ecs@0.0.264 google@0.0.20 kubernetes@0.0.50 oracle@0.0.8 tencentcloud@0.0.5 titus@0.0.144 [#8591](https://github.com/spinnaker/deck/pull/8591) ([2273dee2](https://github.com/spinnaker/deck/commit/2273dee2b3668ba105a34a062da669cdf7c207c5))  
feat(core/*): Deck layout optimizations [#8556](https://github.com/spinnaker/deck/pull/8556) ([2588b7f3](https://github.com/spinnaker/deck/commit/2588b7f3e1ecbfd590e7cc87a225bbfd056449e3))  
refactor(amazon/titus): Reactify instance information [#8579](https://github.com/spinnaker/deck/pull/8579) ([969b404e](https://github.com/spinnaker/deck/commit/969b404e356208010a4fa9c0464b6c0a268db0a5))  
refactor(google): clean up GCE-specific feature config [#8585](https://github.com/spinnaker/deck/pull/8585) ([b8051972](https://github.com/spinnaker/deck/commit/b80519721eb0c98e8910a6852670000252ea45f0))  



## [0.0.509](https://www.github.com/spinnaker/deck/compare/e2dad1f3cc64fd58283c1f8d27031be35610d663...502da13306d416b140b82a52648cfbfb476f6277) (2020-09-21)


### Changes

chore(core): publish core@0.0.509 [#8583](https://github.com/spinnaker/deck/pull/8583) ([502da133](https://github.com/spinnaker/deck/commit/502da13306d416b140b82a52648cfbfb476f6277))  
feat(core/managed): surface git metadata on version list [#8580](https://github.com/spinnaker/deck/pull/8580) ([9d0cf544](https://github.com/spinnaker/deck/commit/9d0cf54405a491b2e84170789104ceac9790f78b))  
feat(notification): Add Microsoft Teams notification support [#8503](https://github.com/spinnaker/deck/pull/8503) ([2a11216f](https://github.com/spinnaker/deck/commit/2a11216f57f2250c194cfd9eb372b9340ee3ad84))  



## [0.0.508](https://www.github.com/spinnaker/deck/compare/0ced60a4961839cc5d878252f338981efd2d5f33...e2dad1f3cc64fd58283c1f8d27031be35610d663) (2020-09-18)


### Changes

chore(package): appengine@0.0.17 core@0.0.508 ecs@0.0.263 kubernetes@0.0.49 titus@0.0.143 [#8577](https://github.com/spinnaker/deck/pull/8577) ([e2dad1f3](https://github.com/spinnaker/deck/commit/e2dad1f3cc64fd58283c1f8d27031be35610d663))  
feat(core/managed): add git metadata to version details pane [#8576](https://github.com/spinnaker/deck/pull/8576) ([edc190ca](https://github.com/spinnaker/deck/commit/edc190cae0ee468ac31701547a0f63f4e5eb5706))  
feat(core): add overridable decorator to BakeKustomizeConfigForm [#8575](https://github.com/spinnaker/deck/pull/8575) ([439617d2](https://github.com/spinnaker/deck/commit/439617d2bed5a215a007d8660a19ea3ad02a6d54))  
refactor(hint): Change incorrect grammar for hint [#8568](https://github.com/spinnaker/deck/pull/8568) ([57771e64](https://github.com/spinnaker/deck/commit/57771e64f3e6e6ce37300235e9721844cd0eb25b))  



## [0.0.507](https://www.github.com/spinnaker/deck/compare/2f25b74759d27fe294eaf070059f1526a14494fd...0ced60a4961839cc5d878252f338981efd2d5f33) (2020-09-16)


### Changes

chore(package): amazon@0.0.266 core@0.0.507 [#8569](https://github.com/spinnaker/deck/pull/8569) ([0ced60a4](https://github.com/spinnaker/deck/commit/0ced60a4961839cc5d878252f338981efd2d5f33))  
chore(core): remove CSS module code + build/test scaffolding [#8567](https://github.com/spinnaker/deck/pull/8567) ([09439726](https://github.com/spinnaker/deck/commit/09439726a3abfb1d8f4843d654b8253f90213a8a))  



## [0.0.506](https://www.github.com/spinnaker/deck/compare/9e9c4eb36f24d80255842a47548ed8390e2cb023...2f25b74759d27fe294eaf070059f1526a14494fd) (2020-09-14)


### Changes

chore(package): amazon@0.0.265 appengine@0.0.16 cloudfoundry@0.0.98 core@0.0.506 ecs@0.0.262 titus@0.0.142 [#8564](https://github.com/spinnaker/deck/pull/8564) ([2f25b747](https://github.com/spinnaker/deck/commit/2f25b74759d27fe294eaf070059f1526a14494fd))  
fix(aws): Get imageId and imageName from server group info [#8548](https://github.com/spinnaker/deck/pull/8548) ([25e1c5f5](https://github.com/spinnaker/deck/commit/25e1c5f55894fccd8510ab987c21683310db3cfc))  
fix(core/managed): give StatusBubbleStack its own z-index context [#8558](https://github.com/spinnaker/deck/pull/8558) ([08419c1d](https://github.com/spinnaker/deck/commit/08419c1dff7768aa0a7c26009622cce80f0cf6b8))  
feat(core/managed): wording + terminology tweaks for resources [#8552](https://github.com/spinnaker/deck/pull/8552) ([6c632a99](https://github.com/spinnaker/deck/commit/6c632a99bdd132004f98b7786038985837315e05))  
feat(core/managed): let resource plugins define their own links [#8550](https://github.com/spinnaker/deck/pull/8550) ([2395fa9d](https://github.com/spinnaker/deck/commit/2395fa9dd8d515912b09f5c01676f040cc3348ad))  
chore(typescript): Upgrade typescript to v4 ([c32bb975](https://github.com/spinnaker/deck/commit/c32bb9750fc6a22f5fa5055a104d5b65d56d9c3d))  
feat(core): Move colorful icons to illustrations [#8551](https://github.com/spinnaker/deck/pull/8551) ([aad914f5](https://github.com/spinnaker/deck/commit/aad914f5fbdbf12d27a21fd96386d3a4dc34117e))  
feat(provider/cf): add bake cf manifest stage [#8529](https://github.com/spinnaker/deck/pull/8529) ([040b0717](https://github.com/spinnaker/deck/commit/040b0717b7fb7f18f078d1aa61ec6348c9ecf53d))  



## [0.0.505](https://www.github.com/spinnaker/deck/compare/ced77a7453a0ffab5a14c38943288138fdcb084b...9e9c4eb36f24d80255842a47548ed8390e2cb023) (2020-09-03)


### Changes

chore(package): amazon@0.0.264 core@0.0.505 titus@0.0.141 [#8543](https://github.com/spinnaker/deck/pull/8543) ([9e9c4eb3](https://github.com/spinnaker/deck/commit/9e9c4eb36f24d80255842a47548ed8390e2cb023))  
fix(core/nav): Increase nav width for long text with alerts [#8542](https://github.com/spinnaker/deck/pull/8542) ([ba093b75](https://github.com/spinnaker/deck/commit/ba093b7595ebf31130b6027dd4d0d2de488c073e))  
refactor(aws/titus): Refactor instance status section to react [#8497](https://github.com/spinnaker/deck/pull/8497) ([f358e5e8](https://github.com/spinnaker/deck/commit/f358e5e8f8615a47ba98ade8b465246054d0c917))  
chore(core): Remove old nav css [#8481](https://github.com/spinnaker/deck/pull/8481) ([e508a6f8](https://github.com/spinnaker/deck/commit/e508a6f83c67d9e336e16262ceee35d5fabf754c))  
feat(core/notifier): Make notifier service work with react components [#8521](https://github.com/spinnaker/deck/pull/8521) ([fbe0e54b](https://github.com/spinnaker/deck/commit/fbe0e54b29c452797a43aaea9f2fab0d7c0fe8d8))  
fix(core/securityGroup): Add job type while updating security groups via infrastructure view [#8522](https://github.com/spinnaker/deck/pull/8522) ([21db6572](https://github.com/spinnaker/deck/commit/21db6572f9b355696012a459d7878edb963e7709))  



## [0.0.504](https://www.github.com/spinnaker/deck/compare/f3f76cd8858ae0415c758f5e5bb5f6f70b1075a5...ced77a7453a0ffab5a14c38943288138fdcb084b) (2020-08-25)


### Changes

chore(package): publish amazon 0.0.263 appengine 0.0.15 azure 0.0.253 cloudfoundry 0.0.97 core 0.0.504 docker 0.0.59 ecs 0.0.261 google 0.0.19 huaweicloud 0.0.5 kubernetes 0.0.48 oracle 0.0.7 tencentcloud 0.0.4 titus 0.0.140 [#8520](https://github.com/spinnaker/deck/pull/8520) ([ced77a74](https://github.com/spinnaker/deck/commit/ced77a7453a0ffab5a14c38943288138fdcb084b))  
feat(core/managed): use displayName for resources in place of moniker [#8519](https://github.com/spinnaker/deck/pull/8519) ([fe8822dd](https://github.com/spinnaker/deck/commit/fe8822dd895acaea79747c5b96107f587d3f2d3a))  
feat(core/managed): add resource kind registry as prep for plugins [#8513](https://github.com/spinnaker/deck/pull/8513) ([0f7b1187](https://github.com/spinnaker/deck/commit/0f7b1187cd32cd1d6718e7245385be0114a93c37))  
fix(gitRepoArtifact): move subpath value to location field. [#8507](https://github.com/spinnaker/deck/pull/8507) ([72100088](https://github.com/spinnaker/deck/commit/72100088ddaefa57fa334a8692bda8a89efecd3e))  



## [0.0.503](https://www.github.com/spinnaker/deck/compare/51eb36e35e0e1da2d0a76be9f9aef60525cb554c...f3f76cd8858ae0415c758f5e5bb5f6f70b1075a5) (2020-08-21)


### Changes

chore(package): publish core 0.0.503 [#8514](https://github.com/spinnaker/deck/pull/8514) ([f3f76cd8](https://github.com/spinnaker/deck/commit/f3f76cd8858ae0415c758f5e5bb5f6f70b1075a5))  
chore(licenses): add license metadata to npm packages [#8512](https://github.com/spinnaker/deck/pull/8512) ([d4afa1bf](https://github.com/spinnaker/deck/commit/d4afa1bf2328cc91cf3195f810073b0b4726b3b5))  
refactor(core/instance): Refactor instance links to react [#8511](https://github.com/spinnaker/deck/pull/8511) ([174bda0d](https://github.com/spinnaker/deck/commit/174bda0d475aca39e2df4c6759aa5e56d3a59a44))  
fix(core): Improve rendering speed of details panel [#8506](https://github.com/spinnaker/deck/pull/8506) ([5caf1322](https://github.com/spinnaker/deck/commit/5caf13229279f5f53bea7b906eddb6df2c27087c))  
feature(UI): Add support for displaying colors in console outputs [#8501](https://github.com/spinnaker/deck/pull/8501) ([e8220118](https://github.com/spinnaker/deck/commit/e82201185fbc4f3feb5693b17a1a4e319f9ca4ee))  



## [0.0.502](https://www.github.com/spinnaker/deck/compare/c026603a590fee1234a79543fd0c8704373ac330...51eb36e35e0e1da2d0a76be9f9aef60525cb554c) (2020-08-20)


### Changes

chore(package): publish core 0.0.502 ecs 0.0.260 google 0.0.18 kubernetes 0.0.47 [#8510](https://github.com/spinnaker/deck/pull/8510) ([51eb36e3](https://github.com/spinnaker/deck/commit/51eb36e35e0e1da2d0a76be9f9aef60525cb554c))  
fix(core/instance): Instance links should render when no cloud providers [#8505](https://github.com/spinnaker/deck/pull/8505) ([78c733c3](https://github.com/spinnaker/deck/commit/78c733c318f757434122840b28612538191ce21f))  
fix(RunJobStage): make RunJobStage restartable [#8492](https://github.com/spinnaker/deck/pull/8492) ([dab7ae79](https://github.com/spinnaker/deck/commit/dab7ae79387031b55f87f0b5051d7e2d29227b68))  
fix(ecs): update z-index for uibModal [#8502](https://github.com/spinnaker/deck/pull/8502) ([9aeb2fb6](https://github.com/spinnaker/deck/commit/9aeb2fb6b037094e69fb698a3c26d3860152dcad))  
fix(core/widgets): Remove CustomDropdown widget ([73805f2f](https://github.com/spinnaker/deck/commit/73805f2f25abad4d0371d70d74897c1d4ce1cd2d))  



## [0.0.501](https://www.github.com/spinnaker/deck/compare/84377118d005024e6c6c0b542940fd958a59a4b6...c026603a590fee1234a79543fd0c8704373ac330) (2020-08-11)


### Changes

chore(package): publish core 0.0.501 kubernetes 0.0.46 [#8489](https://github.com/spinnaker/deck/pull/8489) ([c026603a](https://github.com/spinnaker/deck/commit/c026603a590fee1234a79543fd0c8704373ac330))  
fix(core/managed): supply references when updating constraints [#8485](https://github.com/spinnaker/deck/pull/8485) ([63e31d3c](https://github.com/spinnaker/deck/commit/63e31d3ce744aa19907243d27a61f58ae4aef5f4))  
Replace the word "previous" with "prerequisite" in depends-on constraint text [#8483](https://github.com/spinnaker/deck/pull/8483) ([62109768](https://github.com/spinnaker/deck/commit/621097687e758e38251d5af70e1ef9be23579a57))  



## [0.0.500](https://www.github.com/spinnaker/deck/compare/e8d1ff843841e325dcccd0faf853a58edea92366...84377118d005024e6c6c0b542940fd958a59a4b6) (2020-08-10)


### Changes

chore(package): publish core 0.0.500 docker 0.0.58 google 0.0.17 [#8482](https://github.com/spinnaker/deck/pull/8482) ([84377118](https://github.com/spinnaker/deck/commit/84377118d005024e6c6c0b542940fd958a59a4b6))  
feat(core/managed): new visual treatment for environment badges [#8479](https://github.com/spinnaker/deck/pull/8479) ([83b01d12](https://github.com/spinnaker/deck/commit/83b01d123371c6fb1c62d2c4c306b7883d11d4b0))  
feat(core/managed): new visual iteration for version sidebar, 'decommissioned' cards [#8478](https://github.com/spinnaker/deck/pull/8478) ([3d5d149c](https://github.com/spinnaker/deck/commit/3d5d149c3070fc93b7b6a85ab31f1963af535a49))  
fix(functions): normalizeFunction expects a Promise [#8468](https://github.com/spinnaker/deck/pull/8468) ([7a15f5d7](https://github.com/spinnaker/deck/commit/7a15f5d795f7ee12d7bef470e37379baa6ce08be))  
fix(core/nav): Active nav category for hyperlinks [#8469](https://github.com/spinnaker/deck/pull/8469) ([5048c833](https://github.com/spinnaker/deck/commit/5048c833ecd55f447c2202a6183485536ed5a7ed))  



## [0.0.499](https://www.github.com/spinnaker/deck/compare/aaa2c130cb267d68a0a2aab6424c735d74b2bbfa...e8d1ff843841e325dcccd0faf853a58edea92366) (2020-08-06)


### Changes

chore(package): publish amazon 0.0.262 core 0.0.499 [#8476](https://github.com/spinnaker/deck/pull/8476) ([e8d1ff84](https://github.com/spinnaker/deck/commit/e8d1ff843841e325dcccd0faf853a58edea92366))  
fix(core/application): Handle edits to cloud provider in application config links ([c956d541](https://github.com/spinnaker/deck/commit/c956d541429f107a4d5069a7b63e0b0f26dd3590))  
chore(core): Remove outdated nav components [#8473](https://github.com/spinnaker/deck/pull/8473) ([ff4dc09b](https://github.com/spinnaker/deck/commit/ff4dc09bec11e873f0c9ad21b0341d23437ecc25))  
fix(core/presentation): Do not render the form input even once (render the spel input instead) if spel is present and freeform is enabled [#8474](https://github.com/spinnaker/deck/pull/8474) ([e456347c](https://github.com/spinnaker/deck/commit/e456347ca4ae8aa08165cd469ea73976c23c1638))  



## [0.0.498](https://www.github.com/spinnaker/deck/compare/dcb8ceff74e7013d20a85bd8ac747b453d40bfe5...aaa2c130cb267d68a0a2aab6424c735d74b2bbfa) (2020-08-05)


### Changes

chore(package): publish amazon 0.0.261 core 0.0.498 kubernetes 0.0.45 [#8471](https://github.com/spinnaker/deck/pull/8471) ([aaa2c130](https://github.com/spinnaker/deck/commit/aaa2c130cb267d68a0a2aab6424c735d74b2bbfa))  
fix(core/cluster): Prevent cluster filter from submitting when enter key is pressed ([851d98bd](https://github.com/spinnaker/deck/commit/851d98bd0d2397a8169f2564de6a889057822ed0))  
fix(core/pipeline): Fix dropping of characters while filtering ([a4e6b157](https://github.com/spinnaker/deck/commit/a4e6b157a007b2c3f64a0bad386a0b2dbf553b1e))  
fix(core/help): fix Manual Judgment help text [#8465](https://github.com/spinnaker/deck/pull/8465) ([7d9dcd1f](https://github.com/spinnaker/deck/commit/7d9dcd1ff48d683524e5fdcf8896269bee9c7d08))  
feat(amazon): ASG support for features enabled by launch templates [#8326](https://github.com/spinnaker/deck/pull/8326) ([62e68787](https://github.com/spinnaker/deck/commit/62e6878756def592f1faad0c5db1b6aa62c3e20e))  
fix(helm): Fix helm artifacts not updating name and vesions [#8455](https://github.com/spinnaker/deck/pull/8455) ([92eeb4f3](https://github.com/spinnaker/deck/commit/92eeb4f3d48166bdf657209b63455e47242cea18))  
refactor(kubernetes): remove some unused code [#8458](https://github.com/spinnaker/deck/pull/8458) ([95032582](https://github.com/spinnaker/deck/commit/9503258248de77a0b7193b9f7d30e71d48ea8484))  
fix(managed): Add a fixed width for logo in environments header ([df435ac3](https://github.com/spinnaker/deck/commit/df435ac3580e734eea740ae6707ac62950fce67e))  



## [0.0.497](https://www.github.com/spinnaker/deck/compare/37c7ba506dd5230601681329acbaa133fa903964...dcb8ceff74e7013d20a85bd8ac747b453d40bfe5) (2020-07-31)


### Changes

chore(package): publish amazon 0.0.260 appengine 0.0.14 core 0.0.497 kubernetes 0.0.44 titus 0.0.139 [#8457](https://github.com/spinnaker/deck/pull/8457) ([dcb8ceff](https://github.com/spinnaker/deck/commit/dcb8ceff74e7013d20a85bd8ac747b453d40bfe5))  
fix(core/projects): fix layout for projects view [#8456](https://github.com/spinnaker/deck/pull/8456) ([86308a41](https://github.com/spinnaker/deck/commit/86308a41fe10ab79d3e83b8e43b4f7405089de74))  
fix(core/config): Make app config account dropdowns wider [#8453](https://github.com/spinnaker/deck/pull/8453) ([bad8b297](https://github.com/spinnaker/deck/commit/bad8b297e9f01fb820c49de595e5457eb14436ac))  
refactor(instance): Migrate the instance chiclet tooltip from raw bootstrap to Tooltip component [#8437](https://github.com/spinnaker/deck/pull/8437) ([64cd209f](https://github.com/spinnaker/deck/commit/64cd209f937ab2cd33e4f465f1f53e4c2c5398b2))  
refactor(kubernetes): clean up infrastructure interfaces [#8450](https://github.com/spinnaker/deck/pull/8450) ([30a07842](https://github.com/spinnaker/deck/commit/30a07842244b1871be0ade3c2cfde1e92f92d98f))  



## [0.0.496](https://www.github.com/spinnaker/deck/compare/a7312539380a95562d28028c4a52768d81892725...37c7ba506dd5230601681329acbaa133fa903964) (2020-07-30)


### Changes

chore(package): publish core 0.0.496 [#8448](https://github.com/spinnaker/deck/pull/8448) ([37c7ba50](https://github.com/spinnaker/deck/commit/37c7ba506dd5230601681329acbaa133fa903964))  
fix(core/securityGroup): Clone cached data before mutating it [#8447](https://github.com/spinnaker/deck/pull/8447) ([5a502e74](https://github.com/spinnaker/deck/commit/5a502e7422c4de7331d71360f3cbd296c9d23ae3))  



## [0.0.495](https://www.github.com/spinnaker/deck/compare/e47310cb08dd3eeb2ccbe2dbfc7cde7cad4fa22b...a7312539380a95562d28028c4a52768d81892725) (2020-07-29)


### Changes

chore(package): publish core 0.0.495 kubernetes 0.0.43 [#8443](https://github.com/spinnaker/deck/pull/8443) ([a7312539](https://github.com/spinnaker/deck/commit/a7312539380a95562d28028c4a52768d81892725))  
fix(core/nav): Apply new class to Page App Owner [#8442](https://github.com/spinnaker/deck/pull/8442) ([a7c34415](https://github.com/spinnaker/deck/commit/a7c3441586423b56d2565f99af9ede2a714a8b66))  



## [0.0.494](https://www.github.com/spinnaker/deck/compare/b6e98d1fc71f66f2e2a5c03b6c6133de9c36b98b...e47310cb08dd3eeb2ccbe2dbfc7cde7cad4fa22b) (2020-07-28)


### Changes

chore(package): publish amazon 0.0.259 core 0.0.494 docker 0.0.57 titus 0.0.138 [#8440](https://github.com/spinnaker/deck/pull/8440) ([e47310cb](https://github.com/spinnaker/deck/commit/e47310cb08dd3eeb2ccbe2dbfc7cde7cad4fa22b))  



## [0.0.493](https://www.github.com/spinnaker/deck/compare/adc49ff6e6f06e0def7a4126c09e755bfe33452f...b6e98d1fc71f66f2e2a5c03b6c6133de9c36b98b) (2020-07-28)


### Changes

chore(package): publish amazon 0.0.258 azure 0.0.252 core 0.0.493 titus 0.0.137 [#8439](https://github.com/spinnaker/deck/pull/8439) ([b6e98d1f](https://github.com/spinnaker/deck/commit/b6e98d1fc71f66f2e2a5c03b6c6133de9c36b98b))  
fix(safari): Do not store securityGroups cache in local storage, use memory instead [#8436](https://github.com/spinnaker/deck/pull/8436) ([2dcde667](https://github.com/spinnaker/deck/commit/2dcde667ad58e00f5b043e466dbbdbb2a634ace5))  
feat(notifications): add support for dynamically loaded extension not… [#8432](https://github.com/spinnaker/deck/pull/8432) ([b89e79c8](https://github.com/spinnaker/deck/commit/b89e79c89b25e73ee99cdfb29b4794b7e72b704e))  
fix(core): Rename nav item class to prevent FOUC [#8426](https://github.com/spinnaker/deck/pull/8426) ([98467b29](https://github.com/spinnaker/deck/commit/98467b294e7350bc018fb321fd65efdcd9d9ca32))  
fix(core): CSS for details panel [#8423](https://github.com/spinnaker/deck/pull/8423) ([e9363b4f](https://github.com/spinnaker/deck/commit/e9363b4fa6c479ccdc1f95e49da693b40c79b07f))  
fix(core/pipeline): Pipeline param execution details should handle Ob… [#8431](https://github.com/spinnaker/deck/pull/8431) ([050c7783](https://github.com/spinnaker/deck/commit/050c7783f8b34ca853d7f717ae122a45dbb71ff7))  
fix(core/pipeline): Reformat fixed footer [#8430](https://github.com/spinnaker/deck/pull/8430) ([89c22f80](https://github.com/spinnaker/deck/commit/89c22f8030281078fb744f4e7aa84829b8d8d4b0))  



## [0.0.492](https://www.github.com/spinnaker/deck/compare/ad7adde60a94cafe67cbc5df50dc198fd7c20c24...adc49ff6e6f06e0def7a4126c09e755bfe33452f) (2020-07-24)


### Changes

chore(*): Bump version of core [#8429](https://github.com/spinnaker/deck/pull/8429) ([adc49ff6](https://github.com/spinnaker/deck/commit/adc49ff6e6f06e0def7a4126c09e755bfe33452f))  
fix(environments): Allow environments view to render when there are managed artifacts and/or resources [#8428](https://github.com/spinnaker/deck/pull/8428) ([0c990afa](https://github.com/spinnaker/deck/commit/0c990afa854d8cfde836ea4c36043479a2b039eb))  
feat(core): refactor insight menu from dropdown to buttons [#8424](https://github.com/spinnaker/deck/pull/8424) ([425d2e1a](https://github.com/spinnaker/deck/commit/425d2e1a03473160b6422cbe3a6ffb4cbf3a9491))  
config(pipeline/executions): Increase executions per pipeline options ([d0a0e650](https://github.com/spinnaker/deck/commit/d0a0e6500519bd0abdc01ffee1b29abf3cb8d8cb))  



## [0.0.491](https://www.github.com/spinnaker/deck/compare/5e4c56f8fd9540df3b43ec2d119ea91d9e789724...ad7adde60a94cafe67cbc5df50dc198fd7c20c24) (2020-07-21)


### Changes

chore(package): publish amazon 0.0.257 core 0.0.491 [#8422](https://github.com/spinnaker/deck/pull/8422) ([ad7adde6](https://github.com/spinnaker/deck/commit/ad7adde60a94cafe67cbc5df50dc198fd7c20c24))  
fix(core): Filters should not re-render multiples times after loading [#8414](https://github.com/spinnaker/deck/pull/8414) ([81301c50](https://github.com/spinnaker/deck/commit/81301c509fbf470c7079e05c811b098ce7551faa))  
fix(project dashboard): enabling it so that clusters and pipeline columns don't overlap on each other ([e3767d4d](https://github.com/spinnaker/deck/commit/e3767d4dd45f8f466739a6978cc13180cd22fe25))  
fix(core): Custom banner pushes details panel off screen [#8412](https://github.com/spinnaker/deck/pull/8412) ([c035c4de](https://github.com/spinnaker/deck/commit/c035c4de44ac6a5c0095ac16858b89d8d7b83d26))  
fix(core): Protect rendering when deploymentMonitor is null [#8411](https://github.com/spinnaker/deck/pull/8411) ([19448779](https://github.com/spinnaker/deck/commit/19448779b7c23494b57ac680223b97d9f8a0806d))  



## [0.0.490](https://www.github.com/spinnaker/deck/compare/47357f295e5e4ebae01aee4164f19e358576ce77...5e4c56f8fd9540df3b43ec2d119ea91d9e789724) (2020-07-14)


### Changes

chore(package): publish core 0.0.490 [#8410](https://github.com/spinnaker/deck/pull/8410) ([5e4c56f8](https://github.com/spinnaker/deck/commit/5e4c56f8fd9540df3b43ec2d119ea91d9e789724))  
fix(core): Executions css causing overflow [#8409](https://github.com/spinnaker/deck/pull/8409) ([afddf884](https://github.com/spinnaker/deck/commit/afddf884073719d116f706441e907d2dd12aee98))  
fix(core): Make notfier close button visible [#8407](https://github.com/spinnaker/deck/pull/8407) ([494cb6da](https://github.com/spinnaker/deck/commit/494cb6da1087a919199a12fd004a7ee34be5ea5f))  



## [0.0.489](https://www.github.com/spinnaker/deck/compare/066e8961c3b7562213a2a38382a1418b2a57e8e7...47357f295e5e4ebae01aee4164f19e358576ce77) (2020-07-10)


### Changes

chore(package): publish core 0.0.489 [#8408](https://github.com/spinnaker/deck/pull/8408) ([47357f29](https://github.com/spinnaker/deck/commit/47357f295e5e4ebae01aee4164f19e358576ce77))  
feat(plugins): Add a basic UI for the plugin trigger [#8406](https://github.com/spinnaker/deck/pull/8406) ([f9920d78](https://github.com/spinnaker/deck/commit/f9920d78548df9406828e21d3e232369759e6c6e))  



## [0.0.488](https://www.github.com/spinnaker/deck/compare/ea88ddf8d468fe9922976b64976d08c8481fa4ca...066e8961c3b7562213a2a38382a1418b2a57e8e7) (2020-07-09)


### Changes

chore(package): publish core 0.0.488 [#8405](https://github.com/spinnaker/deck/pull/8405) ([066e8961](https://github.com/spinnaker/deck/commit/066e8961c3b7562213a2a38382a1418b2a57e8e7))  
fix(core): Layout v1 feedback [#8404](https://github.com/spinnaker/deck/pull/8404) ([85786267](https://github.com/spinnaker/deck/commit/85786267a778800b15bf2a536f7d47f84d4f1cdf))  



## [0.0.487](https://www.github.com/spinnaker/deck/compare/5e6cfb7d41be1b7aa2a283224b80ead981626383...ea88ddf8d468fe9922976b64976d08c8481fa4ca) (2020-07-08)


### Changes

chore(package): publish core 0.0.487 [#8402](https://github.com/spinnaker/deck/pull/8402) ([ea88ddf8](https://github.com/spinnaker/deck/commit/ea88ddf8d468fe9922976b64976d08c8481fa4ca))  
fix(core): Flex on pipeline config [#8401](https://github.com/spinnaker/deck/pull/8401) ([48ca72d5](https://github.com/spinnaker/deck/commit/48ca72d56d83388c0e7fc2413b711a2800fbb49e))  
feat(core): Export meme icon [#8400](https://github.com/spinnaker/deck/pull/8400) ([5804da79](https://github.com/spinnaker/deck/commit/5804da798c33df73f46f2491a8b99af16ed35063))  



## [0.0.486](https://www.github.com/spinnaker/deck/compare/935659c889b8089140273c6c90f3a31b4b652bb3...5e6cfb7d41be1b7aa2a283224b80ead981626383) (2020-07-08)


### Changes

chore(package): publish core 0.0.486 google 0.0.16 [#8399](https://github.com/spinnaker/deck/pull/8399) ([5e6cfb7d](https://github.com/spinnaker/deck/commit/5e6cfb7d41be1b7aa2a283224b80ead981626383))  
feat(core): Integrated v1 Layout [#8239](https://github.com/spinnaker/deck/pull/8239) ([58ae5671](https://github.com/spinnaker/deck/commit/58ae56714db4e6655debb45aa17d3986b4e3da1c))  
feat(bakeManifest): can override bakemanifestdetails [#8395](https://github.com/spinnaker/deck/pull/8395) ([dcee89d1](https://github.com/spinnaker/deck/commit/dcee89d16ae4108a929451d5b6ada37ee5bb4782))  
fix(core/presentation): protect against width overflow on Modal + StandardGridTableLayout [#8392](https://github.com/spinnaker/deck/pull/8392) ([25adf5ba](https://github.com/spinnaker/deck/commit/25adf5ba0ca29ba2c107af725ef2661f76b1800b))  
feat(core/pipeline): Add sort pipelines alphabetically button [#8391](https://github.com/spinnaker/deck/pull/8391) ([76a956d3](https://github.com/spinnaker/deck/commit/76a956d3c8b926a202480d1429718a21a68b4976))  



## [0.0.485](https://www.github.com/spinnaker/deck/compare/727819f85d73c307f065336fe608060de438ec7b...935659c889b8089140273c6c90f3a31b4b652bb3) (2020-06-30)


### Changes

chore(package): publish core 0.0.485 ([935659c8](https://github.com/spinnaker/deck/commit/935659c889b8089140273c6c90f3a31b4b652bb3))  
fix(core/managed): Prevent the resource history modal from growing beyond the viewport [#8378](https://github.com/spinnaker/deck/pull/8378) ([8249ef0d](https://github.com/spinnaker/deck/commit/8249ef0d855e7d6a5dc6cb341342da9e775c6fb3))  
feat(core): New App Icon with refresher [#8382](https://github.com/spinnaker/deck/pull/8382) ([162e6c0a](https://github.com/spinnaker/deck/commit/162e6c0af164e6dc43083c93a230e76ba4ba9281))  
Merge branch 'master' into artifact-list-status-bubble ([9ec20032](https://github.com/spinnaker/deck/commit/9ec2003201d11bb7a9e55119fa943e1551c57495))  
Merge branch 'master' into artifact-list-status-bubble ([b9ccec6b](https://github.com/spinnaker/deck/commit/b9ccec6b128bfe00ef33a6149fbcee32de43d357))  
feat(core/managed): Add status bubble in artifact list ([9aeb03d8](https://github.com/spinnaker/deck/commit/9aeb03d87c3c58a18436a9fb852c3dfb24d13ee5))  



## [0.0.484](https://www.github.com/spinnaker/deck/compare/6033b64e28c9d9e7f903f50ea25e503857d57bea...727819f85d73c307f065336fe608060de438ec7b) (2020-06-29)


### Changes

chore(package): publish core 0.0.484 google 0.0.15 kubernetes 0.0.42 ([727819f8](https://github.com/spinnaker/deck/commit/727819f85d73c307f065336fe608060de438ec7b))  
fix(core/rosco): Do not default roscoMode if it has been already set [#8386](https://github.com/spinnaker/deck/pull/8386) ([7a1e8b7f](https://github.com/spinnaker/deck/commit/7a1e8b7f4e65dc1b233b81725b7cb4dae7c94eea))  



## [0.0.483](https://www.github.com/spinnaker/deck/compare/8000d61dc3222b57f56db612609fb5850bcd79d6...6033b64e28c9d9e7f903f50ea25e503857d57bea) (2020-06-29)


### Changes

chore(package): publish core 0.0.483 [#8385](https://github.com/spinnaker/deck/pull/8385) ([6033b64e](https://github.com/spinnaker/deck/commit/6033b64e28c9d9e7f903f50ea25e503857d57bea))  
feat(core/managed): Application pause/resume functionality [#8342](https://github.com/spinnaker/deck/pull/8342) ([996145ad](https://github.com/spinnaker/deck/commit/996145ada0f9caf489e86690b034085706d40acb))  
feat(core): Export nav atoms [#8384](https://github.com/spinnaker/deck/pull/8384) ([638363d7](https://github.com/spinnaker/deck/commit/638363d7c60718f2c3ded7ec459041ffa80249e5))  
feature(core): Setup recoil for state management [#8369](https://github.com/spinnaker/deck/pull/8369) ([eea8d14d](https://github.com/spinnaker/deck/commit/eea8d14d6f619159e27091fb154e05033973084a))  



## [0.0.482](https://www.github.com/spinnaker/deck/compare/db2a3d25e126c6d318bedafbcef8ca058188ac98...8000d61dc3222b57f56db612609fb5850bcd79d6) (2020-06-25)


### Changes

chore(package): publish core 0.0.482 [#8383](https://github.com/spinnaker/deck/pull/8383) ([8000d61d](https://github.com/spinnaker/deck/commit/8000d61dc3222b57f56db612609fb5850bcd79d6))  
feat(core/presentation): nav restructure icons [#8377](https://github.com/spinnaker/deck/pull/8377) ([0daec8e8](https://github.com/spinnaker/deck/commit/0daec8e8337fdfcee4cbb664baf8cb03b39401e2))  
refactor(core): Refactor SpinnakerHeader to functional component [#8375](https://github.com/spinnaker/deck/pull/8375) ([55d6ad1a](https://github.com/spinnaker/deck/commit/55d6ad1a0bd82558796db718acdce730c3a9bbd6))  
fix(core/pipeline): Allow single letter variable names for Cameron Fieber to abuse [#8379](https://github.com/spinnaker/deck/pull/8379) ([a30de437](https://github.com/spinnaker/deck/commit/a30de437f324f3d798e3637d982b4cfb88d8f4cc))  
fix(core): Improve navigation terminology [#8370](https://github.com/spinnaker/deck/pull/8370) ([9c418d54](https://github.com/spinnaker/deck/commit/9c418d5490f8c3a4d98b472550bb0b13156108b1))  
feat(core): Combine General Exception Messages with Custom and Kato Messages [#8330](https://github.com/spinnaker/deck/pull/8330) ([587dcec4](https://github.com/spinnaker/deck/commit/587dcec419c2cae4edacfe4e506c5b9f7ba174ef))  
feat(gce): remove the scale-in control feature flag [#8372](https://github.com/spinnaker/deck/pull/8372) ([aae2270c](https://github.com/spinnaker/deck/commit/aae2270c7fd99cb9324f2a9e9547375eff06fb1e))  
fix(core/application): ensure disable cluster tab waits for task to finish [#8371](https://github.com/spinnaker/deck/pull/8371) ([51433efe](https://github.com/spinnaker/deck/commit/51433efea180c13d5ce6437279ffe024b9a4e890))  
refactor(core): Convert spinnaker container to React [#8366](https://github.com/spinnaker/deck/pull/8366) ([b2b2f034](https://github.com/spinnaker/deck/commit/b2b2f034d37eb4515a67add183c1b05bc9f806e0))  
fix(stages/evaluateVariables): Conditionally display Check Preconditions failures [#8365](https://github.com/spinnaker/deck/pull/8365) ([fcb683a6](https://github.com/spinnaker/deck/commit/fcb683a6a245369a6b98e9b1717c9699fa171e03))  



## [0.0.481](https://www.github.com/spinnaker/deck/compare/638a6036d8a35c60121612c5c033e461626940a8...db2a3d25e126c6d318bedafbcef8ca058188ac98) (2020-06-22)


### Changes

chore(package): publish appengine 0.0.13 core 0.0.481 ecs 0.0.259 kubernetes 0.0.41 [#8364](https://github.com/spinnaker/deck/pull/8364) ([db2a3d25](https://github.com/spinnaker/deck/commit/db2a3d25e126c6d318bedafbcef8ca058188ac98))  
feat(core/page): Add support to add banner to pager view [#8361](https://github.com/spinnaker/deck/pull/8361) ([85953118](https://github.com/spinnaker/deck/commit/85953118d15f6e8f86a13685a9838ce54e205ce9))  
feat(core): add one click details information popover for failed pipeline stage [#8325](https://github.com/spinnaker/deck/pull/8325) ([a2af93fd](https://github.com/spinnaker/deck/commit/a2af93fd961323fbea611a97bf7c0bbbb82c9828))  
feat(storybook): Add storybook support [#8360](https://github.com/spinnaker/deck/pull/8360) ([c72e2243](https://github.com/spinnaker/deck/commit/c72e22434f499e6b835fc533eb9d715590236169))  
feat(deck): Filter pipeline filters with the search filter [#8352](https://github.com/spinnaker/deck/pull/8352) ([1bc7b16b](https://github.com/spinnaker/deck/commit/1bc7b16ba89e4f200853ba10a5b0cddb32223279))  



## [0.0.480](https://www.github.com/spinnaker/deck/compare/6609deeebfec7441cb90bb091b19e71977333afc...638a6036d8a35c60121612c5c033e461626940a8) (2020-06-17)


### Changes

chore(package): publish amazon 0.0.256 core 0.0.480 tencentcloud 0.0.3 titus 0.0.136 [#8355](https://github.com/spinnaker/deck/pull/8355) ([638a6036](https://github.com/spinnaker/deck/commit/638a6036d8a35c60121612c5c033e461626940a8))  
fix(*): Use allowlist and denylist [#8351](https://github.com/spinnaker/deck/pull/8351) ([579560d5](https://github.com/spinnaker/deck/commit/579560d50fe6811c8525e5f287c79e12928d1b46))  
refactor(core): remove What's New module and changelog config [#8353](https://github.com/spinnaker/deck/pull/8353) ([ad13e35d](https://github.com/spinnaker/deck/commit/ad13e35dbe40eea2c096b2f6705ae7252fe63b8a))  
feat(core/presentation): illustration updates [#8350](https://github.com/spinnaker/deck/pull/8350) ([32b8d205](https://github.com/spinnaker/deck/commit/32b8d2054f5c174b6adf0229c6a4e40d16ae70da))  
feat(core/presentation): adds visuals for managed delivery [#8344](https://github.com/spinnaker/deck/pull/8344) ([41ccd7d6](https://github.com/spinnaker/deck/commit/41ccd7d6df97bcac3bf138cdbda75831a25373c7))  
feat(core): link to specific version changelog [#8341](https://github.com/spinnaker/deck/pull/8341) ([74aedcbf](https://github.com/spinnaker/deck/commit/74aedcbfe9522d1f2d16d93754e47d4d455b750e))  
fix(core): Validation for max luxon duration [#8338](https://github.com/spinnaker/deck/pull/8338) ([7971b1c0](https://github.com/spinnaker/deck/commit/7971b1c06209fb062552e76559e297a1f50b3adb))  
fix(core): Filter flashes on non-fetchOnDemand apps [#8329](https://github.com/spinnaker/deck/pull/8329) ([0634363c](https://github.com/spinnaker/deck/commit/0634363c3955ec6646096f457a09e0b7e022e5a5))  
feat(core/managed): support for marking a version 'bad', add illustrations [#8337](https://github.com/spinnaker/deck/pull/8337) ([06e91de4](https://github.com/spinnaker/deck/commit/06e91de46c49397f872c38b9a5bd23a34926d620))  
refactor(core): default SETTINGS.feature.roscoMode to true [#8333](https://github.com/spinnaker/deck/pull/8333) ([359e8e8a](https://github.com/spinnaker/deck/commit/359e8e8ab1f93623254df73296db721cb6c06ac2))  
feat(core/presentation): add <Illustration/>, supporting files [#8336](https://github.com/spinnaker/deck/pull/8336) ([89641f61](https://github.com/spinnaker/deck/commit/89641f6141734ff5bfbd3f7b3be5b578bba8ca64))  
Revert "fix(aws/titus): Server group names should be lower case (#8332)" [#8335](https://github.com/spinnaker/deck/pull/8335) ([a7cd1a5f](https://github.com/spinnaker/deck/commit/a7cd1a5f23eda4e63ab57c6c671fedb3ed252691))  
fix(aws/titus): Server group names should be lower case [#8332](https://github.com/spinnaker/deck/pull/8332) ([c0789da9](https://github.com/spinnaker/deck/commit/c0789da9e0b0f43719684bb1bf9dfbfb917a31bf))  
fix(core): sort application names in ApplicationsPickerInput ([a35c858e](https://github.com/spinnaker/deck/commit/a35c858e5dd33c248f2ef4d078980ab1cd5f64a5))  
fix(core): override styelguide styles on react-select input [#8327](https://github.com/spinnaker/deck/pull/8327) ([105df96e](https://github.com/spinnaker/deck/commit/105df96ea049fd2da8bdb73f4122c29c5a756228))  
feat(core): Add environment views new icon to data source config [#8320](https://github.com/spinnaker/deck/pull/8320) ([a53ff8ff](https://github.com/spinnaker/deck/commit/a53ff8ff6596bce9389953351dcc5a109815c0b7))  
refactor(halyard): clean up halconfig/settings and mark as deprecated [#8312](https://github.com/spinnaker/deck/pull/8312) ([2cc69009](https://github.com/spinnaker/deck/commit/2cc690091189d4e6ffdf8a214f537e69101c781a))  
feat(core/presentation): adds icons for managed delivery route and statuses [#8319](https://github.com/spinnaker/deck/pull/8319) ([6e853405](https://github.com/spinnaker/deck/commit/6e853405cad2be2b045a75f4f0168b7388a9d0ad))  
feat(core/managed): add support for MISSING_DEPENDENCY resource status [#8311](https://github.com/spinnaker/deck/pull/8311) ([2980e34b](https://github.com/spinnaker/deck/commit/2980e34bfa3d1a18e38da09a1b68a54007c79e26))  
fix(core): Details panel height when in multiselect [#8317](https://github.com/spinnaker/deck/pull/8317) ([c6129ef4](https://github.com/spinnaker/deck/commit/c6129ef4bc20347fe0b3aad843b9af0fc4357398))  
fix(core): Remove conditional for master UI View [#8316](https://github.com/spinnaker/deck/pull/8316) ([31e48b52](https://github.com/spinnaker/deck/commit/31e48b523dc9c4ef60cb55475d4f9e70d7e0a591))  



## [0.0.479](https://www.github.com/spinnaker/deck/compare/94f977b83063edd6b0fa215cf3e3b1c9c899a1ce...6609deeebfec7441cb90bb091b19e71977333afc) (2020-05-29)


### Changes

chore(package): publish amazon 0.0.255 core 0.0.479 [#8315](https://github.com/spinnaker/deck/pull/8315) ([6609deee](https://github.com/spinnaker/deck/commit/6609deeebfec7441cb90bb091b19e71977333afc))  
test(core/presentation): Do not index into strings for FormikFormField errors If a field's path is "foo.bar[0]" and errors.foo.bar is a "error", the field "foo.bar[0]" should not have an error. ([6f83f300](https://github.com/spinnaker/deck/commit/6f83f300bf1d110b1b1535f8181a4cf32a2b0280))  
test(core/presentation): refactor FormikFormField.spec.tsx ([66d5b380](https://github.com/spinnaker/deck/commit/66d5b380f06abbc99d7250183cbb6d275a23b1cc))  
fix(core): Export filter search component [#8313](https://github.com/spinnaker/deck/pull/8313) ([8f9d4f4c](https://github.com/spinnaker/deck/commit/8f9d4f4c176832ff766b8ea6678cd2f2082c4d00))  



## [0.0.478](https://www.github.com/spinnaker/deck/compare/c881a42c3516e4b69594f8055df2d3d7995292af...94f977b83063edd6b0fa215cf3e3b1c9c899a1ce) (2020-05-26)


### Changes

chore(package): publish core 0.0.478 tencentcloud 0.0.2 [#8308](https://github.com/spinnaker/deck/pull/8308) ([94f977b8](https://github.com/spinnaker/deck/commit/94f977b83063edd6b0fa215cf3e3b1c9c899a1ce))  
feat(core/managed): make Environments tab visible on config screen [#8307](https://github.com/spinnaker/deck/pull/8307) ([a3437731](https://github.com/spinnaker/deck/commit/a34377313d0eee37ad0e856eeca96377ad413aad))  
fix(pipelines): prevent pipelineConfig pollution caused by retrying a trigger unsuccessfully. [#8287](https://github.com/spinnaker/deck/pull/8287) ([3f2afb7f](https://github.com/spinnaker/deck/commit/3f2afb7f93d86d1377d5ebae1f9132253265d7a7))  
chore(pluginsdk): do not share tslib [#8305](https://github.com/spinnaker/deck/pull/8305) ([f4d5ea85](https://github.com/spinnaker/deck/commit/f4d5ea85ea027cb25fa31dd3951b4a1716576d7b))  
feat(core/presentation): adds form field icons [#8297](https://github.com/spinnaker/deck/pull/8297) ([f16fb2a1](https://github.com/spinnaker/deck/commit/f16fb2a1c93e03c102b095d624d29f1e0f6acfcf))  
feat(core/presentation): adds managed delivery illustrations [#8299](https://github.com/spinnaker/deck/pull/8299) ([12b480f1](https://github.com/spinnaker/deck/commit/12b480f122e6341f338caf01adca0a2482fc953f))  
feat(core/managed): switch 'Artifacts' header to 'Versions' [#8300](https://github.com/spinnaker/deck/pull/8300) ([25566e57](https://github.com/spinnaker/deck/commit/25566e579260134b5ed454939e8e7fffd83dc933))  
refactor(core): Convert SecurityGroupFilters to react [#8281](https://github.com/spinnaker/deck/pull/8281) ([a6f818bf](https://github.com/spinnaker/deck/commit/a6f818bf138b6936d6160b386a92ab2ec963a33b))  



## [0.0.477](https://www.github.com/spinnaker/deck/compare/80e5d2a263da503aa23ed63c6660b7dad479ff42...c881a42c3516e4b69594f8055df2d3d7995292af) (2020-05-18)


### Changes

chore(package): publish amazon 0.0.254 appengine 0.0.12 core 0.0.477 ecs 0.0.258 google 0.0.14 kubernetes 0.0.40 [#8294](https://github.com/spinnaker/deck/pull/8294) ([c881a42c](https://github.com/spinnaker/deck/commit/c881a42c3516e4b69594f8055df2d3d7995292af))  
feat(core/presentation): adds menu icons [#8285](https://github.com/spinnaker/deck/pull/8285) ([48a2fba6](https://github.com/spinnaker/deck/commit/48a2fba634cfd7970026d31fd3258cca6e6f8f61))  
fix(core/presentation): clear out prior 'touched' values in useSaveRestoreMutuallyExclusiveField [#8290](https://github.com/spinnaker/deck/pull/8290) ([495cf7f8](https://github.com/spinnaker/deck/commit/495cf7f8180b079420bed2e1b4594ddba705d26a))  
feat(core/managed): add version unpin flow to Environments [#8278](https://github.com/spinnaker/deck/pull/8278) ([b94a6fcc](https://github.com/spinnaker/deck/commit/b94a6fccf81f6b2d06c264e897bd9a93ba5f73c3))  
fix(core/presentation): Memoize defaultResult in useData.hook.ts [#8289](https://github.com/spinnaker/deck/pull/8289) ([fceeaa12](https://github.com/spinnaker/deck/commit/fceeaa12c6c5371ce2e02f1394d010df62880514))  
refactor(core/presentation): migrate useData and useLatestPromise to use PromiseLike, not IPromise [#8279](https://github.com/spinnaker/deck/pull/8279) ([d7559662](https://github.com/spinnaker/deck/commit/d7559662bd7bd698ccb1648fb2a141976b4d6c56))  
fix(core): UIView div wrapper [#8286](https://github.com/spinnaker/deck/pull/8286) ([f63377f6](https://github.com/spinnaker/deck/commit/f63377f64d2899a221aa67754c5c0e9c680fba77))  
feat(core/presentation): Give validators.ts function names for easier debugging [#8282](https://github.com/spinnaker/deck/pull/8282) ([8a441a65](https://github.com/spinnaker/deck/commit/8a441a65c24a6e0916b745a68e628d251575ebb0))  
fix(bake): adding skip region detection checkbox [#8277](https://github.com/spinnaker/deck/pull/8277) ([200198fe](https://github.com/spinnaker/deck/commit/200198fec1928ba5867949a31cde18e1ea3e1e82))  
test(core/presentation): add basic tests for ApplicationsPickerInput.tsx ([cc7001d9](https://github.com/spinnaker/deck/commit/cc7001d9739a5fd42c5a0f3b2f83360e904e3abb))  
refactor(core/projects): Remove FormikApplicationPicker in favor of ApplicationPickerInput ([f1b0caf5](https://github.com/spinnaker/deck/commit/f1b0caf5b69ced61ae547dc6a24bb7e333fe98fd))  
feat(core/widgets): extract an ApplicationPickerInput widget ([d718066a](https://github.com/spinnaker/deck/commit/d718066aeebbc704886cd02aee33fbcea4322507))  
refactor(core): Convert Cluster filters to react [#8272](https://github.com/spinnaker/deck/pull/8272) ([f3cc20f5](https://github.com/spinnaker/deck/commit/f3cc20f5354001f1a1f6232678ebf1e0c7bc4994))  
refactor(core): Convert InsightLayout to React [#8269](https://github.com/spinnaker/deck/pull/8269) ([02bb7012](https://github.com/spinnaker/deck/commit/02bb70123b88640e70f931cd4b5c94b442e05639))  
refactor(core): legacy artifacts cleanup [#8273](https://github.com/spinnaker/deck/pull/8273) ([f4d41551](https://github.com/spinnaker/deck/commit/f4d415518dd553263ca63f4641ff19facef79464))  
fix(core): Reduce FilterCollapse z-index [#8271](https://github.com/spinnaker/deck/pull/8271) ([6560c1a7](https://github.com/spinnaker/deck/commit/6560c1a73bdfc81c6a178f97b08440404c9a4af7))  
fix(managedDelivery): Documentation link refers to non-existent page on spinnaker.io ([614cbc71](https://github.com/spinnaker/deck/commit/614cbc713fcfa013434a944d31700778092da4e4))  



## [0.0.476](https://www.github.com/spinnaker/deck/compare/9113047e6647a0bd8bafd7cdb20125fa91492cb5...80e5d2a263da503aa23ed63c6660b7dad479ff42) (2020-05-12)


### Changes

chore(package): publish core 0.0.476 [#8275](https://github.com/spinnaker/deck/pull/8275) ([80e5d2a2](https://github.com/spinnaker/deck/commit/80e5d2a263da503aa23ed63c6660b7dad479ff42))  
feat(core): Export FilterSection [#8274](https://github.com/spinnaker/deck/pull/8274) ([6159bf34](https://github.com/spinnaker/deck/commit/6159bf342c7b64df84b1acb1a850723b4756ed19))  



## [0.0.475](https://www.github.com/spinnaker/deck/compare/840685a942865ceb87c71f69a1a2de3101fd2201...9113047e6647a0bd8bafd7cdb20125fa91492cb5) (2020-05-11)


### Changes

chore(package): publish amazon 0.0.253 core 0.0.475 kubernetes 0.0.39 [#8270](https://github.com/spinnaker/deck/pull/8270) ([9113047e](https://github.com/spinnaker/deck/commit/9113047e6647a0bd8bafd7cdb20125fa91492cb5))  
feat(amazon): Filter SG's that are in exclusion list [#8266](https://github.com/spinnaker/deck/pull/8266) ([703fceed](https://github.com/spinnaker/deck/commit/703fceedf8e0cb2a6bed3396decbf7b110edc690))  
refactor(core): remove `skin` and `providerVersion` [#8263](https://github.com/spinnaker/deck/pull/8263) ([72af6cc4](https://github.com/spinnaker/deck/commit/72af6cc495cf9f82634f62631bba1f7b6e148eb7))  
fix(core/managed): prevent expand/collapse from selecting text [#8262](https://github.com/spinnaker/deck/pull/8262) ([051f8c5c](https://github.com/spinnaker/deck/commit/051f8c5cd547a83485487b6305a6adfd2a934d30))  
fix(core/notification): stop infinite-looping on custom notification input [#8267](https://github.com/spinnaker/deck/pull/8267) ([a3abc33f](https://github.com/spinnaker/deck/commit/a3abc33fd3cea9763147d0c38fe3b8509630323f))  
fix(core/presentation): remove unnecessary console.error that snuck in from a debugging session [#8264](https://github.com/spinnaker/deck/pull/8264) ([31dc01ef](https://github.com/spinnaker/deck/commit/31dc01ef636c547ded082c68a5c5a0051a586a0a))  
fix(core/presentation): Export the new save/restore formik fields hook [#8259](https://github.com/spinnaker/deck/pull/8259) ([11b524de](https://github.com/spinnaker/deck/commit/11b524debf8102eac6e0f7eefdaec8db620cc6b0))  
feat(core/utils): Add a function to filter deeply nested leaf nodes from an object [#8254](https://github.com/spinnaker/deck/pull/8254) ([e75d2f20](https://github.com/spinnaker/deck/commit/e75d2f20f48eec5a8cda8d969ed2873afa6636cd))  
test(core/presentation): Test SpinFormik touches initial values [#8248](https://github.com/spinnaker/deck/pull/8248) ([0b82d9d2](https://github.com/spinnaker/deck/commit/0b82d9d288499e837ebb45025cfd1290ebd0b474))  



## [0.0.474](https://www.github.com/spinnaker/deck/compare/55855252f72d540ddef6ca6c7e2f2222a89ef376...840685a942865ceb87c71f69a1a2de3101fd2201) (2020-05-06)


### Changes

chore(package): publish core 0.0.474 [#8253](https://github.com/spinnaker/deck/pull/8253) ([840685a9](https://github.com/spinnaker/deck/commit/840685a942865ceb87c71f69a1a2de3101fd2201))  
fix(core/presentation): Update useSaveRestore hook to store data for each key in a separate object ([b75d3711](https://github.com/spinnaker/deck/commit/b75d3711ef6449babfbc1b1c6483c580f45308b1))  
feat(core/presentation): Add a formik hook to save/restore mutually exclusive form fields when toggling between two or more modes in a form ([3156dff5](https://github.com/spinnaker/deck/commit/3156dff5b7401198bfcf1f40a3cb63c87764bb44))  
feat(core/presentation): Add a 'defaultValue' prop to SelectInput and RadioButtonInput to apply a default value ([49402528](https://github.com/spinnaker/deck/commit/49402528a8c0b6a71a3483faeb880a6c9b79d520))  
fix(core/managed): fix vertical alignment on deploy pills [#8250](https://github.com/spinnaker/deck/pull/8250) ([d0f8c279](https://github.com/spinnaker/deck/commit/d0f8c279e6c0ef8359c359da07056bf20643b4ad))  



## [0.0.473](https://www.github.com/spinnaker/deck/compare/f227aa8ec00fffe63e39abc75b9c504180804623...55855252f72d540ddef6ca6c7e2f2222a89ef376) (2020-05-04)


### Changes

chore(package): publish amazon 0.0.252 core 0.0.473 ecs 0.0.257 kubernetes 0.0.38 [#8247](https://github.com/spinnaker/deck/pull/8247) ([55855252](https://github.com/spinnaker/deck/commit/55855252f72d540ddef6ca6c7e2f2222a89ef376))  
fix(core/managed): mitigate layout jank on ObjectRow + EnvironmentRow [#8246](https://github.com/spinnaker/deck/pull/8246) ([7c182fae](https://github.com/spinnaker/deck/commit/7c182fae4cceada2a05a4c7e9d6869c552770ed7))  
feat(core/presentation): Add two new Validators to test for valid Json and valid XML strings [#8236](https://github.com/spinnaker/deck/pull/8236) ([e7f59904](https://github.com/spinnaker/deck/commit/e7f59904f44b50a74c04d3ded3d845c85b1d95ec))  
feat(core/managed): Add popover to resource status bubble [#8240](https://github.com/spinnaker/deck/pull/8240) ([3facda66](https://github.com/spinnaker/deck/commit/3facda6626ae55a48177a3c7b7685e68ac779f8a))  
feat(core/managed): add support for pinning artifacts to environments [#8238](https://github.com/spinnaker/deck/pull/8238) ([60c5df1a](https://github.com/spinnaker/deck/commit/60c5df1ac7788b687ba5c710c7a6e3d4922741be))  
feat(core/managed): hide artifact name when there's only one [#8243](https://github.com/spinnaker/deck/pull/8243) ([fe2a0eac](https://github.com/spinnaker/deck/commit/fe2a0eac9168b4ea17f4bf86c46bd8df702740af))  
feat(core/presentation): add spCIPullRequestClosed icon [#8245](https://github.com/spinnaker/deck/pull/8245) ([a1980c38](https://github.com/spinnaker/deck/commit/a1980c38500978463829e5b365be15b299664d6b))  
fix(core/presentation): properly handle vertical grow/shrink on Modal [#8242](https://github.com/spinnaker/deck/pull/8242) ([27dda816](https://github.com/spinnaker/deck/commit/27dda816602fa795544b49910897c72237320163))  
fix(core/managed): use smaller icon size for expand/collapse arrows [#8241](https://github.com/spinnaker/deck/pull/8241) ([2cee69ac](https://github.com/spinnaker/deck/commit/2cee69ac8d4dc08f6a7c21b7162cd79f84e9138b))  
feat(core/managed): Add StatusBubble to denote resource status [#8214](https://github.com/spinnaker/deck/pull/8214) ([c6c3fa81](https://github.com/spinnaker/deck/commit/c6c3fa810eb700b463da4785ce9f09b55470061f))  
feat(core/presentation): icon revisions [#8230](https://github.com/spinnaker/deck/pull/8230) ([04f60bd5](https://github.com/spinnaker/deck/commit/04f60bd5e75372aa20c928604195eb42edccb23a))  
refactor(core/managed): use reference for identifying artifacts, not name + type [#8231](https://github.com/spinnaker/deck/pull/8231) ([a9784b5b](https://github.com/spinnaker/deck/commit/a9784b5b0d4bb14e8ffe468b19de02c54e9359ee))  
feat(core/presentation): allow custom max widths on Modal, bugfixes [#8237](https://github.com/spinnaker/deck/pull/8237) ([bc6c1bf3](https://github.com/spinnaker/deck/commit/bc6c1bf383e97a0a4dfeaff720e394c797961397))  
feat(core/presentation): change <textarea class="code"> font size to default [#8235](https://github.com/spinnaker/deck/pull/8235) ([d030ac28](https://github.com/spinnaker/deck/commit/d030ac28d664290f2ab7e40959d89ed499605548))  
feat(core/nav): Compose vertical nav bar [#8203](https://github.com/spinnaker/deck/pull/8203) ([f2096f38](https://github.com/spinnaker/deck/commit/f2096f386d4363ee431e132e7c38c32c447e3941))  
fix(core/presentation): put TetheredSelect menu on critical layer [#8234](https://github.com/spinnaker/deck/pull/8234) ([e3fb2765](https://github.com/spinnaker/deck/commit/e3fb27657a154cb254bc0efb6abd990d47664f9b))  
fix(core/help): make markdown examples code blocks to fix rendering [#8233](https://github.com/spinnaker/deck/pull/8233) ([f6c76000](https://github.com/spinnaker/deck/commit/f6c76000196082cff8bd180dd4dc7f9e4a4bd9e5))  
feat(core/presentation): add 'pin' icon [#8232](https://github.com/spinnaker/deck/pull/8232) ([a019b892](https://github.com/spinnaker/deck/commit/a019b892737d09cc7ca33ce80c68031ca0080755))  
fix(core/presentation): Tweak the RadioButtonInput styles [#8228](https://github.com/spinnaker/deck/pull/8228) ([0f17cff9](https://github.com/spinnaker/deck/commit/0f17cff990e5d132bd9a363189e6e2df72583e28))  
feat(core): remove kubernetes v1 module  [#8226](https://github.com/spinnaker/deck/pull/8226) ([32c8cc8e](https://github.com/spinnaker/deck/commit/32c8cc8e2c2ad07066ff1c1345fb0d07f213f55c))  
feat(core/presentation): Switch from isFirstRender hook to mountStatus for more versatility. [#8223](https://github.com/spinnaker/deck/pull/8223) ([8478d162](https://github.com/spinnaker/deck/commit/8478d16201d6e4932d76f8de3bf80b1dd7475bf9))  
feat(core/filters): Update filter section layout [#8177](https://github.com/spinnaker/deck/pull/8177) ([46a3d14a](https://github.com/spinnaker/deck/commit/46a3d14a13f408117738625c03022fcc44bdf098))  
fix(core/nav): fix NavSection react key [#8218](https://github.com/spinnaker/deck/pull/8218) ([daf7200e](https://github.com/spinnaker/deck/commit/daf7200ecba79044fa0ab0e00966cbf8402d2f01))  
feat(core/filters): New search header for filter section [#8169](https://github.com/spinnaker/deck/pull/8169) ([68cb3847](https://github.com/spinnaker/deck/commit/68cb384776ba22c5f3268c43fbad9f43a2be978a))  
fix(UI): add stage summaries to hydrated graphs [#8202](https://github.com/spinnaker/deck/pull/8202) ([3803dce2](https://github.com/spinnaker/deck/commit/3803dce2557c506cff5bfb842bf7f7ed659f9fbb))  
fix(stage): fix import delivery config doc link [#8209](https://github.com/spinnaker/deck/pull/8209) ([a9a5082d](https://github.com/spinnaker/deck/commit/a9a5082da883fd3d5f7c87a6381525e9031d2479))  
feat(core/nav): Add sections for vertical nav [#8162](https://github.com/spinnaker/deck/pull/8162) ([68335595](https://github.com/spinnaker/deck/commit/6833559501186ae3c5026061a4cc38bb1b33bc38))  
feat(core/nav): Components for new navigation categories [#8150](https://github.com/spinnaker/deck/pull/8150) ([21488bc8](https://github.com/spinnaker/deck/commit/21488bc81be4d3dded2e768452f43d0871f87cd9))  
fix(core): TaskMonitor should notify after clearing errors [#8197](https://github.com/spinnaker/deck/pull/8197) ([02c27b28](https://github.com/spinnaker/deck/commit/02c27b28ee4ccd6ff877b2022b8da54cc14ec7bd))  
feat(plugins): expose more shared libraries: uirouter, rxjs, prop-types, tslib ([482e3def](https://github.com/spinnaker/deck/commit/482e3def179a856d650aeab4451869f57268eb9d))  
docs(plugins): add a one-line description to each extension point ([d8483dfa](https://github.com/spinnaker/deck/commit/d8483dfa07310c410e03532d857648769d354f52))  
docs(plugins): remove reference to deprecated devUrl property ([36739323](https://github.com/spinnaker/deck/commit/367393233f1ac4626a3292b900958cfef02764ef))  
feat(plugins): Pass the plugin object to the initialize method ([dbfd2b86](https://github.com/spinnaker/deck/commit/dbfd2b867526b413f92d7ac76f570045c805c176))  
feat(plugins): Make plugin.initialize() async ([828595ec](https://github.com/spinnaker/deck/commit/828595ec5cf22fb28ce833b1d7602122157dd58c))  
feat(plugins): Add a search extension point ([39fbad24](https://github.com/spinnaker/deck/commit/39fbad2495b1924bdc54e17886deca5362d0ede2))  
feat(plugins): Add a help extension point ([7953220f](https://github.com/spinnaker/deck/commit/7953220f12f65bbff46e57dde106a2ac66b9407b))  
refactor(plugins): Extract registration of extensions to its own file. - add tests. - move the IDeckPlugin interface - remove IPluginManifest interface ([9c27a93a](https://github.com/spinnaker/deck/commit/9c27a93aa0132532705dafb656febb6f5146b38e))  



## [0.0.472](https://www.github.com/spinnaker/deck/compare/68dad3a0f7a71de153e0ad180356808d90c6409f...f227aa8ec00fffe63e39abc75b9c504180804623) (2020-04-21)


### Changes

chore(package): publish amazon 0.0.251 appengine 0.0.11 azure 0.0.251 cloudfoundry 0.0.96 core 0.0.472 docker 0.0.56 ecs 0.0.256 google 0.0.13 huaweicloud 0.0.4 kubernetes 0.0.37 oracle 0.0.6 titus 0.0.135 [#8196](https://github.com/spinnaker/deck/pull/8196) ([f227aa8e](https://github.com/spinnaker/deck/commit/f227aa8ec00fffe63e39abc75b9c504180804623))  
fix(core/managed): make snake case enum values link properly to dashed URLs [#8195](https://github.com/spinnaker/deck/pull/8195) ([0997149c](https://github.com/spinnaker/deck/commit/0997149c99e03f13f6c9b3f16bed8aa3a25cbbee))  
feat(core): replace stage-specific feature flags with hiddenStages setting [#8175](https://github.com/spinnaker/deck/pull/8175) ([de53ab73](https://github.com/spinnaker/deck/commit/de53ab738fc1cac863a1df44751a11f0742d1089))  
feat(core/managed): CURRENTLY_UNRESOLVABLE status, tweak unresolvable event [#8189](https://github.com/spinnaker/deck/pull/8189) ([c8c66de6](https://github.com/spinnaker/deck/commit/c8c66de6904e83b1d10c2790836f02b60606b9a3))  
feat(core): enable standard artifacts UI by default [#8184](https://github.com/spinnaker/deck/pull/8184) ([e7b3b352](https://github.com/spinnaker/deck/commit/e7b3b352c445f8d0ff4c824a2fa9812c77d64e6e))  
feat(plugins): Consolidate typescript config (partially).  Do not strip comments. [#8180](https://github.com/spinnaker/deck/pull/8180) ([4434d99c](https://github.com/spinnaker/deck/commit/4434d99c4b61704c5e53f356ff9f3b31d715e593))  
fix(artifacts): dedupe custom artifact types [#8179](https://github.com/spinnaker/deck/pull/8179) ([59ba88e2](https://github.com/spinnaker/deck/commit/59ba88e285feebc3c445ad71ae77b15448b771ef))  
fix(kubernetes): do not create artificial stage diff in Bake (Manifest) stage [#8176](https://github.com/spinnaker/deck/pull/8176) ([be314f2d](https://github.com/spinnaker/deck/commit/be314f2da986325a65f3c00f30447a60534527bf))  
feat(core): Filter out providers that don't support creating load bal… [#8170](https://github.com/spinnaker/deck/pull/8170) ([a8e5a899](https://github.com/spinnaker/deck/commit/a8e5a899a4d31fa093d7b2412a6ae210dfd7208e))  
fix(pubsub): prevent NPE when retrying a pipeline execution with [#8172](https://github.com/spinnaker/deck/pull/8172) ([5d952716](https://github.com/spinnaker/deck/commit/5d952716b1bc21f3b55733f8172f15fa15c5b68c))  



## [0.0.471](https://www.github.com/spinnaker/deck/compare/87938bdcf53ef098fc9d6fcae0139322c5b2371a...68dad3a0f7a71de153e0ad180356808d90c6409f) (2020-04-14)


### Changes

chore(package): publish core 0.0.471 docker 0.0.55 ecs 0.0.255 [#8168](https://github.com/spinnaker/deck/pull/8168) ([68dad3a0](https://github.com/spinnaker/deck/commit/68dad3a0f7a71de153e0ad180356808d90c6409f))  
feat(core/managed): use StatusCard for artifact cards, add enter transition [#8158](https://github.com/spinnaker/deck/pull/8158) ([211ac431](https://github.com/spinnaker/deck/commit/211ac431c4b4cbb7f12c63458e812ed020e93482))  
feat(core): make artifacts account selector searchable by name [#8165](https://github.com/spinnaker/deck/pull/8165) ([5ea59806](https://github.com/spinnaker/deck/commit/5ea59806f2d30bb29f8df260d5411e2c260b62bd))  
feat(core/managed): add scrolling grid layout, detail pane transition [#8159](https://github.com/spinnaker/deck/pull/8159) ([d99760ad](https://github.com/spinnaker/deck/commit/d99760ad39ed49e1a41a7eba0bc57d17665996f1))  
fix(core): Do not add security group multiple times [#8163](https://github.com/spinnaker/deck/pull/8163) ([4d3bc321](https://github.com/spinnaker/deck/commit/4d3bc3216335e9921a5676fb1c62af08f696f4d1))  
feat(core/managed): add StatusBubble, StatusCard components [#8157](https://github.com/spinnaker/deck/pull/8157) ([126065cf](https://github.com/spinnaker/deck/commit/126065cf69b04af780e32b290aea3cb5c35dc914))  
fix(core/pipelines): typo with PIPELINE_CONFIG_ACTIONS [#8161](https://github.com/spinnaker/deck/pull/8161) ([97359100](https://github.com/spinnaker/deck/commit/9735910047692f298225650f683b026a12c0f75b))  
fix(artifacts): replace legacy-only Find Artifacts UI with Produces Artifacts [#8160](https://github.com/spinnaker/deck/pull/8160) ([df9978fe](https://github.com/spinnaker/deck/commit/df9978fe4d307ede5382088831f777ec7e842275))  
refactor(core): custom -> styleguide colors for icons, artifact status bars [#8156](https://github.com/spinnaker/deck/pull/8156) ([f7be1d42](https://github.com/spinnaker/deck/commit/f7be1d42edfc31281857ad9d858ea019731ea327))  
fix(kubernetes): fix outstanding Bake (Manifest) bug and remove Kustomize feature flag [#8155](https://github.com/spinnaker/deck/pull/8155) ([3c0d3a7f](https://github.com/spinnaker/deck/commit/3c0d3a7fe710d19d8b117a8d6dc04b441824ef0d))  
fix(managed-delivery): Minor cosmetic fixes in import delivery config stage [#8154](https://github.com/spinnaker/deck/pull/8154) ([8da5e515](https://github.com/spinnaker/deck/commit/8da5e51562044236b9c9ed78f932dc7fc8b24cde))  
fix(core/presentation): Avoid excessive RadioButton remounts by moving the component outside the render function [#8153](https://github.com/spinnaker/deck/pull/8153) ([86ee943c](https://github.com/spinnaker/deck/commit/86ee943c76201e2abbec0790ea9dfe92d0cff3bf))  
feat(core/managed): new icons for pending, approved, skipped states [#8149](https://github.com/spinnaker/deck/pull/8149) ([53536950](https://github.com/spinnaker/deck/commit/535369508fe6c54ceb73cacc61dde171e91fdfbb))  
fix(core): Optimize merging of serverGroups from refresh [#8143](https://github.com/spinnaker/deck/pull/8143) ([9dc71ccd](https://github.com/spinnaker/deck/commit/9dc71ccddf5fa0516633856dc2e28bfc0553a4ca))  
feat(core/managed): use backend-supplied build + git metadata [#8144](https://github.com/spinnaker/deck/pull/8144) ([9db0ef63](https://github.com/spinnaker/deck/commit/9db0ef63071b110bd98a26f6f31e5108f6c9f15c))  
feat(core/managed): add proper support for skipped, approved artifact states [#8142](https://github.com/spinnaker/deck/pull/8142) ([b7709ab9](https://github.com/spinnaker/deck/commit/b7709ab97e5ac4da777993f252d1c4084bb3ac52))  
fix(core/managed): add stack + detail filtering on resource links [#8141](https://github.com/spinnaker/deck/pull/8141) ([9b05a07d](https://github.com/spinnaker/deck/commit/9b05a07d267c0cef3371ca3c360720516fa44869))  
fix(core): Temporarily allow disabling refresh cycle [#8136](https://github.com/spinnaker/deck/pull/8136) ([ba414433](https://github.com/spinnaker/deck/commit/ba414433e18f3d850b2d4479f8e6cf4f123c18da))  
refactor(core): simplify trigger state management [#8111](https://github.com/spinnaker/deck/pull/8111) ([7ae78fe7](https://github.com/spinnaker/deck/commit/7ae78fe7049d1e46cfefa2377f3698e1fbcf611c))  



## [0.0.470](https://www.github.com/spinnaker/deck/compare/4b22c7937ffef8e25aecbfaf05a5af3aaeb7b603...87938bdcf53ef098fc9d6fcae0139322c5b2371a) (2020-04-06)


### Changes

chore(package): publish amazon 0.0.250 core 0.0.470 titus 0.0.134 [#8135](https://github.com/spinnaker/deck/pull/8135) ([87938bdc](https://github.com/spinnaker/deck/commit/87938bdcf53ef098fc9d6fcae0139322c5b2371a))  
feat(core/managed): add stateless constraint support [#8131](https://github.com/spinnaker/deck/pull/8131) ([0ee1888e](https://github.com/spinnaker/deck/commit/0ee1888e449b078bc1a0595652e040f54bef0afe))  
feat(core/managed): use child route for artifact details on environments [#8132](https://github.com/spinnaker/deck/pull/8132) ([ab5d1204](https://github.com/spinnaker/deck/commit/ab5d12046558cadd3e55fb3fc0ae68b19412ee95))  
feat(core/managed): Show red background for environment name [#8125](https://github.com/spinnaker/deck/pull/8125) ([3078d8de](https://github.com/spinnaker/deck/commit/3078d8deee12c4f562d2b25a8d721d0f3f7fe54c))  
feat(core/managed): Show deploying state in environments view [#8123](https://github.com/spinnaker/deck/pull/8123) ([56e20a83](https://github.com/spinnaker/deck/commit/56e20a836905d7a49e8faebfd338e340b722a7c5))  
feat(core/presentation): add securityGroup icon [#8133](https://github.com/spinnaker/deck/pull/8133) ([73562e3a](https://github.com/spinnaker/deck/commit/73562e3a05b6324257764a873b71562056cc9dfe))  
fix(core): bump up width on numerical form fields [#8116](https://github.com/spinnaker/deck/pull/8116) ([8dd9fd61](https://github.com/spinnaker/deck/commit/8dd9fd61976e0d1dbe2c39ff7f93a1196d6a4274))  
feat(core/managed): add support for overriding constraints via Environments [#8124](https://github.com/spinnaker/deck/pull/8124) ([a1805a3d](https://github.com/spinnaker/deck/commit/a1805a3d1792757472e330a9023b17ce038d98fd))  
fix(core): default and validate Bake (Manifest) produced artifacts [#8121](https://github.com/spinnaker/deck/pull/8121) ([076cef02](https://github.com/spinnaker/deck/commit/076cef02d2af7ec5a73af93013def1ea00002125))  
feat(serviceAccounts): supply application when populating run as user dropdown [#8126](https://github.com/spinnaker/deck/pull/8126) ([97b0a11b](https://github.com/spinnaker/deck/commit/97b0a11b865b69e5e964b4e43efa31484369f908))  
fix(travis): Refresh travis build information on each render [#8114](https://github.com/spinnaker/deck/pull/8114) ([37292d89](https://github.com/spinnaker/deck/commit/37292d89b1e02f8b5e7691b88db1e6b70e60bef6))  
fix(deck): Set unchecked behavior [#8117](https://github.com/spinnaker/deck/pull/8117) ([dc4c4c38](https://github.com/spinnaker/deck/commit/dc4c4c387439d201ee0525266f49dc0557d45afc))  
feat(core/managed): Adding loading, error, and zero states. [#8119](https://github.com/spinnaker/deck/pull/8119) ([32b1558b](https://github.com/spinnaker/deck/commit/32b1558bbbca8bcf652994501af5d1dd7e7e7dfb))  



## [0.0.469](https://www.github.com/spinnaker/deck/compare/93c2f284dd421b0e8b8a686fa7e61631b5139b60...4b22c7937ffef8e25aecbfaf05a5af3aaeb7b603) (2020-04-01)


### Changes

chore(core): Bump to core@0.0.469 [#8122](https://github.com/spinnaker/deck/pull/8122) ([4b22c793](https://github.com/spinnaker/deck/commit/4b22c7937ffef8e25aecbfaf05a5af3aaeb7b603))  
feat(core/presentation): Expose status in useDataSource hook [#8118](https://github.com/spinnaker/deck/pull/8118) ([eac4cf5a](https://github.com/spinnaker/deck/commit/eac4cf5a64962cbe2d02bb0098e80acc37dd03f9))  
feat(core): Add svg name to dataSource [#8120](https://github.com/spinnaker/deck/pull/8120) ([941cd605](https://github.com/spinnaker/deck/commit/941cd605ea3bc362ef77dbd26fb86e38d85c00e2))  
feat(core/managed): Deep link resources to their infrastructure view [#8115](https://github.com/spinnaker/deck/pull/8115) ([e20115cb](https://github.com/spinnaker/deck/commit/e20115cb8f71a19128238a933276061814ac5bdc))  



## [0.0.468](https://www.github.com/spinnaker/deck/compare/3688018371693ede63df515bd26f3e4fd5fd13bf...93c2f284dd421b0e8b8a686fa7e61631b5139b60) (2020-03-31)


### Changes

chore(package): publish amazon 0.0.249 core 0.0.468 ecs 0.0.254 google 0.0.12 kubernetes 0.0.36 [#8113](https://github.com/spinnaker/deck/pull/8113) ([93c2f284](https://github.com/spinnaker/deck/commit/93c2f284dd421b0e8b8a686fa7e61631b5139b60))  
fix(core/managed): add originally intended error handling on ConstraintCard [#8112](https://github.com/spinnaker/deck/pull/8112) ([061960eb](https://github.com/spinnaker/deck/commit/061960eb9bf0c530d7fca88cfbdf4ffe39e2b7e7))  
feat(core/managed): add read-only stateful constraint support [#8107](https://github.com/spinnaker/deck/pull/8107) ([491a305c](https://github.com/spinnaker/deck/commit/491a305cbefcee43409cae868855fbe081fb4b5b))  
chore(core): remove versionedProviders flag [#8106](https://github.com/spinnaker/deck/pull/8106) ([10a05f9b](https://github.com/spinnaker/deck/commit/10a05f9b915330cb7ecb5e09eb8942db3c350aab))  



## [0.0.467](https://www.github.com/spinnaker/deck/compare/81d56e9221412413a3b05dc74dae6cc0bb8944f9...3688018371693ede63df515bd26f3e4fd5fd13bf) (2020-03-27)


### Changes

chore(bump): Bump core to 0.0.467 [#8103](https://github.com/spinnaker/deck/pull/8103) ([36880183](https://github.com/spinnaker/deck/commit/3688018371693ede63df515bd26f3e4fd5fd13bf))  
feat(core/managed): remove non-deploying state indicators [#8101](https://github.com/spinnaker/deck/pull/8101) ([d34f8f36](https://github.com/spinnaker/deck/commit/d34f8f36a33c8d8603d0a257f7e5aefec7122f7b))  
refactor(titus/serverGroupDetails): Move details field to core and use that in titus [#8097](https://github.com/spinnaker/deck/pull/8097) ([83f65d3b](https://github.com/spinnaker/deck/commit/83f65d3b0c417c5d9222820d41af602e317aa995))  
fix(core): fix NPE in getBaseOsDescription [#8099](https://github.com/spinnaker/deck/pull/8099) ([e948f291](https://github.com/spinnaker/deck/commit/e948f2917696c07770da0f5aa8d429533278e68e))  
refactor(artifacts): consolidate artifacts feature flags checks [#8096](https://github.com/spinnaker/deck/pull/8096) ([51dba018](https://github.com/spinnaker/deck/commit/51dba018763b71db3ddda1eef87a0cd7a9acdc6a))  



## [0.0.466](https://www.github.com/spinnaker/deck/compare/bbb9baeb3429e6e7441b7d564f4a45a7dc33f955...81d56e9221412413a3b05dc74dae6cc0bb8944f9) (2020-03-25)


### Changes

chore(package): publish core 0.0.466 [#8095](https://github.com/spinnaker/deck/pull/8095) ([81d56e92](https://github.com/spinnaker/deck/commit/81d56e9221412413a3b05dc74dae6cc0bb8944f9))  
fix(webhook): Show 'Progress' as Info instead of Warning [#8088](https://github.com/spinnaker/deck/pull/8088) ([8d15878c](https://github.com/spinnaker/deck/commit/8d15878c544d5a8ae59ff3babc3e2aee08f47c2e))  



## [0.0.465](https://www.github.com/spinnaker/deck/compare/185865b12d2040fd0c61fa2bbb103afb6f028114...bbb9baeb3429e6e7441b7d564f4a45a7dc33f955) (2020-03-25)


### Changes

chore(package): publish core 0.0.465 [#8092](https://github.com/spinnaker/deck/pull/8092) ([bbb9baeb](https://github.com/spinnaker/deck/commit/bbb9baeb3429e6e7441b7d564f4a45a7dc33f955))  
feat(core/managed): add real icons to Environments view [#8091](https://github.com/spinnaker/deck/pull/8091) ([1051f8c3](https://github.com/spinnaker/deck/commit/1051f8c3327f1c7ad73ea3430fb6489bbe4a2187))  
feat(core/managed): swap progress direction on artifact rollouts [#8087](https://github.com/spinnaker/deck/pull/8087) ([c9587ac1](https://github.com/spinnaker/deck/commit/c9587ac177847108ed4aa98cfecd62e9aada6f4b))  
fix(core/deploymentStrategy): Handle missing rollback property in command [#8082](https://github.com/spinnaker/deck/pull/8082) ([ebe411c0](https://github.com/spinnaker/deck/commit/ebe411c02d8bafda7831f434f28effd36fa8ec2f))  
feat(core/presentation): adds svg icons for new ui designs [#8083](https://github.com/spinnaker/deck/pull/8083) ([7ae41774](https://github.com/spinnaker/deck/commit/7ae4177453ea11c24d89d8f0b50bbfc008198c2f))  
fix(core): Stop propagation of task monitor close event [#8085](https://github.com/spinnaker/deck/pull/8085) ([b6fe67b7](https://github.com/spinnaker/deck/commit/b6fe67b7a640a1031e320e1c270d291b25204b59))  



## [0.0.464](https://www.github.com/spinnaker/deck/compare/b3f11d468df9add880d2ce6ef592ba7fff281609...185865b12d2040fd0c61fa2bbb103afb6f028114) (2020-03-24)


### Changes

chore(package): publish amazon 0.0.247 core 0.0.464 [#8084](https://github.com/spinnaker/deck/pull/8084) ([185865b1](https://github.com/spinnaker/deck/commit/185865b12d2040fd0c61fa2bbb103afb6f028114))  
feat(core/managed): show relevant resources on version details [#8080](https://github.com/spinnaker/deck/pull/8080) ([84472acb](https://github.com/spinnaker/deck/commit/84472acbe01d0246076317937148cd5a536603e6))  
feat(core/managed): Group resources under EnvironmentRow [#8081](https://github.com/spinnaker/deck/pull/8081) ([26a02137](https://github.com/spinnaker/deck/commit/26a02137091c63b40e94d9d28f103d118416c254))  



## [0.0.463](https://www.github.com/spinnaker/deck/compare/594d2b7c29cdf693e066d261b9c6a0f5a4b02c67...b3f11d468df9add880d2ce6ef592ba7fff281609) (2020-03-24)


### Changes

chore(package): publish core 0.0.463 [#8079](https://github.com/spinnaker/deck/pull/8079) ([b3f11d46](https://github.com/spinnaker/deck/commit/b3f11d468df9add880d2ce6ef592ba7fff281609))  
feat(core/managed): status indicators, explanation cards on artifacts [#8069](https://github.com/spinnaker/deck/pull/8069) ([e51e3428](https://github.com/spinnaker/deck/commit/e51e34280569018a03a9b498c885a142d96beae0))  
refactor(artifacts): clean up ArtifactReferenceService [#8075](https://github.com/spinnaker/deck/pull/8075) ([11f2563f](https://github.com/spinnaker/deck/commit/11f2563fc0fda9a51697124bcd9f7887fcd53197))  



## [0.0.462](https://www.github.com/spinnaker/deck/compare/d956a2460bdfc301c0c9c9d93e5159ed8f00bfc8...594d2b7c29cdf693e066d261b9c6a0f5a4b02c67) (2020-03-23)


### Changes

chore(package): publish core 0.0.462 kubernetes 0.0.35 ([594d2b7c](https://github.com/spinnaker/deck/commit/594d2b7c29cdf693e066d261b9c6a0f5a4b02c67))  
fix(artifacts): only remove deleted expected artifacts from stages on trigger update [#8071](https://github.com/spinnaker/deck/pull/8071) ([a47c6274](https://github.com/spinnaker/deck/commit/a47c6274a9436b775bb0be8fc68a1eec7d72cf30))  
fix(core): Fix effect in TaskMonitorWrapper [#8070](https://github.com/spinnaker/deck/pull/8070) ([29fdb89b](https://github.com/spinnaker/deck/commit/29fdb89bcff11eb522a4a9a7c6393cd5c485fd29))  



## [0.0.461](https://www.github.com/spinnaker/deck/compare/1b759934273de468fdc1c357f5f9c12546c87356...d956a2460bdfc301c0c9c9d93e5159ed8f00bfc8) (2020-03-20)


### Changes

chore(package): publish appengine 0.0.10 azure 0.0.250 cloudfoundry 0.0.95 core 0.0.461 docker 0.0.54 ecs 0.0.253 google 0.0.11 huaweicloud 0.0.3 kubernetes 0.0.34 oracle 0.0.5 [#8064](https://github.com/spinnaker/deck/pull/8064) ([d956a246](https://github.com/spinnaker/deck/commit/d956a2460bdfc301c0c9c9d93e5159ed8f00bfc8))  
feat(core/managed): add resource breakdown to artifact details [#8063](https://github.com/spinnaker/deck/pull/8063) ([14ccb87d](https://github.com/spinnaker/deck/commit/14ccb87d947911041bd67848ee065ca510d72384))  
feat(core/managed): use real data for version progress in Environments [#8060](https://github.com/spinnaker/deck/pull/8060) ([8e58a951](https://github.com/spinnaker/deck/commit/8e58a95110b782403f8f303c5ad79d6c9d6c946c))  
fix(core): replace default account select option [#8052](https://github.com/spinnaker/deck/pull/8052) ([9868d34f](https://github.com/spinnaker/deck/commit/9868d34fd179607af0674473519265757d7f0591))  
feat(core/presentation): add Icon component w/ inline SVG [#8056](https://github.com/spinnaker/deck/pull/8056) ([113c4cdf](https://github.com/spinnaker/deck/commit/113c4cdf4e27e2bf711b92cebaeaff778c94e52e))  
refactor(svg): add SVGR loader for inlined react SVG support [#8055](https://github.com/spinnaker/deck/pull/8055) ([15e47a68](https://github.com/spinnaker/deck/commit/15e47a680a49f048860cd4a5a0688df16a0ce874))  
fix(core/managed): fix check for row expandability on history table [#8058](https://github.com/spinnaker/deck/pull/8058) ([4fdad49a](https://github.com/spinnaker/deck/commit/4fdad49a033474ca1231c27c816f3094ea406c28))  
fix(kubernetes): whitelist helm artifact types for bake manifest stage [#8053](https://github.com/spinnaker/deck/pull/8053) ([93e0d852](https://github.com/spinnaker/deck/commit/93e0d85212dc591cbd53c1a0826648eccd9fab56))  
feat(core/managed): add artifact detail pane to environments [#8048](https://github.com/spinnaker/deck/pull/8048) ([8d44fdb7](https://github.com/spinnaker/deck/commit/8d44fdb79bacdcb61a169ab3732be67e251ee910))  
feat(md): Generate fallback artifact info using Frigga [#8047](https://github.com/spinnaker/deck/pull/8047) ([5d9c8ba9](https://github.com/spinnaker/deck/commit/5d9c8ba952dc26d8f6ab6940794429e7b22dd318))  



## [0.0.460](https://www.github.com/spinnaker/deck/compare/664d174a790d16c8df06d227d0b26dfbd9a759a4...1b759934273de468fdc1c357f5f9c12546c87356) (2020-03-14)


### Changes

chore(package): core to 0.0.460 [#8046](https://github.com/spinnaker/deck/pull/8046) ([1b759934](https://github.com/spinnaker/deck/commit/1b759934273de468fdc1c357f5f9c12546c87356))  
fix(amazon/instance): Show health status failure reason for load balancers [#8041](https://github.com/spinnaker/deck/pull/8041) ([333b9f2e](https://github.com/spinnaker/deck/commit/333b9f2e10ec72ffe8ccd636119023be02f1c649))  
refactor(md): Use useDataSource hook [#8043](https://github.com/spinnaker/deck/pull/8043) ([497aa779](https://github.com/spinnaker/deck/commit/497aa779dd12cd323038ef32f9e94aadb4afbf95))  
fix(core/application): rearrange init ordering of member variables on data sources [#8042](https://github.com/spinnaker/deck/pull/8042) ([b83fcf77](https://github.com/spinnaker/deck/commit/b83fcf77fe6d876afd49756d4f6b38922688554a))  
feat(core/presentation): add useDataSource hook ([d069a288](https://github.com/spinnaker/deck/commit/d069a288b44642e4a2162b9031296019cfb319ec))  
feat(core/presentation): add useObservableValue hook ([643a86ef](https://github.com/spinnaker/deck/commit/643a86ef324992c31bac514b7c07983f868396b0))  
feat(core/presentation): add useObservable hook ([abb0c437](https://github.com/spinnaker/deck/commit/abb0c437730f4b34e4f951e4735d54855fae180b))  
feat(md): Initial draft of Environments view [#8032](https://github.com/spinnaker/deck/pull/8032) ([25a0af3f](https://github.com/spinnaker/deck/commit/25a0af3faa6ac9fcea6d33296f2142a46917deaf))  
fix(core): Do not initialize strategies on mount [#8038](https://github.com/spinnaker/deck/pull/8038) ([c52eed03](https://github.com/spinnaker/deck/commit/c52eed033777142a5b12b471db0122b3e31d6ce2))  
fix(kubernetes): Shorten displayed digest to the first 7 characters i… [#7862](https://github.com/spinnaker/deck/pull/7862) ([fc8fb302](https://github.com/spinnaker/deck/commit/fc8fb302fecc8976698adc7094a7164cf23b0a27))  



## [0.0.459](https://www.github.com/spinnaker/deck/compare/55a4c2f314433dc30454e1df44ff4e6f26d39780...664d174a790d16c8df06d227d0b26dfbd9a759a4) (2020-03-12)


### Changes

chore(package): publish amazon 0.0.245 appengine 0.0.9 azure 0.0.249 cloudfoundry 0.0.94 core 0.0.459 ecs 0.0.252 google 0.0.10 huaweicloud 0.0.2 kubernetes 0.0.33 oracle 0.0.4 [#8035](https://github.com/spinnaker/deck/pull/8035) ([664d174a](https://github.com/spinnaker/deck/commit/664d174a790d16c8df06d227d0b26dfbd9a759a4))  
fix(plugins): better error messages when loading plugin information [#8028](https://github.com/spinnaker/deck/pull/8028) ([e4d03063](https://github.com/spinnaker/deck/commit/e4d03063c0db34f62be51117d0a68087b6c1f6bb))  
refactor(core/pipeline): Migrate Preconfigured Job default config UI to use FormikStageConfig [#8030](https://github.com/spinnaker/deck/pull/8030) ([3d859004](https://github.com/spinnaker/deck/commit/3d8590044cd005b91fbd4f163feb4653113fa871))  
fix(reacthooks): Fix React Hooks linter errors Left the warnings to be dealt with/ignored later ([b347120b](https://github.com/spinnaker/deck/commit/b347120b7952fc6cfd8d2487ddebde46064c3a07))  
test(core/pipeline): update preconfigured job tests ([529300a5](https://github.com/spinnaker/deck/commit/529300a5ce30e8a2db156184e7d2c74d281a504b))  
feat(core/pipeline): improve dx for registering a preconfigured job stage ([750c5d01](https://github.com/spinnaker/deck/commit/750c5d010678e0a4b0552f052def41f1ed6a83ed))  
feat(core/plugins): Support preconfigured job custom stage UI for plugins ([68e2af36](https://github.com/spinnaker/deck/commit/68e2af36b76b00ce4aefff4587b0435657284964))  
feat(core/pipeline): Add PipelineRegistry.registerPreconfiguredJobStage ([29af6707](https://github.com/spinnaker/deck/commit/29af67079dfe177991d00574409466551744deb3))  



## [0.0.458](https://www.github.com/spinnaker/deck/compare/3d74ab567e700c9b695d2f259fcd881664673435...55a4c2f314433dc30454e1df44ff4e6f26d39780) (2020-03-11)


### Changes

chore(package): publish amazon 0.0.244 core 0.0.458 [#8027](https://github.com/spinnaker/deck/pull/8027) ([55a4c2f3](https://github.com/spinnaker/deck/commit/55a4c2f314433dc30454e1df44ff4e6f26d39780))  
feat(md): Types and new data source for environments [#8022](https://github.com/spinnaker/deck/pull/8022) ([8314cfa0](https://github.com/spinnaker/deck/commit/8314cfa03f2631b19f5d0fe8b3a71fa6c423a4e4))  
chore(lint): Fix all linter violations for importing from own subpackage alias ([ae375b14](https://github.com/spinnaker/deck/commit/ae375b145db815b12bd3daa1835d1107b5fd750a))  
fix(core): Fixing AccountSelectInput sync issue [#8013](https://github.com/spinnaker/deck/pull/8013) ([6ea39862](https://github.com/spinnaker/deck/commit/6ea3986258a0f8fca591332764299c28abc6c798))  
Revert "feat(md): Types and new data source for environments" ([d8bf24f8](https://github.com/spinnaker/deck/commit/d8bf24f87de60a70bb7d91761a763a6a4a3fba2f))  
Merge branch 'master' into environments-data-source ([599eb6ab](https://github.com/spinnaker/deck/commit/599eb6ab6d028199b33fc783e20aac383e4a157a))  
feat(md): Types and new data source for environments ([b4512c33](https://github.com/spinnaker/deck/commit/b4512c330f94158234c8be32f9ed3405db8d5f93))  
feat(md): Types and new data source for environments ([cfc347af](https://github.com/spinnaker/deck/commit/cfc347af3c98507490c204cd7843acab2bd8383c))  



## [0.0.457](https://www.github.com/spinnaker/deck/compare/27a5fc681f7759715c576f03ec349404e2219034...3d74ab567e700c9b695d2f259fcd881664673435) (2020-03-10)


### Changes

chore(package): publish core 0.0.457 [#8019](https://github.com/spinnaker/deck/pull/8019) ([3d74ab56](https://github.com/spinnaker/deck/commit/3d74ab567e700c9b695d2f259fcd881664673435))  
Allow markdown in stage descriptions [#8018](https://github.com/spinnaker/deck/pull/8018) ([1376b7d8](https://github.com/spinnaker/deck/commit/1376b7d8ff74be1522440a262936d75b67fc44b5))  
fix(core/pipeline): Fix default values for preconfigured job parameters [#8017](https://github.com/spinnaker/deck/pull/8017) ([b89a1a40](https://github.com/spinnaker/deck/commit/b89a1a40785ac492b90b1c1252620d7cc2b33d07))  



## [0.0.456](https://www.github.com/spinnaker/deck/compare/23867e3e7ef7f84c00e66c4bcfc020873acad9e2...27a5fc681f7759715c576f03ec349404e2219034) (2020-03-09)


### Changes

chore(package): publish amazon 0.0.243 core 0.0.456 [#8015](https://github.com/spinnaker/deck/pull/8015) ([27a5fc68](https://github.com/spinnaker/deck/commit/27a5fc681f7759715c576f03ec349404e2219034))  
fix(core/managed): support new group/name@version syntax for kind field [#8014](https://github.com/spinnaker/deck/pull/8014) ([405ea827](https://github.com/spinnaker/deck/commit/405ea82700fa2086277e64e1af5e9230ed5582d4))  
test(core/pipeline): Fix failing unit tests ([1b4d02e4](https://github.com/spinnaker/deck/commit/1b4d02e4f2e074b62a52126eee2d9b5ed1dec69e))  
fix(core/pipeline): fall back to 'unmatched' stage (JSON editor) instead of null ([f66abce5](https://github.com/spinnaker/deck/commit/f66abce55596748b84c72847270a1ae50c482b80))  
refactor(core/pipeline): Simplify logic finding matching stage types ([ea862fa9](https://github.com/spinnaker/deck/commit/ea862fa9c3fe090a534eef2039793bdc5103ba6d))  
fix(core/pipeline): use react keys in preconfigured job config component loop ([25da5717](https://github.com/spinnaker/deck/commit/25da57170b8cfdd4b18e8227c8d5e4042d065ce3))  
feat(core/pipeline): Create a preconfiguredJob.reader ([f6c3467c](https://github.com/spinnaker/deck/commit/f6c3467cfcc1789eac96980ddc099ec38ce057e3))  
fix(core): Order executions by startTime not endTime [#8010](https://github.com/spinnaker/deck/pull/8010) ([1de3b353](https://github.com/spinnaker/deck/commit/1de3b353fe64953950bb78feaf7b640909ed62dc))  
feat(core/managed): poll for new history events in modal [#7985](https://github.com/spinnaker/deck/pull/7985) ([190cc445](https://github.com/spinnaker/deck/commit/190cc445dfcb66eaeb247e50a10a19b6c20096b9))  
fix(core/managed): use updated URL for status documentation ([1b8bb51e](https://github.com/spinnaker/deck/commit/1b8bb51e9d0dffceb21b5028545be6266463fd69))  
feat(core/managed): adjust status popover language to include history UI ([f17cc406](https://github.com/spinnaker/deck/commit/f17cc406256bf6d431b7b68d5bda50b48a3275fa))  
feat(core/managed): add history modal entry point on status popovers ([133af069](https://github.com/spinnaker/deck/commit/133af069110cef84908bc132402909c991e89c21))  
feat(core/managed): use showModal API for history modal [#7988](https://github.com/spinnaker/deck/pull/7988) ([8f30c02a](https://github.com/spinnaker/deck/commit/8f30c02ad0f1c80c791fc7093cd39b697965443f))  
feat(core/presentation): add imperative showModal API (née ReactModal) [#7987](https://github.com/spinnaker/deck/pull/7987) ([5300ef8c](https://github.com/spinnaker/deck/commit/5300ef8c676d23290239f8861983ad1dbbaef00d))  
fix(core/presentation): useData: continue to return the default value until at least one promise successfully resolves [#8000](https://github.com/spinnaker/deck/pull/8000) ([0d4cdd55](https://github.com/spinnaker/deck/commit/0d4cdd5584e73ef2261e69cdd3dd6f0b64888bf6))  
feat(core/application): export navigation category registry [#7977](https://github.com/spinnaker/deck/pull/7977) ([053e51cf](https://github.com/spinnaker/deck/commit/053e51cf26169cd52d15d4322fc10c82e6c13327))  
feat(core/presentation): add usePollingData hook for scheduled fetching [#7984](https://github.com/spinnaker/deck/pull/7984) ([59a0a185](https://github.com/spinnaker/deck/commit/59a0a18586e74bf0c87c1898d597cf0b069ac4d6))  
fix(artifacts): fix editing expected artifacts in artifact rewrite trigger flow [#7989](https://github.com/spinnaker/deck/pull/7989) ([172a4dde](https://github.com/spinnaker/deck/commit/172a4dde8cfda895957972c52232e4e0a16b773b))  
fix(core/presentation): properly contain sticky row headers on scroll [#7986](https://github.com/spinnaker/deck/pull/7986) ([7ffc5f34](https://github.com/spinnaker/deck/commit/7ffc5f3411c0d034cbf9d301ec22805618a52ad5))  
feat(managed-delivery): Improve errors displayed from unparseable delivery configs [#7959](https://github.com/spinnaker/deck/pull/7959) ([13221e08](https://github.com/spinnaker/deck/commit/13221e081e4c7e85310246d007e6ed9c13bfcde8))  
fix(core): Fix missing maxRemainingAsgs [#7967](https://github.com/spinnaker/deck/pull/7967) ([f1dbb92d](https://github.com/spinnaker/deck/commit/f1dbb92de47bff9cd7532124cfb999d480eb4914))  
feat(mocks): Move test mocks to their own npm package [#7942](https://github.com/spinnaker/deck/pull/7942) ([055e430f](https://github.com/spinnaker/deck/commit/055e430f7ba7ffeea9402f8544859aa03bed5f5b))  



## [0.0.455](https://www.github.com/spinnaker/deck/compare/f62aa6ffb02b0f81da78204d80d34a86eb803b53...23867e3e7ef7f84c00e66c4bcfc020873acad9e2) (2020-02-28)


### Changes

Chore: bump core to 455 [#7979](https://github.com/spinnaker/deck/pull/7979) ([23867e3e](https://github.com/spinnaker/deck/commit/23867e3e7ef7f84c00e66c4bcfc020873acad9e2))  
feat(core/presentation): Perf optimization to avoid unnecessary re-renders ([e5b4f271](https://github.com/spinnaker/deck/commit/e5b4f27198640469d109f65d9644cb49da236270))  
feat(core/presentation): Add useIsFirstRender hook ([1a18429f](https://github.com/spinnaker/deck/commit/1a18429f8aa09f3b97f76e68cb632e61a83cc6be))  



## [0.0.454](https://www.github.com/spinnaker/deck/compare/13024ec41382704405c44b92d130b0110f605470...f62aa6ffb02b0f81da78204d80d34a86eb803b53) (2020-02-27)


### Changes

chore: bump core to 454 [#7974](https://github.com/spinnaker/deck/pull/7974) ([f62aa6ff](https://github.com/spinnaker/deck/commit/f62aa6ffb02b0f81da78204d80d34a86eb803b53))  
refactor(core/presentation): Switch the default value of formik fastField from true to false [#7968](https://github.com/spinnaker/deck/pull/7968) ([77512ecf](https://github.com/spinnaker/deck/commit/77512ecf86abab70556a2e6083a028a8a8072b5f))  
fix(core/region): restore css classes on region select input [#7966](https://github.com/spinnaker/deck/pull/7966) ([b484e936](https://github.com/spinnaker/deck/commit/b484e936cdcea2409f51a0a8b2c19fc20c507936))  
fix(plugins): if gate doesn't have plugin-manifest.json, ignore the [#7932](https://github.com/spinnaker/deck/pull/7932) ([b0f30b5f](https://github.com/spinnaker/deck/commit/b0f30b5f5c54e19c5a6a54b7d6ab088f24b95d76))  
feat(plugins): Switch from /plugin-manifest.js to /plugin-manifest.json (#7905) [#7950](https://github.com/spinnaker/deck/pull/7950) ([128fef1a](https://github.com/spinnaker/deck/commit/128fef1a4844af24e4857a9a1324245f54b09d1f))  



## [0.0.453](https://www.github.com/spinnaker/deck/compare/2251e832ddc14c02366bb1213293adb8690ee419...13024ec41382704405c44b92d130b0110f605470) (2020-02-26)


### Changes

chore(core): bump to 0.0.453 [#7963](https://github.com/spinnaker/deck/pull/7963) ([13024ec4](https://github.com/spinnaker/deck/commit/13024ec41382704405c44b92d130b0110f605470))  
fix(core/managed): remove arrow visual treatment on history modal [#7961](https://github.com/spinnaker/deck/pull/7961) ([1f11571e](https://github.com/spinnaker/deck/commit/1f11571ee2628c34bfb7eb286009a0ba28c46c2b))  
fix(core/managed): add even more message fields on history events [#7960](https://github.com/spinnaker/deck/pull/7960) ([36816088](https://github.com/spinnaker/deck/commit/368160882e908db05f06af51c20752d9f2a37814))  
chore(fonts): update icomoon font files [#7957](https://github.com/spinnaker/deck/pull/7957) ([c5348ae1](https://github.com/spinnaker/deck/commit/c5348ae1f5a79943a258c57720ea27ef7722e94c))  



## [0.0.452](https://www.github.com/spinnaker/deck/compare/7a9dae30e33f9151233f1f9665071919703564ed...2251e832ddc14c02366bb1213293adb8690ee419) (2020-02-25)


### Changes

chore(core): bump to 0.0.452 [#7958](https://github.com/spinnaker/deck/pull/7958) ([2251e832](https://github.com/spinnaker/deck/commit/2251e832ddc14c02366bb1213293adb8690ee419))  
fix(core/account): use relative import for IFormInputProps interface [#7956](https://github.com/spinnaker/deck/pull/7956) ([95963086](https://github.com/spinnaker/deck/commit/95963086409e16f07a369382a4a741cc935fb513))  
feat(codebuild): Add help text to CodeBuild stage [#7948](https://github.com/spinnaker/deck/pull/7948) ([2c7d6024](https://github.com/spinnaker/deck/commit/2c7d60249360de7430118d69fb26752be5e40aa6))  



## [0.0.451](https://www.github.com/spinnaker/deck/compare/4dbf604de29728e66e5f118133f84fcb798ec627...7a9dae30e33f9151233f1f9665071919703564ed) (2020-02-25)


### Changes

chore(package): bump core to 0.0.451 [#7955](https://github.com/spinnaker/deck/pull/7955) ([7a9dae30](https://github.com/spinnaker/deck/commit/7a9dae30e33f9151233f1f9665071919703564ed))  
fix(core/presentation): Fix controlled/uncontrolled warning for number inputs [#7953](https://github.com/spinnaker/deck/pull/7953) ([dde04568](https://github.com/spinnaker/deck/commit/dde045687429ae898374e541ba0f469a64cc701d))  
refactor(core/region): RegionSelectInput: delegate to SelectInput [#7954](https://github.com/spinnaker/deck/pull/7954) ([f0d05296](https://github.com/spinnaker/deck/commit/f0d0529647cf9ff272fa9b6a12de109759d1dc27))  
feat(core/managed): add resource history modal, surface in dropdown ([76a65835](https://github.com/spinnaker/deck/commit/76a658350cc2dbba1fc99e7226a18eb138dadbf6))  
feat(core/managed): add support for history API to ManagedReader ([7d6b44a2](https://github.com/spinnaker/deck/commit/7d6b44a2072010738c00fa979838d2d7b559ace8))  
feat(core/domain): add type defs for managed resource history API ([e90427ef](https://github.com/spinnaker/deck/commit/e90427efbebf1cbd1ee5e8c22225ed223b88c2ee))  
fix(core/modal): display: flex on body, remove outline on close button [#7937](https://github.com/spinnaker/deck/pull/7937) ([33b20037](https://github.com/spinnaker/deck/commit/33b20037cea3745f8f9d956921d894fbb2322d3b))  
refactor(core/account): In AccountSelectInput, delegate to SelectInput and ReactSelectInput [#7952](https://github.com/spinnaker/deck/pull/7952) ([75537815](https://github.com/spinnaker/deck/commit/75537815b842f020da0f44073c2a485e5fd7f7ca))  
feat(core/presentation): add left-align class on vertical flex containers [#7936](https://github.com/spinnaker/deck/pull/7936) ([f9a9bafb](https://github.com/spinnaker/deck/commit/f9a9bafb06eff682878ff229c8286624e8b9a114))  
Revert "feat(plugins): Switch from /plugin-manifest.js to /plugin-manifest.json (#7905)" [#7943](https://github.com/spinnaker/deck/pull/7943) ([2c1308e2](https://github.com/spinnaker/deck/commit/2c1308e2704cb5917a29e7a316857f261919af3d))  
feat(plugins): Switch from /plugin-manifest.js to /plugin-manifest.json [#7905](https://github.com/spinnaker/deck/pull/7905) ([dba6bf14](https://github.com/spinnaker/deck/commit/dba6bf14bd9173f769881acaff7298af1dcb4add))  
fix(core/pipeline): handle missing trigger fields in importDeliveryConfig stage [#7933](https://github.com/spinnaker/deck/pull/7933) ([03d60e4a](https://github.com/spinnaker/deck/commit/03d60e4a3b10ca3104102003422efdee35ba69c7))  
fix(managed-delivery): Yet another fix for rendering import delivery config errors [#7930](https://github.com/spinnaker/deck/pull/7930) ([f60dbec1](https://github.com/spinnaker/deck/commit/f60dbec1f6ecd48b0b05d27925995813efa43ab4))  
feat(core/presentation): add 'standard' grid-based table layout ([d78fad56](https://github.com/spinnaker/deck/commit/d78fad561964d2c44c9fce035746566f8f272bae))  
feat(core/presentation): add 'minimal' native table layout ([7cfc6bc6](https://github.com/spinnaker/deck/commit/7cfc6bc62ef71cac2be8d56c97a3eff00faa3d2d))  
feat(core/presentation): Add high-level Table component API ([98267078](https://github.com/spinnaker/deck/commit/98267078dad77076f66e5c6e9182201d2e374585))  
feat(core/presentation): add useDeepObjectDiff hook for easier memoization of objects [#7928](https://github.com/spinnaker/deck/pull/7928) ([1e7e53a8](https://github.com/spinnaker/deck/commit/1e7e53a8d573bfe71dc81a381f4f6bd077209951))  



## [0.0.450](https://www.github.com/spinnaker/deck/compare/2af06dfe086fa1535957052dc480266953c0c667...4dbf604de29728e66e5f118133f84fcb798ec627) (2020-02-21)


### Changes

chore(core): bump to 0.0.450 [#7929](https://github.com/spinnaker/deck/pull/7929) ([4dbf604d](https://github.com/spinnaker/deck/commit/4dbf604de29728e66e5f118133f84fcb798ec627))  
feat(codebuild): List project names [#7926](https://github.com/spinnaker/deck/pull/7926) ([d0659e8f](https://github.com/spinnaker/deck/commit/d0659e8f0dd4555c165da1c8ce2d111772964459))  
fix(core): Letting angular know when the promises resolve [#7927](https://github.com/spinnaker/deck/pull/7927) ([570a6ea0](https://github.com/spinnaker/deck/commit/570a6ea01e1302b281fbc1c186295eb658ba9409))  
feat(md): Scaffolding environments route [#7923](https://github.com/spinnaker/deck/pull/7923) ([b506dd55](https://github.com/spinnaker/deck/commit/b506dd558703a6922d6b2a42f2d2c9231ef321c7))  



## [0.0.449](https://www.github.com/spinnaker/deck/compare/c8fb7afab15ac2adc6810c4c69e5dae0a9a22201...2af06dfe086fa1535957052dc480266953c0c667) (2020-02-20)


### Changes

chore(package): bump core ([2af06dfe](https://github.com/spinnaker/deck/commit/2af06dfe086fa1535957052dc480266953c0c667))  
fix(managed-delivery): Avoid displaying unnecessary error box [#7920](https://github.com/spinnaker/deck/pull/7920) ([cbfc95b0](https://github.com/spinnaker/deck/commit/cbfc95b0475410d9cbb12990062911c8c75a225c))  
feat(codebuild): Add support for multiple sources [#7915](https://github.com/spinnaker/deck/pull/7915) ([c4a49293](https://github.com/spinnaker/deck/commit/c4a49293856625e00a2a6e87db025791e6af74ee))  
feat(helm): Add HELM3 as Render Engine in Bake Manifest Stage [#7924](https://github.com/spinnaker/deck/pull/7924) ([00dfb668](https://github.com/spinnaker/deck/commit/00dfb668d51b81c3f1d2ebd0884b2f0e2e198762))  
fix(pipeline): Not 'Use default' should be empty string [#7922](https://github.com/spinnaker/deck/pull/7922) ([99765ef0](https://github.com/spinnaker/deck/commit/99765ef0dd9e9fad01cdd71faf939430303b9b93))  
refactor(core): Prefix search result table components with 'Search' [#7899](https://github.com/spinnaker/deck/pull/7899) ([2a40f32d](https://github.com/spinnaker/deck/commit/2a40f32db8eca1838f21f7c43bc53561ca978da8))  
feat(spel): default new pipelines to spel v4 [#7917](https://github.com/spinnaker/deck/pull/7917) ([5b90d53a](https://github.com/spinnaker/deck/commit/5b90d53a13368c99956383bd467cd424a0e67aab))  
feat(plugins): imperative initialization [#7912](https://github.com/spinnaker/deck/pull/7912) ([d090d7d7](https://github.com/spinnaker/deck/commit/d090d7d75ba780e0ea88f45edea2aab9027b6aec))  
feat(core/presentation): add experimental useIsMobile hook [#7901](https://github.com/spinnaker/deck/pull/7901) ([00494429](https://github.com/spinnaker/deck/commit/00494429372e82b0fe9414ba5148041bc897ad93))  
fix(core/securityGroups): Render view if managed resources can't be loaded [#7891](https://github.com/spinnaker/deck/pull/7891) ([3b025b2d](https://github.com/spinnaker/deck/commit/3b025b2de9fe3a5f6eba011475ee4894c9878501))  
feat(plugins): Prefer plugins from deck manifest (over gate) if they have the same ID [#7868](https://github.com/spinnaker/deck/pull/7868) ([82d16dd1](https://github.com/spinnaker/deck/commit/82d16dd1f4dcea400e4d4f435e9fa1705194bd0c))  
fix(core/account): AccountSelectInput pass 'name' in synthetic event [#7904](https://github.com/spinnaker/deck/pull/7904) ([356fb86c](https://github.com/spinnaker/deck/commit/356fb86c4942392a00c1f592eb44ef9471eb4717))  
feat(codebuild): Add support for overriding image and buildspec [#7892](https://github.com/spinnaker/deck/pull/7892) ([72d07feb](https://github.com/spinnaker/deck/commit/72d07feb56a4388316d676cc483d8b3753549a2f))  
chore(package): Update uirouter libs [#7835](https://github.com/spinnaker/deck/pull/7835) ([c81d30dc](https://github.com/spinnaker/deck/commit/c81d30dc4fae5298b3a104da5f2877b591a3b3a7))  
feat(codebuild): Allow AWS CodeBuild stage to produce artifacts [#7882](https://github.com/spinnaker/deck/pull/7882) ([210843f5](https://github.com/spinnaker/deck/commit/210843f504a14d2314b9156e10d3655d58e50942))  
feat(codebuild): Add more options to AWS CodeBuild stage [#7861](https://github.com/spinnaker/deck/pull/7861) ([bead9cd2](https://github.com/spinnaker/deck/commit/bead9cd2569f66d03d90947581ca7ed1752ba1f2))  
fix(core/presentation): allow 0 in NumberInput.tsx [#7881](https://github.com/spinnaker/deck/pull/7881) ([56159156](https://github.com/spinnaker/deck/commit/561591563a29a2abb47ea631565b50c2958e6378))  
fix(core): Use "Authorization" header instead of query parameter to get changelog (what's new) from gist [#7836](https://github.com/spinnaker/deck/pull/7836) ([d4894dd5](https://github.com/spinnaker/deck/commit/d4894dd50718dca014452799a98d38b72bdbc484))  
feat(deck): show expression in check precondition execution details [#7860](https://github.com/spinnaker/deck/pull/7860) ([9194d5ba](https://github.com/spinnaker/deck/commit/9194d5ba2b154dd7e336ebe8c6adce581aaf927d))  



## [0.0.448](https://www.github.com/spinnaker/deck/compare/8bf7e339e896522e78b4d7e34d9b1cbe8f614d05...c8fb7afab15ac2adc6810c4c69e5dae0a9a22201) (2020-02-08)


### Changes

chore(package): bump core, amazon, docker, titus [#7856](https://github.com/spinnaker/deck/pull/7856) ([c8fb7afa](https://github.com/spinnaker/deck/commit/c8fb7afab15ac2adc6810c4c69e5dae0a9a22201))  



## [0.0.447](https://www.github.com/spinnaker/deck/compare/28ace48bd4b855ebbf27e5ccc45e32797796eed2...8bf7e339e896522e78b4d7e34d9b1cbe8f614d05) (2020-02-08)


### Changes

chore(package): bump core to 447, amazon to 235, docker to 51, titus to 127 [#7851](https://github.com/spinnaker/deck/pull/7851) ([8bf7e339](https://github.com/spinnaker/deck/commit/8bf7e339e896522e78b4d7e34d9b1cbe8f614d05))  
fix(packages): Preserve webpackIgnore comments when bundling for npm packages [#7850](https://github.com/spinnaker/deck/pull/7850) ([8b84eedb](https://github.com/spinnaker/deck/commit/8b84eedb2f2130fab2d261935de81a2157b2b00e))  



## [0.0.446](https://www.github.com/spinnaker/deck/compare/805d14ef799ec730aa975eff4a20b93cccffce59...28ace48bd4b855ebbf27e5ccc45e32797796eed2) (2020-02-07)


### Changes

chore(core): Bump core to 0.0.446 [#7848](https://github.com/spinnaker/deck/pull/7848) ([28ace48b](https://github.com/spinnaker/deck/commit/28ace48bd4b855ebbf27e5ccc45e32797796eed2))  
react(core): Introduce LinkWithClipboard component [#7847](https://github.com/spinnaker/deck/pull/7847) ([1ddc70ad](https://github.com/spinnaker/deck/commit/1ddc70ad3c2853edcd367e7efaaed0730c2f926c))  
feat(plugins): Load plugins and plugin manifest from gate [#7842](https://github.com/spinnaker/deck/pull/7842) ([e5b2bc35](https://github.com/spinnaker/deck/commit/e5b2bc3569e0a00b3c22aa8940c6a26ce5c2393e))  



## [0.0.445](https://www.github.com/spinnaker/deck/compare/e1fc2ceaa30d2d56cd4ba8ccb9bb719a3fc1fb82...805d14ef799ec730aa975eff4a20b93cccffce59) (2020-02-06)


### Changes

chore(package): bump core to 445 [#7843](https://github.com/spinnaker/deck/pull/7843) ([805d14ef](https://github.com/spinnaker/deck/commit/805d14ef799ec730aa975eff4a20b93cccffce59))  
fix(core/confirmationModal): Supply the 'source' location when canceling [#7816](https://github.com/spinnaker/deck/pull/7816) ([411fda5e](https://github.com/spinnaker/deck/commit/411fda5eba15363a792a5dc36873246d6a0f870c))  
Re-exporting types from ExecutionMarkerIcon to make it available elsewhere [#7829](https://github.com/spinnaker/deck/pull/7829) ([0feb8249](https://github.com/spinnaker/deck/commit/0feb8249948a167de8a74ae6482f7f34eae09b9d))  
feat(travis): Enable Travis stage as an artifact producer [#7831](https://github.com/spinnaker/deck/pull/7831) ([0ff0ade3](https://github.com/spinnaker/deck/commit/0ff0ade384230201048c2aac81cf2f4945f6645a))  
feat(codebuild): Add AWS CodeBuild Stage [#7798](https://github.com/spinnaker/deck/pull/7798) ([71cda71c](https://github.com/spinnaker/deck/commit/71cda71c27392d7ff21f9bd4a90267c07b680703))  
tests(core): Introduce entity mocks into deck [#7781](https://github.com/spinnaker/deck/pull/7781) ([b632e29b](https://github.com/spinnaker/deck/commit/b632e29b9c5624d43df4b3a312fc9fb2432d6aa1))  
feat(core): enable the "Override Helm chart artifact" checkbox in manual execution by default [#7818](https://github.com/spinnaker/deck/pull/7818) ([65b9463b](https://github.com/spinnaker/deck/commit/65b9463b8f4a7d9ce83fd1c1415539dce5d469ab))  
chore(lint): Run eslint on typescript files ([b51dce46](https://github.com/spinnaker/deck/commit/b51dce46be3df14070f06e06de874108dcf23569))  
chore(lint): Run eslint on javascript files ([38a6324a](https://github.com/spinnaker/deck/commit/38a6324aa9f116c70c7644113f5f84214fd95679))  
feat(deck): Add execution error details to Task Status in Cloud foundry Stage [#7814](https://github.com/spinnaker/deck/pull/7814) ([e15b7964](https://github.com/spinnaker/deck/commit/e15b7964ac7c3025cf4567dbaf803fbf61d28d6b))  
fix(deck): use label for parameters in manual execution if supplied [#7812](https://github.com/spinnaker/deck/pull/7812) ([c4038601](https://github.com/spinnaker/deck/commit/c40386014647eb5bac919024919acc1ad1574d0d))  
feat(core): Forward params through execution lookup [#7811](https://github.com/spinnaker/deck/pull/7811) ([6ac2ed55](https://github.com/spinnaker/deck/commit/6ac2ed550c27eb51e77d6845f277b9741bd7f150))  
config(core): Remove MPTV2 UI feature flag [#7804](https://github.com/spinnaker/deck/pull/7804) ([e7404611](https://github.com/spinnaker/deck/commit/e7404611795ac195f92683cc6fabefebeda46ea9))  
feat(plugins): read plugin config from plugin-manifest.json [#7788](https://github.com/spinnaker/deck/pull/7788) ([c9ce7154](https://github.com/spinnaker/deck/commit/c9ce71546460c7acec92d4ca3f0b1f1285de7017))  



## [0.0.444](https://www.github.com/spinnaker/deck/compare/24fdb05344eb71ccc5acc39fdfb38b47a877abc7...e1fc2ceaa30d2d56cd4ba8ccb9bb719a3fc1fb82) (2020-01-23)


### Changes

chore(core): Bump to verion v0.0.444 ([e1fc2cea](https://github.com/spinnaker/deck/commit/e1fc2ceaa30d2d56cd4ba8ccb9bb719a3fc1fb82))  
fix(aws): Component for additional security group details  [#7803](https://github.com/spinnaker/deck/pull/7803) ([35fcebbc](https://github.com/spinnaker/deck/commit/35fcebbcdd2c6dea2397a90a48db975293096054))  
fix(artifacts): Fix find artifacts from execution stage [#7801](https://github.com/spinnaker/deck/pull/7801) ([821b82f3](https://github.com/spinnaker/deck/commit/821b82f35eda4ba7b87b172e64033f879c9d76f1))  
feat(spel): add SpEL-awareness to FormikFormField [#7740](https://github.com/spinnaker/deck/pull/7740) ([9abbb814](https://github.com/spinnaker/deck/commit/9abbb814a08400ece0d0f6f9bbc8ea1c1e32417a))  
fix(core): prevent tabbing into CopyToClipboard hidden input [#7800](https://github.com/spinnaker/deck/pull/7800) ([8e50c2e2](https://github.com/spinnaker/deck/commit/8e50c2e2c7d987a6c988f6f5d20657a2ca0a1b56))  
chore(core): clean up import warnings [#7797](https://github.com/spinnaker/deck/pull/7797) ([979d266f](https://github.com/spinnaker/deck/commit/979d266fdce8ba13b885e6e8bb8f8f64683451e8))  
fix(core/kubernetes): clean up console noise in tests [#7794](https://github.com/spinnaker/deck/pull/7794) ([05c93f42](https://github.com/spinnaker/deck/commit/05c93f42443e0166a1580cbffd72e79d451bd23d))  
feat(core): always show copied tooltip on CopyToClipboard [#7793](https://github.com/spinnaker/deck/pull/7793) ([2f954531](https://github.com/spinnaker/deck/commit/2f9545316dd5bef688bbd32f5c6e60f8a7e3dfd4))  
feat(core): restrict keyboard navigation in modals via tab [#7786](https://github.com/spinnaker/deck/pull/7786) ([23b30fbd](https://github.com/spinnaker/deck/commit/23b30fbd281f01bba22c8db233b57c3e6dbaa2cd))  
feat(artifacts): link to updated docs from Match Artifact tooltip [#7792](https://github.com/spinnaker/deck/pull/7792) ([a7452fb2](https://github.com/spinnaker/deck/commit/a7452fb2aa97d026ed2199c7a5b575e33e8268c0))  
feat(core): allow yaml responses from ApiService [#7789](https://github.com/spinnaker/deck/pull/7789) ([c2c3b7db](https://github.com/spinnaker/deck/commit/c2c3b7db302af9b610ed9119ae42b579c3bb5ef9))  
fix(core): allow newlines in CopyToClipboard [#7790](https://github.com/spinnaker/deck/pull/7790) ([6815bc0d](https://github.com/spinnaker/deck/commit/6815bc0df9cafde27957de7eeb889f20b91b2f48))  
fix(core): correct method signature for buildServerGroupCommandFromExisting [#7791](https://github.com/spinnaker/deck/pull/7791) ([e26b2642](https://github.com/spinnaker/deck/commit/e26b2642e1d55e2336a811cc018057e69199597f))  
fix(stage): Remove extraneous $ character from details [#7787](https://github.com/spinnaker/deck/pull/7787) ([ebb23cad](https://github.com/spinnaker/deck/commit/ebb23cad4e8e0d951d3934247adad8f56f1b0270))  



## [0.0.443](https://www.github.com/spinnaker/deck/compare/75dee0391a1537aa2b4cd1ecc2488f16b4f6b322...24fdb05344eb71ccc5acc39fdfb38b47a877abc7) (2020-01-15)


### Changes

chore(core): bump package to 0.0.443 [#7785](https://github.com/spinnaker/deck/pull/7785) ([24fdb053](https://github.com/spinnaker/deck/commit/24fdb05344eb71ccc5acc39fdfb38b47a877abc7))  
fix(core): fix pager checkbox click behavior [#7780](https://github.com/spinnaker/deck/pull/7780) ([0f5dbc16](https://github.com/spinnaker/deck/commit/0f5dbc168b606dc46d08e36abbde1338450c5f11))  
chore(core): convert colors to stylesheet variables [#7783](https://github.com/spinnaker/deck/pull/7783) ([1b4d8746](https://github.com/spinnaker/deck/commit/1b4d87466c0ae1524178635fd1d8a4d8ec5fe2c5))  



## [0.0.442](https://www.github.com/spinnaker/deck/compare/0b379c7bc337446c32bd3003a9e5f01e218b674c...75dee0391a1537aa2b4cd1ecc2488f16b4f6b322) (2020-01-14)


### Changes

chore(core): bump package to 0.0.442 [#7778](https://github.com/spinnaker/deck/pull/7778) ([75dee039](https://github.com/spinnaker/deck/commit/75dee0391a1537aa2b4cd1ecc2488f16b4f6b322))  
fix(core): do not render the server group warning message in <pre> [#7774](https://github.com/spinnaker/deck/pull/7774) ([73fa66ee](https://github.com/spinnaker/deck/commit/73fa66ee0d22b35997dc987135f2033825aef500))  
fix(core): display task monitor above page navigator chrome [#7772](https://github.com/spinnaker/deck/pull/7772) ([a8cc8698](https://github.com/spinnaker/deck/commit/a8cc869875e1a27dc84d6b7fb584104ff565f824))  
fix(appconfig): Fix error thrown when slack object is not defined [#7770](https://github.com/spinnaker/deck/pull/7770) ([fc1ae74e](https://github.com/spinnaker/deck/commit/fc1ae74e50e289da890c119afae9c5614ab0f770))  
feat(aws/cfn): Cloudformation ChangeSet execution [#7671](https://github.com/spinnaker/deck/pull/7671) ([73838f7b](https://github.com/spinnaker/deck/commit/73838f7b453df77ca466df8344844a414bb37ec2))  



## [0.0.441](https://www.github.com/spinnaker/deck/compare/49fb76a2c160d883645e4a1b54c7419f1ca5fe57...0b379c7bc337446c32bd3003a9e5f01e218b674c) (2020-01-10)


### Changes

chore(chore): bump to 0.0.441 [#7766](https://github.com/spinnaker/deck/pull/7766) ([0b379c7b](https://github.com/spinnaker/deck/commit/0b379c7bc337446c32bd3003a9e5f01e218b674c))  
fix(core/pipeline): use fresh trigger + stage list for upstream flag validation [#7765](https://github.com/spinnaker/deck/pull/7765) ([99c08cbe](https://github.com/spinnaker/deck/commit/99c08cbef1a977299ca10967cf1fee2c138b1faf))  
fix(core): catch cancelling of resource pause from server group modal [#7764](https://github.com/spinnaker/deck/pull/7764) ([86832e63](https://github.com/spinnaker/deck/commit/86832e63d809d1b5efbe82ad60c2406fd72ccd9d))  



## [0.0.440](https://www.github.com/spinnaker/deck/compare/9cc41c0b6866d48b8d78c2da92be817a62102af5...49fb76a2c160d883645e4a1b54c7419f1ca5fe57) (2020-01-10)


### Changes

chore(core): Bump version to 0.0.440 [#7761](https://github.com/spinnaker/deck/pull/7761) ([49fb76a2](https://github.com/spinnaker/deck/commit/49fb76a2c160d883645e4a1b54c7419f1ca5fe57))  
refactor(*): de-angularize confirmationModalService [#7759](https://github.com/spinnaker/deck/pull/7759) ([e6c6c662](https://github.com/spinnaker/deck/commit/e6c6c662b5326fcb184772c99f2212ce4336a1cb))  
feat(core/amazon/titus): do not allow create/clone in managed clusters [#7754](https://github.com/spinnaker/deck/pull/7754) ([4302a0fc](https://github.com/spinnaker/deck/commit/4302a0fc90d2b1679dee204d15e59d3e53b3d0a0))  
fix(core): catch/handle confirmation modal dismissal [#7758](https://github.com/spinnaker/deck/pull/7758) ([19ff2aac](https://github.com/spinnaker/deck/commit/19ff2aacf973bf382c7de65debb7554c212790f0))  
fix(core): keep health counts on one line in server group title [#7757](https://github.com/spinnaker/deck/pull/7757) ([1882a1eb](https://github.com/spinnaker/deck/commit/1882a1eb32b7e0882d39084f6a93ce9d0859bad8))  
feat(managed-delivery): Add UI for importDeliveryConfig stage [#7733](https://github.com/spinnaker/deck/pull/7733) ([1761ae59](https://github.com/spinnaker/deck/commit/1761ae59398a594ae277680f91890f434b26ab2b))  
fix(core): do not log all help contents to the console [#7755](https://github.com/spinnaker/deck/pull/7755) ([0c6a651a](https://github.com/spinnaker/deck/commit/0c6a651a8a156aa8f8ba26f7531c38e4ad1a936d))  



## [0.0.439](https://www.github.com/spinnaker/deck/compare/7c2f24ddf9b382f51b98ec2f13cc01087b9b2a68...9cc41c0b6866d48b8d78c2da92be817a62102af5) (2020-01-08)


### Changes

chore(core): bump package to 0.0.439 [#7751](https://github.com/spinnaker/deck/pull/7751) ([9cc41c0b](https://github.com/spinnaker/deck/commit/9cc41c0b6866d48b8d78c2da92be817a62102af5))  
feat(core/amazon/titus): restrict menu items on managed resources [#7750](https://github.com/spinnaker/deck/pull/7750) ([ff87bda7](https://github.com/spinnaker/deck/commit/ff87bda72eed677b6e1b792bc5fae346ca459336))  



## [0.0.438](https://www.github.com/spinnaker/deck/compare/ed779e60b077b8630e3e5df987f246a4041e80b7...7c2f24ddf9b382f51b98ec2f13cc01087b9b2a68) (2020-01-08)


### Changes

chore(core): bump package to 0.0.438 [#7746](https://github.com/spinnaker/deck/pull/7746) ([7c2f24dd](https://github.com/spinnaker/deck/commit/7c2f24ddf9b382f51b98ec2f13cc01087b9b2a68))  
feat(core): offer to pause managed resources before performing actions [#7728](https://github.com/spinnaker/deck/pull/7728) ([edacd084](https://github.com/spinnaker/deck/commit/edacd08419e97e7f265538c3015c6e2789bf238b))  
refactor(*): use consistent styles on modal headers ([10b34915](https://github.com/spinnaker/deck/commit/10b34915860ed46f21d0179bf87c3b456de49c56))  
feat(core): add pause/resume to managed resource menu ([9560304a](https://github.com/spinnaker/deck/commit/9560304a063e7afb3af6deca7c64c4684baf78f3))  
feat(core): add validator for stage/trigger providing repository info ([5b7c9cf7](https://github.com/spinnaker/deck/commit/5b7c9cf76aad5552a1d2d3353a566a00349ab8d9))  
Apply suggestions from code review ([c76ca2f1](https://github.com/spinnaker/deck/commit/c76ca2f152d843958c8092437537e8055396c05a))  
refactor(*): favor optional chaining over lodash.get ([dc2b3d74](https://github.com/spinnaker/deck/commit/dc2b3d7419c79159a89ad346bd64e2f4cc9fde75))  
fix(core): do not try to render task monitor if monitor is missing [#7744](https://github.com/spinnaker/deck/pull/7744) ([ecd7d708](https://github.com/spinnaker/deck/commit/ecd7d708f57ce61c51fa2f87c9e9dbbd23659d7f))  
refactor(core): use useForceUpdate hook instead of more opinionated hook ([bbf87f9f](https://github.com/spinnaker/deck/commit/bbf87f9fa4dbc284f570f1a923af488bb926e810))  
refactor(core): convert confirmation modal to react ([a59b2c32](https://github.com/spinnaker/deck/commit/a59b2c3264500080fad7caeb05054eef6f51d52c))  
refactor(core/amazon): remove unused CSS rules [#7732](https://github.com/spinnaker/deck/pull/7732) ([abf39e70](https://github.com/spinnaker/deck/commit/abf39e70e72c3231da29d4c81966aa47ad15fd87))  
refactor(core): provide wrapper for dangerously setting html [#7721](https://github.com/spinnaker/deck/pull/7721) ([65488728](https://github.com/spinnaker/deck/commit/65488728e4ef08c2034123a88a9a4b96cb0e4bd9))  
Template version ui [#7708](https://github.com/spinnaker/deck/pull/7708) ([a6784422](https://github.com/spinnaker/deck/commit/a6784422dd5130434246857bb137918023576247))  
fix(plugins): Default plugins to empty list to prevent NPE [#7736](https://github.com/spinnaker/deck/pull/7736) ([192531ac](https://github.com/spinnaker/deck/commit/192531ac4cfb4fb10c225884ca45c28ab5e50656))  
test(plugins): test the plugin registry ([81386a3c](https://github.com/spinnaker/deck/commit/81386a3c55608dd1c31a30eff1d46f7c39627f30))  
refactor(plugin): rename metadata/manifest references to be consistent - switch 'version' to be a 'string' - add getRegisteredPlugins() - move import() to a function to be testable - add 'module' to manifest interface ([b1381b10](https://github.com/spinnaker/deck/commit/b1381b10f1359e6420c646a188921542fa642cf1))  
refactor(plugin): remove ILocalDevPluginManifestData and move url to IPluginManifestData.devUrl ([996a532b](https://github.com/spinnaker/deck/commit/996a532b839dc822ed78d7b8e4fddf893042b836))  
feat(plugins): Add a PluginRegistry and simplify bootstrapping ([c1d68e12](https://github.com/spinnaker/deck/commit/c1d68e12c06caac5a9ed816470c03ac0bce04fb3))  
feat(plugins): able to load plugin stages into UI ([ba56a2a4](https://github.com/spinnaker/deck/commit/ba56a2a497bdfec8b9555944b2908106be6bf865))  
feat(managed): allow pausing/resuming managed resources via indicator [#7717](https://github.com/spinnaker/deck/pull/7717) ([b1ced5a6](https://github.com/spinnaker/deck/commit/b1ced5a69ebe25ed909e86596e21ad06212e9f72))  



## [0.0.437](https://www.github.com/spinnaker/deck/compare/e607da11f4dcc6d0317cb3636e762647c16c293f...ed779e60b077b8630e3e5df987f246a4041e80b7) (2019-12-19)


### Changes

chore(core): Bump to 0.0.437 [#7724](https://github.com/spinnaker/deck/pull/7724) ([ed779e60](https://github.com/spinnaker/deck/commit/ed779e60b077b8630e3e5df987f246a4041e80b7))  
fix(core): updateStageField should not update reserved fields [#7722](https://github.com/spinnaker/deck/pull/7722) ([01130bd2](https://github.com/spinnaker/deck/commit/01130bd2aac27c8d99c18b442d9f1d00f34e2794))  
refactor(core): remove unused parameter options from confirmation modal [#7716](https://github.com/spinnaker/deck/pull/7716) ([d2838d80](https://github.com/spinnaker/deck/commit/d2838d80c7f14989368fc490a2d842b2d4952a42))  
fix(core): allow scroll in main view when content has extra stuff [#7712](https://github.com/spinnaker/deck/pull/7712) ([0de53cfd](https://github.com/spinnaker/deck/commit/0de53cfd18dab701b3d43d49e22f531a577e6eb2))  
fix(core): increase CSS specificity on select2 overrides [#7719](https://github.com/spinnaker/deck/pull/7719) ([da6eeecf](https://github.com/spinnaker/deck/commit/da6eeecfb16720351d6a85bae8d1a4ee591320dc))  
fix(plugins): Fix linter error and rename file ([932a9473](https://github.com/spinnaker/deck/commit/932a9473ea45659484e9ba0fcd9021ad0ad8641c))  
feat(plugins): Expose sharedLibraries on the Spinnaker global object ([179c5f16](https://github.com/spinnaker/deck/commit/179c5f16068aec9e8c483b47932e288d8b5a45bb))  
fix(core/utils): Only set window.spinnaker if the window object is present ([84add8af](https://github.com/spinnaker/deck/commit/84add8af13fda62b3018080ba39d8e26c5ec0a57))  
chore(core): upgrade to latest prettier [#7713](https://github.com/spinnaker/deck/pull/7713) ([6291f858](https://github.com/spinnaker/deck/commit/6291f858cb111d9c65affeb82ddd840f05c57b65))  
fix(core): derive security group view options from filterModel [#7711](https://github.com/spinnaker/deck/pull/7711) ([bc00902d](https://github.com/spinnaker/deck/commit/bc00902d6aef54f209640fbe03d9ee46690a3b1e))  



## [0.0.436](https://www.github.com/spinnaker/deck/compare/57c0f7f2a3cfeac7fa74b0f1796c5b6d04312296...e607da11f4dcc6d0317cb3636e762647c16c293f) (2019-12-14)


### Changes

chore(core): bump to 0.0.436 [#7703](https://github.com/spinnaker/deck/pull/7703) ([e607da11](https://github.com/spinnaker/deck/commit/e607da11f4dcc6d0317cb3636e762647c16c293f))  
fix(spel2js): Use named exports [#7710](https://github.com/spinnaker/deck/pull/7710) ([1e46a7f5](https://github.com/spinnaker/deck/commit/1e46a7f58f3da51ca34689a97d6338379e3e866c))  
fix(core): Fix security group load performance [#7709](https://github.com/spinnaker/deck/pull/7709) ([37e932f4](https://github.com/spinnaker/deck/commit/37e932f46c37e8b29854b8c832a96df9dac44dd0))  
refactor(google): lazily initialize state with function ([691bfab6](https://github.com/spinnaker/deck/commit/691bfab6804adab02dd2a38232785de1629dbcd3))  
refactor(google): #native defaulting ([f6ad1165](https://github.com/spinnaker/deck/commit/f6ad1165d6ee614225946a8fe1e5f49714e91067))  
refactor(google): refactor GCB stage to use new FormikFormFields and hooks APIs ([1ffdbeeb](https://github.com/spinnaker/deck/commit/1ffdbeeba0a07b720788a2eba38294f7db803090))  
fix(projects): Fixing clusters error validation [#7701](https://github.com/spinnaker/deck/pull/7701) ([ed2433ea](https://github.com/spinnaker/deck/commit/ed2433eac477e2cc5f713b8dbd451337cde77fff))  
refactor(eslint): Fix all '@typescript-eslint/no-inferrable-types' eslint rule violations ([287f5273](https://github.com/spinnaker/deck/commit/287f5273511cd2493d4a7882e0428c5d952116e5))  
refactor(eslint): Fix all 'prefer-const' eslint rule violations ([90aa4775](https://github.com/spinnaker/deck/commit/90aa47754bc8815eb1bdfcceb4d05c9e1cdf325f))  
refactor(eslint): Fix all 'one-var' eslint rule violations ([d070bd45](https://github.com/spinnaker/deck/commit/d070bd45ff3e185999e863e3f48c01f63eb45733))  
refactor(eslint): Fix all 'no-var' eslint rule violations ([17487016](https://github.com/spinnaker/deck/commit/174870161a5a09ab7f15c74cb84d0f3e196cd7cb))  
chore(eslint): remove tslint ([9400826b](https://github.com/spinnaker/deck/commit/9400826bcb119cf7681e1ce37092b9fdd8b76b1b))  
fix(core): make resources paused if app is paused [#7699](https://github.com/spinnaker/deck/pull/7699) ([a9ce021e](https://github.com/spinnaker/deck/commit/a9ce021e8c1bf4373dee8fb8bdc42000789d7b3a))  
fix(angular-ui-bootstrap): Kludge: cast angular-ui-bootstrap default import as 'any' ([e4c06a7b](https://github.com/spinnaker/deck/commit/e4c06a7be782f434dabc0d18d879319473acc645))  
fix(typescript): Add basic typescript definitions for some angular libs ([08c2b347](https://github.com/spinnaker/deck/commit/08c2b347bad84921a596420a375485c54923efd8))  
fix(imports): move imports out of describe() blocks ([8c241f2d](https://github.com/spinnaker/deck/commit/8c241f2d957a3e37439833a7ca70af79d8107dc1))  
refactor(angularjs): use ES6 to import angular - migrate from `const angular = require('angular')` to `import * as angular from 'angular'` - Where possible, migrate from `import angular from 'angular'; angular.module('asdf')`   to `import { module } from 'angular'; module('asdf')` ([88b8f4ae](https://github.com/spinnaker/deck/commit/88b8f4ae0b9e96ac8d8dbdeff592f3787f0617cb))  
refactor(angularjs): use ES6 imports for angularjs module deps - migrate from `require('@uirouter/angularjs').default` to import UIROUTER_ANGULARJS from '@uirouter/angularjs' - migrate from `require('angular-ui-bootstrap')` to import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap' ([a076dc12](https://github.com/spinnaker/deck/commit/a076dc1280b56affcd30cdbea68a84fb7d5ba3f1))  
refactor(angularjs): Import angularjs module dependencies by name - Migrate angularjs module dependencies to import the exported string identifier, not via require('module').name ([ac1c86eb](https://github.com/spinnaker/deck/commit/ac1c86ebbc72e6d2d83eb57d6710c6ae2651ecc0))  
refactor(angularjs): Always export the ng module name, not the module itself ([784d64b6](https://github.com/spinnaker/deck/commit/784d64b66a6410e622803b4b0519f7050e9c5f82))  
refactor(core): add pause status flags to managed resources [#7698](https://github.com/spinnaker/deck/pull/7698) ([7e70a2e0](https://github.com/spinnaker/deck/commit/7e70a2e00abe94e7a00b75d3ea5775d0fe375b93))  



## [0.0.435](https://www.github.com/spinnaker/deck/compare/d8e209726df065a88b6875a5bf634693d415fea7...57c0f7f2a3cfeac7fa74b0f1796c5b6d04312296) (2019-12-11)


### Changes

chore(core): bump to 0.0.435 [#7696](https://github.com/spinnaker/deck/pull/7696) ([57c0f7f2](https://github.com/spinnaker/deck/commit/57c0f7f2a3cfeac7fa74b0f1796c5b6d04312296))  
refactor(core/managed): switch from ApplicationVeto to pause API [#7695](https://github.com/spinnaker/deck/pull/7695) ([4dec4415](https://github.com/spinnaker/deck/commit/4dec441516e02b6dbf3cb32330479c88cb3bcea2))  



## [0.0.434](https://www.github.com/spinnaker/deck/compare/299315f3764c41f95eadace7acac70cb4a18592d...d8e209726df065a88b6875a5bf634693d415fea7) (2019-12-11)


### Changes

chore(core): Bump core to 0.0.434 [#7694](https://github.com/spinnaker/deck/pull/7694) ([d8e20972](https://github.com/spinnaker/deck/commit/d8e209726df065a88b6875a5bf634693d415fea7))  
fix(core/slack): Abstract Slack base url  [#7693](https://github.com/spinnaker/deck/pull/7693) ([9c4d68a2](https://github.com/spinnaker/deck/commit/9c4d68a22191540e71b700738aeee2c8a4f52e13))  
test(core): Fix intermittent Executions test [#7691](https://github.com/spinnaker/deck/pull/7691) ([14eafb48](https://github.com/spinnaker/deck/commit/14eafb48ef9338f022893337263f643d2ea14ffb))  
refactor(core): move managed entity fields to separate interface [#7686](https://github.com/spinnaker/deck/pull/7686) ([5c2d306e](https://github.com/spinnaker/deck/commit/5c2d306e16a088da603f1bfb7fe2a7b8b9164ff4))  
refactor(core): convert security groups views to React [#7676](https://github.com/spinnaker/deck/pull/7676) ([2b6a9411](https://github.com/spinnaker/deck/commit/2b6a9411bfe3d5fd54d815065fe9d99625747241))  
chore(typescript): Migrate most wildcard imports to javascript style imports - Migrate from "import * as foo from 'foo'" to "import foo from 'foo'" ([b6aabe18](https://github.com/spinnaker/deck/commit/b6aabe18a2c71f194087c01fd15ec369460f5e70))  
feat(typescript): enable allowJs and allowSyntheticDefaultImports ([7ef58b6c](https://github.com/spinnaker/deck/commit/7ef58b6c122f9ce91eab95d5f444622a710ff968))  
chore(package): update to react-tether@1.0.5 [#7684](https://github.com/spinnaker/deck/pull/7684) ([f25e5893](https://github.com/spinnaker/deck/commit/f25e5893ade5853ffb6bb9bc2e90d9f4afd8666f))  



## [0.0.433](https://www.github.com/spinnaker/deck/compare/6fdd3310c9aaec4a1967da7db92225be8aabf8bc...299315f3764c41f95eadace7acac70cb4a18592d) (2019-12-09)


### Changes

chore(core): Bump to version 0.0.433 ([299315f3](https://github.com/spinnaker/deck/commit/299315f3764c41f95eadace7acac70cb4a18592d))  
feat(core/presentation): add text breaking/wrapping components [#7679](https://github.com/spinnaker/deck/pull/7679) ([a296021a](https://github.com/spinnaker/deck/commit/a296021a4a7d50a517c7feee32753a7bf0c7afd4))  
feat(gcb): Ability to invoke existing GCB triggers [#7632](https://github.com/spinnaker/deck/pull/7632) ([2752cb48](https://github.com/spinnaker/deck/commit/2752cb483e29f5e24a31804f0741d34bba1634a3))  
fix(core): fix npe on first project cluster creation [#7673](https://github.com/spinnaker/deck/pull/7673) ([884d73e4](https://github.com/spinnaker/deck/commit/884d73e4e6398eb6ae30efe6343b3f6a61efed5d))  
chore(typescript): update to typescript 3.7.x [#7668](https://github.com/spinnaker/deck/pull/7668) ([145f540d](https://github.com/spinnaker/deck/commit/145f540d8bab6936a6d5bfb5caf4e1cba426f215))  
fix(page): Do not auto-close modal on success [#7672](https://github.com/spinnaker/deck/pull/7672) ([2be12fc4](https://github.com/spinnaker/deck/commit/2be12fc45e78691022542146af63f7940f9bcbc0))  
feat(core/presentation): expose all the Modal-related components ([eb47d624](https://github.com/spinnaker/deck/commit/eb47d6245a64a6173e141a6612e830ad35614fd2))  
feat(core/presentation): add <ModalFooter/> component ([36c8cd22](https://github.com/spinnaker/deck/commit/36c8cd22730b0a0ab5cbd24c27163705199822d7))  
feat(core/presentation): add <ModalBody/> component ([55f09bc5](https://github.com/spinnaker/deck/commit/55f09bc5c5a0e489215ba8ba087349a844105dce))  
feat(core/presentation): add <ModalHeader/> component ([4932d642](https://github.com/spinnaker/deck/commit/4932d642d900184c3407bf16ce3c72edc35cfb0b))  
feat(core/presentation): add <Modal/> component ([319a221a](https://github.com/spinnaker/deck/commit/319a221af392d7b5b7964e7ad38a0571b694bfb9))  
feat(core/presentation): add low-level CSS modules for color, z-index, breakpoints ([2099dee2](https://github.com/spinnaker/deck/commit/2099dee2cc44990af07bcd357147b52217986e02))  
chore(core/presentation): expose some new hooks to consumers ([da8047c9](https://github.com/spinnaker/deck/commit/da8047c9790b5478c36f13b22ab7e9f959101a1d))  
chore(deps): add react-transition-group, postcss-nested ([e4043251](https://github.com/spinnaker/deck/commit/e4043251b33e3fa4830dea59f3989a2e96956eb8))  
feat(slack): Introduce slack support channel selector [#7658](https://github.com/spinnaker/deck/pull/7658) ([bf2ffe23](https://github.com/spinnaker/deck/commit/bf2ffe23706c6d493fa5b5b0d5f37c4b7f893945))  
fix(lint): Fixes for linter rules @typescript-eslint/ban-types and @typescript-eslint/no-inferrable-types ([7f3a8a4b](https://github.com/spinnaker/deck/commit/7f3a8a4b3a290548d89c6fb579edf35e542a3932))  
fix(lint): Fixes for no-useless-escape linter rule ([9c23975e](https://github.com/spinnaker/deck/commit/9c23975e4fac47c5393119bb005353274af6148f))  
fix(angularJS): Fix all remaining non-strict angularJS DI code via @spinnaker/strictdi linter rule ([c233af0e](https://github.com/spinnaker/deck/commit/c233af0e4ab2268ab1835177ecf85122aa47e7e6))  
fix(core/pipeline): Fix for eslint rule no-case-declarations ([5b72a90b](https://github.com/spinnaker/deck/commit/5b72a90bc7521efbbfeafd9a1c9b8b6734726289))  
feat(core/presentation): add four new hooks + tests [#7659](https://github.com/spinnaker/deck/pull/7659) ([a02d71cc](https://github.com/spinnaker/deck/commit/a02d71ccd83079acf8e0a7f2e130b360f682f8d8))  
chore(tsconfig): standardize all tsconfig.json files to es2017 [#7656](https://github.com/spinnaker/deck/pull/7656) ([c1c4d423](https://github.com/spinnaker/deck/commit/c1c4d423a0af57c6a8faf135a7a7ee3eb76d5466))  
fix(amazon): Update load balancer validations to match user expectations [#7584](https://github.com/spinnaker/deck/pull/7584) ([0561762b](https://github.com/spinnaker/deck/commit/0561762b9cd8131b75f5befb255637deeec3f400))  
feat(core): add support for CSS modules to dev server + lib build [#7650](https://github.com/spinnaker/deck/pull/7650) ([dbc3eb52](https://github.com/spinnaker/deck/commit/dbc3eb52e10fd9091d0082872e7fbfa40256e2c3))  
fix(core/pipeline): ExecutionAndtagePicker Fix auto selection of stage [#7640](https://github.com/spinnaker/deck/pull/7640) ([dd0e3bbd](https://github.com/spinnaker/deck/commit/dd0e3bbd273745955ef2724a8fbc6dea634212cc))  



## [0.0.432](https://www.github.com/spinnaker/deck/compare/67c475c550bc58ce867b937b91f7df42b15857b0...6fdd3310c9aaec4a1967da7db92225be8aabf8bc) (2019-11-11)


### Changes

chore(core): Bump version to 0.0.432 ([6fdd3310](https://github.com/spinnaker/deck/commit/6fdd3310c9aaec4a1967da7db92225be8aabf8bc))  
fix(core/pipeline): Use markdown to render evaluated variables as JSON [#7628](https://github.com/spinnaker/deck/pull/7628) ([e4b432dd](https://github.com/spinnaker/deck/commit/e4b432dd71f8928bcc0386351e525737639c9952))  
test(core/presentation): Re-add removed isInitialValid logic [#7627](https://github.com/spinnaker/deck/pull/7627) ([f93a2788](https://github.com/spinnaker/deck/commit/f93a2788caa861f3ab4a844c729eaa9ef4e7631d))  
feat(provider/aws): Function create/update/delete feature. [#7586](https://github.com/spinnaker/deck/pull/7586) ([956b43bc](https://github.com/spinnaker/deck/commit/956b43bc05e1bbe471c60cacb994d460af6f74df))  



## [0.0.431](https://www.github.com/spinnaker/deck/compare/2f0817838ba0fa451df9fd74def118dbc2004f47...67c475c550bc58ce867b937b91f7df42b15857b0) (2019-11-11)


### Changes

chore(core): Bump version to 0.0.431 [#7626](https://github.com/spinnaker/deck/pull/7626) ([67c475c5](https://github.com/spinnaker/deck/commit/67c475c550bc58ce867b937b91f7df42b15857b0))  
feat(core/pipeline): Evaluate Variables: show message if no previous executions were found ([822af511](https://github.com/spinnaker/deck/commit/822af5118046f9e0d9a30727ebe2fb540ce42ff8))  
feat(core/pipeline): Added execution/stage picker and some help text to the Evaluate Variables stage - add "column" labels to Evaluate Variables stage - add one variable by default when the component loads ([804d2f96](https://github.com/spinnaker/deck/commit/804d2f9674010b935958c41027c274bda4e81616))  
feat(core/pipeline): Added an ExecutionAndStagePicker component Used in Evaluate Variables to choose the stage to preview Spel Expressions against ([554f3272](https://github.com/spinnaker/deck/commit/554f32723d354bb32654633b1f5af0d80fb03c88))  
feat(core/pipeline): Add spel preview to Evaluate Variables stage - Migrate EvaluateVariablesStageConfig from MapEditor to SpelInputs ([5eef3e74](https://github.com/spinnaker/deck/commit/5eef3e74e28c5b3789c6393bd9055725be46d2d6))  



## [0.0.430](https://www.github.com/spinnaker/deck/compare/110874903ad394dc8491de1120ec8f20b6133917...2f0817838ba0fa451df9fd74def118dbc2004f47) (2019-11-11)


### Changes

Bump package core to 0.0.430 and amazon to 0.0.223 [#7625](https://github.com/spinnaker/deck/pull/7625) ([2f081783](https://github.com/spinnaker/deck/commit/2f0817838ba0fa451df9fd74def118dbc2004f47))  
test(core/presentation): Update tests with new previewStage API ([bbbe77e8](https://github.com/spinnaker/deck/commit/bbbe77e8247ad436e17133595f9278af93673c06))  
feat(core/presentation): Add previous execution description to spel preview ([1ebd4f7e](https://github.com/spinnaker/deck/commit/1ebd4f7ec1abb7f02f0211220d2b075a6f2858f1))  
fix(core/presentation): SpelInput: when updating spel preview, continue to render the previous preview result ([15007ca4](https://github.com/spinnaker/deck/commit/15007ca40ed7f22e90a8e38b5d63be4bd5c8bf8e))  
fix(core/utils): Invoke callback on null/undefined properties [#7620](https://github.com/spinnaker/deck/pull/7620) ([2db2458d](https://github.com/spinnaker/deck/commit/2db2458df58a9291a5c32be4321ff6bcb774a2e4))  
fix(core/presentation): Do not render SpinFormik form until formik has been initialized. [#7619](https://github.com/spinnaker/deck/pull/7619) ([5bbbd010](https://github.com/spinnaker/deck/commit/5bbbd0104345aa88044f9c4501e56d5565f6c6db))  
feat(core/managed): visually refresh/rebrand infra details indicator [#7617](https://github.com/spinnaker/deck/pull/7617) ([fc054fe1](https://github.com/spinnaker/deck/commit/fc054fe1fd9260e40035b531fb00085f6e7f4035))  
fix(core/managed): properly diff/patch mutable infra groupings [#7618](https://github.com/spinnaker/deck/pull/7618) ([9c8ac868](https://github.com/spinnaker/deck/commit/9c8ac8684f8bedb8a10e4b0f0213f62173e07ae1))  
feat(core/presentation): Create a SpelInput that supports server-side preview of spel expressions (against previous executions) ([ae910484](https://github.com/spinnaker/deck/commit/ae9104842489171c6b63fa3419f931d623055428))  
feat(core/spel): Add a SpelService to (initially) evaluate expressions on the Server ([55beff72](https://github.com/spinnaker/deck/commit/55beff728f11b45f15c00e2f64e9f54a7f32a48c))  
refactor(core/presentation): use useInternalValidator in NumberInput ([b01e6db7](https://github.com/spinnaker/deck/commit/b01e6db7540a2514570907bdcbb460957c3f52f9))  
feat(core/presentation): Create a useInternalValidator hook for FormInputs to use ([49f880f9](https://github.com/spinnaker/deck/commit/49f880f9137843656e8ae1796f96edfc499ad283))  
feat(core/presentation): Made useData hook default result behavior more reasonable [#7602](https://github.com/spinnaker/deck/pull/7602) ([e370d47c](https://github.com/spinnaker/deck/commit/e370d47c9577c605ad6415a316d69e450ac9a5ae))  
feat(core/managed): add RESUMED resource status [#7611](https://github.com/spinnaker/deck/pull/7611) ([9d99e04e](https://github.com/spinnaker/deck/commit/9d99e04ee90a44f8c02a1381ec425d949f6fda2c))  
feat(core/managed): add deep links to status reference doc [#7610](https://github.com/spinnaker/deck/pull/7610) ([f49fedf5](https://github.com/spinnaker/deck/commit/f49fedf59233c857a933b1cebee4dd8b984c0c4b))  
fix(core/presentation): Handle 'null' in orEmptyString helper [#7606](https://github.com/spinnaker/deck/pull/7606) ([9abd35fa](https://github.com/spinnaker/deck/commit/9abd35faf1b4724f33a1341e9149cb7a9372d6ac))  
feat(core/presentation): Mark all SpinFormik initialValues fields as 'touched' [#7604](https://github.com/spinnaker/deck/pull/7604) ([e0cc0d62](https://github.com/spinnaker/deck/commit/e0cc0d62d86ef620f95b60cea310107ae95239b5))  
feat(core/presentation): Add generic type param (for selected data type) to ReactSelectInput [#7603](https://github.com/spinnaker/deck/pull/7603) ([431cb860](https://github.com/spinnaker/deck/commit/431cb860c649b6a80011504d4091e69e5513fca8))  
fix(core/presentation): MapEditor: Make errors fill the entire row width. ([e6280e59](https://github.com/spinnaker/deck/commit/e6280e5989aea0c9517724ff0c2f5a72737ac398))  
fix(core/presentation): FormikFormField: call revalidate whenever internal validators change ([3df215b4](https://github.com/spinnaker/deck/commit/3df215b490b45caa7efb8d01e747b9494ac654cb))  
fix(core/presentation): pass objects through in useValidationData There's a weird case where a FormikFormField may be used for a complex object with multiple fields and/or arrays. In this case, the Field's validator should return a structured error object. This isn't fully accounted for in the FormField API. For now, pass the complex error object through as 'validationMessage'. Tag it as "hidden" so we don't try to render it elsewhere as a ReactNode. ([ae396e5f](https://github.com/spinnaker/deck/commit/ae396e5f509bf09bf5c5ce546b9cb6d2cba95765))  
fix(artifacts): enable inline editing of base64 artifacts [#7612](https://github.com/spinnaker/deck/pull/7612) ([ef35c5c0](https://github.com/spinnaker/deck/commit/ef35c5c0c999d06f7fc395aee6c6ee3af9ec75c9))  
chore(core/presentation): rename test file to FormValidator.spec.ts ([97a033f9](https://github.com/spinnaker/deck/commit/97a033f939b3ce14d118480257332f86827af59f))  
fix(core/application): add error state to application models, log exceptions [#7599](https://github.com/spinnaker/deck/pull/7599) ([d3435124](https://github.com/spinnaker/deck/commit/d34351245c2207bd459cc9cc66e974f659f8f85f))  
fix(core/presentation): remove style + wrapperClassName from HoverablePopover [#7597](https://github.com/spinnaker/deck/pull/7597) ([236e6231](https://github.com/spinnaker/deck/commit/236e623144eca4c5a63e69195ac040344c42917c))  
feat(script): expose propertiesFile field [#7595](https://github.com/spinnaker/deck/pull/7595) ([7a81a663](https://github.com/spinnaker/deck/commit/7a81a66316ca672838bac5b745db4635378c3e69))  



## [0.0.429](https://www.github.com/spinnaker/deck/compare/951c4ba7fa84b5bc85276ba3d45897e754015f8a...110874903ad394dc8491de1120ec8f20b6133917) (2019-11-05)


### Changes

chore(core): Bump version to 0.0.429 [#7596](https://github.com/spinnaker/deck/pull/7596) ([11087490](https://github.com/spinnaker/deck/commit/110874903ad394dc8491de1120ec8f20b6133917))  
feat(managed): Add status popovers, new props for HoverablePopover ([5acb0d07](https://github.com/spinnaker/deck/commit/5acb0d07501d950f46bb715864fa00b0df4b7629))  
feat(managed): add resource status indicator to security groups ([88b8e6c3](https://github.com/spinnaker/deck/commit/88b8e6c35d2374f1f4617588295624b029e34683))  
feat(managed): add managed resource data to security group groups ([f02b70de](https://github.com/spinnaker/deck/commit/f02b70deb3bc8bc647bb75f2b408dd9b03893aa1))  
feat(managed): add resource status indicator to load balancers ([14283b2b](https://github.com/spinnaker/deck/commit/14283b2bdd095045c20aafd63ee8c680f8819585))  
feat(managed): add managed resource data to load balancer groups ([aaa349b4](https://github.com/spinnaker/deck/commit/aaa349b4db4212acfb9a04b414a4807af9cdf889))  
feat(managed): add resource status indicator to clusters ([a6d77563](https://github.com/spinnaker/deck/commit/a6d77563030ee30917a171db851a7a49e16459ac))  
feat(managed): add managed resource data to cluster groups ([8453260d](https://github.com/spinnaker/deck/commit/8453260ddbdf53a40b10a7c05076c0623e8a5a16))  
feat(dataSources): add runtime error when defaultData isn't provided [#7591](https://github.com/spinnaker/deck/pull/7591) ([167a1512](https://github.com/spinnaker/deck/commit/167a1512fdc6bb2fefa0f47a1ce309e56392011e))  
feat(core/presentation): Reduce unnecessary renders in useLatestPromise, add tests ([a10e0684](https://github.com/spinnaker/deck/commit/a10e0684fc28d47b849ff14d5be291d571421dac))  
feat(core/presentation): Reduce unnecessary renders in useDebouncedValue, add tests ([90b249d7](https://github.com/spinnaker/deck/commit/90b249d798cbd149e2fa11149fc0f242c7be09e9))  
refactor(core/presentation): extract useIsMountedRef hook ([7ff7aa1e](https://github.com/spinnaker/deck/commit/7ff7aa1e8ba64ee041aaddfcc79c2f963040dd65))  
chore(package): update to @types/enzyme@3.10.3, enzyme@3.10.0, enzyme-adapter-react-16@1.15.1 [#7585](https://github.com/spinnaker/deck/pull/7585) ([b18b577d](https://github.com/spinnaker/deck/commit/b18b577d661d194e95626fb9e5afc674c56cafaa))  
chore(help): Update help.contents.ts [#7588](https://github.com/spinnaker/deck/pull/7588) ([df7120ac](https://github.com/spinnaker/deck/commit/df7120ac0e94e76ee6b7a6b84f0f9235af2c5896))  
fix(rerun): Hiding re-run as strategies should not be re-run [#7583](https://github.com/spinnaker/deck/pull/7583) ([98e8a4bf](https://github.com/spinnaker/deck/commit/98e8a4bfeb3df0b9927c0e06c5e1ca3b0f1a97d4))  
fix(helptext): clarify text for stage timeout [#7336](https://github.com/spinnaker/deck/pull/7336) ([8d478a08](https://github.com/spinnaker/deck/commit/8d478a08ebcaea64f45878172cb16f3a91f61418))  



## [0.0.428](https://www.github.com/spinnaker/deck/compare/f8b7b1ab9100b5803ff04f6b2975d9d0f3cb58b8...951c4ba7fa84b5bc85276ba3d45897e754015f8a) (2019-10-30)


### Changes

chore(core): Bump version to 0.0.428 ([951c4ba7](https://github.com/spinnaker/deck/commit/951c4ba7fa84b5bc85276ba3d45897e754015f8a))  
feat(managed): Update resource indicators to use new data source ([34a3b00b](https://github.com/spinnaker/deck/commit/34a3b00beea0287a68a05014862351c0f5904afa))  
feat(managed): Join managed data to infra data, add moniker to Security Groups ([7b33d2b4](https://github.com/spinnaker/deck/commit/7b33d2b490987a070febbd3478e902dd37c6b7b7))  
feat(managed): Use new data source for 'Managed Resources' config section ([d4c89cef](https://github.com/spinnaker/deck/commit/d4c89ceffd2f14eca7660a930d1d675a737da150))  
feat(managed): add 'managedResources' data source ([efcceb34](https://github.com/spinnaker/deck/commit/efcceb34a541ad71d887f0957fe43741a4ed1de4))  
feat(dataSources): widen + parameterize types, add default values ([4ed015a0](https://github.com/spinnaker/deck/commit/4ed015a07c028eb58807601a0b0fb9783b02b0d9))  
Reactify titus launch configuration [#7581](https://github.com/spinnaker/deck/pull/7581) ([8c29cbae](https://github.com/spinnaker/deck/commit/8c29cbae0278695316ba493a9f0cb7ae52b51d10))  
feat(google): add gce scale-down controls [#7570](https://github.com/spinnaker/deck/pull/7570) ([5d89a044](https://github.com/spinnaker/deck/commit/5d89a04465f911d49bf171ef47b9d88aa2b8c4a9))  
feat(git/repo): add git/repo artifact support in kustomize bake manifest [#7572](https://github.com/spinnaker/deck/pull/7572) ([f8825445](https://github.com/spinnaker/deck/commit/f88254455238b566936fcca02e0a9e7f0a647958))  



## [0.0.427](https://www.github.com/spinnaker/deck/compare/99bb034204437313b194117e987dda2dc597d559...f8b7b1ab9100b5803ff04f6b2975d9d0f3cb58b8) (2019-10-28)


### Changes

chore(core): Bump version to 0.0.427 ([f8b7b1ab](https://github.com/spinnaker/deck/commit/f8b7b1ab9100b5803ff04f6b2975d9d0f3cb58b8))  
fix(core/pipeline): fully re-render list of trigger configs after a delete [#7571](https://github.com/spinnaker/deck/pull/7571) ([d71daa55](https://github.com/spinnaker/deck/commit/d71daa55481e3ba1895b25831c26f01d9e981b53))  
fix(core/pipeline): make revision dropdown usable, layout tweaks [#7569](https://github.com/spinnaker/deck/pull/7569) ([629a98f6](https://github.com/spinnaker/deck/commit/629a98f62a9c8b12dad4337e34ef60b46ecfef4d))  
feat(provider/aws): Functions (listing and searching) [#7568](https://github.com/spinnaker/deck/pull/7568) ([ca176fc3](https://github.com/spinnaker/deck/commit/ca176fc325edc41ee90d22b2c3d1aaa041a9c434))  
Revert "feat(provider/aws): Functions (listing and searching) (#7536)" [#7567](https://github.com/spinnaker/deck/pull/7567) ([e49ffaf4](https://github.com/spinnaker/deck/commit/e49ffaf4d5896294cf66300167aefdecbf36499c))  
fix(kubernetes): disable project cluster filtration by stack/detail [#7562](https://github.com/spinnaker/deck/pull/7562) ([114303ac](https://github.com/spinnaker/deck/commit/114303aca9804c2300f2554a5702644b32d3f481))  
feat(provider/aws): Functions (listing and searching) [#7536](https://github.com/spinnaker/deck/pull/7536) ([86a365bd](https://github.com/spinnaker/deck/commit/86a365bd406125498c1bbc45de2ee4d67f9fd0d5))  
fix(core/pipeline): make UX less bad when a pipeline stage never happened [#7563](https://github.com/spinnaker/deck/pull/7563) ([6236a9fd](https://github.com/spinnaker/deck/commit/6236a9fdabf968954155ee3c75eeff99d98cfff3))  



## [0.0.426](https://www.github.com/spinnaker/deck/compare/cc8c9bf8e5a3363c0792b404e9565acae597721d...99bb034204437313b194117e987dda2dc597d559) (2019-10-24)


### Changes

chore(core): Bump version to 0.0.426 ([99bb0342](https://github.com/spinnaker/deck/commit/99bb034204437313b194117e987dda2dc597d559))  
fix(core/pipeline): Force rebake not displayed for templated pipelines [#7558](https://github.com/spinnaker/deck/pull/7558) ([17bf82af](https://github.com/spinnaker/deck/commit/17bf82af6024cd0f3b60c58f851c900ff420ab9e))  
chore(core/notification): remove remainder of hipchat notifications [#7557](https://github.com/spinnaker/deck/pull/7557) ([81eaac6c](https://github.com/spinnaker/deck/commit/81eaac6c753e281c9d101a328492d2d695a783a1))  
fix(config): Fix typings for SpinnakerSettings [#7556](https://github.com/spinnaker/deck/pull/7556) ([230ffb5f](https://github.com/spinnaker/deck/commit/230ffb5f65a128a21475855c675c917c41cb90a3))  
fix(core/config): Fix typing for githubStatus notification type ([727300df](https://github.com/spinnaker/deck/commit/727300df77b705b2fc230f3286d73308daa013ea))  
fix(core/instance): use fragment on non-UP health code path [#7555](https://github.com/spinnaker/deck/pull/7555) ([4ae63f4f](https://github.com/spinnaker/deck/commit/4ae63f4fd75c1851630130181dd6f23bb243c0e3))  
chore(settings): Remove unused feature toggle: 'jobs' [#7553](https://github.com/spinnaker/deck/pull/7553) ([cd8c2322](https://github.com/spinnaker/deck/commit/cd8c232290c35d7bae0d91b73694ee8a8f78705a))  



## [0.0.425](https://www.github.com/spinnaker/deck/compare/8668bb903b74c04066a98ad2ce91cbe528d427ac...cc8c9bf8e5a3363c0792b404e9565acae597721d) (2019-10-23)


### Changes

Bump package core to 0.0.425 and amazon to 0.0.218 [#7551](https://github.com/spinnaker/deck/pull/7551) ([cc8c9bf8](https://github.com/spinnaker/deck/commit/cc8c9bf8e5a3363c0792b404e9565acae597721d))  
chore(settings): remove defaultCategory from settings.js [#7550](https://github.com/spinnaker/deck/pull/7550) ([2d784317](https://github.com/spinnaker/deck/commit/2d7843174e00c5fda9acf8944845e91867904885))  
feat(core): alphabetize applications in project config dropdown [#7549](https://github.com/spinnaker/deck/pull/7549) ([f57c70b4](https://github.com/spinnaker/deck/commit/f57c70b40468e7b7f574fc2a71aab07ec328a12c))  
feat(core/jenkins): Refer to Jenkins controller instead of master [#7531](https://github.com/spinnaker/deck/pull/7531) ([78cbe156](https://github.com/spinnaker/deck/commit/78cbe1561e0d5961d15b5dae4c437686de355089))  
feat(core/presentation): add revalidate api to IFormInputValidation - Make all fields on IFormInputValidation non-optional ([8c0d087a](https://github.com/spinnaker/deck/commit/8c0d087a0ae7d7e4bd2f1ca7e2e3d99c36518feb))  
feat(core/presentation): Add useDebouncedValue react hook renamed hooks files to '.hook.ts' and exported all from an index.ts ([b3840382](https://github.com/spinnaker/deck/commit/b3840382a2006637fc10e174af3986899910a776))  
feat(kubernetes): support rolling restart operation for deployments [#7538](https://github.com/spinnaker/deck/pull/7538) ([17be6af0](https://github.com/spinnaker/deck/commit/17be6af0ce874a98ce07ece879c0e4f8522ed78b))  
fix(monitored deploy): fix the rollback config to match what orca expects [#7532](https://github.com/spinnaker/deck/pull/7532) ([7e8cb7e0](https://github.com/spinnaker/deck/commit/7e8cb7e0e1ef58d315e0cfe83e3f3e199979f908))  



## [0.0.424](https://www.github.com/spinnaker/deck/compare/cc49e1a4f913507cf9c32ea003e644005196c164...8668bb903b74c04066a98ad2ce91cbe528d427ac) (2019-10-16)


### Changes

chore(core): Bump version to 0.0.424 ([8668bb90](https://github.com/spinnaker/deck/commit/8668bb903b74c04066a98ad2ce91cbe528d427ac))  
fix(artifact/bitbucket): Bitbucket Use Default Artifact [#7523](https://github.com/spinnaker/deck/pull/7523) ([c8c06e4f](https://github.com/spinnaker/deck/commit/c8c06e4f3c4d4f8372e0ffd9fb6226d0f42fee03))  
feat(ui): Show health check url beside target group [#7520](https://github.com/spinnaker/deck/pull/7520) ([ed7c4458](https://github.com/spinnaker/deck/commit/ed7c44589cfbf482047a0f47d30df97165053aa4))  



## [0.0.423](https://www.github.com/spinnaker/deck/compare/35241afd7dc58cc129b71ac35bf459b3617da73a...cc49e1a4f913507cf9c32ea003e644005196c164) (2019-10-15)


### Changes

chore(core): Bump version to 0.0.423 ([cc49e1a4](https://github.com/spinnaker/deck/commit/cc49e1a4f913507cf9c32ea003e644005196c164))  
fix(core/presentation): Fix null reference in FieldLayout components ([64d88cca](https://github.com/spinnaker/deck/commit/64d88ccadf0d25f9271882ef1dbf4858a20149db))  



## [0.0.422](https://www.github.com/spinnaker/deck/compare/c8d6c7135da1cfe7b7da6cab823684b44a75563e...35241afd7dc58cc129b71ac35bf459b3617da73a) (2019-10-14)


### Changes

chore(core): Bump version to 0.0.422 ([35241afd](https://github.com/spinnaker/deck/commit/35241afd7dc58cc129b71ac35bf459b3617da73a))  
fix(core/presentation): Fix FormikExpressionInput initial state ([088f50b2](https://github.com/spinnaker/deck/commit/088f50b290f143e71edcfe635f67ce25089f1c2d))  



## [0.0.421](https://www.github.com/spinnaker/deck/compare/e56cc29684ce32bdf656a89e0e111b4d2cb5cbc4...c8d6c7135da1cfe7b7da6cab823684b44a75563e) (2019-10-11)


### Changes

chore(core): Bump version to 0.0.421 ([c8d6c713](https://github.com/spinnaker/deck/commit/c8d6c7135da1cfe7b7da6cab823684b44a75563e))  
fix(core/presentation): Do not use a regexp with /test/s because it's not supported in firefox [#7518](https://github.com/spinnaker/deck/pull/7518) ([c1a28fef](https://github.com/spinnaker/deck/commit/c1a28fef4097b5b5bd3f1c7c45f4140d82f65f1e))  



## [0.0.420](https://www.github.com/spinnaker/deck/compare/d0777fad5c712fdd6225169965c3e183d25f8347...e56cc29684ce32bdf656a89e0e111b4d2cb5cbc4) (2019-10-11)


### Changes

chore(core): Bump version to 0.0.420 ([e56cc296](https://github.com/spinnaker/deck/commit/e56cc29684ce32bdf656a89e0e111b4d2cb5cbc4))  
fix(core/presentation): fix null reference in ReactSelectInput [#7515](https://github.com/spinnaker/deck/pull/7515) ([c18f307e](https://github.com/spinnaker/deck/commit/c18f307e555a07b0088fa72aab54e2ae48feae3a))  
feat(core/presentation): allow markdown in ValidationMessage ([075bce18](https://github.com/spinnaker/deck/commit/075bce18b42af069d153bcaa6cbcb723a19964c6))  
fix(core/presentation): Make all DOMPurify'd links open in a new window [#7511](https://github.com/spinnaker/deck/pull/7511) ([aef69cfc](https://github.com/spinnaker/deck/commit/aef69cfc321a0a3569895d04e3c7548afee2c70d))  
fix(bakeManifest/helm): rawOverrides option [#7514](https://github.com/spinnaker/deck/pull/7514) ([a0829090](https://github.com/spinnaker/deck/commit/a0829090fb576a36c173dced5d15af8a68c6db84))  
fix(deck): Show fail fast status code only if they are not pre-configured [#7512](https://github.com/spinnaker/deck/pull/7512) ([752b1ab7](https://github.com/spinnaker/deck/commit/752b1ab713b6ed478afb585b4844c71ecb0619a6))  
fix(core/pipeline): fix Artifactory and Nexus trigger NPE These were throwing upon initial mount because 'error' wasn't defined. ([d03ce484](https://github.com/spinnaker/deck/commit/d03ce4848f37c09341474a0a2b79a778767b8e88))  
refactor: Migrate user code to new validation message API ([a9469e97](https://github.com/spinnaker/deck/commit/a9469e9723fb5b5e539ce5400e7a7077549b4245))  
refactor(core/presentation): Remove validationStatus from forms apis ([4c5f532b](https://github.com/spinnaker/deck/commit/4c5f532b395fd5a660dcca1d2d56b4f3b470ec67))  
refactor(core/presentation): Split forms interfaces into three files: - /forms/fields/interface.ts - /forms/inputs/interface.ts - /forms/layouts/interface.ts ([eafb077c](https://github.com/spinnaker/deck/commit/eafb077ca8b6f43cbddb42478b55c355cbed68da))  
feat(core/utils): extract firstDefined utility function ([2d6c9e78](https://github.com/spinnaker/deck/commit/2d6c9e783734f072f314f543db8c5e2e2ad9d858))  
refactor(core/validation): Move ValidationMessage from core/validation to core/presentation/forms/validation ([b5534656](https://github.com/spinnaker/deck/commit/b5534656251c92180f8598cb22e8f50574e9cf1a))  
feat(core/presentation): Extract useValidationData hook ([85182869](https://github.com/spinnaker/deck/commit/85182869d63c7645cc1061c64286fceb8235c07f))  
fix(core/presentation): Handle empty message in validation message functions, e.g., `errorMessage(undefined)` ([aad69b04](https://github.com/spinnaker/deck/commit/aad69b04478fd8bf3ba17906b4f49c8fec47d842))  
fix(core): fix jumping cursor in trigger inputs [#7486](https://github.com/spinnaker/deck/pull/7486) ([8529faf7](https://github.com/spinnaker/deck/commit/8529faf7919b097c06e5c4f8b1dc8c3f60977df5))  
fix(core/pipeline): Pass Trigger validateFn to the trigger's Formik ([36192ab7](https://github.com/spinnaker/deck/commit/36192ab76b03c85f4a19fdea0a142b028d85fea0))  



## [0.0.419](https://www.github.com/spinnaker/deck/compare/b8207c45d28d1ae702120b3c52a62222652efc4f...d0777fad5c712fdd6225169965c3e183d25f8347) (2019-10-08)


### Changes

chore(core): Bump version to 0.0.419 ([d0777fad](https://github.com/spinnaker/deck/commit/d0777fad5c712fdd6225169965c3e183d25f8347))  
fix(ui): Add icon when target group registration in progress [#7502](https://github.com/spinnaker/deck/pull/7502) ([438d22cd](https://github.com/spinnaker/deck/commit/438d22cd77057028157bc03df1e5a358e0baf55f))  
refactor(application): reactify delete application section [#7501](https://github.com/spinnaker/deck/pull/7501) ([babbe889](https://github.com/spinnaker/deck/commit/babbe889c25ef401668d716d24798cd0954ed922))  



## [0.0.418](https://www.github.com/spinnaker/deck/compare/da0c2c4b5ba2d4a3067873692746dde1ae20fd4d...b8207c45d28d1ae702120b3c52a62222652efc4f) (2019-10-07)


### Changes

chore(core): Bump version to 0.0.418 ([b8207c45](https://github.com/spinnaker/deck/commit/b8207c45d28d1ae702120b3c52a62222652efc4f))  
fix(core/pipeline): KLUDGE: use react 'key' to reinitialize formik when pipeline reverted ([829c2951](https://github.com/spinnaker/deck/commit/829c2951108404f434c325360e2771b97f18595b))  



## [0.0.417](https://www.github.com/spinnaker/deck/compare/ca7e70b1d41de96b7f25cd654736e4b0a11119ac...da0c2c4b5ba2d4a3067873692746dde1ae20fd4d) (2019-10-07)


### Changes

chore(core): Bump version to 0.0.417 ([da0c2c4b](https://github.com/spinnaker/deck/commit/da0c2c4b5ba2d4a3067873692746dde1ae20fd4d))  
fix(core/pipeline): When a trigger is updated, replace the entire object [#7496](https://github.com/spinnaker/deck/pull/7496) ([3192f2cf](https://github.com/spinnaker/deck/commit/3192f2cf246de552a79e32387d53ecba24e32b13))  
feat(core): filter out providers that don't support creating security groups [#7370](https://github.com/spinnaker/deck/pull/7370) ([64e47b4a](https://github.com/spinnaker/deck/commit/64e47b4a294c1ff39b4ff724715b0d8a09f55451))  
fix(core): Separate how config and plans are updated, add tests [#7491](https://github.com/spinnaker/deck/pull/7491) ([2ef87886](https://github.com/spinnaker/deck/commit/2ef878860df5d9c2135ff68400fa57fab3e17cc0))  



## [0.0.416](https://www.github.com/spinnaker/deck/compare/747123139d2fc2da25c7771a9f718eaafa3e71c3...ca7e70b1d41de96b7f25cd654736e4b0a11119ac) (2019-10-04)


### Changes

chore(core): Bump version to 0.0.416 [#7493](https://github.com/spinnaker/deck/pull/7493) ([ca7e70b1](https://github.com/spinnaker/deck/commit/ca7e70b1d41de96b7f25cd654736e4b0a11119ac))  
feat(core/presentation): Add helper functions for generating categorized validation messages [#7488](https://github.com/spinnaker/deck/pull/7488) ([a5f192e8](https://github.com/spinnaker/deck/commit/a5f192e84c3294b40aabec5980dc6b122ea9c456))  
fix(pipeline): triggers were not reverting in the ui [#7485](https://github.com/spinnaker/deck/pull/7485) ([7d91de69](https://github.com/spinnaker/deck/commit/7d91de697e9d653064924aa37fa577fb8926f06c))  
refactor(core/help): Migrate HelpContext to react hooks style [#7487](https://github.com/spinnaker/deck/pull/7487) ([5c3fcc74](https://github.com/spinnaker/deck/commit/5c3fcc749c307f642a50271c44532af344fda910))  
feat(core/presentation): Migrate ValidationMessage to new CSS styles [#7481](https://github.com/spinnaker/deck/pull/7481) ([3c08b388](https://github.com/spinnaker/deck/commit/3c08b388e6a00d4c9d14ad9babeb857e99d3d0e2))  
fix(triggers): fix a few minor issues with manual execution triggers [#7484](https://github.com/spinnaker/deck/pull/7484) ([523c6bb5](https://github.com/spinnaker/deck/commit/523c6bb5bfdd5e95fd884321c4eec49023fa3b47))  
fix(monitored deploy): properly initialize defaults [#7473](https://github.com/spinnaker/deck/pull/7473) ([0d0caa99](https://github.com/spinnaker/deck/commit/0d0caa994f2fa70b75e8d6d62b28ef7fca8d2e8a))  
feat(core/presentation): Put margin between StandardFieldLayout's input and validation [#7476](https://github.com/spinnaker/deck/pull/7476) ([dc2a74bb](https://github.com/spinnaker/deck/commit/dc2a74bbccf74e30d050f5c77309bc34d92c298c))  
refactor(pipeline): Reactify the copy stage modal [#7453](https://github.com/spinnaker/deck/pull/7453) ([6cd7e9d2](https://github.com/spinnaker/deck/commit/6cd7e9d2ca237e4ee579e5916921a8f923fb5874))  
feat(precondition): add custom message to precondition [#7448](https://github.com/spinnaker/deck/pull/7448) ([5867de1e](https://github.com/spinnaker/deck/commit/5867de1eafecbc466f81d82056a0597fbc153ce2))  
fix(artifacts/bitbucket): Update the help key to the correct reference to bitbucket [#7475](https://github.com/spinnaker/deck/pull/7475) ([f121b71a](https://github.com/spinnaker/deck/commit/f121b71ac68d6e254b02bdab868da4a4f1877bd4))  
fix(core/serverGroup): Correct 'simple scaling' heuristic [#7385](https://github.com/spinnaker/deck/pull/7385) ([ee18eb04](https://github.com/spinnaker/deck/commit/ee18eb0445736fb24a1b086a68384f5f0734010a))  
fix(core/utils): Support traversing keys which contain dots in them using array notation [#7471](https://github.com/spinnaker/deck/pull/7471) ([30c7f2c8](https://github.com/spinnaker/deck/commit/30c7f2c89471b3f6fd82b07676766af60f23e3a8))  
feat(core/presentation): Begin adding support for error categories in validation API [#7467](https://github.com/spinnaker/deck/pull/7467) ([f2790a77](https://github.com/spinnaker/deck/commit/f2790a77a2a164dbe8eebe3d037bb9cdb6f00e88))  



## [0.0.415](https://www.github.com/spinnaker/deck/compare/3377ee18c7c9184b4c0f91b79b6a91e9681ebbb1...747123139d2fc2da25c7771a9f718eaafa3e71c3) (2019-10-01)


### Changes

chore(core): Bump version to 0.0.415 ([74712313](https://github.com/spinnaker/deck/commit/747123139d2fc2da25c7771a9f718eaafa3e71c3))  
feat(monitored deploy): add basic monitored deploy UI [#7426](https://github.com/spinnaker/deck/pull/7426) ([b55a49a7](https://github.com/spinnaker/deck/commit/b55a49a7337ff9db8f435be7437877adc1184b79))  
fix(artifacts/bitbucket): Allow updates to bitbucket default artifact text input Allows updates to the artifact text input even when the regex pattern does not match the bitbucket cloud regex Create regex pattern for bitbucket server to match input against Fixes https://github.com/spinnaker/spinnaker/issues/4958 ([5e255016](https://github.com/spinnaker/deck/commit/5e255016aeac4bceae09fb152de5bf6be127ea54))  



## [0.0.414](https://www.github.com/spinnaker/deck/compare/5414465cd7021fc72315f27f03d5ce8ae89b3131...3377ee18c7c9184b4c0f91b79b6a91e9681ebbb1) (2019-09-30)


### Changes

chore(core): Bump version to 0.0.414 ([3377ee18](https://github.com/spinnaker/deck/commit/3377ee18c7c9184b4c0f91b79b6a91e9681ebbb1))  
fix(core/task): properly cleanup TaskMonitor polling, fix digest thrashing [#7458](https://github.com/spinnaker/deck/pull/7458) ([57d28c9c](https://github.com/spinnaker/deck/commit/57d28c9c0b15611b6b4aeceb37d17c47aab1e128))  
feat(displayName): Adding display name property for the bakery baseOS options [#7464](https://github.com/spinnaker/deck/pull/7464) ([564af884](https://github.com/spinnaker/deck/commit/564af8847915288f5db3c9baaafcee72bb0c13e7))  
fix(bakeManifest): fix bake manifest UI rendering [#7463](https://github.com/spinnaker/deck/pull/7463) ([e0c57aff](https://github.com/spinnaker/deck/commit/e0c57aff7768361fe0d1c0fa7777f0b07b54e0b9))  
fix(core/infrastructure): Fix deep links with filters [#7459](https://github.com/spinnaker/deck/pull/7459) ([f41d9554](https://github.com/spinnaker/deck/commit/f41d95544c563d8814ba4d6ce730162c21fd1c1d))  
fix(help text): update webhook help text [#7456](https://github.com/spinnaker/deck/pull/7456) ([01bc0dae](https://github.com/spinnaker/deck/commit/01bc0dae2b61aa03bd38d87958c462199517a53b))  
refactor(core/validation): move FormValidator classes to separate files [#7460](https://github.com/spinnaker/deck/pull/7460) ([53300b15](https://github.com/spinnaker/deck/commit/53300b154ab526b057d197dca23033e75a735403))  
feat(core/utils): Add `traverseObject` which deeply walks object properties [#7452](https://github.com/spinnaker/deck/pull/7452) ([1873094f](https://github.com/spinnaker/deck/commit/1873094fa886f96ab29dac5b551123043a450c05))  
refactor(pipeline): reactify pipeline config actions dropdown [#7447](https://github.com/spinnaker/deck/pull/7447) ([f535e525](https://github.com/spinnaker/deck/commit/f535e525d4145a0d7ae744734ee821272af29e8f))  
fix(bake/kustomize): fix name validation [#7450](https://github.com/spinnaker/deck/pull/7450) ([677ab6b8](https://github.com/spinnaker/deck/commit/677ab6b84c0b3941a9a6f504ed5cb9d4e73da1b6))  
fix(core/pipeline): Revision history is vertically challenged in Safari [#7449](https://github.com/spinnaker/deck/pull/7449) ([4f24db3a](https://github.com/spinnaker/deck/commit/4f24db3ab970ea14f31d27209bc40e2875e216fb))  
feat(core/utils): Add 'api' to window.spinnaker object for interactive debugging [#7439](https://github.com/spinnaker/deck/pull/7439) ([592e74ff](https://github.com/spinnaker/deck/commit/592e74ffc8a0d09ec07e90e72e71702ab37734ba))  
fix(pipeline): unset `locked` instead of `lock` when unlocking pipeline [#7445](https://github.com/spinnaker/deck/pull/7445) ([585ac157](https://github.com/spinnaker/deck/commit/585ac1578e1ec468aaf2fe601cfce20a91b7b257))  
fix(core/pipeline): "Depends On" doesn't always update when reverting [#7441](https://github.com/spinnaker/deck/pull/7441) ([67fc87eb](https://github.com/spinnaker/deck/commit/67fc87eb3813f1ad3f490b505f2fa6f2137d9a9e))  
fix(core/pipeline): Fix revert button for non-templated pipelines [#7440](https://github.com/spinnaker/deck/pull/7440) ([bd4ba0e7](https://github.com/spinnaker/deck/commit/bd4ba0e7ee03d317a42e4867f403ca2bb843ff56))  
feat(core/presentation): Migrate form validation API to class-based API ([45f3c248](https://github.com/spinnaker/deck/commit/45f3c248a2df72905484f8a90416b1d339e29660))  
fix(core/presentation): In min/max validators, validate that the value is a number ([447d76f8](https://github.com/spinnaker/deck/commit/447d76f81c069406871c08dbef0e15489b8338bd))  



## [0.0.413](https://www.github.com/spinnaker/deck/compare/245cb843a3a08384b8e12054bb6be2146e2b0ece...5414465cd7021fc72315f27f03d5ce8ae89b3131) (2019-09-25)


### Changes

chore(core): Bump version to 0.0.413 [#7438](https://github.com/spinnaker/deck/pull/7438) ([5414465c](https://github.com/spinnaker/deck/commit/5414465cd7021fc72315f27f03d5ce8ae89b3131))  
fix(core/pipeline): Fixes UX of saving non-templated pipelines [#7437](https://github.com/spinnaker/deck/pull/7437) ([2b7b60c3](https://github.com/spinnaker/deck/commit/2b7b60c384e5d29454fa4553402960c6fe0d7701))  
feat(core/forms): Remove all async support from the spinnaker validation apis [#7435](https://github.com/spinnaker/deck/pull/7435) ([6ccc8cd1](https://github.com/spinnaker/deck/commit/6ccc8cd1c5c20a5274c68c4123b06fdd91971796))  
fix(pipeline): Fix NPE in stage requisiteStageRefIds [#7417](https://github.com/spinnaker/deck/pull/7417) ([bfa0d173](https://github.com/spinnaker/deck/commit/bfa0d1731cc34a4ef5f0cf3cc37466d303726d32))  
fix(core/pipeline): Fix execution details chevron for grouped stages [#7421](https://github.com/spinnaker/deck/pull/7421) ([a125b03c](https://github.com/spinnaker/deck/commit/a125b03c26f486e41651f490f8ed3aea270b18ea))  



## [0.0.412](https://www.github.com/spinnaker/deck/compare/fa47f48f9fef478927c2a40aebc431b987bd235a...245cb843a3a08384b8e12054bb6be2146e2b0ece) (2019-09-24)


### Changes

chore(core): Bump version to 0.0.412 ([245cb843](https://github.com/spinnaker/deck/commit/245cb843a3a08384b8e12054bb6be2146e2b0ece))  
fix(tasks): (re)-enable durations on canceled tasks [#7433](https://github.com/spinnaker/deck/pull/7433) ([91e4c42d](https://github.com/spinnaker/deck/commit/91e4c42d33caf4a944786cf1c356a2b6a251286e))  
fix(core/pipeline): migrate more manual execution field layouts ([4d3454c8](https://github.com/spinnaker/deck/commit/4d3454c87ec15fcd065af43766190ac114b3e30c))  
fix(core/forms): MapEditorInput: Add validation for empty keys and empty values. Remove `errors` prop in favor of using `validation.validationMessage` which is passed from FormField/FormikFormField ([15de4a81](https://github.com/spinnaker/deck/commit/15de4a81bd594fd960757decbaf5e9cdb27a881a))  
fix(core/pipeline): Don't break templated pipelines when updating config [#7428](https://github.com/spinnaker/deck/pull/7428) ([70dee3f2](https://github.com/spinnaker/deck/commit/70dee3f22e486c976784a4b51865801747c9cc49))  
fix(core/pipeline): Fix revert button regression for templated pipelines [#7427](https://github.com/spinnaker/deck/pull/7427) ([b942b5b5](https://github.com/spinnaker/deck/commit/b942b5b5d3a9ce45255236ab2aa106fcbe91d37a))  
feat(core): rerun child pipelines with artifacts [#7422](https://github.com/spinnaker/deck/pull/7422) ([d7490f9d](https://github.com/spinnaker/deck/commit/d7490f9dd63a1dda16d226ae3cd3c74c466a2db5))  



## [0.0.411](https://www.github.com/spinnaker/deck/compare/09bbec0dbac1b61c91212eb66ec8d1215b8abf5b...fa47f48f9fef478927c2a40aebc431b987bd235a) (2019-09-23)


### Changes

Bump package core to 0.0.411 and titus to 0.0.112 [#7423](https://github.com/spinnaker/deck/pull/7423) ([fa47f48f](https://github.com/spinnaker/deck/commit/fa47f48f9fef478927c2a40aebc431b987bd235a))  
feat(core/pipeline): Add timestamp for failed executions [#7419](https://github.com/spinnaker/deck/pull/7419) ([5d2e4695](https://github.com/spinnaker/deck/commit/5d2e469584447812f51a3ebfd602d464dbf21331))  
feat(core/pipeline): Make custom artifacts more readable [#7418](https://github.com/spinnaker/deck/pull/7418) ([3cfb3f10](https://github.com/spinnaker/deck/commit/3cfb3f1004f870f79dfedf62c4200dd63fbcf409))  
fix(core/pipeline): Add space before Source-link for failed executions [#7420](https://github.com/spinnaker/deck/pull/7420) ([0034fa81](https://github.com/spinnaker/deck/commit/0034fa812b01fbdc20e4c918fc8a48782458571b))  
fix(core/pipeline): standardize manual execution field layouts [#7413](https://github.com/spinnaker/deck/pull/7413) ([bce3ac53](https://github.com/spinnaker/deck/commit/bce3ac5344f6ab38fe72e07c16f4c03610008994))  
fix(artifacts): Support custom artifacts with custom type [#7415](https://github.com/spinnaker/deck/pull/7415) ([9c0a994c](https://github.com/spinnaker/deck/commit/9c0a994cd730922a56363be984a51862a17d9290))  



## [0.0.410](https://www.github.com/spinnaker/deck/compare/4c6f7c1e160cf86864dffb866ba9c2f82a90ada8...09bbec0dbac1b61c91212eb66ec8d1215b8abf5b) (2019-09-18)


### Changes

chore(core): Bump version to 0.0.410 [#7412](https://github.com/spinnaker/deck/pull/7412) ([09bbec0d](https://github.com/spinnaker/deck/commit/09bbec0dbac1b61c91212eb66ec8d1215b8abf5b))  
fix(core): Sanitize confirmation modal body [#7407](https://github.com/spinnaker/deck/pull/7407) ([68cc18f0](https://github.com/spinnaker/deck/commit/68cc18f0bd0df00966a85113dec8972c4e16f170))  
feat(core/managed): add Managed Resources section to app config, allow opting out [#7409](https://github.com/spinnaker/deck/pull/7409) ([f8267a4b](https://github.com/spinnaker/deck/commit/f8267a4bc3555938ffece37d84d27e88ae71d425))  



## [0.0.409](https://www.github.com/spinnaker/deck/compare/04498f17f8cea130bb4c40d8930b7dd6eb52bd9e...4c6f7c1e160cf86864dffb866ba9c2f82a90ada8) (2019-09-16)


### Changes

chore(core): Bump version to 0.0.409 ([4c6f7c1e](https://github.com/spinnaker/deck/commit/4c6f7c1e160cf86864dffb866ba9c2f82a90ada8))  
fix(core): fix manual execution [#7408](https://github.com/spinnaker/deck/pull/7408) ([b396658c](https://github.com/spinnaker/deck/commit/b396658cc8a0f400bb2dadd474bb3e355573284c))  
feat(pipeline): Custom alert for start manual execution dialog [#7406](https://github.com/spinnaker/deck/pull/7406) ([476ad625](https://github.com/spinnaker/deck/commit/476ad6255f4e871d8d6d7de3672f42b683533eaf))  
fix(help): reflect reality in rollingredblack help text bubble-things [#7405](https://github.com/spinnaker/deck/pull/7405) ([ec1d32f2](https://github.com/spinnaker/deck/commit/ec1d32f27ce1ca9d04a4c6bde0c44227a9eb74fd))  
fix(nexus): nexus trigger selectable in UI [#7381](https://github.com/spinnaker/deck/pull/7381) ([23540b61](https://github.com/spinnaker/deck/commit/23540b61003d8eb08c73ee9e53d6ff15e69c5ecd))  



## [0.0.408](https://www.github.com/spinnaker/deck/compare/30cb4bad48d2d280485e905fba415a4612148284...04498f17f8cea130bb4c40d8930b7dd6eb52bd9e) (2019-09-12)


### Changes

chore(core): Bump version to 0.0.408 [#7397](https://github.com/spinnaker/deck/pull/7397) ([04498f17](https://github.com/spinnaker/deck/commit/04498f17f8cea130bb4c40d8930b7dd6eb52bd9e))  
fix(core): Exporting DiffView component [#7396](https://github.com/spinnaker/deck/pull/7396) ([3731ba80](https://github.com/spinnaker/deck/commit/3731ba8069ea38651ad4810b514faa9238650cf4))  



## [0.0.407](https://www.github.com/spinnaker/deck/compare/192130eedebf0a482b0430e85607d0f31ee5a582...30cb4bad48d2d280485e905fba415a4612148284) (2019-09-12)


### Changes

chore(core): Bump version to 0.0.407 ([30cb4bad](https://github.com/spinnaker/deck/commit/30cb4bad48d2d280485e905fba415a4612148284))  
feat(pager): Looking up multiple apps by name to page [#7387](https://github.com/spinnaker/deck/pull/7387) ([e0ce768d](https://github.com/spinnaker/deck/commit/e0ce768dcdaa66fbbe0d347c2aa696f58185324e))  
fix(core): ensure filter tag removal removes tags from filter [#7212](https://github.com/spinnaker/deck/pull/7212) ([42c29462](https://github.com/spinnaker/deck/commit/42c29462a794a259c992f26226711fbc84e0e1ad))  
refactor(core): reactify the show pipeline history modal [#7382](https://github.com/spinnaker/deck/pull/7382) ([733132ba](https://github.com/spinnaker/deck/commit/733132ba6b250eca9fba18b89dff84af5915d15e))  
refactor(titus): Adding load balancer incompatibility [#7386](https://github.com/spinnaker/deck/pull/7386) ([7d65ea9e](https://github.com/spinnaker/deck/commit/7d65ea9ee7f9856ae4670f1e5462c7cb94e45890))  
fix(core/pipeline): exclude correlation IDs (and more) when re-running [#7374](https://github.com/spinnaker/deck/pull/7374) ([3e761865](https://github.com/spinnaker/deck/commit/3e761865750989b28000df19bf7bbe419b2b5827))  



## [0.0.406](https://www.github.com/spinnaker/deck/compare/a68eddf4ee4c09ab37a450de7cb521d3df68f779...192130eedebf0a482b0430e85607d0f31ee5a582) (2019-09-05)


### Changes

Bump package core to 0.0.406 and amazon to 0.0.209 [#7378](https://github.com/spinnaker/deck/pull/7378) ([192130ee](https://github.com/spinnaker/deck/commit/192130eedebf0a482b0430e85607d0f31ee5a582))  
fix(triggers): Protecting from undefined triggers [#7377](https://github.com/spinnaker/deck/pull/7377) ([d8f1a51e](https://github.com/spinnaker/deck/commit/d8f1a51e910e5f1ff6008ba14f78c56e9df80d77))  
feat(core/managed): Add resource dropdown with links to history + source JSON [#7376](https://github.com/spinnaker/deck/pull/7376) ([ad4c1447](https://github.com/spinnaker/deck/commit/ad4c1447c6134b8c823d9b92f12364754a11168a))  
fix(typos): fix a few typos in help messages [#7373](https://github.com/spinnaker/deck/pull/7373) ([3665d3ec](https://github.com/spinnaker/deck/commit/3665d3ec9387764573162a62c20f938e94002073))  



## [0.0.405](https://www.github.com/spinnaker/deck/compare/648b523c3e00d102d2cab4ca2da45955e8c63855...a68eddf4ee4c09ab37a450de7cb521d3df68f779) (2019-09-03)


### Changes

Bump package core to 0.0.405 and titus to 0.0.109 [#7372](https://github.com/spinnaker/deck/pull/7372) ([a68eddf4](https://github.com/spinnaker/deck/commit/a68eddf4ee4c09ab37a450de7cb521d3df68f779))  
refactor(core): Reactify rename pipeline modal [#7368](https://github.com/spinnaker/deck/pull/7368) ([6a475573](https://github.com/spinnaker/deck/commit/6a47557397866f2a611232fd0ae1667df0fef22f))  
feat(notifications): Add additional fields for Github and get config from halyard [#7239](https://github.com/spinnaker/deck/pull/7239) ([0d886441](https://github.com/spinnaker/deck/commit/0d8864414b8ee2d225648302689a153e306eec37))  
refactor(core): use a fieldLayout for manualExecution [#7356](https://github.com/spinnaker/deck/pull/7356) ([902c0b3d](https://github.com/spinnaker/deck/commit/902c0b3defc6a196249944c7d210fe900bf50b55))  
fix(core/pipeline): fix date picker for manual execution parameters where constraint == 'date' ([9f5a860f](https://github.com/spinnaker/deck/commit/9f5a860fba02f7db13e916b567ca2de9da0fc263))  
feat(core/presentation): Add DayPickerInput form input component ([744cf19f](https://github.com/spinnaker/deck/commit/744cf19f452de48f783554549755a7f5841c0e4b))  
feat(core): improve readability of pipeline cancellation message [#7369](https://github.com/spinnaker/deck/pull/7369) ([5a354b1d](https://github.com/spinnaker/deck/commit/5a354b1df6a5f64f90a3f3b883ba89dfe7b993b5))  
fix(artifacts): fix uncaught undefined exception with artifacts [#7362](https://github.com/spinnaker/deck/pull/7362) ([b8898576](https://github.com/spinnaker/deck/commit/b88985763c8f28c8417f8bb1ac23273e5f1b4e40))  
fix(core): Fix shadowed var usage [#7367](https://github.com/spinnaker/deck/pull/7367) ([dc161668](https://github.com/spinnaker/deck/commit/dc1616686cc3a92183de2e305820600a084ff2c8))  
 refactor(core): Reactify lock and unlock pipeline modal [#7366](https://github.com/spinnaker/deck/pull/7366) ([5d1facd4](https://github.com/spinnaker/deck/commit/5d1facd4d4a26d99f5652e4aabeb176fdaca40db))  
fix(core): separate multiple task errors by newline [#7355](https://github.com/spinnaker/deck/pull/7355) ([d131e011](https://github.com/spinnaker/deck/commit/d131e0119367c009c08e530f8c8e870e78011b63))  
refactor(core): Reactify enable pipeline modal [#7360](https://github.com/spinnaker/deck/pull/7360) ([450d6816](https://github.com/spinnaker/deck/commit/450d681676f4b8ccda400162f87497d1c3d644b8))  
refactor(core): Reactify disable pipeline modal [#7358](https://github.com/spinnaker/deck/pull/7358) ([5155ba10](https://github.com/spinnaker/deck/commit/5155ba10494d1b216b43f2902ccfe68220afd38a))  
fix(kustomize): fix artifact selector [#7359](https://github.com/spinnaker/deck/pull/7359) ([14035aeb](https://github.com/spinnaker/deck/commit/14035aebda172520afaee6bbeb73211f0c567bda))  
refactor(core): Reactify delete pipeline modal [#7357](https://github.com/spinnaker/deck/pull/7357) ([12f2eaa1](https://github.com/spinnaker/deck/commit/12f2eaa132bee5fbb6ef9fdd051a12e783f52085))  
fix(kubernetes): add StageFailureMessage to Bake Manifest execution details [#7354](https://github.com/spinnaker/deck/pull/7354) ([48b7195c](https://github.com/spinnaker/deck/commit/48b7195cad32edda2580b0ce190d2c07eaa7f4b0))  
feat(core): add stage status precondition type [#7348](https://github.com/spinnaker/deck/pull/7348) ([5fae6ef0](https://github.com/spinnaker/deck/commit/5fae6ef0973503a320e5c183e5b03c54d3a47da4))  
fix(core): pipeline execution was not displaying all resolvedArtifacts [#7353](https://github.com/spinnaker/deck/pull/7353) ([f42977fb](https://github.com/spinnaker/deck/commit/f42977fb8860018fd7a0b92d2323ebff708951a0))  
fix(artifacts/k8s): fix rewrite k8s artifact edit [#7352](https://github.com/spinnaker/deck/pull/7352) ([251250be](https://github.com/spinnaker/deck/commit/251250beb2c671bedb03e0bf90a0e3a4832c0757))  



## [0.0.404](https://www.github.com/spinnaker/deck/compare/05e41e6591a8df4ba834439b9b153bc68d026a7d...648b523c3e00d102d2cab4ca2da45955e8c63855) (2019-08-20)


### Changes

chore(core): Bump version to 0.0.404 [#7351](https://github.com/spinnaker/deck/pull/7351) ([648b523c](https://github.com/spinnaker/deck/commit/648b523c3e00d102d2cab4ca2da45955e8c63855))  
fix(notifications): Fixing custom message text shape [#7349](https://github.com/spinnaker/deck/pull/7349) ([a7896bf4](https://github.com/spinnaker/deck/commit/a7896bf406722141b2f2435d947940e5c2270d75))  
 fix(core): fix vertical alignment of radio buttons [#7344](https://github.com/spinnaker/deck/pull/7344) ([8bdca890](https://github.com/spinnaker/deck/commit/8bdca89075a5f16f4f318f7fafe9980dfdf3956e))  
fix(artifacts): set the artifacts on trigger before the pipeline to sync problems with Angular [#7345](https://github.com/spinnaker/deck/pull/7345) ([d00f2a4b](https://github.com/spinnaker/deck/commit/d00f2a4b7de890c8e137a5509d2630b8e91b143e))  
fix(core): fix produces artifacts UI [#7343](https://github.com/spinnaker/deck/pull/7343) ([b6ea1de5](https://github.com/spinnaker/deck/commit/b6ea1de57645430b9e739c42470a568959e40d74))  
feat(bakeManifest): add kustomize support [#7342](https://github.com/spinnaker/deck/pull/7342) ([9812469a](https://github.com/spinnaker/deck/commit/9812469a653d7302a6b006f7c1195416b5371469))  



## [0.0.403](https://www.github.com/spinnaker/deck/compare/9fbfca16bf3bee5475efdf24dad0153aa844a2c6...05e41e6591a8df4ba834439b9b153bc68d026a7d) (2019-08-16)


### Changes

chore(core): Bump version to 0.0.403 [#7341](https://github.com/spinnaker/deck/pull/7341) ([05e41e65](https://github.com/spinnaker/deck/commit/05e41e6591a8df4ba834439b9b153bc68d026a7d))  
fix(core/forms): revert legacy MapEditor adapter to old component [#7340](https://github.com/spinnaker/deck/pull/7340) ([593370e0](https://github.com/spinnaker/deck/commit/593370e0556506721386f2bc433b411c6f0738f4))  
fix(core/pipeline): fix side menu rendering for trigger config [#7337](https://github.com/spinnaker/deck/pull/7337) ([8361c0ad](https://github.com/spinnaker/deck/commit/8361c0ad31d03e54aee82fb7e4e0cd75be9f4204))  



## [0.0.402](https://www.github.com/spinnaker/deck/compare/bf4fc3dfee113883140d228ac439119eb371d48b...9fbfca16bf3bee5475efdf24dad0153aa844a2c6) (2019-08-15)


### Changes

chore(core): Bump version to 0.0.402 [#7335](https://github.com/spinnaker/deck/pull/7335) ([9fbfca16](https://github.com/spinnaker/deck/commit/9fbfca16bf3bee5475efdf24dad0153aa844a2c6))  
fix(execution): Parameter names should be treated as strings [#7334](https://github.com/spinnaker/deck/pull/7334) ([7f0fdb4d](https://github.com/spinnaker/deck/commit/7f0fdb4da0af985e0f9312fd6b510fdc5e574df8))  



## [0.0.401](https://www.github.com/spinnaker/deck/compare/cf8c35bea4144c519dcba4bc4214184301c8e142...bf4fc3dfee113883140d228ac439119eb371d48b) (2019-08-15)


### Changes

chore(core): Bump version to 0.0.401 [#7333](https://github.com/spinnaker/deck/pull/7333) ([bf4fc3df](https://github.com/spinnaker/deck/commit/bf4fc3dfee113883140d228ac439119eb371d48b))  
fix(pipelines): Correct text about cancelling [#7332](https://github.com/spinnaker/deck/pull/7332) ([20109da7](https://github.com/spinnaker/deck/commit/20109da7a0819dac957561a4af955614420b38b2))  
fix(executions): Fixing execution marker ordering [#7331](https://github.com/spinnaker/deck/pull/7331) ([7125c730](https://github.com/spinnaker/deck/commit/7125c73048fbdd72f387cf4c4c15828baeb7adf9))  
fix(artifacts): fix artifact account default logic [#7324](https://github.com/spinnaker/deck/pull/7324) ([c39bf0ba](https://github.com/spinnaker/deck/commit/c39bf0ba07a1ed544a58fe452ab9891b3d3b5ab6))  



## [0.0.400](https://www.github.com/spinnaker/deck/compare/1ab9ce545d359b0781230390e3cb61f9cc3c540c...cf8c35bea4144c519dcba4bc4214184301c8e142) (2019-08-14)


### Changes

chore(core): Bump version to 0.0.400 ([cf8c35be](https://github.com/spinnaker/deck/commit/cf8c35bea4144c519dcba4bc4214184301c8e142))  
fix(core/pipeline): Fix github trigger manual execution missing 'hash' property Adding `key={trigger.description}` to `Triggers.tsx` in a recent PR had an unexpected side effect of remounting the component when this submit method deletes the trigger 'description' field. Clone the trigger before mutating to stop this. ([6d53fafe](https://github.com/spinnaker/deck/commit/6d53fafe17d993233884ca6e8767be4c8882ca4a))  
feat(tasks): Adding redirect for task by id without application [#7307](https://github.com/spinnaker/deck/pull/7307) ([a67226c1](https://github.com/spinnaker/deck/commit/a67226c10c5994a0210e826024d9026c39a8c54c))  



## [0.0.399](https://www.github.com/spinnaker/deck/compare/7cc71686fd8d37051e17855962a043551b9b92d2...1ab9ce545d359b0781230390e3cb61f9cc3c540c) (2019-08-14)


### Changes

chore(core): Bump version to 0.0.399 ([1ab9ce54](https://github.com/spinnaker/deck/commit/1ab9ce545d359b0781230390e3cb61f9cc3c540c))  
refactor(hooks): Pulling out useData [#7325](https://github.com/spinnaker/deck/pull/7325) ([598b5b3d](https://github.com/spinnaker/deck/commit/598b5b3d46393f262c10e067c5aa9a26280aa10d))  
fix(core/pipeline): Refresh jenkins jobs component when manual execution trigger is changed [#7323](https://github.com/spinnaker/deck/pull/7323) ([0fbafe6f](https://github.com/spinnaker/deck/commit/0fbafe6f87dde6b2f9400a3d4999894a19f16f5b))  
fix(core/pipeline): make dropdown option labels always be strings [#7322](https://github.com/spinnaker/deck/pull/7322) ([12511594](https://github.com/spinnaker/deck/commit/12511594b582512dfd76efc2c6274a67fe775fff))  
perf(core): avoid nested looping in execution transformation [#7217](https://github.com/spinnaker/deck/pull/7217) ([1e779ef9](https://github.com/spinnaker/deck/commit/1e779ef9a01d32a2740680f413346226738e0e48))  
refactor(core): Reactify the pipeline trigger configuration [#7318](https://github.com/spinnaker/deck/pull/7318) ([bec54dfa](https://github.com/spinnaker/deck/commit/bec54dfa5f0b9c55b7fc8062cf47784f972a0107))  



## [0.0.398](https://www.github.com/spinnaker/deck/compare/2581c9a39b3a4b1871a287ef477c6a51d0b5173d...7cc71686fd8d37051e17855962a043551b9b92d2) (2019-08-13)


### Changes

chore(core): Bump version to 0.0.398 [#7319](https://github.com/spinnaker/deck/pull/7319) ([7cc71686](https://github.com/spinnaker/deck/commit/7cc71686fd8d37051e17855962a043551b9b92d2))  
refactor(core/presentation): use render props everywhere [#7316](https://github.com/spinnaker/deck/pull/7316) ([40c31972](https://github.com/spinnaker/deck/commit/40c31972129a1c74f5087fb5a8a7a181b2144c33))  
fix(core/presentation): prevent the hover jitters on pipeline graph labels [#7311](https://github.com/spinnaker/deck/pull/7311) ([cef1c099](https://github.com/spinnaker/deck/commit/cef1c099e2bdbd6bdcb4db55179edd48afe992f3))  
fix(ui): require application, pipeline, and a status when adding a pipeline trigger [#7308](https://github.com/spinnaker/deck/pull/7308) ([74ea2e7e](https://github.com/spinnaker/deck/commit/74ea2e7ef0cc71d03510fd5f7fb6ea8fa02af3dd))  
fix(ui): require type on the stage [#7304](https://github.com/spinnaker/deck/pull/7304) ([f3de1671](https://github.com/spinnaker/deck/commit/f3de1671dbb57214b8b09816a541e99ca8d0e1a5))  
feat(helm): allow manual execution overrides for helm charts [#7312](https://github.com/spinnaker/deck/pull/7312) ([834f4eaf](https://github.com/spinnaker/deck/commit/834f4eafc75ace3529f51c0bddd928efa05b7dd2))  
fix(tasks): Not bothering with null user when we have an authed one [#7305](https://github.com/spinnaker/deck/pull/7305) ([ea29d8d1](https://github.com/spinnaker/deck/commit/ea29d8d13417fed4ae6851717e6590d4e3fe0c92))  



## [0.0.397](https://www.github.com/spinnaker/deck/compare/b07510e82f0b101da05437ccc942f18cc4469357...2581c9a39b3a4b1871a287ef477c6a51d0b5173d) (2019-08-07)


### Changes

chore(core): Bump version to 0.0.397 ([2581c9a3](https://github.com/spinnaker/deck/commit/2581c9a39b3a4b1871a287ef477c6a51d0b5173d))  
feat(executions): Allow rerun on active executions [#7293](https://github.com/spinnaker/deck/pull/7293) ([a6cd7e60](https://github.com/spinnaker/deck/commit/a6cd7e603e5d6dd0c4a288778e855f2cb449b715))  
chore(core/pipeline): remove debug statement from werckertrigger ([a823cd7d](https://github.com/spinnaker/deck/commit/a823cd7d6e9dc25ec8efefd5dc46bbe9a0e36b39))  
fix(core/pipeline): did you know 'window.status' is a thing?  Me neither. ([a4298e01](https://github.com/spinnaker/deck/commit/a4298e011844d9db4b76701f7640ca35291aeb83))  
refactor(core/pipeline): Migrate triggers to formik. Fetch data with react hooks. ([9a37c5d5](https://github.com/spinnaker/deck/commit/9a37c5d522ed02b93033c2d59af54f855f225d42))  
refactor(core/forms): Extract a controlled MapEditorInput component Retains the old API in MapEditor.tsx ([0c547a87](https://github.com/spinnaker/deck/commit/0c547a873aa71798120b152fc29725627f8636d8))  
refactor(core/forms): Remove some cached state from MapEditor Move MapPair component to its own file ([29d470d3](https://github.com/spinnaker/deck/commit/29d470d3ff5e90f716d2d46dabe3296572e2ad8f))  
feat(core/presentation): Add refresh() api to useLatestPromise react hook [#7300](https://github.com/spinnaker/deck/pull/7300) ([3f5e4e6d](https://github.com/spinnaker/deck/commit/3f5e4e6de11b51315e8974792a4c17a1ca7c7149))  
feat(core/form): Disable ReactSelect ignoreAccents by default [#7301](https://github.com/spinnaker/deck/pull/7301) ([1472b3da](https://github.com/spinnaker/deck/commit/1472b3da7d12d747ac973f9ed74ddfe16d7e63a3))  
fix(execution): Adding rerun option to execution details view [#7292](https://github.com/spinnaker/deck/pull/7292) ([70669e6b](https://github.com/spinnaker/deck/commit/70669e6bcc8cc4f86dbd33736b17da0b31732b91))  
fix(executions): Fix time boundary grouping with never started executions [#7294](https://github.com/spinnaker/deck/pull/7294) ([923c7369](https://github.com/spinnaker/deck/commit/923c73692cac82dabe927cd94bf41183568d87a7))  
feat(webhooks): add support for cancellation to webhooks [#7289](https://github.com/spinnaker/deck/pull/7289) ([7368523f](https://github.com/spinnaker/deck/commit/7368523f7485726a069e9ad8b06558f147125508))  
Louis/modal fixes [#7287](https://github.com/spinnaker/deck/pull/7287) ([10617c58](https://github.com/spinnaker/deck/commit/10617c5833873b52917df85ccb8ae5f1b2287ec1))  
feat(*/pipeline): Remove the concept of default stage timeouts, rename option [#7286](https://github.com/spinnaker/deck/pull/7286) ([abac63ce](https://github.com/spinnaker/deck/commit/abac63ce5c88b809fcf5ed1509136fe96489a051))  
fix(core/pipeline): Fix concourse trigger state onChange callbacks ([fce5a1a4](https://github.com/spinnaker/deck/commit/fce5a1a47a9bcdd4e49bf2a2bc65c951a3dad641))  
fix(core): plan templated pipelines when triggering manual exec [#7283](https://github.com/spinnaker/deck/pull/7283) ([15850767](https://github.com/spinnaker/deck/commit/158507673d53b2d7d25d0db8f6f6888dcfc782a9))  
fix(history): use the correct query param name when getting history [#7274](https://github.com/spinnaker/deck/pull/7274) ([4eb850dd](https://github.com/spinnaker/deck/commit/4eb850ddc72bb4a2f05a1186640bfafe80403598))  
fix(core/presentation): Add support for multi in ReactSelectInput onChange adapter ([8e58b9b7](https://github.com/spinnaker/deck/commit/8e58b9b7615338a275943e69134670ddbfc7dfb3))  
fix(core/presentation): Only flex the first direct child, not descendent ([682763df](https://github.com/spinnaker/deck/commit/682763dfae6900fced9c6cfa30862b323c6e0f3e))  



## [0.0.396](https://www.github.com/spinnaker/deck/compare/8991c37426da493d2ddbed55b5ef565fbd5acbea...b07510e82f0b101da05437ccc942f18cc4469357) (2019-07-26)


### Changes

chore(core): Bump version to 0.0.396 ([b07510e8](https://github.com/spinnaker/deck/commit/b07510e82f0b101da05437ccc942f18cc4469357))  
fix(core): Prevent reloads when hitting enter in create pipeline modal [#7277](https://github.com/spinnaker/deck/pull/7277) ([d73fc997](https://github.com/spinnaker/deck/commit/d73fc997ebac87d8dc4643506d2f72c756103652))  
feat(ui): add spinnaker version to UI, addresses #4383 [#7254](https://github.com/spinnaker/deck/pull/7254) ([96e1b08e](https://github.com/spinnaker/deck/commit/96e1b08e8e5d48c3ba46f4aa0ccc4c8e6326f27a))  
fix(core): remove the logic to initialize after props change [#7271](https://github.com/spinnaker/deck/pull/7271) ([592392f9](https://github.com/spinnaker/deck/commit/592392f9bbbaba6b2a76e4cbaa10cb83fa887eed))  
refactor(core/pipeline): Remove TriggerFieldLayout and BaseTrigger - Switch from TriggerFieldLayout to StandardFieldLayout - Switch to rendering Trigger description inside the dropdown ([d60b3bcf](https://github.com/spinnaker/deck/commit/d60b3bcf585a9d72970aeff4ebf6cd9bdc91fcde))  
fix(core/presentation): StandardFieldLayout: add top margin, flex first element, render blank label - Add margin-top between fields - Flex-fill the first element in the input content area - Render the label section even when passed an empty string - Add CSS classes to layout sections for customization by callers ([9c9faf4f](https://github.com/spinnaker/deck/commit/9c9faf4fbdd6249f7f075fe3c68690652d5f16c4))  
fix(core/presentation): Fix mount check -- useRef instead of useState ([f8c8f94c](https://github.com/spinnaker/deck/commit/f8c8f94cdc8f7d8c4f1d1e27d5a8e68ecc8ff681))  
feat(core/presentation): Add a <Formik/> wrapper which applies fixes and opinions that we want in Spinnaker [#7272](https://github.com/spinnaker/deck/pull/7272) ([9c7885f6](https://github.com/spinnaker/deck/commit/9c7885f6e6645f7028db7ea3102edd59f2b67e76))  
feat(core/entityTag): Add maxResults to settings.js [#7270](https://github.com/spinnaker/deck/pull/7270) ([4adc3d46](https://github.com/spinnaker/deck/commit/4adc3d469c48135a94874f7d94ca2c4f29e90f5f))  
refactor(core/details): Creating generic component for entity details [#7262](https://github.com/spinnaker/deck/pull/7262) ([608b83a7](https://github.com/spinnaker/deck/commit/608b83a7a359dfccb16d14d85c93c1d1b08fe745))  
refactor(core): reactify notification and manual execution modal [#7075](https://github.com/spinnaker/deck/pull/7075) ([71a4d9ea](https://github.com/spinnaker/deck/commit/71a4d9ea3af6aa52bbd65dbe89555df089dbb15d))  
refactor(core/pipeline): Refactor most trigger to use <FormField/> components [#7255](https://github.com/spinnaker/deck/pull/7255) ([bec74d94](https://github.com/spinnaker/deck/commit/bec74d94c38e2b12e4dc323f71e6539925d6617f))  
fix(core/pipeline): stage executionDetailsSections resolving the wrong cloudProvider [#7260](https://github.com/spinnaker/deck/pull/7260) ([661e0217](https://github.com/spinnaker/deck/commit/661e021714af72737ddfcf76831fec0871a02b06))  
fix(k8s/runJob): External logs URL to support manifest with implicit default namespace [#7252](https://github.com/spinnaker/deck/pull/7252) ([4dba1b12](https://github.com/spinnaker/deck/commit/4dba1b1253652da14b365be4e84096c09e008cf3))  
fix(k8s): fix job log modal overflow [#7256](https://github.com/spinnaker/deck/pull/7256) ([2ca4eef6](https://github.com/spinnaker/deck/commit/2ca4eef69dcb5107731846360f194dd27c499dcf))  
feat(core/presentation): Add virtualized support to ReactSelectInput [#7253](https://github.com/spinnaker/deck/pull/7253) ([64fc4180](https://github.com/spinnaker/deck/commit/64fc4180b8676f1910de49c561cec5ea3beb7c79))  
fix(artifacts): correct bitbucket placeholder text [#7251](https://github.com/spinnaker/deck/pull/7251) ([c58b2bdc](https://github.com/spinnaker/deck/commit/c58b2bdce322e525db8307b13ba1025d9090b818))  
fix(k8s): fix bake manifest selector [#7249](https://github.com/spinnaker/deck/pull/7249) ([a61aa7a4](https://github.com/spinnaker/deck/commit/a61aa7a416e942d50a395b429d063c7e461322f4))  
fix(core/presentation): Default FormField 'name' prop to '', not noop [#7248](https://github.com/spinnaker/deck/pull/7248) ([46760963](https://github.com/spinnaker/deck/commit/4676096331f29893f326ae04549af123fb294886))  
fix(core/pipeline): When changing trigger type, only retain the fields common to all trigger types [#7247](https://github.com/spinnaker/deck/pull/7247) ([040c80f1](https://github.com/spinnaker/deck/commit/040c80f119a45346c198ba235271d89e892f4c48))  



## [0.0.395](https://www.github.com/spinnaker/deck/compare/3aae11085a0c4fcea04f93f1320ed23c5c8eadad...8991c37426da493d2ddbed55b5ef565fbd5acbea) (2019-07-19)


### Changes

chore(core): Bump version to 0.0.395 ([8991c374](https://github.com/spinnaker/deck/commit/8991c37426da493d2ddbed55b5ef565fbd5acbea))  
fix(core): save and validate trigger type in pipeline config [#7241](https://github.com/spinnaker/deck/pull/7241) ([f38580f3](https://github.com/spinnaker/deck/commit/f38580f3c4d04bb554d25dbdad2b3e573d84c046))  
feat(pager): Allow providing pager duty subject/details in URL [#7242](https://github.com/spinnaker/deck/pull/7242) ([f37ccf05](https://github.com/spinnaker/deck/commit/f37ccf05a02e5248849a2d8d2459a1c6535d60b2))  



## [0.0.394](https://www.github.com/spinnaker/deck/compare/edd88a1718ff4d92e4cd3014f64b02e87bd66c78...3aae11085a0c4fcea04f93f1320ed23c5c8eadad) (2019-07-17)


### Changes

chore(core): Bump version to 0.0.394 [#7238](https://github.com/spinnaker/deck/pull/7238) ([3aae1108](https://github.com/spinnaker/deck/commit/3aae11085a0c4fcea04f93f1320ed23c5c8eadad))  
fix(core): Changing switch to a map [#7236](https://github.com/spinnaker/deck/pull/7236) ([73d4f4cf](https://github.com/spinnaker/deck/commit/73d4f4cf9051aaca2ee3a01a31177e4bfe31145a))  



## [0.0.393](https://www.github.com/spinnaker/deck/compare/57ccecb9f90150ab972ac0279d10fccac4068ce7...edd88a1718ff4d92e4cd3014f64b02e87bd66c78) (2019-07-16)


### Changes

chore(core): Bump version to 0.0.393 [#7232](https://github.com/spinnaker/deck/pull/7232) ([edd88a17](https://github.com/spinnaker/deck/commit/edd88a1718ff4d92e4cd3014f64b02e87bd66c78))  
refactor(pipeline): Export PipelineStageExecutionDetails [#7230](https://github.com/spinnaker/deck/pull/7230) ([d4d71bc3](https://github.com/spinnaker/deck/commit/d4d71bc33c483b4d87554df6288c66d5920b7dfb))  
fix(core): Render templated pipeline params in the pipeline run stage [#7228](https://github.com/spinnaker/deck/pull/7228) ([18a839b5](https://github.com/spinnaker/deck/commit/18a839b5d86e185f030533be2cd25f566019d6d7))  



## [0.0.392](https://www.github.com/spinnaker/deck/compare/8c0d18ac1fe20e02af1aa3f8a6ccce52fcdd4f1e...57ccecb9f90150ab972ac0279d10fccac4068ce7) (2019-07-15)


### Changes

chore(core): Bump version to 0.0.392 ([57ccecb9](https://github.com/spinnaker/deck/commit/57ccecb9f90150ab972ac0279d10fccac4068ce7))  
refactor(core): Script stage to react and formik [#7213](https://github.com/spinnaker/deck/pull/7213) ([b9324184](https://github.com/spinnaker/deck/commit/b9324184194135bd176f370a75997b7ae0fafd5f))  
test(core/filterModel): Add tests for restoring filters on router transitions, remove tests for removed functionality ([2dbc6962](https://github.com/spinnaker/deck/commit/2dbc696243b4d4a068b182f9bee64256e4d0db25))  
refactor(core/filterModel): Simplify the AngularJS compatibility code which syncs the 'sortFilter' object and the router params - Removed the FilterModelServiceConverters code which duplicates the router param types code ([f6dbdf51](https://github.com/spinnaker/deck/commit/f6dbdf5150fc6f5b62546248cd151fd083182370))  
refactor(core/filterModel): Use router hooks to save/restore filters ([d39ab96f](https://github.com/spinnaker/deck/commit/d39ab96f71ac1a023a9aa17ff75eeeb182ced293))  
fix(core/reactShims): Fix the state.go wrapper such that it correctly exposes the `transition` (it's been inconsequentially broken a long time and nobody noticed) ([39a27e6c](https://github.com/spinnaker/deck/commit/39a27e6cd4332c11c7a93b662711364b35bbc319))  
fix(core/pipeline): Trigger pipeline validation when a trigger is updated ([ac2f61c3](https://github.com/spinnaker/deck/commit/ac2f61c3a182823d445c9f6e047358504d04e801))  
fix(core/pipeline): Fix initial trigger type state ([4a8b341f](https://github.com/spinnaker/deck/commit/4a8b341f8a0bae23ea507d04e75d15763e185751))  
refactor(core): refactored the trigger to react ([a4ebd0aa](https://github.com/spinnaker/deck/commit/a4ebd0aa6b0358d451e77869beff677f5f736319))  
fix(core): Render template triggers, notif, params for manual execution [#7223](https://github.com/spinnaker/deck/pull/7223) ([67314e05](https://github.com/spinnaker/deck/commit/67314e054d8723cef149bb63448588266274fd8d))  
feat(core): Allow filtering PAUSED executions ([8275ba6d](https://github.com/spinnaker/deck/commit/8275ba6df49006a2d6037f190975ea81cce6fb06))  



## [0.0.391](https://www.github.com/spinnaker/deck/compare/fba3c4a3c11a68b6064f5ddd95722344ab2974b1...8c0d18ac1fe20e02af1aa3f8a6ccce52fcdd4f1e) (2019-07-11)


### Changes

Bump package core to 0.0.391 and amazon to 0.0.203 [#7218](https://github.com/spinnaker/deck/pull/7218) ([8c0d18ac](https://github.com/spinnaker/deck/commit/8c0d18ac1fe20e02af1aa3f8a6ccce52fcdd4f1e))  
feat(titus): Adding support for service job processes [#7186](https://github.com/spinnaker/deck/pull/7186) ([0e5e66a3](https://github.com/spinnaker/deck/commit/0e5e66a3fae50abddb98cb2d97036a32907ea811))  
feat({core,amazon}/pipeline): add support per-OS AWS VM type choices [#7187](https://github.com/spinnaker/deck/pull/7187) ([68ee670d](https://github.com/spinnaker/deck/commit/68ee670d5993c9649bd9cc1c6b312512132dcdac))  



## [0.0.390](https://www.github.com/spinnaker/deck/compare/d51b0d486dc9360e8d2dc37b43edf0d59a5de521...fba3c4a3c11a68b6064f5ddd95722344ab2974b1) (2019-07-09)


### Changes

Bump package core to 0.0.390 and amazon to 0.0.202 and titus to 0.0.104 [#7207](https://github.com/spinnaker/deck/pull/7207) ([fba3c4a3](https://github.com/spinnaker/deck/commit/fba3c4a3c11a68b6064f5ddd95722344ab2974b1))  
fix(executions): Correctly populate trigger when rerunning an execution [#7205](https://github.com/spinnaker/deck/pull/7205) ([768c2109](https://github.com/spinnaker/deck/commit/768c2109ab2d5ddb98a1d521fb74b5e508a3ee07))  
feat(google): support stateful MIG operations [#7196](https://github.com/spinnaker/deck/pull/7196) ([f340b026](https://github.com/spinnaker/deck/commit/f340b0268c1c85aae96c54acee77aa39ef9770d6))  



## [0.0.389](https://www.github.com/spinnaker/deck/compare/6dd149b650db9f3bccd1866dffe02dfe76f68007...d51b0d486dc9360e8d2dc37b43edf0d59a5de521) (2019-07-09)


### Changes

chore(core): Bump version to 0.0.389 [#7204](https://github.com/spinnaker/deck/pull/7204) ([d51b0d48](https://github.com/spinnaker/deck/commit/d51b0d486dc9360e8d2dc37b43edf0d59a5de521))  
refactor(core): export ExecutionsTransformer in core module [#7203](https://github.com/spinnaker/deck/pull/7203) ([fc6823f1](https://github.com/spinnaker/deck/commit/fc6823f1f0f027384b3fe06658d32ebbe556d250))  



## [0.0.388](https://www.github.com/spinnaker/deck/compare/e3f2b8d79cdc7c62b24eb7bccc9c278f0f80b1fa...6dd149b650db9f3bccd1866dffe02dfe76f68007) (2019-07-09)


### Changes

Bump package core to 0.0.388 and amazon to 0.0.201 [#7200](https://github.com/spinnaker/deck/pull/7200) ([6dd149b6](https://github.com/spinnaker/deck/commit/6dd149b650db9f3bccd1866dffe02dfe76f68007))  
fix(core): correctly compute pipeline graph scroll position [#7198](https://github.com/spinnaker/deck/pull/7198) ([5d2f3075](https://github.com/spinnaker/deck/commit/5d2f3075ea15472e5bdc163515934ad35f753055))  



## [0.0.387](https://www.github.com/spinnaker/deck/compare/45ca048d602dcc0db7c01db3e3dcd1be26e2a755...e3f2b8d79cdc7c62b24eb7bccc9c278f0f80b1fa) (2019-07-08)


### Changes

chore(core): Bump version to 0.0.387 [#7195](https://github.com/spinnaker/deck/pull/7195) ([e3f2b8d7](https://github.com/spinnaker/deck/commit/e3f2b8d79cdc7c62b24eb7bccc9c278f0f80b1fa))  
fix(core): consider stage count when hydrating executions [#7194](https://github.com/spinnaker/deck/pull/7194) ([6ff736e2](https://github.com/spinnaker/deck/commit/6ff736e28a54d41d57cd496c720b6871b112feb0))  



## [0.0.386](https://www.github.com/spinnaker/deck/compare/1f5896d9829905bc451dab4b0926dc43788ee50f...45ca048d602dcc0db7c01db3e3dcd1be26e2a755) (2019-07-08)


### Changes

chore(core): Bump version to 0.0.386 [#7193](https://github.com/spinnaker/deck/pull/7193) ([45ca048d](https://github.com/spinnaker/deck/commit/45ca048d602dcc0db7c01db3e3dcd1be26e2a755))  
fix(core): check stage label hydration status when mousing over [#7192](https://github.com/spinnaker/deck/pull/7192) ([83b62db1](https://github.com/spinnaker/deck/commit/83b62db119d8af3363cca0834b8d59b0a1dba256))  



## [0.0.385](https://www.github.com/spinnaker/deck/compare/87e2e4f48c6067ef39108fc9749ee154c168bca1...1f5896d9829905bc451dab4b0926dc43788ee50f) (2019-07-08)


### Changes

chore(core): Bump version to 0.0.385 [#7191](https://github.com/spinnaker/deck/pull/7191) ([1f5896d9](https://github.com/spinnaker/deck/commit/1f5896d9829905bc451dab4b0926dc43788ee50f))  
fix(core): fix overrideTimeout behavior on existing stages [#7190](https://github.com/spinnaker/deck/pull/7190) ([7d2c4e1f](https://github.com/spinnaker/deck/commit/7d2c4e1fd6f99098248281ffc572454e9a0ee7f1))  
fix(core): fix flickering render of stage labels on hydration [#7181](https://github.com/spinnaker/deck/pull/7181) ([e26f7977](https://github.com/spinnaker/deck/commit/e26f797774189c3e2c4ae55fd6289d9b7c4f88bf))  



## [0.0.384](https://www.github.com/spinnaker/deck/compare/b66de00a420e3e371abdae4edc77e4b5d904e434...87e2e4f48c6067ef39108fc9749ee154c168bca1) (2019-07-05)


### Changes

chore(core): Bump version to 0.0.384 ([87e2e4f4](https://github.com/spinnaker/deck/commit/87e2e4f48c6067ef39108fc9749ee154c168bca1))  
feat(gce): Support new artifact model for deploy SG [#7178](https://github.com/spinnaker/deck/pull/7178) ([ecb2dd17](https://github.com/spinnaker/deck/commit/ecb2dd17aea1958e78089ed58f18a19ed502998f))  
fix(executions): Clarify why executions are NOT_STARTED [#7183](https://github.com/spinnaker/deck/pull/7183) ([c93b4d01](https://github.com/spinnaker/deck/commit/c93b4d0130011aa6be927c3ce9760faf1a9e5890))  
fix(core/pipeline): Change 'pipelines' state to redirect to the default child 'executions' ... instead of having an abstract state ([1f3882b8](https://github.com/spinnaker/deck/commit/1f3882b87a76d0e8db57d4172f7a62b65ae76d17))  



## [0.0.383](https://www.github.com/spinnaker/deck/compare/4d9a192d81c2538514deb35046cea5eef3e54038...b66de00a420e3e371abdae4edc77e4b5d904e434) (2019-07-03)


### Changes

chore(core): Bump version to 0.0.383 ([b66de00a](https://github.com/spinnaker/deck/commit/b66de00a420e3e371abdae4edc77e4b5d904e434))  
refactor(core/serverGroup): Extract capacity details components to reuse across providers [#7182](https://github.com/spinnaker/deck/pull/7182) ([6630f9bc](https://github.com/spinnaker/deck/commit/6630f9bca7bd17e99bafa174b7659bc6c63f79fd))  



## [0.0.382](https://www.github.com/spinnaker/deck/compare/3a390c9da3901425c2eb97bdf41b3c628f31a80a...4d9a192d81c2538514deb35046cea5eef3e54038) (2019-07-02)


### Changes

Bump package core to 0.0.382 and amazon to 0.0.199 [#7179](https://github.com/spinnaker/deck/pull/7179) ([4d9a192d](https://github.com/spinnaker/deck/commit/4d9a192d81c2538514deb35046cea5eef3e54038))  
feat(trigger): Allow entry of explicit build number [#7176](https://github.com/spinnaker/deck/pull/7176) ([51b8d2da](https://github.com/spinnaker/deck/commit/51b8d2dab596db97ea9e6110cb6e2317ad97bb43))  
fix(pipeline): Fixed stage config for faileventual [#7174](https://github.com/spinnaker/deck/pull/7174) ([9eb04b48](https://github.com/spinnaker/deck/commit/9eb04b48eb5d667f963e3496a23bbb7626047c8e))  
feat(core/task): UserVerification: Accept an 'account' prop. [#7173](https://github.com/spinnaker/deck/pull/7173) ([40eb9394](https://github.com/spinnaker/deck/commit/40eb9394247966f51b1975bbbb069ebda8593a82))  
refactor(core/serverGroup): Extract MinMaxDesiredChanges component [#7171](https://github.com/spinnaker/deck/pull/7171) ([97c4aed4](https://github.com/spinnaker/deck/commit/97c4aed44ea05672dcbdc2316cc857fb1f96c0dc))  



## [0.0.381](https://www.github.com/spinnaker/deck/compare/4422b34071224020b983b4c8a56fc8dd043f3e4f...3a390c9da3901425c2eb97bdf41b3c628f31a80a) (2019-07-01)


### Changes

chore(core): Bump version to 0.0.381 [#7172](https://github.com/spinnaker/deck/pull/7172) ([3a390c9d](https://github.com/spinnaker/deck/commit/3a390c9da3901425c2eb97bdf41b3c628f31a80a))  
fix(core): correctly build permalinks for executions [#7167](https://github.com/spinnaker/deck/pull/7167) ([7d3b83a3](https://github.com/spinnaker/deck/commit/7d3b83a34ed8464462866f66dea57d699c317b47))  
fix(core/managed): add docs link to managed infra indicator [#7166](https://github.com/spinnaker/deck/pull/7166) ([ccf080aa](https://github.com/spinnaker/deck/commit/ccf080aaf6fe6e887bdb4b71279a6aacf00782b4))  
refactor(core/presentation): Refactor FormField components using hooks [#7148](https://github.com/spinnaker/deck/pull/7148) ([d23ee1c8](https://github.com/spinnaker/deck/commit/d23ee1c8fddb7bfb72ca818fdb1f1fde3e9a877c))  



## [0.0.380](https://www.github.com/spinnaker/deck/compare/d9e58aadd786e9f0a0c951972b840b419499482f...4422b34071224020b983b4c8a56fc8dd043f3e4f) (2019-06-28)


### Changes

Bump package core to 0.0.380 and docker to 0.0.43 and amazon to 0.0.198 and titus to 0.0.100 [#7163](https://github.com/spinnaker/deck/pull/7163) ([4422b340](https://github.com/spinnaker/deck/commit/4422b34071224020b983b4c8a56fc8dd043f3e4f))  
 feat(core/amazon): add a visual indicator to infra managed by Keel [#7161](https://github.com/spinnaker/deck/pull/7161) ([2fd1aa97](https://github.com/spinnaker/deck/commit/2fd1aa97195cc1bf8c26447a3d144e6d6192021f))  



## [0.0.379](https://www.github.com/spinnaker/deck/compare/ea94928cbb30e8543f9c6cc8eb492954b5cb43fb...d9e58aadd786e9f0a0c951972b840b419499482f) (2019-06-27)


### Changes

Bump package core to 0.0.379 and amazon to 0.0.197 [#7159](https://github.com/spinnaker/deck/pull/7159) ([d9e58aad](https://github.com/spinnaker/deck/commit/d9e58aadd786e9f0a0c951972b840b419499482f))  
fix(core/pipeline): When a pipeline or jenkins parameter is no longer accepted by the job/pipeline, show the parameter value (in addition to the name) in the warning message. [#7149](https://github.com/spinnaker/deck/pull/7149) ([973bd5c0](https://github.com/spinnaker/deck/commit/973bd5c0d643f9437c17a512e95fb88307cb5444))  
fix(core): fix alignment on cards in card choices [#7158](https://github.com/spinnaker/deck/pull/7158) ([8d929f06](https://github.com/spinnaker/deck/commit/8d929f06670e27055f488c282c13aa929578eccc))  



## [0.0.378](https://www.github.com/spinnaker/deck/compare/ce194afac6673ffb49f1413bfc368ef12308df58...ea94928cbb30e8543f9c6cc8eb492954b5cb43fb) (2019-06-26)


### Changes

Bump package core to 0.0.378 and amazon to 0.0.195 [#7155](https://github.com/spinnaker/deck/pull/7155) ([ea94928c](https://github.com/spinnaker/deck/commit/ea94928cbb30e8543f9c6cc8eb492954b5cb43fb))  
fix(core): properly update execution permalink on location change [#7152](https://github.com/spinnaker/deck/pull/7152) ([058007ea](https://github.com/spinnaker/deck/commit/058007eaaeadd1ea5f4b9e9ba119bd1b757869b0))  
fix(projects): Fixed project dashboard to application link [#7154](https://github.com/spinnaker/deck/pull/7154) ([9bd9624e](https://github.com/spinnaker/deck/commit/9bd9624e9df2a009eaa85f80a246e1cd49cf308b))  
feat(appengine): Enable new artifacts for config artifacts in deploy SG [#7153](https://github.com/spinnaker/deck/pull/7153) ([d6ad8c25](https://github.com/spinnaker/deck/commit/d6ad8c252c596405cbd0344e82083b67579dec5d))  
feat(appengine): Enable new artifacts workflow in deploy SG [#7147](https://github.com/spinnaker/deck/pull/7147) ([a9a927b7](https://github.com/spinnaker/deck/commit/a9a927b70917f4867a5d2882312671bc10d1d639))  



## [0.0.377](https://www.github.com/spinnaker/deck/compare/e9b09fe8391874799e03507f02746d3756e5bcb6...ce194afac6673ffb49f1413bfc368ef12308df58) (2019-06-25)


### Changes

Bump package core to 0.0.377 and docker to 0.0.42 and amazon to 0.0.194 [#7150](https://github.com/spinnaker/deck/pull/7150) ([ce194afa](https://github.com/spinnaker/deck/commit/ce194afac6673ffb49f1413bfc368ef12308df58))  
refactor(core): virtualize execution rendering [#7140](https://github.com/spinnaker/deck/pull/7140) ([d5425b45](https://github.com/spinnaker/deck/commit/d5425b45b9692ccc989cacbde469343fd9919227))  
feat(core): allow users to override pipeline graph positions [#7141](https://github.com/spinnaker/deck/pull/7141) ([342087cf](https://github.com/spinnaker/deck/commit/342087cf996b2bcadb991e7086552b29509e7e4b))  
fix(core): do not send a cloud provider on v2 search calls [#7142](https://github.com/spinnaker/deck/pull/7142) ([fb58d70d](https://github.com/spinnaker/deck/commit/fb58d70ddf8f14f2b2ca128505e6b255557ee583))  
chore(core): clarify clone stage help text [#7131](https://github.com/spinnaker/deck/pull/7131) ([5d6d9aad](https://github.com/spinnaker/deck/commit/5d6d9aad3ee09afd70614aa821e48927a6920e50))  
fix(core): Display latest template in pipeline template list [#7145](https://github.com/spinnaker/deck/pull/7145) ([745f0a16](https://github.com/spinnaker/deck/commit/745f0a16ac689209ff44fe2bc6839317476034a6))  
fix(webhooks): addresses issue 3450 - introduce a delay before polling webhook [#7144](https://github.com/spinnaker/deck/pull/7144) ([456172b5](https://github.com/spinnaker/deck/commit/456172b52d41fb7e014a8ac7344d9476687bbe27))  
feat(core): Enable new artifacts workflow in bakeManifest [#7138](https://github.com/spinnaker/deck/pull/7138) ([7238d1de](https://github.com/spinnaker/deck/commit/7238d1de6ebef092c1c25d1851d9524593c63f8b))  
feat(artifacts): find multiple artifacts from single execution [#7139](https://github.com/spinnaker/deck/pull/7139) ([45e5aa34](https://github.com/spinnaker/deck/commit/45e5aa348ab7138b3b2a078c457c218bc129869c))  
fix(core): provide key for repeating param JSX elements [#7136](https://github.com/spinnaker/deck/pull/7136) ([dae73dad](https://github.com/spinnaker/deck/commit/dae73dad404ba35715217c147ba82bc33fe3a5c3))  
fix(core): filter falsy error messages from errors object on tasks [#7135](https://github.com/spinnaker/deck/pull/7135) ([bbc1d065](https://github.com/spinnaker/deck/commit/bbc1d06598b5edc40fb59fd3ff448db4717a3a29))  
refactor(core): Reactify overrideTimeout [#7126](https://github.com/spinnaker/deck/pull/7126) ([47828080](https://github.com/spinnaker/deck/commit/47828080e629cf9c16ebfb510f1d316dfc332c9e))  
feat(core/presentation): Always call onBlur in Checklist to "mark as touched" [#7134](https://github.com/spinnaker/deck/pull/7134) ([0a3bd68b](https://github.com/spinnaker/deck/commit/0a3bd68babe3b3a9c7b44da27e461b15f2abeb07))  
chore(package): Just Update Prettier™ ([cdd6f237](https://github.com/spinnaker/deck/commit/cdd6f2379859d3c2b13bac59aa470c08b391a865))  
fix(amazon): Support SpEL in advanced capacity [#7124](https://github.com/spinnaker/deck/pull/7124) ([7e464a9a](https://github.com/spinnaker/deck/commit/7e464a9a7d56326c57d027b96d4e567af9e5db3f))  
chore(deck): Update to Typescript 3.4 ([08e95063](https://github.com/spinnaker/deck/commit/08e950634131cd5fdd0f37cbcea68386d0a662a0))  
fix(core): do not stretch provider logos in selection modal [#7128](https://github.com/spinnaker/deck/pull/7128) ([fa515dc7](https://github.com/spinnaker/deck/commit/fa515dc7462b2ffaad51ca81f9faa71215b449db))  



## [0.0.376](https://www.github.com/spinnaker/deck/compare/a256d509140a71f98d39e0badec8e5e5dd519ebc...e9b09fe8391874799e03507f02746d3756e5bcb6) (2019-06-18)


### Changes

chore(core): Bump version to 0.0.376 [#7129](https://github.com/spinnaker/deck/pull/7129) ([e9b09fe8](https://github.com/spinnaker/deck/commit/e9b09fe8391874799e03507f02746d3756e5bcb6))  
fix(pipeline): fix invisible parameter when default is not in options [#7125](https://github.com/spinnaker/deck/pull/7125) ([939d608e](https://github.com/spinnaker/deck/commit/939d608e989c771b225dc2d69cec8a798065c218))  
fix(core/pipeline): stop searching stage context, being greedy about parentExecutions [#7127](https://github.com/spinnaker/deck/pull/7127) ([5c4facbd](https://github.com/spinnaker/deck/commit/5c4facbddf8f44e6ed78b9f4776397b551c5c3c4))  
fix(core): do not inject default execution window values on render [#7122](https://github.com/spinnaker/deck/pull/7122) ([8fe74bc5](https://github.com/spinnaker/deck/commit/8fe74bc56b02baa985b4c71b95a0ea8c30a291f0))  
fix(core/pipeline): use correct visibility default for stage durations [#7121](https://github.com/spinnaker/deck/pull/7121) ([ee6fbe0a](https://github.com/spinnaker/deck/commit/ee6fbe0a3b554437a62f284dc360488997204a6e))  
refactor(stages): Fixed alias matching, added fallback and unit tests [#7080](https://github.com/spinnaker/deck/pull/7080) ([c88234c6](https://github.com/spinnaker/deck/commit/c88234c6fcc5730b9e238d97dc85122af8c70c3d))  
refactor(core): Reactify ExecutionWindows component [#7113](https://github.com/spinnaker/deck/pull/7113) ([c3588010](https://github.com/spinnaker/deck/commit/c35880104520faf56736e0121e47e34a45b3e864))  
fix(artifact): use artifact icons in server group link [#7118](https://github.com/spinnaker/deck/pull/7118) ([744123de](https://github.com/spinnaker/deck/commit/744123debbe6d1615f1781a2f1b9f8e04ca04502))  
fix(forms): Fixed SpelText not firing onChange upon autocomplete [#7114](https://github.com/spinnaker/deck/pull/7114) ([b8aebd7c](https://github.com/spinnaker/deck/commit/b8aebd7c1d76d391067f17b7bb23f15acf269b9c))  
refactor(core): reactify overrideFailure component [#7107](https://github.com/spinnaker/deck/pull/7107) ([4e2f7495](https://github.com/spinnaker/deck/commit/4e2f749599fcaa2e5329d42c8455d932fa43553b))  
fix(core): Make template table list scrollable [#7111](https://github.com/spinnaker/deck/pull/7111) ([4f07aeb1](https://github.com/spinnaker/deck/commit/4f07aeb1ac0550e5bf15c0a7402edb2ac9654701))  
fix(core): Display template inherited items (mptv2) as read only [#7102](https://github.com/spinnaker/deck/pull/7102) ([e5d61155](https://github.com/spinnaker/deck/commit/e5d611553a364a6b37725496ee848bd4906a08e6))  



## [0.0.375](https://www.github.com/spinnaker/deck/compare/bf964c02b0dc79072f8e9ce7c274c47d3771c761...a256d509140a71f98d39e0badec8e5e5dd519ebc) (2019-06-11)


### Changes

chore(core): Bump version to 0.0.375 [#7108](https://github.com/spinnaker/deck/pull/7108) ([a256d509](https://github.com/spinnaker/deck/commit/a256d509140a71f98d39e0badec8e5e5dd519ebc))  
fix(core): fix auto-navigation on route=true searches [#7092](https://github.com/spinnaker/deck/pull/7092) ([0686fd8a](https://github.com/spinnaker/deck/commit/0686fd8a7450daa3ce02096c2cb0bda4fc799d06))  
refactor(core): reactify pipelineRoles component [#7104](https://github.com/spinnaker/deck/pull/7104) ([a3e678ed](https://github.com/spinnaker/deck/commit/a3e678ed3314963883527b588b2b802a5da1d52b))  



## [0.0.374](https://www.github.com/spinnaker/deck/compare/63e1f9e667ce46310503689511834d097d27aedd...bf964c02b0dc79072f8e9ce7c274c47d3771c761) (2019-06-05)


### Changes

chore(core): Bump version to 0.0.374 ([bf964c02](https://github.com/spinnaker/deck/commit/bf964c02b0dc79072f8e9ce7c274c47d3771c761))  
refactor(*): make accountExtractor return an array of strings [#7068](https://github.com/spinnaker/deck/pull/7068) ([8398d770](https://github.com/spinnaker/deck/commit/8398d7706951ce567c352e5f96351366103ef2e3))  
feat(core/presentation): support JSX for validationMessage in FormFields [#7083](https://github.com/spinnaker/deck/pull/7083) ([e6d1586b](https://github.com/spinnaker/deck/commit/e6d1586b891ee3c553054c0d638c37271f1eec52))  
fix(core): Disable rewriteLinks to allow proper event handling [#7064](https://github.com/spinnaker/deck/pull/7064) ([78a9f728](https://github.com/spinnaker/deck/commit/78a9f728d0d687001dbf12293da7932924885bba))  
feat(executions): Adding redirect for execution without application [#7076](https://github.com/spinnaker/deck/pull/7076) ([3f21c1f4](https://github.com/spinnaker/deck/commit/3f21c1f431a01adb12c86b36b042f8bd87de4fd8))  
feat(core/presentation): Add "success" type to ValidationMessage [#7082](https://github.com/spinnaker/deck/pull/7082) ([f89d97d0](https://github.com/spinnaker/deck/commit/f89d97d06341b0ac09a99fc64b539b10375ee021))  
refactor(core/presentation): Consolidate Checklist and ChecklistInput components [#7077](https://github.com/spinnaker/deck/pull/7077) ([4e89a39e](https://github.com/spinnaker/deck/commit/4e89a39e6575954fccae660e38f48bf8b106a978))  
feat(core/presentation): Support inline radio buttons [#7078](https://github.com/spinnaker/deck/pull/7078) ([9bbc002a](https://github.com/spinnaker/deck/commit/9bbc002ad8cefc538f61b0936803febe2c67310c))  
fix(triggers): Poll on execution id instead of event id [#7067](https://github.com/spinnaker/deck/pull/7067) ([b142789b](https://github.com/spinnaker/deck/commit/b142789b4e5708f6b331221ada9e076a33f8c556))  
refactor(pipeline): Pipeline stage execution details to react [#7063](https://github.com/spinnaker/deck/pull/7063) ([6af93e3f](https://github.com/spinnaker/deck/commit/6af93e3ff6500cac622a41e8eb867d8fbe73acb2))  



## [0.0.373](https://www.github.com/spinnaker/deck/compare/d794173e0e4554ba1ce2db9ecee3eefed5e1e727...63e1f9e667ce46310503689511834d097d27aedd) (2019-05-28)


### Changes

Bump package core to 0.0.373 and docker to 0.0.41 and titus to 0.0.98 [#7066](https://github.com/spinnaker/deck/pull/7066) ([63e1f9e6](https://github.com/spinnaker/deck/commit/63e1f9e667ce46310503689511834d097d27aedd))  
fix(runJob/kubernetes): reliably display logs [#7060](https://github.com/spinnaker/deck/pull/7060) ([89e4e785](https://github.com/spinnaker/deck/commit/89e4e78505ed8760214389eea683ad929c7a5a9f))  
refactor(core): allow checklist component to accept objects as a prop [#7058](https://github.com/spinnaker/deck/pull/7058) ([50f23fcc](https://github.com/spinnaker/deck/commit/50f23fcca731ebbc3bf9a25805223632ea8a3364))  
refactor(core): expose clusterService in react injector [#7043](https://github.com/spinnaker/deck/pull/7043) ([0eaae9a8](https://github.com/spinnaker/deck/commit/0eaae9a8e938a5ec3387ca471051417eda25cb61))  
fix(core): Build triggers: Properly render large number of jobs [#7056](https://github.com/spinnaker/deck/pull/7056) ([8dd1417e](https://github.com/spinnaker/deck/commit/8dd1417ed18557e2b37fceb820b6255bab4e1d1e))  
fix(artifacts/helm): support regex/spel in version [#7033](https://github.com/spinnaker/deck/pull/7033) ([49752334](https://github.com/spinnaker/deck/commit/497523348967d2d8c84dc6f6a1c57b7ae895e1be))  



## [0.0.372](https://www.github.com/spinnaker/deck/compare/ec7eb0f1a8c31b7dab7c0888e016b3a40c228bb9...d794173e0e4554ba1ce2db9ecee3eefed5e1e727) (2019-05-22)


### Changes

chore(core): Bump version to 0.0.372 [#7049](https://github.com/spinnaker/deck/pull/7049) ([d794173e](https://github.com/spinnaker/deck/commit/d794173e0e4554ba1ce2db9ecee3eefed5e1e727))  
fix(core): set runAsUser field correctly on triggers [#7048](https://github.com/spinnaker/deck/pull/7048) ([a6838326](https://github.com/spinnaker/deck/commit/a6838326fdb8d0879ac062cacd421801615b6760))  
fix(runJob/kubernetes): use explicit pod name [#7039](https://github.com/spinnaker/deck/pull/7039) ([f0287a11](https://github.com/spinnaker/deck/commit/f0287a11ccf92ab902adb55de8664bcde43175bd))  
fix(core): allow clearing of run as user field in triggers [#7045](https://github.com/spinnaker/deck/pull/7045) ([fc1cd1be](https://github.com/spinnaker/deck/commit/fc1cd1be9a361aad29f55ec4b17943273bd862f9))  
refactor(core): reactify CRON trigger [#7020](https://github.com/spinnaker/deck/pull/7020) ([24663793](https://github.com/spinnaker/deck/commit/246637936d56234d84801a91733afb630c9cb0b4))  



## [0.0.371](https://www.github.com/spinnaker/deck/compare/411db036f68be1ff16645d20cd63b5492b43c0eb...ec7eb0f1a8c31b7dab7c0888e016b3a40c228bb9) (2019-05-21)


### Changes

Bump package core to 0.0.371 and docker to 0.0.40 and amazon to 0.0.191 [#7041](https://github.com/spinnaker/deck/pull/7041) ([ec7eb0f1](https://github.com/spinnaker/deck/commit/ec7eb0f1a8c31b7dab7c0888e016b3a40c228bb9))  
feat(core): allow any json subtypes in API response [#7040](https://github.com/spinnaker/deck/pull/7040) ([2268217b](https://github.com/spinnaker/deck/commit/2268217b0f3cc8bf52be773a0d7e4ef3899bc621))  
fix(core/amazon): validate target group healthcheck fields [#6962](https://github.com/spinnaker/deck/pull/6962) ([b55e3b81](https://github.com/spinnaker/deck/commit/b55e3b819c219ec75544c6753891b31ff92ee5d2))  
fix(core): blur active element when rendering task monitor [#7034](https://github.com/spinnaker/deck/pull/7034) ([c9791e62](https://github.com/spinnaker/deck/commit/c9791e62d032ab06f5fe06ffc3f3c070a8e2fea9))  
fix(core): unwrap font-awesome span from button label [#7032](https://github.com/spinnaker/deck/pull/7032) ([43fb8a0b](https://github.com/spinnaker/deck/commit/43fb8a0b6ed63f818ab0a65cf15b3590dd6b40f5))  
fix(cf): server group header build links should precede images [#7027](https://github.com/spinnaker/deck/pull/7027) ([8982b348](https://github.com/spinnaker/deck/commit/8982b34874cefc7e2862d05acb735e808b62703d))  
fix(executions): Fixed missing account tags in standalone [#7036](https://github.com/spinnaker/deck/pull/7036) ([59b69b89](https://github.com/spinnaker/deck/commit/59b69b8968631712985b8d0c4285696adf416c9c))  
fix(artifact/helm): fix version list [#7030](https://github.com/spinnaker/deck/pull/7030) ([f3fd44cb](https://github.com/spinnaker/deck/commit/f3fd44cb512ce594733fba983ac0eb38db77b905))  
fix(chaos): Stack and detail are actually not required [#7028](https://github.com/spinnaker/deck/pull/7028) ([f1ca123f](https://github.com/spinnaker/deck/commit/f1ca123fdbfe237fadb3f6054d2b6066f361b1e0))  
refactor(core): minor fixes to the refactored triggers [#7024](https://github.com/spinnaker/deck/pull/7024) ([b505b5cc](https://github.com/spinnaker/deck/commit/b505b5cc40ec50389e2740eee5c0df750bcd7c51))  
fix(authz): Handle apps without execute permissions [#7017](https://github.com/spinnaker/deck/pull/7017) ([9cf9a623](https://github.com/spinnaker/deck/commit/9cf9a623d0b706bc242265564bbbb1101fb2ff8d))  
fix(kubernetes): Fix NPE in bake manifest details [#7022](https://github.com/spinnaker/deck/pull/7022) ([6390c71e](https://github.com/spinnaker/deck/commit/6390c71e38da44ad5395ec45a2b79506f1eac3f4))  



## [0.0.370](https://www.github.com/spinnaker/deck/compare/05b043659cbbee06728f98f12b0aae1ce1b2d320...411db036f68be1ff16645d20cd63b5492b43c0eb) (2019-05-17)


### Changes

chore(core): Bump version to 0.0.370 [#7021](https://github.com/spinnaker/deck/pull/7021) ([411db036](https://github.com/spinnaker/deck/commit/411db036f68be1ff16645d20cd63b5492b43c0eb))  
fix(core/pipeline): fix type mismatch in pipeline trigger, broken webhook trigger [#7018](https://github.com/spinnaker/deck/pull/7018) ([80af4684](https://github.com/spinnaker/deck/commit/80af46840150dcbb0073b54edafb8562ade5261b))  
refactor(kubernetes): convert deploy manifest stage to react [#7002](https://github.com/spinnaker/deck/pull/7002) ([53cb4229](https://github.com/spinnaker/deck/commit/53cb42296a5dbbff15a96232ea4010d72b81ace2))  
fix(artifacts): Artifacts are shown on pipeline execution when artifactsRewrite is enabled [#6973](https://github.com/spinnaker/deck/pull/6973) ([96ac95a3](https://github.com/spinnaker/deck/commit/96ac95a3c0d269c9467397ad7331a01495e2e435))  
fix(core): Hide "Run as user" when using managed service users [#7013](https://github.com/spinnaker/deck/pull/7013) ([5b6ad2a3](https://github.com/spinnaker/deck/commit/5b6ad2a3de2ccdd8df5b125c39b41851a21a42e3))  



## [0.0.369](https://www.github.com/spinnaker/deck/compare/1c8b570d9b733674c2e01d26f4c7d7d199353b15...05b043659cbbee06728f98f12b0aae1ce1b2d320) (2019-05-16)


### Changes

chore(core): Bump version to 0.0.369 [#7010](https://github.com/spinnaker/deck/pull/7010) ([05b04365](https://github.com/spinnaker/deck/commit/05b043659cbbee06728f98f12b0aae1ce1b2d320))  
fix(core): do not automatically inject parameterConfig on pipelines [#7009](https://github.com/spinnaker/deck/pull/7009) ([d805c185](https://github.com/spinnaker/deck/commit/d805c185c3d299b14e9746ef522ea702a96cbedc))  



## [0.0.368](https://www.github.com/spinnaker/deck/compare/83bc1522ed53e2c9f0511afe13754bfe3585eb36...1c8b570d9b733674c2e01d26f4c7d7d199353b15) (2019-05-15)


### Changes

chore(core): bump package to 0.0.368 [#7008](https://github.com/spinnaker/deck/pull/7008) ([1c8b570d](https://github.com/spinnaker/deck/commit/1c8b570d9b733674c2e01d26f4c7d7d199353b15))  
fix(core): provide formatLabel option for all trigger types [#7007](https://github.com/spinnaker/deck/pull/7007) ([7639f803](https://github.com/spinnaker/deck/commit/7639f803ab3d197d861aa4ac2c03062ea2b1f494))  



## [0.0.367](https://www.github.com/spinnaker/deck/compare/044410b9ca50aace1ffb779cafd0d639ae23325c...83bc1522ed53e2c9f0511afe13754bfe3585eb36) (2019-05-15)


### Changes

chore(core): Bump version to 0.0.367 [#7004](https://github.com/spinnaker/deck/pull/7004) ([83bc1522](https://github.com/spinnaker/deck/commit/83bc1522ed53e2c9f0511afe13754bfe3585eb36))  
fix(core): do not validate pipeline configs before initialization [#7003](https://github.com/spinnaker/deck/pull/7003) ([cfb2a51e](https://github.com/spinnaker/deck/commit/cfb2a51e906c1b2cf50280413fab4db06ba8ef05))  



## [0.0.366](https://www.github.com/spinnaker/deck/compare/52c637b446c5bc8732a927a42d1e18a914b4e688...044410b9ca50aace1ffb779cafd0d639ae23325c) (2019-05-15)


### Changes

Bump package core to 0.0.366 and titus to 0.0.97 [#7001](https://github.com/spinnaker/deck/pull/7001) ([044410b9](https://github.com/spinnaker/deck/commit/044410b9ca50aace1ffb779cafd0d639ae23325c))  
feat(titus): Render run job output file as YAML [#6992](https://github.com/spinnaker/deck/pull/6992) ([4c06d10d](https://github.com/spinnaker/deck/commit/4c06d10d70bc53008aaeeee1ae7441c36bfcf485))  
fix(core): filter empty URL parts in ApiService, do not next scheduler on unsubscribe [#7000](https://github.com/spinnaker/deck/pull/7000) ([b888ff58](https://github.com/spinnaker/deck/commit/b888ff581c718da3dda9d3274b20c706184f5aff))  



## [0.0.365](https://www.github.com/spinnaker/deck/compare/fa85ebec53bced777eb62cef247a67508a1a6bab...52c637b446c5bc8732a927a42d1e18a914b4e688) (2019-05-15)


### Changes

Bump package core to 0.0.365 and docker to 0.0.39 [#6997](https://github.com/spinnaker/deck/pull/6997) ([52c637b4](https://github.com/spinnaker/deck/commit/52c637b446c5bc8732a927a42d1e18a914b4e688))  
fix(core): request project pipeline configs just in time [#6980](https://github.com/spinnaker/deck/pull/6980) ([0fc8946a](https://github.com/spinnaker/deck/commit/0fc8946ac251424f25c83a26cd212a5de5117eb5))  
fix(core): include un-run pipelines when filtering by text [#6979](https://github.com/spinnaker/deck/pull/6979) ([cfd7690a](https://github.com/spinnaker/deck/commit/cfd7690aa49b61b0ff40b41af519cad0acda3b41))  
fix(core): set graph label node height correctly [#6993](https://github.com/spinnaker/deck/pull/6993) ([10fbec5e](https://github.com/spinnaker/deck/commit/10fbec5ef2622dce2e7522aff585db00c8afa732))  
fix(artifacts): Fix fetching helm artifact versions [#6995](https://github.com/spinnaker/deck/pull/6995) ([0ed0ab63](https://github.com/spinnaker/deck/commit/0ed0ab63370ca15de33609d7ebdbf797b0d8829a))  
refactor(core): add React Components for PageSection and PageNavigator [#6926](https://github.com/spinnaker/deck/pull/6926) ([dfc84b1b](https://github.com/spinnaker/deck/commit/dfc84b1bcca8253e94f9eaa3bc46102ba3d4e538))  
fix(cf): add Artifactory link [#6994](https://github.com/spinnaker/deck/pull/6994) ([ce8a1637](https://github.com/spinnaker/deck/commit/ce8a1637505ddde40cacfea9ba8cc807c127bb8f))  
chore(core): remove hipchat notification module [#6884](https://github.com/spinnaker/deck/pull/6884) ([f7cb7ab8](https://github.com/spinnaker/deck/commit/f7cb7ab8a4f27351af34a7a28e73e31567637b7e))  
fix(*): Fix imports [#6991](https://github.com/spinnaker/deck/pull/6991) ([98c93274](https://github.com/spinnaker/deck/commit/98c9327400b1144d5d2ab32ebedc7a8fa667a493))  
refactor(core): Convert most triggers from angular to react ([c8590ff9](https://github.com/spinnaker/deck/commit/c8590ff994d70e0a0a03bc71381bfebe847fc030))  



## [0.0.364](https://www.github.com/spinnaker/deck/compare/435fdb1bd5b70c07785a4e70424e275a8d2d1ff0...fa85ebec53bced777eb62cef247a67508a1a6bab) (2019-05-14)


### Changes

chore(core): Bump version to 0.0.364 [#6986](https://github.com/spinnaker/deck/pull/6986) ([fa85ebec](https://github.com/spinnaker/deck/commit/fa85ebec53bced777eb62cef247a67508a1a6bab))  
fix(titus): Shimming the execution logs for preconfigured jobs [#6972](https://github.com/spinnaker/deck/pull/6972) ([f44e2e8f](https://github.com/spinnaker/deck/commit/f44e2e8f93c3a509765633c4ab1e79694d7c2177))  



## [0.0.363](https://www.github.com/spinnaker/deck/compare/1dce7cec0a00144ae283395b37ef53dcacc698f8...435fdb1bd5b70c07785a4e70424e275a8d2d1ff0) (2019-05-13)


### Changes

Bump package core to 0.0.363 and amazon to 0.0.189 [#6985](https://github.com/spinnaker/deck/pull/6985) ([435fdb1b](https://github.com/spinnaker/deck/commit/435fdb1bd5b70c07785a4e70424e275a8d2d1ff0))  
fix(core): show no results message on v2 search [#6982](https://github.com/spinnaker/deck/pull/6982) ([8edcc221](https://github.com/spinnaker/deck/commit/8edcc221c45f4d3533ffe2056323688a4ae31e32))  
feat(core): allow dynamic resolution of docker insights URL [#6981](https://github.com/spinnaker/deck/pull/6981) ([1bbd9366](https://github.com/spinnaker/deck/commit/1bbd93664306eaea595a746ae664c64944559ed4))  
fix(cf): reorg build info details on server group detail and summary [#6983](https://github.com/spinnaker/deck/pull/6983) ([84db0ef6](https://github.com/spinnaker/deck/commit/84db0ef6c88ba6b234a4f06d7f2af91b2d84ee41))  
fix(core): Add expected artifacts to inheritable mptv2 collections [#6975](https://github.com/spinnaker/deck/pull/6975) ([c48a0179](https://github.com/spinnaker/deck/commit/c48a0179a179524fe02cdda76fb0eedb3d25bfaa))  
refactor(core): Reactified parameters for triggers [#6923](https://github.com/spinnaker/deck/pull/6923) ([a21e95e8](https://github.com/spinnaker/deck/commit/a21e95e8da9f7d3ed8a83da708e353dee71cd8ef))  
feat(gcb): allow pre-artifacts-rewrite expected artifacts to be selected in gcb stage [#6948](https://github.com/spinnaker/deck/pull/6948) ([e439d2c6](https://github.com/spinnaker/deck/commit/e439d2c6bb703f6f5a6483be86fbaa4163445c64))  
fix(executions): URI Encode pipeline names [#6971](https://github.com/spinnaker/deck/pull/6971) ([fc50576e](https://github.com/spinnaker/deck/commit/fc50576eb0ff54ed1dd0a1c8efbac08c074e42cc))  
fix(server group): show a CI build link in server group.buildinfo [#6967](https://github.com/spinnaker/deck/pull/6967) ([70078307](https://github.com/spinnaker/deck/commit/7007830742d30ee7ebc182321ce79d13a20ee8ff))  
feat(core): Enable manual execution of mptv2 pipelines [#6968](https://github.com/spinnaker/deck/pull/6968) ([a79edb09](https://github.com/spinnaker/deck/commit/a79edb09480e1b3665879802c2f0590418810598))  



## [0.0.362](https://www.github.com/spinnaker/deck/compare/ffde9061b77ae4dba6db89781a7228171fc2db3b...1dce7cec0a00144ae283395b37ef53dcacc698f8) (2019-05-09)


### Changes

Bump package core to 0.0.362 and titus to 0.0.96 [#6964](https://github.com/spinnaker/deck/pull/6964) ([1dce7cec](https://github.com/spinnaker/deck/commit/1dce7cec0a00144ae283395b37ef53dcacc698f8))  
feat(stages): Support SpEL autocompletion for Evaluate Variables stage editor [#6958](https://github.com/spinnaker/deck/pull/6958) ([6d151c04](https://github.com/spinnaker/deck/commit/6d151c042e125e0de9f87da8eb5f373e9e3eed89))  
fix(core/presentation): Handle non-string inputs to Markdown component using .toString() [#6960](https://github.com/spinnaker/deck/pull/6960) ([b74c8555](https://github.com/spinnaker/deck/commit/b74c8555f625c68ea0be7194d2c404db18f3264f))  
fix(core/executions): make text on truncated params selectable [#6959](https://github.com/spinnaker/deck/pull/6959) ([c931524e](https://github.com/spinnaker/deck/commit/c931524e74b424282651f972f0e8841c17a316d4))  
fix(artifacts): Allow defaulting in execution artifact [#6961](https://github.com/spinnaker/deck/pull/6961) ([3d7c7947](https://github.com/spinnaker/deck/commit/3d7c79475835252ca456f515458f46b9fa7228ba))  
feat(stages): Add Concourse stage [#6957](https://github.com/spinnaker/deck/pull/6957) ([60ced4ad](https://github.com/spinnaker/deck/commit/60ced4ad2f84cff5536631c196d88ce4e03b2639))  



## [0.0.361](https://www.github.com/spinnaker/deck/compare/b2f3c8e9c1a7d364d0a490e5eae6ed45f57b136e...ffde9061b77ae4dba6db89781a7228171fc2db3b) (2019-05-08)


### Changes

Bump package core to 0.0.361 and titus to 0.0.95 [#6954](https://github.com/spinnaker/deck/pull/6954) ([ffde9061](https://github.com/spinnaker/deck/commit/ffde9061b77ae4dba6db89781a7228171fc2db3b))  
fix(core): use new ProviderSelectionService everywhere [#6953](https://github.com/spinnaker/deck/pull/6953) ([61c4afa0](https://github.com/spinnaker/deck/commit/61c4afa03fd275f094925c64853af2851d15c563))  



## [0.0.360](https://www.github.com/spinnaker/deck/compare/9b2d53c1182f3908ed026d46cd27adae8b5fec2b...b2f3c8e9c1a7d364d0a490e5eae6ed45f57b136e) (2019-05-08)


### Changes

Bump package core to 0.0.360 and amazon to 0.0.188 and titus to 0.0.94 [#6951](https://github.com/spinnaker/deck/pull/6951) ([b2f3c8e9](https://github.com/spinnaker/deck/commit/b2f3c8e9c1a7d364d0a490e5eae6ed45f57b136e))  
feat(core): compress AWS security groups before caching [#6947](https://github.com/spinnaker/deck/pull/6947) ([954506ac](https://github.com/spinnaker/deck/commit/954506ac56629b416441455ad1bb91929df19860))  
refactor(core): convert providerSelection modal to React [#6936](https://github.com/spinnaker/deck/pull/6936) ([67baf43a](https://github.com/spinnaker/deck/commit/67baf43add977faded5eabb1e01b454acbdd1bce))  
feat(kubernetes): expose rendered helm template in execution details [#6943](https://github.com/spinnaker/deck/pull/6943) ([1ada0219](https://github.com/spinnaker/deck/commit/1ada02195c197978e714126bf867121971ec7ccb))  
fix(artifacts): remove stage references to removed artifacts in artifacts rewrite mode [#6939](https://github.com/spinnaker/deck/pull/6939) ([edee9eff](https://github.com/spinnaker/deck/commit/edee9effaf06ea096e98b5b65eed0d945d2bb76c))  



## [0.0.359](https://www.github.com/spinnaker/deck/compare/4b4a360875f1db097b9d24ca0fff0b297b215a9c...9b2d53c1182f3908ed026d46cd27adae8b5fec2b) (2019-05-07)


### Changes

Bump package core to 0.0.359 and titus to 0.0.92 [#6942](https://github.com/spinnaker/deck/pull/6942) ([9b2d53c1](https://github.com/spinnaker/deck/commit/9b2d53c1182f3908ed026d46cd27adae8b5fec2b))  
feat(gcb): accept gcb definition file as artifact [#6935](https://github.com/spinnaker/deck/pull/6935) ([0c4c5b2f](https://github.com/spinnaker/deck/commit/0c4c5b2f71dff58da43a2ef4e0aaa69f619521df))  
fix(bake/manifest): Preserve artifact account selection [#6937](https://github.com/spinnaker/deck/pull/6937) ([e8024005](https://github.com/spinnaker/deck/commit/e80240058c369f45b561bcb28a512080830ce538))  
feat(runJob/kubernetes): render external link [#6930](https://github.com/spinnaker/deck/pull/6930) ([d1227493](https://github.com/spinnaker/deck/commit/d122749348d196b8626225d8741e6cdcb6e714bd))  
fix(core): use padding instead of margin on form flex columns [#6934](https://github.com/spinnaker/deck/pull/6934) ([3647ca55](https://github.com/spinnaker/deck/commit/3647ca552cc9fcdcb524cc748a462ba57def8849))  
fix(CRON): update links for CRON expression reference [#6933](https://github.com/spinnaker/deck/pull/6933) ([01cf072a](https://github.com/spinnaker/deck/commit/01cf072a3bf50ccdfad60db774f324cced733293))  
fix(core/api): normalize API request urls [#6927](https://github.com/spinnaker/deck/pull/6927) ([a369da2b](https://github.com/spinnaker/deck/commit/a369da2b20fb058fb37eaf9cb4c72c6044e33212))  



## [0.0.358](https://www.github.com/spinnaker/deck/compare/8a9d363717423f34a8d335cc0833c81b3b3c16e0...4b4a360875f1db097b9d24ca0fff0b297b215a9c) (2019-05-06)


### Changes

Bump package core to 0.0.358 and titus to 0.0.91 [#6931](https://github.com/spinnaker/deck/pull/6931) ([4b4a3608](https://github.com/spinnaker/deck/commit/4b4a360875f1db097b9d24ca0fff0b297b215a9c))  
feat(titus): job disruption budget UI [#6925](https://github.com/spinnaker/deck/pull/6925) ([8a2667c4](https://github.com/spinnaker/deck/commit/8a2667c4b82199a63445223e5fc4e946dda9ce11))  
feat(roles): Add execute permission type to create new application. [#6901](https://github.com/spinnaker/deck/pull/6901) ([fc6c8430](https://github.com/spinnaker/deck/commit/fc6c84301ae02a7d6add6dbe31f9b1f33ebfc0a3))  
refactor(runJob/kubernetes): refactor exec details [#6924](https://github.com/spinnaker/deck/pull/6924) ([9e03e52f](https://github.com/spinnaker/deck/commit/9e03e52fbaf480b5689e4e186b7f5928c976cf6f))  
feat(gcb): add gcb-specific execution details tab ([3b7e5560](https://github.com/spinnaker/deck/commit/3b7e5560dda48ebea94ab8ab63be0a5bce3b2a24))  



## [0.0.357](https://www.github.com/spinnaker/deck/compare/ff7d8e2e60492b7d737969f9711a1cadb868c349...8a9d363717423f34a8d335cc0833c81b3b3c16e0) (2019-05-01)


### Changes

chore(core): Bump version to 0.0.357 [#6921](https://github.com/spinnaker/deck/pull/6921) ([8a9d3637](https://github.com/spinnaker/deck/commit/8a9d363717423f34a8d335cc0833c81b3b3c16e0))  
feat(core): modify form CSS for help text, group headers [#6920](https://github.com/spinnaker/deck/pull/6920) ([88c4cd29](https://github.com/spinnaker/deck/commit/88c4cd299a2fd5ac8724fe43dcaa129a49aad6a0))  
feat(core): add ChecklistInput form component [#6919](https://github.com/spinnaker/deck/pull/6919) ([81a7ce3d](https://github.com/spinnaker/deck/commit/81a7ce3de788fc5868aa006bd917b4848c77b8dd))  
fix(core): Fixed exception while configuring strategies [#6918](https://github.com/spinnaker/deck/pull/6918) ([89db6040](https://github.com/spinnaker/deck/commit/89db6040ee9dc5adb90b9d358e2795bb7f984712))  
fix(helm): Fix Helm artifact editor (names state unset) [#6914](https://github.com/spinnaker/deck/pull/6914) ([d9e7123e](https://github.com/spinnaker/deck/commit/d9e7123e74a6d41a97c86619fba0563ca6989685))  



## [0.0.356](https://www.github.com/spinnaker/deck/compare/ac6b899e72fb0b9ef006148be06ee054a17be237...ff7d8e2e60492b7d737969f9711a1cadb868c349) (2019-04-29)


### Changes

Bump package core to 0.0.356 and docker to 0.0.38 and amazon to 0.0.187 [#6910](https://github.com/spinnaker/deck/pull/6910) ([ff7d8e2e](https://github.com/spinnaker/deck/commit/ff7d8e2e60492b7d737969f9711a1cadb868c349))  
fix(core): use relative imports for ReactSelectInput [#6909](https://github.com/spinnaker/deck/pull/6909) ([bd7bccea](https://github.com/spinnaker/deck/commit/bd7bcceab36617c79bf0e3851ffd55c6adfcabf6))  
fix(core): update stage failure component when JSON changes [#6889](https://github.com/spinnaker/deck/pull/6889) ([e44f22ba](https://github.com/spinnaker/deck/commit/e44f22ba6b39a975f0721106d0f2ffbf03e4091d))  
fix(core): filter pipeline param choices by search text [#6903](https://github.com/spinnaker/deck/pull/6903) ([9454e340](https://github.com/spinnaker/deck/commit/9454e34046282b3bd1471c00fc509d30c946bed9))  
fix(core): Restore config button on exec view and dropdown for mptv2 [#6894](https://github.com/spinnaker/deck/pull/6894) ([960048f7](https://github.com/spinnaker/deck/commit/960048f772a3a14044b13429c4391486b58d18c2))  
feat(cf): Create service key SpEL expression [#6828](https://github.com/spinnaker/deck/pull/6828) ([af5ba5ba](https://github.com/spinnaker/deck/commit/af5ba5ba15c4a750acaf3f8f02ee780d715a6a69))  
feat(cf): Reduce angular dependencies [#6893](https://github.com/spinnaker/deck/pull/6893) ([62c2b513](https://github.com/spinnaker/deck/commit/62c2b513c9a7cdfbaea5d23d7328153ad4084178))  
fix(core): Show all app selections when creating a pipeline from mptv2 [#6891](https://github.com/spinnaker/deck/pull/6891) ([7fde1fad](https://github.com/spinnaker/deck/commit/7fde1fad4f3e6663dc388fce45f380f9f0531a4f))  
feat(core): UI for configuring pipelines from mptv2 [#6880](https://github.com/spinnaker/deck/pull/6880) ([90fb96c4](https://github.com/spinnaker/deck/commit/90fb96c4bfbbb07b23c8c3072229e58aa6f68868))  
fix(core): do not overdo showing the auth modal [#6882](https://github.com/spinnaker/deck/pull/6882) ([fee4c120](https://github.com/spinnaker/deck/commit/fee4c120334f68f54162e5dda552320f7bee23c0))  
fix(core): initialize default artifact with type [#6885](https://github.com/spinnaker/deck/pull/6885) ([9c8f12ea](https://github.com/spinnaker/deck/commit/9c8f12ea1a444b33fd863e96be529ad386e9b5c1))  
refactor(stages): Wire up Pipeline validation to stage validateFn [#6881](https://github.com/spinnaker/deck/pull/6881) ([1ced961c](https://github.com/spinnaker/deck/commit/1ced961c1e71a8003d4ae93aa04dc44932164b07))  



## [0.0.355](https://www.github.com/spinnaker/deck/compare/647d7c7ad6c4b6a4fd4a55bd86195ae84ab82fce...ac6b899e72fb0b9ef006148be06ee054a17be237) (2019-04-23)


### Changes

chore(core): Bump version to 0.0.355 [#6878](https://github.com/spinnaker/deck/pull/6878) ([ac6b899e](https://github.com/spinnaker/deck/commit/ac6b899e72fb0b9ef006148be06ee054a17be237))  
fix(core): make imports relative on AccountSelectInput, RegionSelectInput [#6877](https://github.com/spinnaker/deck/pull/6877) ([a5e20598](https://github.com/spinnaker/deck/commit/a5e205981c451a0bad5162477238a5380d4e0971))  



## [0.0.354](https://www.github.com/spinnaker/deck/compare/792ccd4e1f87720dbcc3b0606785a1dae2afd022...647d7c7ad6c4b6a4fd4a55bd86195ae84ab82fce) (2019-04-23)


### Changes

Bump package [#6875](https://github.com/spinnaker/deck/pull/6875) ([647d7c7a](https://github.com/spinnaker/deck/commit/647d7c7ad6c4b6a4fd4a55bd86195ae84ab82fce))  
refactor(stages): FormikStageConfig to provide Formik for StageConfigs [#6871](https://github.com/spinnaker/deck/pull/6871) ([96dcf58a](https://github.com/spinnaker/deck/commit/96dcf58a1acfc507c404640f68040983645d2aed))  
feat(kubernetes): remove rollout strategies feature flag ([bd94593d](https://github.com/spinnaker/deck/commit/bd94593d91f99259a0bff97776909d1572e0540f))  
fix(kubernetes): handle k8s-specific account/region task keys in tasks history view [#6869](https://github.com/spinnaker/deck/pull/6869) ([2056b64d](https://github.com/spinnaker/deck/commit/2056b64d85af64e4cc687fd320f5d6e5467c12a1))  



## [0.0.353](https://www.github.com/spinnaker/deck/compare/3c32cd35c7a139fa0717b7b1b8b4d10bc1523cd9...792ccd4e1f87720dbcc3b0606785a1dae2afd022) (2019-04-18)


### Changes

Bump package core to 0.0.353 and titus to 0.0.88 [#6867](https://github.com/spinnaker/deck/pull/6867) ([792ccd4e](https://github.com/spinnaker/deck/commit/792ccd4e1f87720dbcc3b0606785a1dae2afd022))  
fix(core): conditionally show/label parameters section of execution bar [#6865](https://github.com/spinnaker/deck/pull/6865) ([3764ce7e](https://github.com/spinnaker/deck/commit/3764ce7eaa7d1c117daf0c09e3fa2d9d26d579cc))  



## [0.0.352](https://www.github.com/spinnaker/deck/compare/2abfe4ff8e4d1ecd651a4d0e8267e6f1dcae62b2...3c32cd35c7a139fa0717b7b1b8b4d10bc1523cd9) (2019-04-18)


### Changes

chore(core): Bump version to 0.0.352 [#6863](https://github.com/spinnaker/deck/pull/6863) ([3c32cd35](https://github.com/spinnaker/deck/commit/3c32cd35c7a139fa0717b7b1b8b4d10bc1523cd9))  
refactor(*): remove cache-clearing calls that do not do anything [#6861](https://github.com/spinnaker/deck/pull/6861) ([0a5fd58e](https://github.com/spinnaker/deck/commit/0a5fd58ed76c894f9a8b0ab7027bd07e0dfc43ce))  
fix(core): do not assume READ and WRITE are set on app permissions attribute [#6862](https://github.com/spinnaker/deck/pull/6862) ([a084b74c](https://github.com/spinnaker/deck/commit/a084b74ccffb3781eb8b534e8e6799d841c3ea28))  
fix(stage): remove pipeline filter for findArtifactFromExecution [#6858](https://github.com/spinnaker/deck/pull/6858) ([0333b67d](https://github.com/spinnaker/deck/commit/0333b67defc62ae77cf5e48096ce7ea4715c1a5b))  



## [0.0.351](https://www.github.com/spinnaker/deck/compare/700b8f6f7b536f909a0cc10389309f8b325906e4...2abfe4ff8e4d1ecd651a4d0e8267e6f1dcae62b2) (2019-04-16)


### Changes

Bump package core to 0.0.351 and docker to 0.0.36 and amazon to 0.0.185 and titus to 0.0.87 [#6855](https://github.com/spinnaker/deck/pull/6855) ([2abfe4ff](https://github.com/spinnaker/deck/commit/2abfe4ff8e4d1ecd651a4d0e8267e6f1dcae62b2))  
refactor(core/amazon): allow custom load balancer creation flow [#6852](https://github.com/spinnaker/deck/pull/6852) ([f6c87a33](https://github.com/spinnaker/deck/commit/f6c87a338abd78d11ca1e4099fe5a2229ed6dfcb))  
fix(titus): correctly set dirty field on command viewstate [#6795](https://github.com/spinnaker/deck/pull/6795) ([6ddc4760](https://github.com/spinnaker/deck/commit/6ddc47602b701fce5890fe266429ddff38e8b0c3))  
feat(help): Custom help link [#6842](https://github.com/spinnaker/deck/pull/6842) ([20d67666](https://github.com/spinnaker/deck/commit/20d676662a7caca429d4ccd52ef712f7fec140b0))  
chore: upgrade to react 16.8 [#6846](https://github.com/spinnaker/deck/pull/6846) ([a9cfe570](https://github.com/spinnaker/deck/commit/a9cfe570a2a00c460716ac7c38d1f4b8da3ac7d4))  
fix(pipeline/executionStatus): "details" link hidden by scrollbar. [#6850](https://github.com/spinnaker/deck/pull/6850) ([2e4135a7](https://github.com/spinnaker/deck/commit/2e4135a71ac9bec264a989b51c06af10f8f3ed07))  
feat(preconfiguredJobs): support produce artifacts [#6845](https://github.com/spinnaker/deck/pull/6845) ([af8fa5fe](https://github.com/spinnaker/deck/commit/af8fa5fe9bffe740ba7c0745ae542405a299cc24))  
fix(core/pipeline): un-shadow parameter [#6843](https://github.com/spinnaker/deck/pull/6843) ([48ba3fcd](https://github.com/spinnaker/deck/commit/48ba3fcd1e23cd1c4beab18a9e19d44deb2059af))  
feat(core/pipeline): add execution UI for waitForCondition stage [#6826](https://github.com/spinnaker/deck/pull/6826) ([ec125272](https://github.com/spinnaker/deck/commit/ec125272d65eef0495a120873ae621a14eec70f1))  
fix(artifacts): HTTP default artifact needs reference field [#6836](https://github.com/spinnaker/deck/pull/6836) ([e17f3871](https://github.com/spinnaker/deck/commit/e17f387111ce824d231ff1b39804786879bedf71))  
feat(preconfiguredJob): logs for k8s jobs [#6840](https://github.com/spinnaker/deck/pull/6840) ([e6185486](https://github.com/spinnaker/deck/commit/e61854866fbc8e5a4b1a369815d68998a6dc3d79))  
feat(core/pipelineConfig): toggle pins individually [#6830](https://github.com/spinnaker/deck/pull/6830) ([6072ee5a](https://github.com/spinnaker/deck/commit/6072ee5a8937855d39bee0783bbe97e794f4bdbc))  
fix(artifacts): Clean pipeline expected artifacts when triggers are removed [#6799](https://github.com/spinnaker/deck/pull/6799) ([c25363c3](https://github.com/spinnaker/deck/commit/c25363c3d7ead99787326df8bd20d675d3575f7b))  
fix(k8s): Fix deploy manifest [#6833](https://github.com/spinnaker/deck/pull/6833) ([56404313](https://github.com/spinnaker/deck/commit/56404313d6c8001f51eb98905454f7f0f4b5f243))  
feat(core/execution-parameters): condense parameters/artifacts and make it collapsable [#6756](https://github.com/spinnaker/deck/pull/6756) ([2020bf6c](https://github.com/spinnaker/deck/commit/2020bf6c9d402079efa8a8908df576ae67fa604b))  
feat(kubernetes): feature-flagged support for kubernetes traffic management strategies [#6816](https://github.com/spinnaker/deck/pull/6816) ([2d7f3885](https://github.com/spinnaker/deck/commit/2d7f3885248e71b930df101b942bb0998e16b635))  
feat(core): Create pipeline from v2 template list ([bcca9dcc](https://github.com/spinnaker/deck/commit/bcca9dccb18cb91604cd3f0fbec5b540396bf5d0))  
fix(triggers): Remove RunAsUser if pipeline permissions enabled for react triggers. [#6818](https://github.com/spinnaker/deck/pull/6818) ([d7e860c9](https://github.com/spinnaker/deck/commit/d7e860c92b1151aea30baf31d286f4406eb4bdfd))  
fix(mptv2): clicking a view link on template list screen throws exception [#6815](https://github.com/spinnaker/deck/pull/6815) ([1201896e](https://github.com/spinnaker/deck/commit/1201896e69bd35dcb4b81bebaff7ebcb499398cb))  
fix(artifacts): helm artifact, replace object assignw with object spread [#6814](https://github.com/spinnaker/deck/pull/6814) ([985a3ad9](https://github.com/spinnaker/deck/commit/985a3ad9bcf1b096824394071bda3e08f3d0247e))  
fix(artifacts): default helm artifact editor is broken [#6811](https://github.com/spinnaker/deck/pull/6811) ([2039b347](https://github.com/spinnaker/deck/commit/2039b3473409292e3f7fbe82f1a758ee2d485798))  
fix(google): GCE create server group and load balancer fixes [#6806](https://github.com/spinnaker/deck/pull/6806) ([bdcbc977](https://github.com/spinnaker/deck/commit/bdcbc977567f055dc05ad99ecac8385028dda4e6))  



## [0.0.350](https://www.github.com/spinnaker/deck/compare/2a2c78c31ed26c6513d9b88c334bef1acc640884...700b8f6f7b536f909a0cc10389309f8b325906e4) (2019-04-02)


### Changes

Bump package core to 0.0.350 and amazon to 0.0.184 [#6807](https://github.com/spinnaker/deck/pull/6807) ([700b8f6f](https://github.com/spinnaker/deck/commit/700b8f6f7b536f909a0cc10389309f8b325906e4))  
refactor(core): de-angularize ApplicationModelBuilder, fix project executions [#6802](https://github.com/spinnaker/deck/pull/6802) ([72e164df](https://github.com/spinnaker/deck/commit/72e164dfcf11afbac559195a8fa6a91dd77ad2b9))  
fix(triggers): Add lastSuccessfulBuild as a build option in Jenkins default artifact [#6797](https://github.com/spinnaker/deck/pull/6797) ([cc053391](https://github.com/spinnaker/deck/commit/cc053391ad7d11725066f8b25dcbe1853e4120cd))  
feat(mptv2): Add delete button & confirm modal to pipeline templates list [#6792](https://github.com/spinnaker/deck/pull/6792) ([daf72c4c](https://github.com/spinnaker/deck/commit/daf72c4c0076af68d4d85720b3efc99454ba2f8f))  



## [0.0.349](https://www.github.com/spinnaker/deck/compare/98ea5245661ecbe0c0ab4c2053b038eda19c4c03...2a2c78c31ed26c6513d9b88c334bef1acc640884) (2019-03-30)


### Changes

chore(core): Bump version to 0.0.349 [#6790](https://github.com/spinnaker/deck/pull/6790) ([2a2c78c3](https://github.com/spinnaker/deck/commit/2a2c78c31ed26c6513d9b88c334bef1acc640884))  
fix(core/pagerDuty): give appropriate app to PagerDutyWriter [#6789](https://github.com/spinnaker/deck/pull/6789) ([8b1e5bf4](https://github.com/spinnaker/deck/commit/8b1e5bf48a9d07007408d3eed9e355f6180857fb))  
fix(pipeline): Fix target impedance validator for clone server group [#6785](https://github.com/spinnaker/deck/pull/6785) ([8422d6a2](https://github.com/spinnaker/deck/commit/8422d6a2312ff08577fdcdbc74c49e107b2b06b2))  
fix(artifacts): Persist default artifact account in ExpectedArtifactModal [#6783](https://github.com/spinnaker/deck/pull/6783) ([ce12338f](https://github.com/spinnaker/deck/commit/ce12338f0d90925321a71299099bad162480fce8))  
fix(lint): Fix lint in pipeline templates list [#6782](https://github.com/spinnaker/deck/pull/6782) ([3519f6f9](https://github.com/spinnaker/deck/commit/3519f6f9e7d479c19755e2e40f46f323b0a82cf3))  
feat(templates): add Save button to export pipeline json modal [#6761](https://github.com/spinnaker/deck/pull/6761) ([36b86da6](https://github.com/spinnaker/deck/commit/36b86da691b44174a69984b06036c40e4900ad49))  
feat(gcb): add Google Cloud Build stage [#6774](https://github.com/spinnaker/deck/pull/6774) ([50b74f2d](https://github.com/spinnaker/deck/commit/50b74f2d0db86b15b66fd7a9a4985d0efb8fdb4b))  



## [0.0.348](https://www.github.com/spinnaker/deck/compare/0858fbf476387d53bf69e14ea98078cffc5371d5...98ea5245661ecbe0c0ab4c2053b038eda19c4c03) (2019-03-28)


### Changes

chore(core): Bump version to 0.0.348 [#6773](https://github.com/spinnaker/deck/pull/6773) ([98ea5245](https://github.com/spinnaker/deck/commit/98ea5245661ecbe0c0ab4c2053b038eda19c4c03))  
feat(core): provide link when task/stage fails due to traffic guards [#6772](https://github.com/spinnaker/deck/pull/6772) ([ad8d5048](https://github.com/spinnaker/deck/commit/ad8d504863f654a659dc7c82b97b1c1b7a61a7e0))  
fix(core): make chaos monkey opt-in for new apps [#6766](https://github.com/spinnaker/deck/pull/6766) ([d819cf37](https://github.com/spinnaker/deck/commit/d819cf3740f9341f66aee9c8cfa40eb01c404054))  



## [0.0.347](https://www.github.com/spinnaker/deck/compare/b85bd3ef21ea12ce882f28962ae1f4fb23d37597...0858fbf476387d53bf69e14ea98078cffc5371d5) (2019-03-27)


### Changes

chore(core): Bump version to 0.0.347 [#6765](https://github.com/spinnaker/deck/pull/6765) ([0858fbf4](https://github.com/spinnaker/deck/commit/0858fbf476387d53bf69e14ea98078cffc5371d5))  
fix(core): send application when paging via PagerDuty [#6764](https://github.com/spinnaker/deck/pull/6764) ([65fbb679](https://github.com/spinnaker/deck/commit/65fbb679c66251db38e3db0ed62ce7f0c559ef1f))  



## [0.0.346](https://www.github.com/spinnaker/deck/compare/33986e01c7902243e71e6cffc722df4d995f9833...b85bd3ef21ea12ce882f28962ae1f4fb23d37597) (2019-03-27)


### Changes

Bump package core to 0.0.346 and amazon to 0.0.182 and titus to 0.0.81 [#6758](https://github.com/spinnaker/deck/pull/6758) ([b85bd3ef](https://github.com/spinnaker/deck/commit/b85bd3ef21ea12ce882f28962ae1f4fb23d37597))  
fix(core/projects): Submit task against the 'spinnaker' application [#6748](https://github.com/spinnaker/deck/pull/6748) ([4bc6ecd3](https://github.com/spinnaker/deck/commit/4bc6ecd3ee0a3938627cb8b6c8a9c3c68148b3f8))  
feat(amazon): show certificate upload/expiration when selected for load balancers [#6731](https://github.com/spinnaker/deck/pull/6731) ([1f58407a](https://github.com/spinnaker/deck/commit/1f58407a8f8d1c28bf14c0e372cd3b482f4bb526))  
feat(core): add markdown functionality to custom app banners [#6745](https://github.com/spinnaker/deck/pull/6745) ([efcec969](https://github.com/spinnaker/deck/commit/efcec9696f70153ea09c4b0763ee15466a40c563))  
feat(mptv2): add a search input to the mptv2 list [#6742](https://github.com/spinnaker/deck/pull/6742) ([9c14b02e](https://github.com/spinnaker/deck/commit/9c14b02e3212bfce64d2e228273a5ae2a2907fb6))  
fix(concourse): Support manual trigger execution by build number [#6738](https://github.com/spinnaker/deck/pull/6738) ([a84eda8e](https://github.com/spinnaker/deck/commit/a84eda8efb966221756c4ccad7a5106722892c6b))  
fix(core): Grey out execution actions for mptv2 pipelines [#6734](https://github.com/spinnaker/deck/pull/6734) ([5529d53d](https://github.com/spinnaker/deck/commit/5529d53d562e9a8a6884ef93f96296c3d703b998))  
feat(ecs): docker image selection [#6687](https://github.com/spinnaker/deck/pull/6687) ([1c9e0754](https://github.com/spinnaker/deck/commit/1c9e07543aaefd36d8e1a5ea9d51321c350b57a8))  
feat(mptv2): add list screen for pipeline templates [#6723](https://github.com/spinnaker/deck/pull/6723) ([2fd608a1](https://github.com/spinnaker/deck/commit/2fd608a1eb238fb352fbccedbce506195801f02d))  
fix:(core): Replace copy icon with descriptive text in template modal [#6725](https://github.com/spinnaker/deck/pull/6725) ([5cd0aa0c](https://github.com/spinnaker/deck/commit/5cd0aa0c263247153cb809929782ffff6faed698))  
refactor(forms): Creating contexts for Layout and Help [#6718](https://github.com/spinnaker/deck/pull/6718) ([0d68c809](https://github.com/spinnaker/deck/commit/0d68c809a4b1ebbaa32204691de00638dd2133f2))  
fix(stages): Do not delete stageTimeoutMs when rendering [#6729](https://github.com/spinnaker/deck/pull/6729) ([7ab32f98](https://github.com/spinnaker/deck/commit/7ab32f98df1ef12957002372937b13485e3fd9a2))  
fix(artifacts): Exclude unmatchable expected artifact types [#6709](https://github.com/spinnaker/deck/pull/6709) ([03fa0d96](https://github.com/spinnaker/deck/commit/03fa0d962d743318bc63354676b2ea15c2216831))  



## [0.0.345](https://www.github.com/spinnaker/deck/compare/b225135e00af700e451beeb11f8fd69168ebd61c...33986e01c7902243e71e6cffc722df4d995f9833) (2019-03-20)


### Changes

Bump package core to 0.0.345 and amazon to 0.0.181 and titus to 0.0.80 [#6726](https://github.com/spinnaker/deck/pull/6726) ([33986e01](https://github.com/spinnaker/deck/commit/33986e01c7902243e71e6cffc722df4d995f9833))  
fix({core,cloudfoundry}/deploy): better red/black, rolling red/black help text [#6699](https://github.com/spinnaker/deck/pull/6699) ([29b001b6](https://github.com/spinnaker/deck/commit/29b001b67abaaa0d401bf8e468bddb6d43502d91))  
perf(core): cache subnets in window session [#6717](https://github.com/spinnaker/deck/pull/6717) ([647e3312](https://github.com/spinnaker/deck/commit/647e3312dafbad43d59f051ba93329c4e5647f3b))  
refactor(core): use non-deprecated tasks endpoint for app tasks [#6721](https://github.com/spinnaker/deck/pull/6721) ([5b6f229d](https://github.com/spinnaker/deck/commit/5b6f229dfab78b6d72a29e50e92a64bb4f0a6f41))  
fix(triggers): Add timeout to polling on manual trigger [#6707](https://github.com/spinnaker/deck/pull/6707) ([061a60c5](https://github.com/spinnaker/deck/commit/061a60c58c31dcf3b8c7bd475cb9dcaf88adb86c))  
fix(concourse): Fix concourse trigger config [#6715](https://github.com/spinnaker/deck/pull/6715) ([40c5e091](https://github.com/spinnaker/deck/commit/40c5e091903ba90944cb488e035a3393df482189))  
fix(artifacts): Fix SpEL text input used in React components [#6712](https://github.com/spinnaker/deck/pull/6712) ([2ac15b2f](https://github.com/spinnaker/deck/commit/2ac15b2f7c3bdd2764c97383e671078b617d7afc))  
fix(trigger): Fix react trigger components don't receive prop updates [#6711](https://github.com/spinnaker/deck/pull/6711) ([8c4104fa](https://github.com/spinnaker/deck/commit/8c4104fa0804f149c31ec177f9e12616eeffc59f))  
feat(kubernetes): add expression evaluation options to bake and deploy manifest stages [#6696](https://github.com/spinnaker/deck/pull/6696) ([a5a54bd3](https://github.com/spinnaker/deck/commit/a5a54bd327cdcb1a1c254f582db88636774a0714))  
fix(artifacts): Fix trigger artifact feature conditionalization [#6708](https://github.com/spinnaker/deck/pull/6708) ([78786f3e](https://github.com/spinnaker/deck/commit/78786f3eb8ed084a3a65cc849cdbacd08a03411b))  
fix(artifacts): HTTP artifact needs to set the reference field [#6679](https://github.com/spinnaker/deck/pull/6679) ([04746e0f](https://github.com/spinnaker/deck/commit/04746e0f167619f399510e87eede033e44acabf0))  
chore(core): upgrade the version to formik 1.4.1 [#6705](https://github.com/spinnaker/deck/pull/6705) ([51eeba48](https://github.com/spinnaker/deck/commit/51eeba480ac1bc232c0df020e4bb788a28a8b744))  
fix(cf): fix the alignment issue for artifacts in deploy SG [#6701](https://github.com/spinnaker/deck/pull/6701) ([4f826d1a](https://github.com/spinnaker/deck/commit/4f826d1a39465a62e7da1c1c6be0759d36d99db2))  
fix(triggers): Add pipeline name to search request [#6703](https://github.com/spinnaker/deck/pull/6703) ([2d9fb156](https://github.com/spinnaker/deck/commit/2d9fb156e57b69db6c2ba271d3db3b5951861332))  
feat(triggers): Add Concourse trigger type [#6692](https://github.com/spinnaker/deck/pull/6692) ([75de845c](https://github.com/spinnaker/deck/commit/75de845c2efb9af77b0574a06b158f5ce69b8cc5))  
feat(cf): Share/Unshare services [#6685](https://github.com/spinnaker/deck/pull/6685) ([e2940b4f](https://github.com/spinnaker/deck/commit/e2940b4f43f4bf0bde7568d36cf40e55bce2bbaf))  
feat(core): save pipelines stage was added [#6654](https://github.com/spinnaker/deck/pull/6654) ([88c8b5dd](https://github.com/spinnaker/deck/commit/88c8b5dd34acf7c1633063840cd4c8b5d480a3e4))  
 fix(artifacts): Make artifacts and artifactsRewrite flags mutually exclusive [#6694](https://github.com/spinnaker/deck/pull/6694) ([4a95a78e](https://github.com/spinnaker/deck/commit/4a95a78ef98d502762232a2b02c5e48780f0b2f5))  
chore(artifactory): Polish imports and labels [#6693](https://github.com/spinnaker/deck/pull/6693) ([50cffc94](https://github.com/spinnaker/deck/commit/50cffc94f1848be425b16211a4a5d41fd8244618))  
feat(core/deploy): UI widget for 'Delay Before Scale Down' [#6698](https://github.com/spinnaker/deck/pull/6698) ([4ac54cea](https://github.com/spinnaker/deck/commit/4ac54cea3f040b3259d684456fa35fde9dcbb3ae))  
feat(core): add jenkins artifact type [#6690](https://github.com/spinnaker/deck/pull/6690) ([792fcaa1](https://github.com/spinnaker/deck/commit/792fcaa10cc4e699fdbfac6e1cc72ba0bfdf52f1))  
 refactor(core): Remove triggerViaEcho feature flag [#6680](https://github.com/spinnaker/deck/pull/6680) ([7f68f221](https://github.com/spinnaker/deck/commit/7f68f2218ed19905ecb3d0fea1ea390be9dc0e81))  



## [0.0.344](https://www.github.com/spinnaker/deck/compare/1deafa4550bb0e1d5f01c45af924be0de3f46186...b225135e00af700e451beeb11f8fd69168ebd61c) (2019-03-12)


### Changes

chore(core): Bump version to 0.0.344 [#6674](https://github.com/spinnaker/deck/pull/6674) ([b225135e](https://github.com/spinnaker/deck/commit/b225135e00af700e451beeb11f8fd69168ebd61c))  
fix(artifacts): Simplify display name generation [#6670](https://github.com/spinnaker/deck/pull/6670) ([a68f5f16](https://github.com/spinnaker/deck/commit/a68f5f1672d8584346417b9579c6a37bd4a8c87d))  
fix(core): In baseProvider, fixed autoselection of React stage [#6673](https://github.com/spinnaker/deck/pull/6673) ([7a49d822](https://github.com/spinnaker/deck/commit/7a49d8222e4b52dd067a82f40e29b5055167df59))  



## [0.0.343](https://www.github.com/spinnaker/deck/compare/7b7b4465a446030f350ce4ccda32ad4833a0085d...1deafa4550bb0e1d5f01c45af924be0de3f46186) (2019-03-12)


### Changes

Bump package core to 0.0.343 and amazon to 0.0.179 and titus to 0.0.79 [#6672](https://github.com/spinnaker/deck/pull/6672) ([1deafa45](https://github.com/spinnaker/deck/commit/1deafa4550bb0e1d5f01c45af924be0de3f46186))  
feat(core): Warning message about invalid job params [#6669](https://github.com/spinnaker/deck/pull/6669) ([eacfe759](https://github.com/spinnaker/deck/commit/eacfe75944210fb41f1d857f2450fb9cc55f3a10))  
fix(core): Surface invalid params for a pipeline stage [#6668](https://github.com/spinnaker/deck/pull/6668) ([a28f7aff](https://github.com/spinnaker/deck/commit/a28f7aff2f8d614f3e7c236f383562bcf85ea9b1))  
refactor(*): remove unused local storage caches [#6665](https://github.com/spinnaker/deck/pull/6665) ([e2b4d8e9](https://github.com/spinnaker/deck/commit/e2b4d8e9371d5d2f1f9b60e2a592e10b47df73e2))  
feat(core): Add support for an Artifactory Trigger [#6664](https://github.com/spinnaker/deck/pull/6664) ([35e82ac0](https://github.com/spinnaker/deck/commit/35e82ac05b29de5091bdcbc05d46ddaecad3131c))  
feat(jenkins): Add artifact status tab to Jenkins execution details [#6666](https://github.com/spinnaker/deck/pull/6666) ([bc2bf43e](https://github.com/spinnaker/deck/commit/bc2bf43e2e07dbec2d9e1e457fd61435a1cef1c4))  
feat(artifacts): Re-use artifacts when re-running a pipeline [#6663](https://github.com/spinnaker/deck/pull/6663) ([e0bda863](https://github.com/spinnaker/deck/commit/e0bda863cf5bbd266e0cdbd941b9d02ade1db484))  
feat(core): Filter v2 pipeline templates from create pipeline modal [#6660](https://github.com/spinnaker/deck/pull/6660) ([29582dd4](https://github.com/spinnaker/deck/commit/29582dd47fc29b4048123be0484e13dc2f95ae4f))  
fix(core/pipeline): make cancelmodal take markdown for body [#6662](https://github.com/spinnaker/deck/pull/6662) ([45678a18](https://github.com/spinnaker/deck/commit/45678a1854b1f8d4066e3cc3565181ac3f272679))  
fix(artifacts): Correct render-if-feature for new artifacts on stage 'produces artifact' [#6661](https://github.com/spinnaker/deck/pull/6661) ([02af17f4](https://github.com/spinnaker/deck/commit/02af17f45002ba1db6f94ee29d5ab49fb8cb7d4c))  
fix(artifacts): Maven/ivy reference field, Base64 validation, SpelText performance [#6656](https://github.com/spinnaker/deck/pull/6656) ([0eb634c0](https://github.com/spinnaker/deck/commit/0eb634c0f1d8a80bed400d88dfb84843831f17d1))  
fix(core): Remove ability to trigger manual exec for mptv2 pipelines [#6651](https://github.com/spinnaker/deck/pull/6651) ([93e89be8](https://github.com/spinnaker/deck/commit/93e89be8e345dae20db0813068dcbc8beeed9036))  
feat(core): Add pipeline to IStageConfigProps [#6655](https://github.com/spinnaker/deck/pull/6655) ([d3209d44](https://github.com/spinnaker/deck/commit/d3209d441e9535e1aa4a169fcd0e6bb7de21f549))  
chore(angularjs): explicitly annotate more angularjs injections [#6653](https://github.com/spinnaker/deck/pull/6653) ([1bc94a1d](https://github.com/spinnaker/deck/commit/1bc94a1d519e5d64d4700447ec9356c51cb95a42))  
fix(artifacts): save HTTP URL as artifact reference [#6650](https://github.com/spinnaker/deck/pull/6650) ([78d0aae1](https://github.com/spinnaker/deck/commit/78d0aae1c264b557501079ee18ef1c823a6ff574))  
fix({core,amazon}/serverGroup): filter out empty tags, change 'tags' field type [#6645](https://github.com/spinnaker/deck/pull/6645) ([09d7fee2](https://github.com/spinnaker/deck/commit/09d7fee21f60d1f448497f806f5a599c95880514))  
fix(core): titus run jobs override all other providers [#6647](https://github.com/spinnaker/deck/pull/6647) ([8e9cb0bb](https://github.com/spinnaker/deck/commit/8e9cb0bb0c9327ec16e0192d104e4d4301a4cad1))  
fix(core): Remove configure button and setup redirect for mptv2 pipeline [#6644](https://github.com/spinnaker/deck/pull/6644) ([12e3bffe](https://github.com/spinnaker/deck/commit/12e3bffeafe6d4454499c4b7fb1781c5c42030cc))  
refactor(artifacts): Combine expected artifacts and trigger artifact constraints [#6634](https://github.com/spinnaker/deck/pull/6634) ([5da29652](https://github.com/spinnaker/deck/commit/5da29652e0f73726ce26de157db4ebff6956f54a))  
feat(users): Always surface authenticated user for executions/tasks [#6638](https://github.com/spinnaker/deck/pull/6638) ([eae6b458](https://github.com/spinnaker/deck/commit/eae6b458e5e22de58c3b8ffd3d5021f23bf3be2b))  



## [0.0.342](https://www.github.com/spinnaker/deck/compare/497489e47917056c5e2f171ce784324124475a0e...7b7b4465a446030f350ce4ccda32ad4833a0085d) (2019-03-03)


### Changes

chore(core): Bump version to 0.0.342 [#6636](https://github.com/spinnaker/deck/pull/6636) ([7b7b4465](https://github.com/spinnaker/deck/commit/7b7b4465a446030f350ce4ccda32ad4833a0085d))  
feature(core): allow custom tooltip, modal body on Cancel Execution [#6635](https://github.com/spinnaker/deck/pull/6635) ([6f4eb89b](https://github.com/spinnaker/deck/commit/6f4eb89b7653ce645f4eaefe97122fe879faa475))  
fix(chaos): do not let user save invalid chaos monkey config [#6629](https://github.com/spinnaker/deck/pull/6629) ([042943e5](https://github.com/spinnaker/deck/commit/042943e5b18a695a691dd7925f621a4b20dfa0c8))  
feat(core): Export Pipeline Template action with modal and command copy [#6595](https://github.com/spinnaker/deck/pull/6595) ([9ba734bc](https://github.com/spinnaker/deck/commit/9ba734bc7bf745c8e874f83622b7da26f51187ab))  



## [0.0.341](https://www.github.com/spinnaker/deck/compare/a9877861b97a784631d2ecbd0c84d57d207cfd76...497489e47917056c5e2f171ce784324124475a0e) (2019-02-27)


### Changes

Bump package core to 0.0.341 and amazon to 0.0.178 [#6624](https://github.com/spinnaker/deck/pull/6624) ([497489e4](https://github.com/spinnaker/deck/commit/497489e47917056c5e2f171ce784324124475a0e))  
fix(artifacts): lookup of artifact id with incorrect string [#6622](https://github.com/spinnaker/deck/pull/6622) ([69a2d56c](https://github.com/spinnaker/deck/commit/69a2d56c5d026679530b3cb59d51ae0ca5edd606))  
fix(projects): config state was stale after update [#6464](https://github.com/spinnaker/deck/pull/6464) ([10153213](https://github.com/spinnaker/deck/commit/10153213b2bdabbf5fb166863c6bb28eb5b398a6))  
chore(angularjs): Move $inject annotation above hoisted functions [#6621](https://github.com/spinnaker/deck/pull/6621) ([c0f6b24e](https://github.com/spinnaker/deck/commit/c0f6b24ec35e5e9eb438791a2eaf0b540af2830e))  
fix(gremlin): Check for fetched data from API [#6553](https://github.com/spinnaker/deck/pull/6553) ([a6a6b739](https://github.com/spinnaker/deck/commit/a6a6b7394a4d09ee646a452a804d23bdf7134616))  
chore(angularjs): Explicitly annotate directive controllers ([d828a53e](https://github.com/spinnaker/deck/commit/d828a53e55919c16a8cea82af404c05bad081066))  



## [0.0.340](https://www.github.com/spinnaker/deck/compare/28301107f3de0a574b7030bfd3cf442c7061b2e2...a9877861b97a784631d2ecbd0c84d57d207cfd76) (2019-02-25)


### Changes

Bump package core to 0.0.340 and titus to 0.0.78 [#6620](https://github.com/spinnaker/deck/pull/6620) ([a9877861](https://github.com/spinnaker/deck/commit/a9877861b97a784631d2ecbd0c84d57d207cfd76))  
chore(core): yank out fastProperties formatters [#6619](https://github.com/spinnaker/deck/pull/6619) ([4f15b541](https://github.com/spinnaker/deck/commit/4f15b5410c77a28be09e6587e752558456d9b3c8))  



## [0.0.339](https://www.github.com/spinnaker/deck/compare/814236a3b5dee648e46e0dcefeea665edbabbca4...28301107f3de0a574b7030bfd3cf442c7061b2e2) (2019-02-25)


### Changes

Bump package core to 0.0.339 and amazon to 0.0.177 and titus to 0.0.77 [#6615](https://github.com/spinnaker/deck/pull/6615) ([28301107](https://github.com/spinnaker/deck/commit/28301107f3de0a574b7030bfd3cf442c7061b2e2))  
refactor(core): migrate momentjs functionality to luxon + date-fns [#6604](https://github.com/spinnaker/deck/pull/6604) ([3e758150](https://github.com/spinnaker/deck/commit/3e758150672fb49e7f66e520f0b6bae87591bbc5))  
Merge branch 'master' into lb-is-loading ([754c5407](https://github.com/spinnaker/deck/commit/754c54073a5d0f1c4a5dc6f87fb9406a858cee0e))  
fix(core/executions): Disable text selection when re-ordering pipelines [#6603](https://github.com/spinnaker/deck/pull/6603) ([70fb404d](https://github.com/spinnaker/deck/commit/70fb404d23cd9bbf2c58ebe75f26be585367e444))  
Merge branch 'master' into lb-is-loading ([a35b4cbb](https://github.com/spinnaker/deck/commit/a35b4cbb619cebe02121ffa19ee981e78cd9fc21))  
Merge branch 'master' into lb-is-loading ([1d320d65](https://github.com/spinnaker/deck/commit/1d320d659f524aa4772fba1b1e42edde819c1d36))  
Merge branch 'master' into lb-is-loading ([c026514e](https://github.com/spinnaker/deck/commit/c026514e4b42c6027dd2dd97e31f37918fa57521))  
fix(core): loading spinner for LBs not dismissed ([cf528dc0](https://github.com/spinnaker/deck/commit/cf528dc08afe64f68b5201c5706d229785f44497))  



## [0.0.338](https://www.github.com/spinnaker/deck/compare/2b2ccff259cedc559b6aad5e0cc16f072a551a8a...814236a3b5dee648e46e0dcefeea665edbabbca4) (2019-02-21)


### Changes

chore(core): Bump version to 0.0.338 ([814236a3](https://github.com/spinnaker/deck/commit/814236a3b5dee648e46e0dcefeea665edbabbca4))  
chore(prettier): Just Use Prettier™ [#6600](https://github.com/spinnaker/deck/pull/6600) ([7d5fc346](https://github.com/spinnaker/deck/commit/7d5fc346bca54c5d53f9eb46d823cd993c102058))  
fix(html): Fix various invalid HTML [#6597](https://github.com/spinnaker/deck/pull/6597) ([64fb4892](https://github.com/spinnaker/deck/commit/64fb4892ee3e7114eccb8f6acc9d84ae652f1af3))  
fix(core/diffs): Fix misnamed tempate field ([39f68587](https://github.com/spinnaker/deck/commit/39f6858797af81265a6de1aa0735c33b9c35bd30))  
chore(prettier): Just Use Prettier™ ([5cf6c79d](https://github.com/spinnaker/deck/commit/5cf6c79da63404bb7238291d38bb7f5cfd10c26b))  
chore(angularjs): Do not use .component('foo', new Foo()) ([3ffa4fb7](https://github.com/spinnaker/deck/commit/3ffa4fb7498df815014d61071e8588f0b34bf8b9))  
feat(gremlin): Per feedback review within gate, change the Gate gremlin endpoint prefix from "gremlin" to "integrations/gremlin" [#6591](https://github.com/spinnaker/deck/pull/6591) ([14bf52b2](https://github.com/spinnaker/deck/commit/14bf52b2da88555cbab3bca7ae33062cf32d7625))  



## [0.0.337](https://www.github.com/spinnaker/deck/compare/273e1db3b9ac21a57a4b018edda97369b59abaa2...2b2ccff259cedc559b6aad5e0cc16f072a551a8a) (2019-02-21)


### Changes

Bump package core to 0.0.337 and docker to 0.0.34 and amazon to 0.0.175 and titus to 0.0.75 [#6593](https://github.com/spinnaker/deck/pull/6593) ([2b2ccff2](https://github.com/spinnaker/deck/commit/2b2ccff259cedc559b6aad5e0cc16f072a551a8a))  
fix(core): Child pipeline should route back correctly [#6586](https://github.com/spinnaker/deck/pull/6586) ([d8538271](https://github.com/spinnaker/deck/commit/d8538271bd69a429902e5e2aa33cfa01ca435cb0))  
fix(core): allow selecting accounts via simple select field in AccountSelectInput [#6592](https://github.com/spinnaker/deck/pull/6592) ([bc9a30bb](https://github.com/spinnaker/deck/commit/bc9a30bbe31b4473a758bda96460ceab0416d1e5))  
refactor(core): move Ace Editor CSS to core module [#6588](https://github.com/spinnaker/deck/pull/6588) ([4b91b36c](https://github.com/spinnaker/deck/commit/4b91b36c6dc52af529efd89461ce0a44d6432ef5))  
chore(angularjs): Remove all 'ngInject'; in favor of explicit DI annotation ([cc52bee0](https://github.com/spinnaker/deck/commit/cc52bee0b9956693f948806322658f225efa5546))  
chore(prettier): Just Use Prettier™ ([b6bab1e1](https://github.com/spinnaker/deck/commit/b6bab1e16bb46697fec347cd30934f00fb2e9807))  
chore(angularjs): Explicitly annotate all AngularJS injection points ([f3fd790e](https://github.com/spinnaker/deck/commit/f3fd790e20a4c3056edcb2c41282517e1cf35004))  
fix(securityGroups): User `securityGroupName` for upsertSecurityGroupTask [#6569](https://github.com/spinnaker/deck/pull/6569) ([78d0b689](https://github.com/spinnaker/deck/commit/78d0b689efd4de00ab203d55df632dd5ece89133))  



## [0.0.336](https://www.github.com/spinnaker/deck/compare/bb8e5fbe277c509254e043a085535b44abb17832...273e1db3b9ac21a57a4b018edda97369b59abaa2) (2019-02-19)


### Changes

Bump package core to 0.0.336 and amazon to 0.0.174 [#6581](https://github.com/spinnaker/deck/pull/6581) ([273e1db3](https://github.com/spinnaker/deck/commit/273e1db3b9ac21a57a4b018edda97369b59abaa2))  
fix(core/clipboard): correctly type CopyToClipboard's displayText prop [#6580](https://github.com/spinnaker/deck/pull/6580) ([4d816b54](https://github.com/spinnaker/deck/commit/4d816b54a0dade16becd5c0fa8aa0a4abd9782ee))  



## [0.0.335](https://www.github.com/spinnaker/deck/compare/d5eb706cb458677b39813b64d868fc860c28f72c...bb8e5fbe277c509254e043a085535b44abb17832) (2019-02-18)


### Changes

chore(core): Bump version to 0.0.335 [#6575](https://github.com/spinnaker/deck/pull/6575) ([bb8e5fbe](https://github.com/spinnaker/deck/commit/bb8e5fbe277c509254e043a085535b44abb17832))  
refactor(core): move stages/core to stages/common [#6574](https://github.com/spinnaker/deck/pull/6574) ([8430dfcc](https://github.com/spinnaker/deck/commit/8430dfccc3b3e792da2f33bc5419c5f6116f14d1))  



## [0.0.334](https://www.github.com/spinnaker/deck/compare/7956ef163f87edd4e5d80d597d5a90c0e25a8aa1...d5eb706cb458677b39813b64d868fc860c28f72c) (2019-02-18)


### Changes

chore(core): Bump version to 0.0.334 [#6573](https://github.com/spinnaker/deck/pull/6573) ([d5eb706c](https://github.com/spinnaker/deck/commit/d5eb706cb458677b39813b64d868fc860c28f72c))  



## [0.0.333](https://www.github.com/spinnaker/deck/compare/b848693156862a79314a4ab8048c6aeeaca514f2...7956ef163f87edd4e5d80d597d5a90c0e25a8aa1) (2019-02-18)


### Changes

Bump package core to 0.0.333 and amazon to 0.0.173 [#6572](https://github.com/spinnaker/deck/pull/6572) ([7956ef16](https://github.com/spinnaker/deck/commit/7956ef163f87edd4e5d80d597d5a90c0e25a8aa1))  
fix(core): do not blow away the screen when copying to clipboard [#6571](https://github.com/spinnaker/deck/pull/6571) ([a3c08ee5](https://github.com/spinnaker/deck/commit/a3c08ee598345a143c7af4cce54ecde146878769))  
fix(core/amazon): Loadbalancer tags should have spinner to avoid panic [#6562](https://github.com/spinnaker/deck/pull/6562) ([3664ce2a](https://github.com/spinnaker/deck/commit/3664ce2a4b571fcda570d454f1c124191f98aaec))  
fix(core): do not try to parse/unescape execution status parameters [#6570](https://github.com/spinnaker/deck/pull/6570) ([d11da07a](https://github.com/spinnaker/deck/commit/d11da07addfe99eb07f7ddaf88019dc8a3abc34e))  
chore(eslint): disable eslint namespace rule for Gremlin component ([8ee56878](https://github.com/spinnaker/deck/commit/8ee56878f1acbb6752dc75f5812e4d8fab5fd9f6))  



## [0.0.332](https://www.github.com/spinnaker/deck/compare/3470a3a339cd5299835ab774e26e0e5f753ee7bb...b848693156862a79314a4ab8048c6aeeaca514f2) (2019-02-18)


### Changes

Bump package core to 0.0.332 and amazon to 0.0.172 [#6565](https://github.com/spinnaker/deck/pull/6565) ([b8486931](https://github.com/spinnaker/deck/commit/b848693156862a79314a4ab8048c6aeeaca514f2))  
fix(aws): fix security group rule updates [#6564](https://github.com/spinnaker/deck/pull/6564) ([b18a815f](https://github.com/spinnaker/deck/commit/b18a815fbc215290070e07a1152d4d916d325d6c))  
fix(core): use $timeout to handle change event in accountSelectField [#6563](https://github.com/spinnaker/deck/pull/6563) ([1b424135](https://github.com/spinnaker/deck/commit/1b424135d2d3515e052f011110223832601e6b1b))  
fix(core/css): be explicit on which file we're importing [#6554](https://github.com/spinnaker/deck/pull/6554) ([8199164e](https://github.com/spinnaker/deck/commit/8199164e647979bcd0a036657dc93df211103ffd))  
feat(core): display unescaped JSON if pipeline parameter input was JSON [#6556](https://github.com/spinnaker/deck/pull/6556) ([c4287c65](https://github.com/spinnaker/deck/commit/c4287c657b0e037ccca71d8139c3e3c08ab8a705))  
fix(core): make one request per pipeline/strategy re-indexing event [#6519](https://github.com/spinnaker/deck/pull/6519) ([6fd4bf3e](https://github.com/spinnaker/deck/commit/6fd4bf3ea3b5d2805cf8da6143f68c6057116b53))  
fix(eslint): Fix eslint warnings for @typescript-eslint/camelcase ([592073fa](https://github.com/spinnaker/deck/commit/592073fa8724525eaf50419c1ae28f4fd3fb0dd3))  
fix(eslint): Fix eslint warnings for @typescript-eslint/no-empty ([c17c742a](https://github.com/spinnaker/deck/commit/c17c742a15a47b49f94b7fb24fcff717bcc0a088))  
fix(eslint): Fix eslint warnings for @typescript-eslint/camelcase ([d72bc173](https://github.com/spinnaker/deck/commit/d72bc1733596ff96d1685e2245aa23145d007f63))  
fix(eslint): Fix eslint warnings for @typescript-eslint/array-type ([9818dec7](https://github.com/spinnaker/deck/commit/9818dec74f94693ae4b88820bf2f72424ffb7a14))  
fix(eslint): Fix eslint warnings for @typescript-eslint/no-namespace ([93d83d68](https://github.com/spinnaker/deck/commit/93d83d680d9f5432d4735b89a91dcce1829ab6af))  
fix(eslint): Fix eslint warnings for @typescript-eslint/no-use-before-define ([e1b6663c](https://github.com/spinnaker/deck/commit/e1b6663c1960ebc7b2a9754713d6c0e502817cbc))  
fix(eslint): Fix eslint warnings for @typescript-eslint/ban-types ([aa4e8df6](https://github.com/spinnaker/deck/commit/aa4e8df647185b5aa1338c4da2871dbe74fbab40))  
fix(eslint): Fix eslint warnings for no-extra-boolean-cast ([cccca97a](https://github.com/spinnaker/deck/commit/cccca97a1e79b03e1c8933b7222c6a2524b640d8))  
fix(eslint): Fix eslint warnings for no-console ([3b8fcd95](https://github.com/spinnaker/deck/commit/3b8fcd95055493c84210a23565a700472599d533))  
fix(eslint): Fix eslint warnings for no-useless-escape ([ddbe2082](https://github.com/spinnaker/deck/commit/ddbe208282f96c73239a32bddd4af6f9d098b088))  
fix(eslint): Fix eslint warnings for no-case-declarations ([8cba7ac4](https://github.com/spinnaker/deck/commit/8cba7ac43c171b39b92b5a68dfec3d0d229aae48))  



## [0.0.331](https://www.github.com/spinnaker/deck/compare/66cf2556007c999861df6bb4451a3ba1ccea4a6d...3470a3a339cd5299835ab774e26e0e5f753ee7bb) (2019-02-14)


### Changes

Bump package core to 0.0.331 and amazon to 0.0.171 and titus to 0.0.74 [#6551](https://github.com/spinnaker/deck/pull/6551) ([3470a3a3](https://github.com/spinnaker/deck/commit/3470a3a339cd5299835ab774e26e0e5f753ee7bb))  
refactor(core): remove navigation from stage config details [#6528](https://github.com/spinnaker/deck/pull/6528) ([40bfc792](https://github.com/spinnaker/deck/commit/40bfc7922ab54bb43bcf34babc3a251362aef65b))  
feat(gremlin): Gremlin UI which takes API key input then fetches comm… [#6549](https://github.com/spinnaker/deck/pull/6549) ([c23989b5](https://github.com/spinnaker/deck/commit/c23989b5c087ebd5d41256d5f0a1cbd04b2ad0ce))  
fix(aws): allow ingress creation from all accounts [#6543](https://github.com/spinnaker/deck/pull/6543) ([ee7b9364](https://github.com/spinnaker/deck/commit/ee7b9364da35ad1580a813b0e595ad47a4171d5b))  
refactor(core): de-angularize UrlBuilder [#6541](https://github.com/spinnaker/deck/pull/6541) ([eb0374c4](https://github.com/spinnaker/deck/commit/eb0374c47e5b0fef21507462c540bdd4db632201))  
fix(core): Match order of pipeline config nav items to page sections [#6546](https://github.com/spinnaker/deck/pull/6546) ([b2912bff](https://github.com/spinnaker/deck/commit/b2912bffb6ff0608b296993bbaed7b45db0c144a))  



## [0.0.330](https://www.github.com/spinnaker/deck/compare/2ee805d83b2dd7142bd6843e663d07d4b6ea83c1...66cf2556007c999861df6bb4451a3ba1ccea4a6d) (2019-02-12)


### Changes

Bump package core to 0.0.330 and amazon to 0.0.170 [#6537](https://github.com/spinnaker/deck/pull/6537) ([66cf2556](https://github.com/spinnaker/deck/commit/66cf2556007c999861df6bb4451a3ba1ccea4a6d))  
chore(eslint): Fix some linter errors ([d7291cc4](https://github.com/spinnaker/deck/commit/d7291cc4ce28c12241dda4f7b8f9d7318824582b))  
fix(amazon): Display capacity as text if using SPEL [#6535](https://github.com/spinnaker/deck/pull/6535) ([7e4b4571](https://github.com/spinnaker/deck/commit/7e4b45711de31637d1a97aa046c6696885578a52))  
chore(eslint): Fix lint errors ([27d8a12c](https://github.com/spinnaker/deck/commit/27d8a12c6e133ba0a25d874535f3c77da431113e))  
chore(package): Just Update Prettier™ ([a8c17492](https://github.com/spinnaker/deck/commit/a8c174925f64045f70c11b2bfc11fe1fdd558660))  
fix(core): enable new traffic guards by default [#6527](https://github.com/spinnaker/deck/pull/6527) ([58d3e909](https://github.com/spinnaker/deck/commit/58d3e909ecceae471a2cd012440f30ffc454b8fc))  
fix(core): only use <g> for popovers in within SVGs [#6530](https://github.com/spinnaker/deck/pull/6530) ([578a7fd7](https://github.com/spinnaker/deck/commit/578a7fd75d0bb0aaaf5a1ad5d620e161259013d8))  
refactor(validation): First class support for required or optional [#6526](https://github.com/spinnaker/deck/pull/6526) ([70b8e018](https://github.com/spinnaker/deck/commit/70b8e0181419f36aae93a6e36d15b5ed3c9d0b91))  
feat(core): rename feature flag for managed pipeline templates v2 ui [#6525](https://github.com/spinnaker/deck/pull/6525) ([0eb3e290](https://github.com/spinnaker/deck/commit/0eb3e29069c9c825120df2cbba05a22aae8e11c6))  
fix(core): Max remaining ASG should honor value being removed and reflect correctly [#6522](https://github.com/spinnaker/deck/pull/6522) ([3359789e](https://github.com/spinnaker/deck/commit/3359789ef5c0aaedb57d31825c22f7e4ffbefa15))  
feat(core): add feature flag for managed pipeline templates v2 [#6520](https://github.com/spinnaker/deck/pull/6520) ([bc2a4236](https://github.com/spinnaker/deck/commit/bc2a423603c5814b58a1f6474727113c8e1de113))  
refactor(artifacts): Generalize artifact delegate for reuse [#6495](https://github.com/spinnaker/deck/pull/6495) ([9eccc0f1](https://github.com/spinnaker/deck/commit/9eccc0f19d3f5aa5a55354d0c9344bb79a9c77bc))  
fix(amazon/core): Sorting order of regions in bake stage + lint fix [#6518](https://github.com/spinnaker/deck/pull/6518) ([a2ebca0a](https://github.com/spinnaker/deck/commit/a2ebca0a272a393953f066bb43f14deb155adcfc))  
fix(core): introduce state when the modal has been initialized [#6516](https://github.com/spinnaker/deck/pull/6516) ([b1b04f03](https://github.com/spinnaker/deck/commit/b1b04f03e0fca03655dadc64f56c78f8a0738c42))  
chore(core): update banner spec ([ed74abf7](https://github.com/spinnaker/deck/commit/ed74abf77892c53bd873ed9b8d2de7c5c4d5318b))  



## [0.0.329](https://www.github.com/spinnaker/deck/compare/6cd6042fabc17724911ca4797727a6638d8609ad...2ee805d83b2dd7142bd6843e663d07d4b6ea83c1) (2019-02-07)


### Changes

chore(core): Bump version to 0.0.329 [#6513](https://github.com/spinnaker/deck/pull/6513) ([2ee805d8](https://github.com/spinnaker/deck/commit/2ee805d83b2dd7142bd6843e663d07d4b6ea83c1))  
feat(core): add getApplicationAttributes method [#6512](https://github.com/spinnaker/deck/pull/6512) ([f7dc012e](https://github.com/spinnaker/deck/commit/f7dc012e61178d66f43b7ca0eb698d689dacbed9))  



## [0.0.328](https://www.github.com/spinnaker/deck/compare/f552700261a20c5e304dd67e042291219b743669...6cd6042fabc17724911ca4797727a6638d8609ad) (2019-02-07)


### Changes

Bump package core to 0.0.328 and docker to 0.0.33 and amazon to 0.0.167 and titus to 0.0.73 [#6507](https://github.com/spinnaker/deck/pull/6507) ([6cd6042f](https://github.com/spinnaker/deck/commit/6cd6042fabc17724911ca4797727a6638d8609ad))  
chore(webpack): Switch to TerserPlugin.  Split bundles into ~5mb chunks ([a35088ab](https://github.com/spinnaker/deck/commit/a35088ab28cc3b25c9e6731f6fb70bf7d0e14ef0))  
fix(style): Fix all lint errors for colors in forms [#6500](https://github.com/spinnaker/deck/pull/6500) ([fe642b77](https://github.com/spinnaker/deck/commit/fe642b77e3d3acf9b19cc60a2b9f0b0e7ef1114b))  
refactor(amazon): make cluster selection optional [#6502](https://github.com/spinnaker/deck/pull/6502) ([8cf90fcc](https://github.com/spinnaker/deck/commit/8cf90fcc4d5d23a55372b93a5a8bc2db6bd8b838))  



## [0.0.327](https://www.github.com/spinnaker/deck/compare/d8b477bbc48769d0f5a5e3ea4596253d71fe1486...f552700261a20c5e304dd67e042291219b743669) (2019-02-06)


### Changes

chore(core): Bump version to 0.0.327 ([f5527002](https://github.com/spinnaker/deck/commit/f552700261a20c5e304dd67e042291219b743669))  
fix(core): filter unauthorized accounts [#6490](https://github.com/spinnaker/deck/pull/6490) ([1c55e89c](https://github.com/spinnaker/deck/commit/1c55e89c6fedaf5c0142e1ac0208295d041d1359))  
fix(core/modal): Validate initialValues so isValid is real [#6494](https://github.com/spinnaker/deck/pull/6494) ([4acb0df6](https://github.com/spinnaker/deck/commit/4acb0df6905a100f6049d7b45324f8352bea36f1))  



## [0.0.326](https://www.github.com/spinnaker/deck/compare/60f4f345d5b4c3f1fb9ec1ae013f3a6cc48e0297...d8b477bbc48769d0f5a5e3ea4596253d71fe1486) (2019-02-05)


### Changes

chore(core): Bump version to 0.0.326 [#6493](https://github.com/spinnaker/deck/pull/6493) ([d8b477bb](https://github.com/spinnaker/deck/commit/d8b477bbc48769d0f5a5e3ea4596253d71fe1486))  



## [0.0.325](https://www.github.com/spinnaker/deck/compare/a93921f920b6c963306cf93e946045cd925be772...60f4f345d5b4c3f1fb9ec1ae013f3a6cc48e0297) (2019-02-05)


### Changes

Bump package core to 0.0.325 and docker to 0.0.32 and amazon to 0.0.165 and titus to 0.0.71 [#6491](https://github.com/spinnaker/deck/pull/6491) ([60f4f345](https://github.com/spinnaker/deck/commit/60f4f345d5b4c3f1fb9ec1ae013f3a6cc48e0297))  
refactor(core): modularize execution status display on executions [#6489](https://github.com/spinnaker/deck/pull/6489) ([bc9a14ba](https://github.com/spinnaker/deck/commit/bc9a14ba64b0f5812f17d09114ddb22aa3f26633))  
refactor(core/insight): refactor modules to avoid circular dependency ([c1bcada8](https://github.com/spinnaker/deck/commit/c1bcada8b78f889b608a9b5a86a3e6d7ffa9dd36))  
chore(visualizer): Switch from deprecated System.import() to dynamic import() ([0ea42c24](https://github.com/spinnaker/deck/commit/0ea42c2483c6e21b29a17a6ba368c32789d4f99e))  
chore(typescript): Switch module from 'commonjs' to 'esnext' to emit raw dynamic 'import()' ([5c49dd2a](https://github.com/spinnaker/deck/commit/5c49dd2ab3c4226295a7e8041c25dabdbeee6a2c))  
fix(core): read displayTimestampsInUserLocalTime off SETTINGS.feature ([cf861ba1](https://github.com/spinnaker/deck/commit/cf861ba17b06f405e9250d8db4b7b157ca5dcf95))  
feat(core): add application-specific custom banners ([c6a3528e](https://github.com/spinnaker/deck/commit/c6a3528edf48b9f05a4532be7f109ed5e24d5e0a))  



## [0.0.324](https://www.github.com/spinnaker/deck/compare/a74ab2970bd97809d5a5310934aa6fb4b704aaa6...a93921f920b6c963306cf93e946045cd925be772) (2019-02-04)


### Changes

Bump package core to 0.0.324 and amazon to 0.0.163 and titus to 0.0.70 [#6482](https://github.com/spinnaker/deck/pull/6482) ([a93921f9](https://github.com/spinnaker/deck/commit/a93921f920b6c963306cf93e946045cd925be772))  
fix(stages): Fixed stage/details out-of-sync due to state bug [#6480](https://github.com/spinnaker/deck/pull/6480) ([cd32f9d7](https://github.com/spinnaker/deck/commit/cd32f9d70513df5e6286c72be6904a20ca98ec3a))  
feat(artifacts): Human readable expected artifact display names [#6344](https://github.com/spinnaker/deck/pull/6344) ([42644185](https://github.com/spinnaker/deck/commit/426441852c31c3fd56123dd90d2700bc4fee8c5c))  
fix(core/modal): Import IModalComponentProps relatively instead of from 'core' so downstream projects don't need a 'core' alias in tsconfig ([20cf9b76](https://github.com/spinnaker/deck/commit/20cf9b7686511d16b781099a9602b7c21a5e85ce))  
Merge branch 'master' into 3798-route-mapping ([b92c8aae](https://github.com/spinnaker/deck/commit/b92c8aaee203cacbab25312a0b345cfaa5c90a2e))  
feat(cf): Add Map/Unmap SGs and LBs ([04bc98b7](https://github.com/spinnaker/deck/commit/04bc98b74688c102b7ab888b42a80e509b1048b8))  



## [0.0.323](https://www.github.com/spinnaker/deck/compare/ef9f09bef31e4903085693f4905c29f540b698ad...a74ab2970bd97809d5a5310934aa6fb4b704aaa6) (2019-02-02)


### Changes

Bump package core to 0.0.323 and amazon to 0.0.162 and titus to 0.0.69 [#6475](https://github.com/spinnaker/deck/pull/6475) ([a74ab297](https://github.com/spinnaker/deck/commit/a74ab2970bd97809d5a5310934aa6fb4b704aaa6))  
refactor(validation): Create validation directory, split up validation and validators, de-class Validation ([aa028227](https://github.com/spinnaker/deck/commit/aa0282270d4511628c5b34f03532e338a7439f0e))  
fix(executions): Fixed stage does not open when clicked [#6469](https://github.com/spinnaker/deck/pull/6469) ([c5e6d3d6](https://github.com/spinnaker/deck/commit/c5e6d3d69221caeae29bc42086e1aa6aa275247a))  
refactor(core/projects): Migrate to wizardmodal render props ([c5a53285](https://github.com/spinnaker/deck/commit/c5a532850da632909dc5008294cec7ddfbd8251b))  
refactor(core/modal): Rewrite wizard modal to use render props - Move label from wrapped component to WizardPage prop - Expose 'formik' as a separate prop in the render prop callback - Access wrapped component validate() function using 'innerRef' - Remove wizardPage() HOC - Remove hideSections prop ([e1889a2c](https://github.com/spinnaker/deck/commit/e1889a2ce1c4fd4844d499c03878525d9542e49a))  



## [0.0.322](https://www.github.com/spinnaker/deck/compare/a5914333e9ef7da55ae038c9c13c629d16c9de8f...ef9f09bef31e4903085693f4905c29f540b698ad) (2019-01-31)


### Changes

chore(core): Bump version to 0.0.322 [#6465](https://github.com/spinnaker/deck/pull/6465) ([ef9f09be](https://github.com/spinnaker/deck/commit/ef9f09bef31e4903085693f4905c29f540b698ad))  
feat(core): better handle stage removal [#6461](https://github.com/spinnaker/deck/pull/6461) ([0daebded](https://github.com/spinnaker/deck/commit/0daebded57408e0537a56a46ae1c9fbb7a2b4315))  
fix(validation): Small fixes for validation [#6458](https://github.com/spinnaker/deck/pull/6458) ([12460864](https://github.com/spinnaker/deck/commit/1246086496255ea7be933380d5b7a7cd857598e2))  



## [0.0.321](https://www.github.com/spinnaker/deck/compare/d04d29d95ea3ecece3b85f131a02f7ec8dbea537...a5914333e9ef7da55ae038c9c13c629d16c9de8f) (2019-01-31)


### Changes

chore(core): Bump version to 0.0.321 [#6460](https://github.com/spinnaker/deck/pull/6460) ([a5914333](https://github.com/spinnaker/deck/commit/a5914333e9ef7da55ae038c9c13c629d16c9de8f))  
fix(core): do not edit live copy of pipeline configs [#6454](https://github.com/spinnaker/deck/pull/6454) ([4abd7d37](https://github.com/spinnaker/deck/commit/4abd7d3745c77f01b2b04799d563f4de49ac8376))  
fix(core): consider NOT_INITIALIZED as fetched when setting app status ([ba8ac10b](https://github.com/spinnaker/deck/commit/ba8ac10b8e1f06400110a274f7806d6fb925439d))  



## [0.0.320](https://www.github.com/spinnaker/deck/compare/1bd15760e11260a5c05c555251951e696600f0f9...d04d29d95ea3ecece3b85f131a02f7ec8dbea537) (2019-01-30)


### Changes

chore(core): Bump version to 0.0.320 ([d04d29d9](https://github.com/spinnaker/deck/commit/d04d29d95ea3ecece3b85f131a02f7ec8dbea537))  
fix(core/ga): Re-enable google analytics [#6453](https://github.com/spinnaker/deck/pull/6453) ([201d4456](https://github.com/spinnaker/deck/commit/201d4456cb923589c96a58fe68a9a42c3accd718))  
fix(webhooks): Ensure body and status code are shown for webhooks with monitoring [#6452](https://github.com/spinnaker/deck/pull/6452) ([b6166e0f](https://github.com/spinnaker/deck/commit/b6166e0f1de2a171a2381a38902d7770502d85a9))  
feat(kubernetes/v2): Converts CopyToClipboard to React Component [#6451](https://github.com/spinnaker/deck/pull/6451) ([dba26d82](https://github.com/spinnaker/deck/commit/dba26d8225841a9c8512f0aeebc1ba2741fbf96f))  
fix(core/pipeline): Support expressions for pipeline name in the pipeline stage ([95b1f5c3](https://github.com/spinnaker/deck/commit/95b1f5c3d03823f9bf1e86a33994c61e0ef70b83))  
fix(*): Remove all self closing tags in AngularJS templates Reference: https://github.com/angular/angular.js/issues/1953#issuecomment-13135021 ([6f608a0a](https://github.com/spinnaker/deck/commit/6f608a0ab43616eb130c7417e560bc3df780f335))  



## [0.0.319](https://www.github.com/spinnaker/deck/compare/bfc29fe75d3b35363c9390457e7b2e3f2512f796...1bd15760e11260a5c05c555251951e696600f0f9) (2019-01-27)


### Changes

Bump package core to 0.0.319 and docker to 0.0.31 and amazon to 0.0.159 and titus to 0.0.68 [#6436](https://github.com/spinnaker/deck/pull/6436) ([1bd15760](https://github.com/spinnaker/deck/commit/1bd15760e11260a5c05c555251951e696600f0f9))  
fix(core): better word break [#6412](https://github.com/spinnaker/deck/pull/6412) ([eec6f922](https://github.com/spinnaker/deck/commit/eec6f922999b3f53a1b585fda2d83feaa85ff19f))  
feat(core): allow disabling traffic guards [#6380](https://github.com/spinnaker/deck/pull/6380) ([f1711d48](https://github.com/spinnaker/deck/commit/f1711d48c1620185897ec4e491a5e8a303e6e10e))  
fix(core): make react-select CSS resets more specific [#6432](https://github.com/spinnaker/deck/pull/6432) ([de277c0d](https://github.com/spinnaker/deck/commit/de277c0d6ebb0fde6aa1edee7b1cc5d768c989a3))  
chore(package): remove unused (hopefully) spin.js package ([4600bbd9](https://github.com/spinnaker/deck/commit/4600bbd9d1588963c6bdde31a334674ffa90d8bc))  
refactor(*): Don't use ts or js file extension in imports ([e5bf0538](https://github.com/spinnaker/deck/commit/e5bf0538003b291ef3d965cb184cc5895e2040b6))  
refactor(*): Don't use js or ts file extension in require() ([35be1f08](https://github.com/spinnaker/deck/commit/35be1f0872f5958514c920ee97510d36484e33eb))  
refactor(validation): Adding new form validation builders [#6394](https://github.com/spinnaker/deck/pull/6394) ([a87b9389](https://github.com/spinnaker/deck/commit/a87b9389dc7b1dbfca1adff6c697280d6be151c1))  
fix(core/instance): Show instance id not found message in details panel - Export react components from index ([04e90a62](https://github.com/spinnaker/deck/commit/04e90a627a01a2eed9665580eca54f2050db40b6))  



## [0.0.318](https://www.github.com/spinnaker/deck/compare/af31515ba162a28496a0fe9074c315a1a9f337d4...bfc29fe75d3b35363c9390457e7b2e3f2512f796) (2019-01-24)


### Changes

Bump package core to 0.0.318 and amazon to 0.0.158 [#6429](https://github.com/spinnaker/deck/pull/6429) ([bfc29fe7](https://github.com/spinnaker/deck/commit/bfc29fe75d3b35363c9390457e7b2e3f2512f796))  
refactor(aws): move certificate config to second line on elb/alb [#6428](https://github.com/spinnaker/deck/pull/6428) ([ff8f055e](https://github.com/spinnaker/deck/commit/ff8f055eebf50253e30c9e071d055804f56cdf34))  
refactor(core/artifact): Explicitly annotate summarizeExpectedArtifact ([1824eb21](https://github.com/spinnaker/deck/commit/1824eb21e502de70860b547a33e93afd7db3ffbb))  
refactor(core/bootstrap): Move uiSelect decorator to bootstrap file and convert to typescript ([83cfd890](https://github.com/spinnaker/deck/commit/83cfd89014b3f1835a41275f4f0d2e76da37f52c))  
refactor(pageTitleService): Use exact DI name in .run block ([0fca9031](https://github.com/spinnaker/deck/commit/0fca9031f48066d88264bd6c2e5b69fbb7d6e649))  



## [0.0.317](https://www.github.com/spinnaker/deck/compare/a40fd3b41669ca8e96d0914d5b14e0aab4e806c4...af31515ba162a28496a0fe9074c315a1a9f337d4) (2019-01-23)


### Changes

chore(core): Bump version to 0.0.317 [#6424](https://github.com/spinnaker/deck/pull/6424) ([af31515b](https://github.com/spinnaker/deck/commit/af31515ba162a28496a0fe9074c315a1a9f337d4))  
fix(core): fix filtering on no details in clusters view [#6423](https://github.com/spinnaker/deck/pull/6423) ([5be1a825](https://github.com/spinnaker/deck/commit/5be1a825f85d30359596e4e38bbf41bf145f7833))  
Revert "chore(uiSelect): Remove decorators for uiSelectMultiple which we no longer use, AFAICT" ([abe1eea2](https://github.com/spinnaker/deck/commit/abe1eea2164a5dbae04f3755273e35a46bdb185f))  



## [0.0.316](https://www.github.com/spinnaker/deck/compare/35fe4c4a7703511d9d9a921a0c9e335048ec592d...a40fd3b41669ca8e96d0914d5b14e0aab4e806c4) (2019-01-23)


### Changes

chore(core): Bump version to 0.0.316 [#6422](https://github.com/spinnaker/deck/pull/6422) ([a40fd3b4](https://github.com/spinnaker/deck/commit/a40fd3b41669ca8e96d0914d5b14e0aab4e806c4))  
fix(core): Fix stack filter on none [#6421](https://github.com/spinnaker/deck/pull/6421) ([06089c82](https://github.com/spinnaker/deck/commit/06089c82d16dfa3ef18ea57c0a8b630077876b12))  
chore(uiSelect): Remove decorators for uiSelectMultiple which we no longer use, AFAICT ([be2fc98c](https://github.com/spinnaker/deck/commit/be2fc98cd2a217d90ffd4f47c9ddf60fba8c63fd))  
refactor(core/forms): Save 19kb in deck bundle by not using 'util' package ([6926c897](https://github.com/spinnaker/deck/commit/6926c897f72bbb476b5c064e76fa76a0c8b56708))  



## [0.0.315](https://www.github.com/spinnaker/deck/compare/524780fa3d45bd40a491c5fb8f50cf8c4bbc9dfc...35fe4c4a7703511d9d9a921a0c9e335048ec592d) (2019-01-23)


### Changes

chore(core): Bump version to 0.0.315 [#6413](https://github.com/spinnaker/deck/pull/6413) ([35fe4c4a](https://github.com/spinnaker/deck/commit/35fe4c4a7703511d9d9a921a0c9e335048ec592d))  
fix(webhooks): Various webhook stage improvements [#6407](https://github.com/spinnaker/deck/pull/6407) ([e276fd5d](https://github.com/spinnaker/deck/commit/e276fd5dbded891d08279e7e391e547280b34094))  



## [0.0.314](https://www.github.com/spinnaker/deck/compare/4449cbfddf7880d55f76774135a9cb129ce055eb...524780fa3d45bd40a491c5fb8f50cf8c4bbc9dfc) (2019-01-23)


### Changes

Bump package core to 0.0.314 and amazon to 0.0.157 [#6411](https://github.com/spinnaker/deck/pull/6411) ([524780fa](https://github.com/spinnaker/deck/commit/524780fa3d45bd40a491c5fb8f50cf8c4bbc9dfc))  
fix(core): correctly sort instances by launch time [#6409](https://github.com/spinnaker/deck/pull/6409) ([4a1506fa](https://github.com/spinnaker/deck/commit/4a1506fa063d20186e9ee9bf86b554bd167b85bf))  
(feat/webhook) Make urls of the webhook stage clickable [#6403](https://github.com/spinnaker/deck/pull/6403) ([58266343](https://github.com/spinnaker/deck/commit/582663430d705dd7d1c827ef8ef43f98d29c38e2))  



## [0.0.313](https://www.github.com/spinnaker/deck/compare/86caff77cbb6e916097e03fc3b3e635a97a42ec3...4449cbfddf7880d55f76774135a9cb129ce055eb) (2019-01-17)


### Changes

Bump package core to 0.0.313 and amazon to 0.0.154 [#6396](https://github.com/spinnaker/deck/pull/6396) ([4449cbfd](https://github.com/spinnaker/deck/commit/4449cbfddf7880d55f76774135a9cb129ce055eb))  
fix(amazon): better style of react-select for certificates [#6391](https://github.com/spinnaker/deck/pull/6391) ([433e5547](https://github.com/spinnaker/deck/commit/433e5547f0754f40e90faca9b2bea270ecce0de6))  
fix(core): clear cluster height cache when resizing window [#6393](https://github.com/spinnaker/deck/pull/6393) ([0675b7a8](https://github.com/spinnaker/deck/commit/0675b7a8235bf6b924c3a7de63c097a9a275fe75))  



## [0.0.312](https://www.github.com/spinnaker/deck/compare/921360628eb88ebc7e6313ff9afd4015b54b4c32...86caff77cbb6e916097e03fc3b3e635a97a42ec3) (2019-01-16)


### Changes

chore(core): Bump version to 0.0.312 [#6385](https://github.com/spinnaker/deck/pull/6385) ([86caff77](https://github.com/spinnaker/deck/commit/86caff77cbb6e916097e03fc3b3e635a97a42ec3))  
feat(core): include original capacity in server group resize title [#6352](https://github.com/spinnaker/deck/pull/6352) ([2d8d20e3](https://github.com/spinnaker/deck/commit/2d8d20e314291b18c9aa2bf3ea282ac399cf7263))  
fix(core): do not overwrite strategy app in cluster config [#6384](https://github.com/spinnaker/deck/pull/6384) ([94fa5cb4](https://github.com/spinnaker/deck/commit/94fa5cb482669f4cf9445ed733d37d204b8c1cc1))  
fix(aws): filter out subnets with no purpose [#6383](https://github.com/spinnaker/deck/pull/6383) ([795f1081](https://github.com/spinnaker/deck/commit/795f1081ee241f1912232fe5217d4fa16b9effa5))  
fix(stage): Fixing addAliasToConfig [#6381](https://github.com/spinnaker/deck/pull/6381) ([d6548d5f](https://github.com/spinnaker/deck/commit/d6548d5f6e88ba80595faf85ef076e1d24d45e2d))  
feat(core): Show timestamp in user's (browser's) timezone [#6362](https://github.com/spinnaker/deck/pull/6362) ([286d4ba4](https://github.com/spinnaker/deck/commit/286d4ba44ed9fd1afcae447995ba5194e0c62b6a))  



## [0.0.311](https://www.github.com/spinnaker/deck/compare/752da1bdf8ae7a64e82e7be8f05411d0208774e7...921360628eb88ebc7e6313ff9afd4015b54b4c32) (2019-01-15)


### Changes

chore(core): Bump version to 0.0.311 ([92136062](https://github.com/spinnaker/deck/commit/921360628eb88ebc7e6313ff9afd4015b54b4c32))  
fix(core): include non-run strategy headers [#6367](https://github.com/spinnaker/deck/pull/6367) ([95744dba](https://github.com/spinnaker/deck/commit/95744dba57779095fef0704f76a519154cce2326))  
fix(core/deploymentStrategy): Fix rolling red/black NPE [#6373](https://github.com/spinnaker/deck/pull/6373) ([a89de173](https://github.com/spinnaker/deck/commit/a89de17356870eb4a9bd83df6ef25bbd1d2819e2))  
fix(pubsub): Change from topic to publisherName [#6371](https://github.com/spinnaker/deck/pull/6371) ([64adfeb9](https://github.com/spinnaker/deck/commit/64adfeb997432ffc502df05d5f03627cb3edf58f))  
fix(validation): Validator should pass checkParentTriggers through [#6360](https://github.com/spinnaker/deck/pull/6360) ([6fe5e67b](https://github.com/spinnaker/deck/commit/6fe5e67b376fb1ea91d01e2a2cabdf140729838d))  
fix(core): get region for deployed link from multiple sources [#6350](https://github.com/spinnaker/deck/pull/6350) ([5be89c6f](https://github.com/spinnaker/deck/commit/5be89c6ffbf20f9e02c9a15596d854a208cf6a7c))  
refactor(artifacts): Deprecate kind field [#6359](https://github.com/spinnaker/deck/pull/6359) ([ac3e4058](https://github.com/spinnaker/deck/commit/ac3e4058d84702f446cf24995cc20644e4a2fcb4))  
fix(core/presentation): make detail dropdowns not visually crop ([5c196ce8](https://github.com/spinnaker/deck/commit/5c196ce825379dc9c0346bde0216c290a0c354fe))  
 refactor(artifacts): Add customKind flag to artifact config [#6357](https://github.com/spinnaker/deck/pull/6357) ([911ea078](https://github.com/spinnaker/deck/commit/911ea078eae8ca682aec0b3e3dd5eb747d7670b3))  
 refactor(artifacts): Remove explicit references to kind from artifacts [#6351](https://github.com/spinnaker/deck/pull/6351) ([2ac29eaa](https://github.com/spinnaker/deck/commit/2ac29eaafda0cbeca802544b935ac2c585adc790))  
fix(core): clear stage-specific fields when changing stage types [#6355](https://github.com/spinnaker/deck/pull/6355) ([1bf0c169](https://github.com/spinnaker/deck/commit/1bf0c1691a0711ac6d204af0228bc50f4152bbc1))  
feat(core): allow min/max on NumberSpelInput, zero wait on Wait Stage [#6353](https://github.com/spinnaker/deck/pull/6353) ([3ff80fd6](https://github.com/spinnaker/deck/commit/3ff80fd6611778a55c0c007fc6e2d12027bfe6de))  
fix(core): fix instance details sorting [#6349](https://github.com/spinnaker/deck/pull/6349) ([24d2d9e3](https://github.com/spinnaker/deck/commit/24d2d9e3f761d39bff3ba2b49630d0900b0a6b50))  



## [0.0.310](https://www.github.com/spinnaker/deck/compare/478936eb234c71dbd94a1fa69a148945b69b283e...752da1bdf8ae7a64e82e7be8f05411d0208774e7) (2019-01-10)


### Changes

Bump package [#6346](https://github.com/spinnaker/deck/pull/6346) ([752da1bd](https://github.com/spinnaker/deck/commit/752da1bdf8ae7a64e82e7be8f05411d0208774e7))  
fix(core/deployment): Fix rolling red back rollback checkbox state [#6345](https://github.com/spinnaker/deck/pull/6345) ([fe944f40](https://github.com/spinnaker/deck/commit/fe944f406c4187f2b099e2eeaf0ca301de445411))  



## [0.0.309](https://www.github.com/spinnaker/deck/compare/4eef691645f8683db2f5665615a5129d4bd142ec...478936eb234c71dbd94a1fa69a148945b69b283e) (2019-01-10)


### Changes

Bump package core to 0.0.309 and docker to 0.0.29 and amazon to 0.0.150 and titus to 0.0.66 [#6343](https://github.com/spinnaker/deck/pull/6343) ([478936eb](https://github.com/spinnaker/deck/commit/478936eb234c71dbd94a1fa69a148945b69b283e))  
 fix(docker): Do not clear an existing imageId even if fields cannot be found in registry [#6342](https://github.com/spinnaker/deck/pull/6342) ([aefc576d](https://github.com/spinnaker/deck/commit/aefc576d60cf062fab77ea1b3cdc6a704d27e13f))  
fix(*): allow modal to stay open on auto-close [#6329](https://github.com/spinnaker/deck/pull/6329) ([e802c451](https://github.com/spinnaker/deck/commit/e802c4515726e74f0f2157bde292fc43a6b46271))  
fix(core): do not flag pipelines dirty on initial save [#6340](https://github.com/spinnaker/deck/pull/6340) ([33e2e5b8](https://github.com/spinnaker/deck/commit/33e2e5b8dee6d74011c4a6a5a478850e2878bac8))  
feat(core): allow searching in pipeline JSON [#6341](https://github.com/spinnaker/deck/pull/6341) ([94b1e9fd](https://github.com/spinnaker/deck/commit/94b1e9fdb83bc5bc38eaa63dbb7d2d6354c60232))  



## [0.0.308](https://www.github.com/spinnaker/deck/compare/5d1cc9d26e450362ae51f365d6a33a2ac5b83d77...4eef691645f8683db2f5665615a5129d4bd142ec) (2019-01-09)


### Changes

Bump package core to 0.0.308 and amazon to 0.0.149 [#6336](https://github.com/spinnaker/deck/pull/6336) ([4eef6916](https://github.com/spinnaker/deck/commit/4eef691645f8683db2f5665615a5129d4bd142ec))  
refactor(stages): Deriving stages that provide version info for bakes [#6328](https://github.com/spinnaker/deck/pull/6328) ([6336232c](https://github.com/spinnaker/deck/commit/6336232cdc5fdc243233d9edaf88b5065e23fa4a))  



## [0.0.307](https://www.github.com/spinnaker/deck/compare/7ea825d6641af7dd63dd9cab6a1e1a7591ac3614...5d1cc9d26e450362ae51f365d6a33a2ac5b83d77) (2019-01-09)


### Changes

chore(core): Bump version to 0.0.307 [#6335](https://github.com/spinnaker/deck/pull/6335) ([5d1cc9d2](https://github.com/spinnaker/deck/commit/5d1cc9d26e450362ae51f365d6a33a2ac5b83d77))  
feat(core): add InstanceWriter to CoreReactInject ([a69f8177](https://github.com/spinnaker/deck/commit/a69f817793d0da82a0440d781fb7eaf86d6a0434))  
feat(artifacts): Add HTTP to artifact icon list and service [#6333](https://github.com/spinnaker/deck/pull/6333) ([a5a2c78a](https://github.com/spinnaker/deck/commit/a5a2c78a154ac60bb6c5f0d6325a79c2ade556a3))  
feat(core/serverGroup): Add digests to docker insight server group link [#6327](https://github.com/spinnaker/deck/pull/6327) ([0c7d2f0d](https://github.com/spinnaker/deck/commit/0c7d2f0d15f06d9fb4b7d22190a5f6121f41f100))  



## [0.0.306](https://www.github.com/spinnaker/deck/compare/672e4d3f4f21de6ea13354a030dd9ba71a18900a...7ea825d6641af7dd63dd9cab6a1e1a7591ac3614) (2019-01-08)


### Changes

Bump package core to 0.0.306 and docker to 0.0.28 [#6332](https://github.com/spinnaker/deck/pull/6332) ([7ea825d6](https://github.com/spinnaker/deck/commit/7ea825d6641af7dd63dd9cab6a1e1a7591ac3614))  
fix(core): navigate to failed stage with message if possible on details toggle [#6331](https://github.com/spinnaker/deck/pull/6331) ([0dce29e2](https://github.com/spinnaker/deck/commit/0dce29e2d9100eabc8ffdcf8a779562a791f3d9a))  
fix(core): do not overhydrate executions [#6330](https://github.com/spinnaker/deck/pull/6330) ([67b80795](https://github.com/spinnaker/deck/commit/67b80795a3aad72106df1c3df12574d869672724))  
fix(core): allow deep linking to filtered tasks view [#6319](https://github.com/spinnaker/deck/pull/6319) ([44e949f8](https://github.com/spinnaker/deck/commit/44e949f8e744a95a5d4ef583d9573628d502e0cc))  
fix(pipelines): hit target for labels is off by 8px [#6324](https://github.com/spinnaker/deck/pull/6324) ([13caedf2](https://github.com/spinnaker/deck/commit/13caedf200207009efccdecd4238d28acaf1a121))  



## [0.0.305](https://www.github.com/spinnaker/deck/compare/ac922b55c6e4ee177c3a5388d09d30b7c5f10135...672e4d3f4f21de6ea13354a030dd9ba71a18900a) (2019-01-07)


### Changes

Bump package core to 0.0.305 and amazon to 0.0.148 and titus to 0.0.65 [#6315](https://github.com/spinnaker/deck/pull/6315) ([672e4d3f](https://github.com/spinnaker/deck/commit/672e4d3f4f21de6ea13354a030dd9ba71a18900a))  
fix(core): disable manual executions while trigger data loads [#6301](https://github.com/spinnaker/deck/pull/6301) ([80091f10](https://github.com/spinnaker/deck/commit/80091f10fd38d6ae40c4b024fcb67ffe2b0f71cc))  
feat(core): use Ace Editor for pipeline/stage JSON editing [#6226](https://github.com/spinnaker/deck/pull/6226) ([308a9911](https://github.com/spinnaker/deck/commit/308a9911678a58d167e16df323a96af0e6e5786b))  
feat(core): allow setting params via query string on manual execution [#6302](https://github.com/spinnaker/deck/pull/6302) ([f455307c](https://github.com/spinnaker/deck/commit/f455307c082c0736e4cfe096f67fee7a0282e413))  
feat(artifacts): Changed helm artifact API requests [#6202](https://github.com/spinnaker/deck/pull/6202) ([c48bac25](https://github.com/spinnaker/deck/commit/c48bac25ca4f0071eb9b361adf69b097a923e908))  
fix(core/account): Return empty array if no preferred zones are found in a region ([616e46e7](https://github.com/spinnaker/deck/commit/616e46e7e3a5b1163e2779b75bf3d842968f15ed))  
refactor(core/help): Switch HelpField to PureComponent ([e0525267](https://github.com/spinnaker/deck/commit/e0525267f5fd8a4ef83e87d9413ff936ff1be71a))  
feat(artifacts): add ivy and maven expected artifact editors [#6241](https://github.com/spinnaker/deck/pull/6241) ([2c3e5dfa](https://github.com/spinnaker/deck/commit/2c3e5dfa5de94729ac9e959166360617dcac502a))  
refactor(*): use mask-image CSS for cloud provider logos [#6280](https://github.com/spinnaker/deck/pull/6280) ([86baac96](https://github.com/spinnaker/deck/commit/86baac96af19a15b1339cd5f1856ee1e78d9d800))  
fix(core): fix next CRON trigger calculation offset [#6242](https://github.com/spinnaker/deck/pull/6242) ([dac9b213](https://github.com/spinnaker/deck/commit/dac9b213c288609ef3c76e68d906b87cd367c7e4))  
fix(core): avoid in sync race condition when saving pipelines [#6235](https://github.com/spinnaker/deck/pull/6235) ([6b7c1ec0](https://github.com/spinnaker/deck/commit/6b7c1ec0b39359a290511d65421a1ddbb04a9400))  
feat(core): show pipeline stage durations by default [#6215](https://github.com/spinnaker/deck/pull/6215) ([d29b359f](https://github.com/spinnaker/deck/commit/d29b359f6a12c32900181a1ef273025b89ba434b))  
feat(stage): manual judgement continue button moved to right [#6292](https://github.com/spinnaker/deck/pull/6292) ([70be323e](https://github.com/spinnaker/deck/commit/70be323ef3bc154bf165f04697a3d0158af4d8d4))  
styles(core): Making the main ui-view container positive relative [#6264](https://github.com/spinnaker/deck/pull/6264) ([641a2837](https://github.com/spinnaker/deck/commit/641a28377897459e2488041ed1d2c2bc1aab4998))  
feat(core/deploy): Support `scaleDown` as part of a rolling red/black deployment [#6265](https://github.com/spinnaker/deck/pull/6265) ([425ca354](https://github.com/spinnaker/deck/commit/425ca354d606a5dcb13ca5d294fcccf71065c7c2))  



## [0.0.304](https://www.github.com/spinnaker/deck/compare/1a0eada2dee143097ec4bb256af88c1b316f00e8...ac922b55c6e4ee177c3a5388d09d30b7c5f10135) (2018-12-21)


### Changes

chore(core): Bump version to 0.0.304 ([ac922b55](https://github.com/spinnaker/deck/commit/ac922b55c6e4ee177c3a5388d09d30b7c5f10135))  
fix(bake): Allow null extended attributes in bake stages [#6256](https://github.com/spinnaker/deck/pull/6256) ([3afbd000](https://github.com/spinnaker/deck/commit/3afbd0001ad934ef9febe87579cc3fe81bd5f3f8))  
fix(core): Prevent cluster saved state filter overrides [#6252](https://github.com/spinnaker/deck/pull/6252) ([14b8cc41](https://github.com/spinnaker/deck/commit/14b8cc41943654e3205552dc82c4495a44b1c8bc))  
fix(pubsub): Make application-level notifications show up (and with details!) [#6254](https://github.com/spinnaker/deck/pull/6254) ([88cb6c63](https://github.com/spinnaker/deck/commit/88cb6c63175e2e37512d55682bb348963b184eaf))  
feat(core): add label filter UI to clusters view ([1ab4c92e](https://github.com/spinnaker/deck/commit/1ab4c92edc50298fe16011c40be364712337006c))  
fix(judgement): Adding stage refId as component key to bust residual state [#6243](https://github.com/spinnaker/deck/pull/6243) ([42a50e09](https://github.com/spinnaker/deck/commit/42a50e099e4e5cc6c7090d3a21cea5f3305217cb))  
feat(notifications): Adds pubsub notification module. Alphabetize the notification list. [#6234](https://github.com/spinnaker/deck/pull/6234) ([62840e9f](https://github.com/spinnaker/deck/commit/62840e9f6efe9352ba3d2666fdaa401dca04f015))  
refactor(triggers): move expected artifact definition before trigger definition [#6233](https://github.com/spinnaker/deck/pull/6233) ([c4222236](https://github.com/spinnaker/deck/commit/c422223629ae79b01b2a9cb5a9f0000c3446b08e))  
feat(core): add ability to search clusters by labels ([fd79e219](https://github.com/spinnaker/deck/commit/fd79e21942bc647ea66b5dbf7ea943f71a0570f5))  
style(tests): Fixed misspelled test filename [#6222](https://github.com/spinnaker/deck/pull/6222) ([cf967e39](https://github.com/spinnaker/deck/commit/cf967e39b59d45a550e7a98f45eb3f03b3a65e3b))  
fix(core): encode pipeline names in API request paths [#6221](https://github.com/spinnaker/deck/pull/6221) ([1d7f0c06](https://github.com/spinnaker/deck/commit/1d7f0c06db12da5844e0fe09897f6d32c0b5f295))  
refactor(provider/cf): refactor create SG to use FormikFormField [#6212](https://github.com/spinnaker/deck/pull/6212) ([283fb366](https://github.com/spinnaker/deck/commit/283fb3665384594d76945bee6e508d7a88f67dda))  
refactor(core): convert analytics initializer to plain TS [#6213](https://github.com/spinnaker/deck/pull/6213) ([0e4394cc](https://github.com/spinnaker/deck/commit/0e4394cc7fac22406d89adab30664ce0cbed57a7))  
refactor(core/modal): Extract WizardStepLabel to its own file [#6209](https://github.com/spinnaker/deck/pull/6209) ([ffdd2190](https://github.com/spinnaker/deck/commit/ffdd219029b4949a5ce565f3abc5389ecf84b6f1))  
fix(quiet): Add caveat that quietPeriod does not affect pipeline triggers [#6206](https://github.com/spinnaker/deck/pull/6206) ([570adc14](https://github.com/spinnaker/deck/commit/570adc1427b2c03be09ffc2fac7924913e4f56b0))  
refactor(amazon/subnet): Reactify SubnetSelectField [#6192](https://github.com/spinnaker/deck/pull/6192) ([b6de8f82](https://github.com/spinnaker/deck/commit/b6de8f8268b20865ee64751e999f9166c6312d8d))  



## [0.0.303](https://www.github.com/spinnaker/deck/compare/e2e18e6b0c94416ba11ddf49a75e8373d7228983...1a0eada2dee143097ec4bb256af88c1b316f00e8) (2018-12-12)


### Changes

chore(core): Bump version to 0.0.303 [#6187](https://github.com/spinnaker/deck/pull/6187) ([1a0eada2](https://github.com/spinnaker/deck/commit/1a0eada2dee143097ec4bb256af88c1b316f00e8))  
fix(core): Allow auto removal of pipelines when applications are removed [#6185](https://github.com/spinnaker/deck/pull/6185) ([18d9e696](https://github.com/spinnaker/deck/commit/18d9e696c6afe24c07ddef2555873188a7d1a4db))  



## [0.0.302](https://www.github.com/spinnaker/deck/compare/df2630ac96fe890236282ac9a0f27bd5424bb1b3...e2e18e6b0c94416ba11ddf49a75e8373d7228983) (2018-12-12)


### Changes

Package bump [#6183](https://github.com/spinnaker/deck/pull/6183) ([e2e18e6b](https://github.com/spinnaker/deck/commit/e2e18e6b0c94416ba11ddf49a75e8373d7228983))  
fix(preconfigured): Stage config needs alias attribute [#6182](https://github.com/spinnaker/deck/pull/6182) ([59665973](https://github.com/spinnaker/deck/commit/5966597339ed3b80ad2e8b3b2b64726279d6cbee))  
feat(core): Add new form styles ([5cd6c17d](https://github.com/spinnaker/deck/commit/5cd6c17d1877fe076f4e6d358a57520bb8e85f73))  
fix(core/presentation): Handle null `validation` prop ([c4c1c987](https://github.com/spinnaker/deck/commit/c4c1c987f63a88cfc96c01610178142b919aaf8b))  
feat(core): differentiate pipelines and strategies in filters [#6173](https://github.com/spinnaker/deck/pull/6173) ([0f1f9e88](https://github.com/spinnaker/deck/commit/0f1f9e88d0a2a61fda7bb9eb420f909fcf228f70))  
feat(core): allow deep linking to app config sections [#6170](https://github.com/spinnaker/deck/pull/6170) ([e604f11e](https://github.com/spinnaker/deck/commit/e604f11e461b01451e06d18e5a0f3b56fdf18ba5))  
fix(core): do not load GA script if not configured [#6177](https://github.com/spinnaker/deck/pull/6177) ([feaa5791](https://github.com/spinnaker/deck/commit/feaa5791d2065ff6f6bfeaf02e509b9f5869aa6d))  
fix(core/presentation): Give WatchValue better typing, tolerate no children [#6176](https://github.com/spinnaker/deck/pull/6176) ([34a1fdb4](https://github.com/spinnaker/deck/commit/34a1fdb4c6659b74f43902254c7f2dde6e40f1c9))  
feat(core): increase visibility of hover on clickable pods [#6146](https://github.com/spinnaker/deck/pull/6146) ([8977593d](https://github.com/spinnaker/deck/commit/8977593d4891bea6d1ff4b6383baf5146993ec53))  
fix(formik): Fixed validation and props for AccountSelectInput and RegionSelectInput [#6171](https://github.com/spinnaker/deck/pull/6171) ([00fc0f7b](https://github.com/spinnaker/deck/commit/00fc0f7b766da6fe74ec7684d5dc385aba075804))  
feat(runJob): Stage config defaults, execution details for preconfigured [#6168](https://github.com/spinnaker/deck/pull/6168) ([bde39c49](https://github.com/spinnaker/deck/commit/bde39c49b6fd485a169328da6153f0486319553d))  



## [0.0.301](https://www.github.com/spinnaker/deck/compare/a5d0aa0d7021637baff96c5425c0321e0fbeb7ad...df2630ac96fe890236282ac9a0f27bd5424bb1b3) (2018-12-10)


### Changes

chore(core): Bump version to 0.0.301 [#6167](https://github.com/spinnaker/deck/pull/6167) ([df2630ac](https://github.com/spinnaker/deck/commit/df2630ac96fe890236282ac9a0f27bd5424bb1b3))  
refactor(core): remove ON_DEMAND_THRESHOLD from cluster service, use settings ([4082b926](https://github.com/spinnaker/deck/commit/4082b9266e6d24bd3ab4450bbaf2cb373b7354a2))  



## [0.0.300](https://www.github.com/spinnaker/deck/compare/ab1a8340f222fdd87c4fc1fe59b492e878d436f3...a5d0aa0d7021637baff96c5425c0321e0fbeb7ad) (2018-12-10)


### Changes

chore(core): Bump version to 0.0.300 [#6165](https://github.com/spinnaker/deck/pull/6165) ([a5d0aa0d](https://github.com/spinnaker/deck/commit/a5d0aa0d7021637baff96c5425c0321e0fbeb7ad))  
refactor(core): make onDemandClusterThreshold configurable ([5509b48b](https://github.com/spinnaker/deck/commit/5509b48b0a7f8afef8605306d3b9a417bbfb8940))  
chore(core): alphabetize settings ([55a48af6](https://github.com/spinnaker/deck/commit/55a48af657afcec06382bb048dbd07c36f644e16))  
feat(core): Export FormikApplicationsPicker for reuse [#6156](https://github.com/spinnaker/deck/pull/6156) ([e6cb682e](https://github.com/spinnaker/deck/commit/e6cb682e8e6bf2df545373fcd74ba564a92ee5ce))  



## [0.0.299](https://www.github.com/spinnaker/deck/compare/5bbdf606324d279e2df5955914542cdfc24718db...ab1a8340f222fdd87c4fc1fe59b492e878d436f3) (2018-12-07)


### Changes

chore(core): Bump version to 0.0.299 ([ab1a8340](https://github.com/spinnaker/deck/commit/ab1a8340f222fdd87c4fc1fe59b492e878d436f3))  
feat(runJob): Adding support for preconfigured run job stages [#6152](https://github.com/spinnaker/deck/pull/6152) ([e7e6558e](https://github.com/spinnaker/deck/commit/e7e6558e1507feef20622e5080509683bfd8cc54))  
refactor(core/region): extract RegionSelectInput from RegionSelectField ([aa9ec84e](https://github.com/spinnaker/deck/commit/aa9ec84ee53253cb8c40a7cb646ac5df7c0c7fcc))  
refactor(core/account): Refactor AccountSelectInput to use 'value' prop ([307da1b9](https://github.com/spinnaker/deck/commit/307da1b93fb7a77364f51a2f7ea49652a20bb2d7))  
refactor(core/account): rename AccountSelectField to AccountSelectInput ([6f7f5435](https://github.com/spinnaker/deck/commit/6f7f543508176aee9eeb5ae40dea2bf03aa6a9ca))  
feat(core/pipelines): Add an apply entity tags stage [#6151](https://github.com/spinnaker/deck/pull/6151) ([4d808f7d](https://github.com/spinnaker/deck/commit/4d808f7df40fe1d019915ede9b4beec21d53cb10))  
fix(core/amazon): avoid overflow on server group modal components [#6153](https://github.com/spinnaker/deck/pull/6153) ([9e3415cb](https://github.com/spinnaker/deck/commit/9e3415cb4ef5afd01831d64cb26ce9e7650c5db7))  
fix(imports): Avoid "import { thing } from 'core'" ([af292f69](https://github.com/spinnaker/deck/commit/af292f69b82631e31ae77979336b2b7d7c930083))  
fix(core): do not cache getAllSecurityGroups API call [#6145](https://github.com/spinnaker/deck/pull/6145) ([cec89622](https://github.com/spinnaker/deck/commit/cec8962246add6e5e26831131d09b8466bfcd20d))  
refactor(core/presentation): Switch from a separate IControlledInputProps `field` object prop to spread props [#6141](https://github.com/spinnaker/deck/pull/6141) ([49ec7716](https://github.com/spinnaker/deck/commit/49ec7716c0130264648e3a5c6e4664d79e1853c1))  
refactor(amazon/image): Convert amazon image reader to a TS class [#6118](https://github.com/spinnaker/deck/pull/6118) ([9cef2486](https://github.com/spinnaker/deck/commit/9cef2486758bb4bcc21095f451e7dafef952fdd9))  
fix(core/artifacts): hide artifact list on trigger if artifacts disabled [#6138](https://github.com/spinnaker/deck/pull/6138) ([d53b479c](https://github.com/spinnaker/deck/commit/d53b479cf5c1e57fbab50cf45f9a2551492eee44))  



## [0.0.298](https://www.github.com/spinnaker/deck/compare/dfd67288664f18e8eb334f3f48efcc2e63725103...5bbdf606324d279e2df5955914542cdfc24718db) (2018-12-03)


### Changes

chore(core): Bump version to 0.0.298 [#6136](https://github.com/spinnaker/deck/pull/6136) ([5bbdf606](https://github.com/spinnaker/deck/commit/5bbdf606324d279e2df5955914542cdfc24718db))  
fix(core/dataSources): make child sources always honor their parents' disabled state [#6134](https://github.com/spinnaker/deck/pull/6134) ([a06e1e83](https://github.com/spinnaker/deck/commit/a06e1e83d9b3d5be3e8388eae283138046a913a1))  
feat(core): allow custom stuck deploy instructions on deploy details [#6131](https://github.com/spinnaker/deck/pull/6131) ([9c9a3cbb](https://github.com/spinnaker/deck/commit/9c9a3cbbd17024fc176c7a98c9f6128b21988fce))  
fix(core): increment running task time in step details [#6132](https://github.com/spinnaker/deck/pull/6132) ([e73b3d64](https://github.com/spinnaker/deck/commit/e73b3d646a7a8b143b3d5ba1b76a2c2850630638))  
fix(core): correctly render value in CRON minutes select field [#6133](https://github.com/spinnaker/deck/pull/6133) ([adf29630](https://github.com/spinnaker/deck/commit/adf29630a7776af9e0bede77abf8b2a9fc9fc1e2))  
feat(core): Support for setting application attributes from react [#6122](https://github.com/spinnaker/deck/pull/6122) ([b8acb944](https://github.com/spinnaker/deck/commit/b8acb94463a8c81d20116ec76cf95188140d8acd))  



## [0.0.297](https://www.github.com/spinnaker/deck/compare/018ff5b6e641cadfa6c9e0b01044992a7504f328...dfd67288664f18e8eb334f3f48efcc2e63725103) (2018-12-03)


### Changes

Package bump [#6130](https://github.com/spinnaker/deck/pull/6130) ([dfd67288](https://github.com/spinnaker/deck/commit/dfd67288664f18e8eb334f3f48efcc2e63725103))  
fix(stages/evaluateVariables): Make empty string less confusing by not replacing it with a dash [#6119](https://github.com/spinnaker/deck/pull/6119) ([1f19fa6c](https://github.com/spinnaker/deck/commit/1f19fa6cc65921e7b00386960e65bf51cefd0acd))  
fix(core): sort stage matches by label, then description [#6112](https://github.com/spinnaker/deck/pull/6112) ([fbb816a6](https://github.com/spinnaker/deck/commit/fbb816a6ad3e6ca5424a549de72c2e3569d64aed))  
feat(docker): Add help text to digest field [#6111](https://github.com/spinnaker/deck/pull/6111) ([132ca646](https://github.com/spinnaker/deck/commit/132ca646dfb9a9cacfcb3f2dd23bfc680bc9adc2))  
fix(core/pipeline): Fix grouped stages rendering in execution graph [#6113](https://github.com/spinnaker/deck/pull/6113) ([a02041f6](https://github.com/spinnaker/deck/commit/a02041f6a3338230993c0a89a674d1ec85276ae2))  
fix(core/entityTag): Use pipeline.id for pipeline entity tag's `entityId` value [#6098](https://github.com/spinnaker/deck/pull/6098) ([38b7d52c](https://github.com/spinnaker/deck/commit/38b7d52cb0d0a43fb5dfd739a7b6155704199138))  
fix(core): fix copy-to-clipboard on deep-linked tasks [#6102](https://github.com/spinnaker/deck/pull/6102) ([3a8ca114](https://github.com/spinnaker/deck/commit/3a8ca1145b434de626771efe2f297c018ed6b182))  
feat(webhook): add artifact status tab to webhook stage execution details [#6095](https://github.com/spinnaker/deck/pull/6095) ([195312b3](https://github.com/spinnaker/deck/commit/195312b334016ab1538896bd394783209db3192d))  
fix(core/executions): Fix NPE in ExecutionGroup->Notifications when there is a Strategy visible [#6087](https://github.com/spinnaker/deck/pull/6087) ([197460c7](https://github.com/spinnaker/deck/commit/197460c7f343a2c92e3119ccf67b60a67579d96d))  
fix(core/pipeline): Inherit artifacts/parameters/triggers from MPT template by default [#6088](https://github.com/spinnaker/deck/pull/6088) ([f43c9875](https://github.com/spinnaker/deck/commit/f43c9875922668761ad83f116c10cf30d982b394))  



## [0.0.296](https://www.github.com/spinnaker/deck/compare/1c4de1af3ca113bd6ce885352660de5680463db8...018ff5b6e641cadfa6c9e0b01044992a7504f328) (2018-11-28)


### Changes

chore(core): Bump version to 0.0.296 [#6096](https://github.com/spinnaker/deck/pull/6096) ([018ff5b6](https://github.com/spinnaker/deck/commit/018ff5b6e641cadfa6c9e0b01044992a7504f328))  
feat(core): export worker pool class [#6093](https://github.com/spinnaker/deck/pull/6093) ([49fc1cc0](https://github.com/spinnaker/deck/commit/49fc1cc0af8e3a80bd7cd36632b35fd7c5779e30))  



## [0.0.295](https://www.github.com/spinnaker/deck/compare/11d5401d56446970e86b5d0935e29ec44f8d4999...1c4de1af3ca113bd6ce885352660de5680463db8) (2018-11-28)


### Changes

Package bump [#6091](https://github.com/spinnaker/deck/pull/6091) ([1c4de1af](https://github.com/spinnaker/deck/commit/1c4de1af3ca113bd6ce885352660de5680463db8))  
refactor(core): remove unused applicationMap code from ApplicationReader [#6089](https://github.com/spinnaker/deck/pull/6089) ([7b1c99a0](https://github.com/spinnaker/deck/commit/7b1c99a0db8a06ee9c90b1e641f6f339d0c3e756))  
fix(core/pipeline): Fix manual execution dropdown when execution has no buildInfo [#6086](https://github.com/spinnaker/deck/pull/6086) ([263b80d3](https://github.com/spinnaker/deck/commit/263b80d37b5b04f2b0357e8efe3c47df701e4474))  
feat(analytics): Allow to configure siteSpeedSampleRate for Google Analytics [#5922](https://github.com/spinnaker/deck/pull/5922) ([ef53be31](https://github.com/spinnaker/deck/commit/ef53be311ce8d1558487f8a50485b79ee58e670a))  
fix(webhook): Move from buildInfo to webhook field. buildInfo is deprecated. [#6053](https://github.com/spinnaker/deck/pull/6053) ([5d2cc840](https://github.com/spinnaker/deck/commit/5d2cc840eccf4d130d96e16240bbd04bc43a0bbd))  
feat(deck): Support Github Status notification type [#6084](https://github.com/spinnaker/deck/pull/6084) ([8cfa14c0](https://github.com/spinnaker/deck/commit/8cfa14c04d69a503e489e34f3da19111feaf7093))  
fix(docker): RunAsUser select box appears doubled [#6082](https://github.com/spinnaker/deck/pull/6082) ([ce9f2cb9](https://github.com/spinnaker/deck/commit/ce9f2cb970840d24345e6f697b64b3106a289d55))  
feat(core/utils): Add WorkerPool class to limit concurrency of promise based tasks [#6065](https://github.com/spinnaker/deck/pull/6065) ([e19c832d](https://github.com/spinnaker/deck/commit/e19c832df2a53c68ca13b4f24fb68644ec9fcb52))  
feat(entityTags):kubernetes support [#5498](https://github.com/spinnaker/deck/pull/5498) ([7a4b0cd7](https://github.com/spinnaker/deck/commit/7a4b0cd77d993d801f4b9b96178a8f0b4a69fd8b))  



## [0.0.294](https://www.github.com/spinnaker/deck/compare/8ac6676b1fd513d4c171156ffd88f45bc5390da6...11d5401d56446970e86b5d0935e29ec44f8d4999) (2018-11-16)


### Changes

chore(core): Bump version to 0.0.294 ([11d5401d](https://github.com/spinnaker/deck/commit/11d5401d56446970e86b5d0935e29ec44f8d4999))  
fix(core/spel): Truncate spel autocomplete values to 90 chars [#6049](https://github.com/spinnaker/deck/pull/6049) ([4d3086bb](https://github.com/spinnaker/deck/commit/4d3086bbe61fd7cb42b35eeb58d725ae27e961c5))  
fix(dependencies): Use fontawesome-free instead of deprecated fonrtawesome-free-webfonts [#5873](https://github.com/spinnaker/deck/pull/5873) ([5671d531](https://github.com/spinnaker/deck/commit/5671d5313ddbfbfea6d2bc3868fd3bddcb5da599))  
fix(core/serverGroup): Fix occasional NPE when serverGroup.runningTasks is null [#6054](https://github.com/spinnaker/deck/pull/6054) ([c7980163](https://github.com/spinnaker/deck/commit/c79801639bf48bb46fc9bac106a56b4972ad4e24))  
fix(aws): only send user-changed capacity fields on resize [#6040](https://github.com/spinnaker/deck/pull/6040) ([c60b256f](https://github.com/spinnaker/deck/commit/c60b256f6194c72e837a0804c14f5284ba525777))  
fix(core/application): Move observable subscription to componentDidMount() [#6045](https://github.com/spinnaker/deck/pull/6045) ([768f3d27](https://github.com/spinnaker/deck/commit/768f3d27af88527aa1424a267c0b3dd1567de0ba))  
feat(core/presentation): Create IFormFieldApi interface and implement it in FormField and FormikFormField [#6020](https://github.com/spinnaker/deck/pull/6020) ([c1bfbcd2](https://github.com/spinnaker/deck/commit/c1bfbcd235fad7cf05c747bb55681b8e7f2e2c0a))  
fix(core): only show load error message when server groups fail to load [#6031](https://github.com/spinnaker/deck/pull/6031) ([28330810](https://github.com/spinnaker/deck/commit/2833081064096da70981761e9a9721c5806170c9))  
refactor(core): Reactify AccountRegionClusterSelector [#6029](https://github.com/spinnaker/deck/pull/6029) ([73fa99dc](https://github.com/spinnaker/deck/commit/73fa99dcc6b076c3d77c471fd171293bcb9d8bab))  



## [0.0.293](https://www.github.com/spinnaker/deck/compare/7bbf2ddfc17188d8b68230bcc573157a7594d55a...8ac6676b1fd513d4c171156ffd88f45bc5390da6) (2018-11-14)


### Changes

chore(core): Bump version to 0.0.293 [#6039](https://github.com/spinnaker/deck/pull/6039) ([8ac6676b](https://github.com/spinnaker/deck/commit/8ac6676b1fd513d4c171156ffd88f45bc5390da6))  
fix(core/pipeline): Only show quiet period tag if respect flag is true [#6038](https://github.com/spinnaker/deck/pull/6038) ([c8cebde7](https://github.com/spinnaker/deck/commit/c8cebde7c3612db006d5d95654b4f080dbb12e8f))  



## [0.0.292](https://www.github.com/spinnaker/deck/compare/afe47c65f7a51873cfcc2d19b9f693aeaa7e1730...7bbf2ddfc17188d8b68230bcc573157a7594d55a) (2018-11-13)


### Changes

chore(core): Bump version to 0.0.292 ([7bbf2ddf](https://github.com/spinnaker/deck/commit/7bbf2ddfc17188d8b68230bcc573157a7594d55a))  



## [0.0.291](https://www.github.com/spinnaker/deck/compare/8cd7b343e1accef6bf59038f68bdc329fc24b51f...afe47c65f7a51873cfcc2d19b9f693aeaa7e1730) (2018-11-13)


### Changes

Package bump [#6026](https://github.com/spinnaker/deck/pull/6026) ([afe47c65](https://github.com/spinnaker/deck/commit/afe47c65f7a51873cfcc2d19b9f693aeaa7e1730))  
fix(core/pipeline): Fix required for manual execution date picker [#6033](https://github.com/spinnaker/deck/pull/6033) ([385e4484](https://github.com/spinnaker/deck/commit/385e4484ef20b1d9020443802bbad704472284c5))  
feat(core/pipeline): Indicate quiet period enabled on pipeline trigger status [#6008](https://github.com/spinnaker/deck/pull/6008) ([5cbc8ec3](https://github.com/spinnaker/deck/commit/5cbc8ec326b15dd60828832b18979b0480d4bbc8))  
fix(core): restrict CRON trigger minutes options [#6027](https://github.com/spinnaker/deck/pull/6027) ([e5f3cd89](https://github.com/spinnaker/deck/commit/e5f3cd89e1d3d605ce9f64a64883da963d27ffbb))  



## [0.0.288](https://www.github.com/spinnaker/deck/compare/1617aa5fba514f2eaa4ddfcf39476ef3e0e5f3a6...8cd7b343e1accef6bf59038f68bdc329fc24b51f) (2018-11-12)


### Changes

chore(core): Bump version to 0.0.288 [#6023](https://github.com/spinnaker/deck/pull/6023) ([8cd7b343](https://github.com/spinnaker/deck/commit/8cd7b343e1accef6bf59038f68bdc329fc24b51f))  
refactor(amazon): convert resize modal to react [#6013](https://github.com/spinnaker/deck/pull/6013) ([b6151455](https://github.com/spinnaker/deck/commit/b6151455434bdcf9283dae58d703606dd2991916))  
fix(core): Fixed DeploymentStrategySelector not updating strategy [#6022](https://github.com/spinnaker/deck/pull/6022) ([0107e0ff](https://github.com/spinnaker/deck/commit/0107e0ff0c3e980c1bf35dcae27079dc3901c520))  
fix(core/presentation): Use official formik `connect()` api to create `FormikForm` render-prop component. ([ee4e22be](https://github.com/spinnaker/deck/commit/ee4e22be0caa80bced4e831b05067b6f7a4ca685))  
fix(core): allow in-page deep linking to tasks [#6014](https://github.com/spinnaker/deck/pull/6014) ([e4db08be](https://github.com/spinnaker/deck/commit/e4db08bea2108d0905b3e5dff3d46678d0825cb4))  
fix(core/presentation): Make fastField property optional ([95751587](https://github.com/spinnaker/deck/commit/95751587dabdb3d69d9585cdef3174db27a430e7))  
feat(kubernetes): collapsible container list in ServerGroupHeader [#5986](https://github.com/spinnaker/deck/pull/5986) ([5fc06d41](https://github.com/spinnaker/deck/commit/5fc06d411aa8595c7ea4881754b5aeb2d805f985))  
refactor(core/presentation): Move some code around - Extract evaluateExpression to a separate file - Move FormField and FormikFormField to parent directory ([ef4a669a](https://github.com/spinnaker/deck/commit/ef4a669a39ab5a5e19cd5e364928c387c2bb539e))  
feat(core/presentation): Support internal Validator(s) in Inputs [#5995](https://github.com/spinnaker/deck/pull/5995) ([d08b202d](https://github.com/spinnaker/deck/commit/d08b202d637333b9b505b85593b48207b69db478))  
refactor(stages): Adding StageConfigWrapper for cleaner StageConfigs [#5994](https://github.com/spinnaker/deck/pull/5994) ([89977dc1](https://github.com/spinnaker/deck/commit/89977dc1323f3bb658ae6221b1b28665dd94d4d6))  



## [0.0.287](https://www.github.com/spinnaker/deck/compare/ef335337c83d5ada2bd433158d80619cb07f251f...1617aa5fba514f2eaa4ddfcf39476ef3e0e5f3a6) (2018-11-09)


### Changes

chore(core): Bump version to 0.0.287 ([1617aa5f](https://github.com/spinnaker/deck/commit/1617aa5fba514f2eaa4ddfcf39476ef3e0e5f3a6))  
fix(pager): Sanitize any html rendered ([a6f6b07e](https://github.com/spinnaker/deck/commit/a6f6b07ec9a11d005f8263fd5b3a9f00f7e8b828))  



## [0.0.286](https://www.github.com/spinnaker/deck/compare/219479842c97b12b0e42d112e467e8bbf31a9507...ef335337c83d5ada2bd433158d80619cb07f251f) (2018-11-08)


### Changes

chore(core): Bump version to 0.0.286 ([ef335337](https://github.com/spinnaker/deck/commit/ef335337c83d5ada2bd433158d80619cb07f251f))  
feat(core/presentation): Automatically generate '${label} cannot be negative' validation messages [#5988](https://github.com/spinnaker/deck/pull/5988) ([b8a95d73](https://github.com/spinnaker/deck/commit/b8a95d7356ec1e5bd140a9854c2dcc778f535803))  



## [0.0.285](https://www.github.com/spinnaker/deck/compare/145a0e04fdffc906220b06b8dfad036f23152c83...219479842c97b12b0e42d112e467e8bbf31a9507) (2018-11-07)


### Changes

chore(core): Bump version to 0.0.285 ([21947984](https://github.com/spinnaker/deck/commit/219479842c97b12b0e42d112e467e8bbf31a9507))  
feat(artifacts): Allow preconfigured webhooks to produce artifacts [#5984](https://github.com/spinnaker/deck/pull/5984) ([fccade72](https://github.com/spinnaker/deck/commit/fccade7223212509c8ac01161d82e1fba2cae33c))  
feat(core/presentation): Perf: use Formik FastField to speed up forms.  Opt out using fastField={false} ([ead3efd6](https://github.com/spinnaker/deck/commit/ead3efd629eb25428b2ae2549246879bd26105d3))  
feat(core/presentation): Add isStringArray and switch to string options in various react components ([19b83ce4](https://github.com/spinnaker/deck/commit/19b83ce4f5039ccad7a4cb7f40c08da7a57a425f))  
feat(core/presentation): Create SelectInput and use in HealthCheck component ([1858eadd](https://github.com/spinnaker/deck/commit/1858eadd2882111e7001eb513d7935d9913cd0ae))  
feat(core/presentation): Add onChange prop to FormikFormField as a replacement for formik-effect - Add WatchValue component to be notified when prop value changes ([5e44a7f9](https://github.com/spinnaker/deck/commit/5e44a7f99f81abd7ccba09f0709f94b25b07518e))  
feat(core/presentation): Add minValue/maxValue Validators and use in AdvancedSettings ([fb2272a3](https://github.com/spinnaker/deck/commit/fb2272a30aa9fa7ec70af64ad6195264de2ca5e9))  
feat(core/presentation): StandardFieldLayout: move inline styles into .css ([e2646fc6](https://github.com/spinnaker/deck/commit/e2646fc6b2902fef6656840242c50dcd808c1bd2))  
feat(core/presentation): Add CheckboxInput and NumberInput and use in ALBAdvancedSettings ([b3020c13](https://github.com/spinnaker/deck/commit/b3020c1373232dde3b96af6f48dd6d67455ae05d))  
feat(core/presentation): Add RadioButton and TextArea Inputs; use in EntityTagEditor w/FormikFormField ([10c24739](https://github.com/spinnaker/deck/commit/10c24739ec90378e415483ac9e3c602796ac9d53))  
refactor(core/presentation): Extract validationClassName to utils.ts ([d67aae00](https://github.com/spinnaker/deck/commit/d67aae00d66a68acdf888ac538e554f99e51d80d))  
fix(kubernetes): add k8s kind in artifact icon list [#5978](https://github.com/spinnaker/deck/pull/5978) ([8634093a](https://github.com/spinnaker/deck/commit/8634093a8b33e5e65b4195403c0bf97b62743a4a))  
feat(core/modal): Perf: do not rerender entire modal on every scroll ([1b899700](https://github.com/spinnaker/deck/commit/1b899700dc3e902a284e6a7bb5b24afa49abb2e3))  
fix(core/application): Do not JSON.stringify this.data ([956b07bc](https://github.com/spinnaker/deck/commit/956b07bcca32f8189b1d80653a37a22abc67535c))  
feat(core): Export projects to be re-used by teams [#5979](https://github.com/spinnaker/deck/pull/5979) ([ce3e3e3f](https://github.com/spinnaker/deck/commit/ce3e3e3f18201a398f532a95325d97da80d1374a))  
feat(artifacts): Add support for helm/chart artifacts [#5918](https://github.com/spinnaker/deck/pull/5918) ([ca9df6f2](https://github.com/spinnaker/deck/commit/ca9df6f29c7aab824b5d344fce9eab4b8156b65f))  
Merge branch 'master' into search-fix-firefox ([6942f69b](https://github.com/spinnaker/deck/commit/6942f69beb548c6387398fcf44533df4d5a9a769))  
fix(search): firefox unable to scroll search results ([bdf52691](https://github.com/spinnaker/deck/commit/bdf52691d9b60cfdc4174a51327a45d565966ed3))  
fix(kubernetes): bake manifest Name field is required [#5965](https://github.com/spinnaker/deck/pull/5965) ([85ac8653](https://github.com/spinnaker/deck/commit/85ac86530d31869a6e51ff6979c4da88e025aaa7))  
fix(core/pipeline): Make quiet period message more specific [#5964](https://github.com/spinnaker/deck/pull/5964) ([dd30cd62](https://github.com/spinnaker/deck/commit/dd30cd620f64fb608a0a805c8cb76cd5f147bf31))  
fix(core/presentation): change warning class to match class applied by ValidationMessage component ([fc22e490](https://github.com/spinnaker/deck/commit/fc22e49029fc625677e8051ee558b4de708d90e8))  



## [0.0.284](https://www.github.com/spinnaker/deck/compare/275db97289eaf30f5a7192423983501a3184af46...145a0e04fdffc906220b06b8dfad036f23152c83) (2018-11-02)


### Changes

chore(core): Bump version to 0.0.284 ([145a0e04](https://github.com/spinnaker/deck/commit/145a0e04fdffc906220b06b8dfad036f23152c83))  
feat(kubernetes): include server group manager name in clickable clusters box [#5952](https://github.com/spinnaker/deck/pull/5952) ([649f5d6d](https://github.com/spinnaker/deck/commit/649f5d6d5fe74d2393f4cf51deb833990fdcc664))  
feat(kubernetes): enable / disable stages [#5940](https://github.com/spinnaker/deck/pull/5940) ([0cb4ea9d](https://github.com/spinnaker/deck/commit/0cb4ea9dc526497566f3c1309f02c458ee6f6514))  
fix(artifacts): bake manifest artifact scope used before defined [#5947](https://github.com/spinnaker/deck/pull/5947) ([2bcb0431](https://github.com/spinnaker/deck/commit/2bcb04314ba230a58a3926b67f3bd6309a502b0e))  



## [0.0.283](https://www.github.com/spinnaker/deck/compare/473fecf90ed41992ab8b4c8ebe1403ccd33e4d4a...275db97289eaf30f5a7192423983501a3184af46) (2018-11-01)


### Changes

chore(core): Bump version to 0.0.283 ([275db972](https://github.com/spinnaker/deck/commit/275db97289eaf30f5a7192423983501a3184af46))  
refactor(core/deploy): Remove angular strategy config support ([eeda562a](https://github.com/spinnaker/deck/commit/eeda562a8c2c848ecfb81c98fb0a643a578d3f7e))  
refactor(core/deploy): Convert red black strategy config to react ([413ed660](https://github.com/spinnaker/deck/commit/413ed660b5dcb0012bd10e9208352a015acf51b3))  
refactor(core/deploy): Convert custom strategy config to react ([3960ad13](https://github.com/spinnaker/deck/commit/3960ad132f376b9b615c4cd4c1cf7a64f221e0e9))  
refactor(core/deploy): Convert rolling red back strategy config to react ([6ae6c516](https://github.com/spinnaker/deck/commit/6ae6c516ac18da5083ccd3838af2b2ad27c40c82))  
refactor(core): Expose NumberList in react ([0dbdbcdc](https://github.com/spinnaker/deck/commit/0dbdbcdc098623e89f0c229fb904bf1ee5e54ff1))  
refactor(core): Convert DeploymentStrategySelector to react ([79587138](https://github.com/spinnaker/deck/commit/795871386c51f7c5b21bac4a3d2806853759ffe7))  



## [0.0.282](https://www.github.com/spinnaker/deck/compare/4f3f7ddb978105b1183aa5c5e82d2f4937c903c2...473fecf90ed41992ab8b4c8ebe1403ccd33e4d4a) (2018-11-01)


### Changes

chore(core): Bump version to 0.0.282 [#5938](https://github.com/spinnaker/deck/pull/5938) ([473fecf9](https://github.com/spinnaker/deck/commit/473fecf90ed41992ab8b4c8ebe1403ccd33e4d4a))  
feat(webhook): add PATCH to list of options for webhook [#5912](https://github.com/spinnaker/deck/pull/5912) ([e1b94b0a](https://github.com/spinnaker/deck/commit/e1b94b0a9368978414910c1eb39457ca9e570352))  
feat(core/triggers): Add UI to toggle on/off respectQuietPeriod [#5921](https://github.com/spinnaker/deck/pull/5921) ([0d938b1d](https://github.com/spinnaker/deck/commit/0d938b1d7857843ddbe13b1aff933c31b40b0fcc))  
fix(core/presentation): use Option<string> in StringToOptions ([5afff2cf](https://github.com/spinnaker/deck/commit/5afff2cf35af7fb388513bc2685614b1a63a6471))  
fix(core/help): Export HelpField and HelpMenu ([7b1fa979](https://github.com/spinnaker/deck/commit/7b1fa9799a229faf13becbf824f5a17a6e052cca))  



## [0.0.281](https://www.github.com/spinnaker/deck/compare/3114d181fcfd22839fb8c9e8e0e3939d830bc99b...4f3f7ddb978105b1183aa5c5e82d2f4937c903c2) (2018-10-31)


### Changes

chore(core): Bump version to 0.0.281 [#5919](https://github.com/spinnaker/deck/pull/5919) ([4f3f7ddb](https://github.com/spinnaker/deck/commit/4f3f7ddb978105b1183aa5c5e82d2f4937c903c2))  
 chore(core/presentation): Update formik from 0.11.11 to 1.3.1 [#5917](https://github.com/spinnaker/deck/pull/5917) ([57b1e490](https://github.com/spinnaker/deck/commit/57b1e4904c04d04cb485d2850fd0675d41c4f60c))  
fix(provider/cf): fetch services on a per-region basis ([c0842ad0](https://github.com/spinnaker/deck/commit/c0842ad00bb6afb6b63e943fa5d9c78fe9ec1b3f))  
feat(core/presentation): Introduce Validation class for reusable validation fns. [#5913](https://github.com/spinnaker/deck/pull/5913) ([f22fecd5](https://github.com/spinnaker/deck/commit/f22fecd5e774ddf42e5c308df26cfee431597c86))  
chore(core/pipeline): Cleaning up imports [#5914](https://github.com/spinnaker/deck/pull/5914) ([9eacd0c8](https://github.com/spinnaker/deck/commit/9eacd0c813bee30086daf8d36a60af4e950cb70d))  
refactor(core/projects): Simplify TrashButton click handler ([b08bab2d](https://github.com/spinnaker/deck/commit/b08bab2d4198deb0ba3b84c7ba7ae887e6c7b492))  
refactor(core): Remove invalid <g> from HoverablePopover [#5904](https://github.com/spinnaker/deck/pull/5904) ([1abc6ba4](https://github.com/spinnaker/deck/commit/1abc6ba441641fd1e9c13f346bf3a7a4eeb6af32))  
test(core/cluster): fix lint error unused variable [#5910](https://github.com/spinnaker/deck/pull/5910) ([8523bbd7](https://github.com/spinnaker/deck/commit/8523bbd7eb620cb4ec43754e02508fecb086b64e))  
feat(core): Support a date constraint on trigger parameters [#5907](https://github.com/spinnaker/deck/pull/5907) ([cc68c9c4](https://github.com/spinnaker/deck/commit/cc68c9c468520b14e65eef8b6f5b6261ef5bae72))  
feat(pipeline_templates): Display "Force Rebake" checkbox for MPT [#5854](https://github.com/spinnaker/deck/pull/5854) ([9488e335](https://github.com/spinnaker/deck/commit/9488e33590b875fb7c8dbf7d7e3848a0201f54a3))  



## [0.0.280](https://www.github.com/spinnaker/deck/compare/7c3a8d457afbf34b8c7fa080bcf272faaa019fc9...3114d181fcfd22839fb8c9e8e0e3939d830bc99b) (2018-10-25)


### Changes

chore(core): Bump to 0.0.280 [#5897](https://github.com/spinnaker/deck/pull/5897) ([3114d181](https://github.com/spinnaker/deck/commit/3114d181fcfd22839fb8c9e8e0e3939d830bc99b))  
feat(stages/evaluatevariables): Using lodash.defaultsDeep instead [#5896](https://github.com/spinnaker/deck/pull/5896) ([7fa35048](https://github.com/spinnaker/deck/commit/7fa35048af31de358157fa648f3861ab70311d33))  
feat(stages/evaluatevariables): Default failOnFailedExpressions to true [#5895](https://github.com/spinnaker/deck/pull/5895) ([5f5dff7f](https://github.com/spinnaker/deck/commit/5f5dff7f315e5a1cbc149c8d7363f3d3a7d61166))  
refactor(core/projects): Convert projects header and configuration to react [#5886](https://github.com/spinnaker/deck/pull/5886) ([23437377](https://github.com/spinnaker/deck/commit/23437377aace9cc6cd7fec8ecd0613b840cfe0d2))  
fix(lint): prefer-object-spread false positive ([1518dc12](https://github.com/spinnaker/deck/commit/1518dc124c8f49735441a1e6c5e3ace3669fe942))  
feat(provider/appengine): enable artifacts as config files [#5888](https://github.com/spinnaker/deck/pull/5888) ([0a6d3f92](https://github.com/spinnaker/deck/commit/0a6d3f92733736c6ee75d679971afc3eb62bb33f))  
feat(stages/evaluatevariables) Add UI for Evaluate Variables stage [#5871](https://github.com/spinnaker/deck/pull/5871) ([e1d2c6ec](https://github.com/spinnaker/deck/commit/e1d2c6ecb13e1cbd6a4fe7117efa0fb1a87273b7))  
feat(core/projects): Add a FormikApplicationsPicker react component [#5883](https://github.com/spinnaker/deck/pull/5883) ([e40e10db](https://github.com/spinnaker/deck/commit/e40e10db3a65b2146f3f70f71f6dd1c78903ab5e))  



## [0.0.279](https://www.github.com/spinnaker/deck/compare/f91c01a53dfb74a5ee1bf16f65f62a2575c88989...7c3a8d457afbf34b8c7fa080bcf272faaa019fc9) (2018-10-24)


### Changes

chore(core): bump package version ([7c3a8d45](https://github.com/spinnaker/deck/commit/7c3a8d457afbf34b8c7fa080bcf272faaa019fc9))  



## [0.0.278](https://www.github.com/spinnaker/deck/compare/51d0124e8ded2af092ed8591c7d5ef80fa3166c5...f91c01a53dfb74a5ee1bf16f65f62a2575c88989) (2018-10-24)


### Changes

chore(core): bump to 0.0.278 ([f91c01a5](https://github.com/spinnaker/deck/commit/f91c01a53dfb74a5ee1bf16f65f62a2575c88989))  
feat(core/presentation): add onBlur to react-select-input [#5887](https://github.com/spinnaker/deck/pull/5887) ([f749e764](https://github.com/spinnaker/deck/commit/f749e764135d0ce49ce300dc7f0b6be82a82834d))  
feat(provider/aws): allow editing of idle timeout and deletion protection ([b4571185](https://github.com/spinnaker/deck/commit/b45711858994012c830b4c740911d79ed1e54a50))  
feat(core/presentation): Add ReactSelectInput component [#5882](https://github.com/spinnaker/deck/pull/5882) ([af1e84a6](https://github.com/spinnaker/deck/commit/af1e84a6573a3ffb0f18687beb7331368b567d54))  
feat(artifacts): infer kind from type if kind is not available [#5879](https://github.com/spinnaker/deck/pull/5879) ([03ae18f6](https://github.com/spinnaker/deck/commit/03ae18f65543ab2f588540c3bdf7056a52babc3f))  
fix(core/modal): Deep merge `errors` object [#5877](https://github.com/spinnaker/deck/pull/5877) ([4652ab99](https://github.com/spinnaker/deck/commit/4652ab995717452129d1b7aa683b87e859aa6dd3))  
refactor(provider/cf): rename deleteService to destroyService ([73ed9ae0](https://github.com/spinnaker/deck/commit/73ed9ae0c54ad2b90e346cdd63a19c145fc8f14e))  
fix(tasks): Have minimum progress bar when task fails on first step [#5867](https://github.com/spinnaker/deck/pull/5867) ([a607e132](https://github.com/spinnaker/deck/commit/a607e132aac869ba26bf1698b2f3014caf8f5271))  
fix(chaos): Fix mean and min mins [#5866](https://github.com/spinnaker/deck/pull/5866) ([4d5e7f5f](https://github.com/spinnaker/deck/commit/4d5e7f5ff1e06e86e2326b065988dfd796caf583))  
docs(core/presentation): Add doc for the validate prop ([2b67cedc](https://github.com/spinnaker/deck/commit/2b67cedc748ee84d9d4940b4a676d3f2f79a2b95))  



## [0.0.277](https://www.github.com/spinnaker/deck/compare/4901bb6ee6d709b23ca2d83b0a62620fe2df6785...51d0124e8ded2af092ed8591c7d5ef80fa3166c5) (2018-10-17)


### Changes

chore(core): bump to 277 [#5864](https://github.com/spinnaker/deck/pull/5864) ([51d0124e](https://github.com/spinnaker/deck/commit/51d0124e8ded2af092ed8591c7d5ef80fa3166c5))  
refactor(core/presentation): Switch from separate 'error', 'warning', 'preview' props to 'validationMessage' and 'validationStatus' Refactor Expression Inputs and switch to 'validationMessage' ([6e29dacb](https://github.com/spinnaker/deck/commit/6e29dacbac867900087c2c169ee971ac084e963a))  
refactor(core/presentation): Refactor Expression Form Fields to use new API/Components ([0b51dca1](https://github.com/spinnaker/deck/commit/0b51dca1032423a6e24eb4564829171e1832c50d))  
refactor(core/presentation): Refactor form inputs and layouts for better composability and reuse. - Introduce <CurrentForm render={formikProps => { render the form values }} /> component - Introduce <FormField/> and <FormikFormField/> components with render props - Remove 'formikField' HOC in favor of <FormikFormField/> - Remove TextField in favor of using <FormikFormField input={TextInput} /> - Rename BasicLayout to StandardFieldLayout ([0766688f](https://github.com/spinnaker/deck/commit/0766688faf5719f673f307941e12dec995fcd909))  
fix(core/account): Fixed AccountSelectField missing state initialization [#5860](https://github.com/spinnaker/deck/pull/5860) ([32468f38](https://github.com/spinnaker/deck/commit/32468f38f89e1e63b880a3b97d38309019a8bb4e))  
fix(core/pipeline): Do not explode when `buildInfo` is missing in previous execution [#5859](https://github.com/spinnaker/deck/pull/5859) ([85926431](https://github.com/spinnaker/deck/commit/859264311d7526d7af4e95c83112440310601733))  
refactor(*): Replace all uses of wrapped AccountSelectField with react version [#5832](https://github.com/spinnaker/deck/pull/5832) ([8e23f8fa](https://github.com/spinnaker/deck/commit/8e23f8fa06433783a1cd2ad5b1f571376340792b))  
feat(webhook): show status code and response body [#5850](https://github.com/spinnaker/deck/pull/5850) ([4a4e5ff3](https://github.com/spinnaker/deck/commit/4a4e5ff339e2c062f0ba0b0768bdaa807aafe400))  



## [0.0.276](https://www.github.com/spinnaker/deck/compare/9cf6821398243d4f3ff6cb9d846268bf2fff2230...4901bb6ee6d709b23ca2d83b0a62620fe2df6785) (2018-10-11)


### Changes

chore(core): bump package to 0.0.276 [#5847](https://github.com/spinnaker/deck/pull/5847) ([4901bb6e](https://github.com/spinnaker/deck/commit/4901bb6ee6d709b23ca2d83b0a62620fe2df6785))  
feat(core): skip stage button [#5835](https://github.com/spinnaker/deck/pull/5835) ([696bf2b8](https://github.com/spinnaker/deck/commit/696bf2b8a1e57e37ab3d632e0a634981d97f8a9a))  
refactor(core/account): Cleanup react account select field [#5840](https://github.com/spinnaker/deck/pull/5840) ([b08381b1](https://github.com/spinnaker/deck/commit/b08381b114d53b2bef0d595d48c0563ade9d2ecf))  
fix(deck): Change Build Stage description [#5839](https://github.com/spinnaker/deck/pull/5839) ([4f48a3ed](https://github.com/spinnaker/deck/commit/4f48a3edf2803785ac7122f20ad72dd374e06212))  



## [0.0.275](https://www.github.com/spinnaker/deck/compare/1694345a74ce061cac9d5a2daa6df41128a15eab...9cf6821398243d4f3ff6cb9d846268bf2fff2230) (2018-10-04)


### Changes

chore(core): Bump to 0.0.275 ([9cf68213](https://github.com/spinnaker/deck/commit/9cf6821398243d4f3ff6cb9d846268bf2fff2230))  
refactor(core/account): Create react version of AccountSelectField ([24f020b6](https://github.com/spinnaker/deck/commit/24f020b666bea5a7a34021b9df2153ceb2f8eacb))  
feat(provider/kubernetes): split kubernetes pod logs by container [#5824](https://github.com/spinnaker/deck/pull/5824) ([761df950](https://github.com/spinnaker/deck/commit/761df9507a6b7e2d72cb45dfd46eba16a70674c1))  
fix (core/deploy): Fix rolling red black pipeline selector and add number button ([83db04e4](https://github.com/spinnaker/deck/commit/83db04e4946218aaea669f3800ccf5a7c836c324))  
fix(google): backend service selection in server group wizard [#5813](https://github.com/spinnaker/deck/pull/5813) ([3b1f1c59](https://github.com/spinnaker/deck/commit/3b1f1c5940512dc2fce8118dea48b21f34a94d14))  



## [0.0.273](https://www.github.com/spinnaker/deck/compare/3e35fda1a5fe4ae9b510a9a1b23c2d7805b04d85...1694345a74ce061cac9d5a2daa6df41128a15eab) (2018-10-01)


### Changes

chore(core): Bump to 0.0.273 ([1694345a](https://github.com/spinnaker/deck/commit/1694345a74ce061cac9d5a2daa6df41128a15eab))  
chore(core/account): Add registry to IAccountDetails ([2fe01839](https://github.com/spinnaker/deck/commit/2fe01839040d40314848bbf2681ae09c590d9229))  
refactor(core/config): Add application to stage config and export ([0cc05553](https://github.com/spinnaker/deck/commit/0cc05553d2190cda0dc29d9bd46d68d637a20e3a))  
fix(provider/kubernetes): remove warnings for stages [#5811](https://github.com/spinnaker/deck/pull/5811) ([6ece6359](https://github.com/spinnaker/deck/commit/6ece6359aca55996f657e0f4f7c3c26a6f57f71e))  



## [0.0.272](https://www.github.com/spinnaker/deck/compare/5f4539731094d84ab2dca98d76c1b1b6f726205c...3e35fda1a5fe4ae9b510a9a1b23c2d7805b04d85) (2018-10-01)


### Changes

chore(core): Bump to 0.0.272 ([3e35fda1](https://github.com/spinnaker/deck/commit/3e35fda1a5fe4ae9b510a9a1b23c2d7805b04d85))  
chore(package): prepare -> prepublishOnly for everything [#5806](https://github.com/spinnaker/deck/pull/5806) ([06f45b5c](https://github.com/spinnaker/deck/commit/06f45b5c0da71227e4f1d7bb9e7187e95231f4d2))  
feat(core/presentation): Support validation when fields are touched (and some other things) [#5808](https://github.com/spinnaker/deck/pull/5808) ([b8bc8f74](https://github.com/spinnaker/deck/commit/b8bc8f74d633d0e317751040c718b26bf0f40bdb))  
Call new manual execution endpoint in Executions.tsx [#5805](https://github.com/spinnaker/deck/pull/5805) ([8000b731](https://github.com/spinnaker/deck/commit/8000b731df0cac264e6d1657a1f4929b224fa4d8))  
fix(core): Fix polling cancellation on manual trigger [#5804](https://github.com/spinnaker/deck/pull/5804) ([45551447](https://github.com/spinnaker/deck/commit/455514470c7d5c0feefe7e87371b9daae7ede351))  
feat(webhooks): Sort predefined webhook parameters by order attr [#5770](https://github.com/spinnaker/deck/pull/5770) ([63a93394](https://github.com/spinnaker/deck/commit/63a9339475e7eea34ef9540fcd354828c2811906))  
feat(kubernetes): provide namespace and kind hints in manifest selector component [#5769](https://github.com/spinnaker/deck/pull/5769) ([36d50467](https://github.com/spinnaker/deck/commit/36d504671b735477cd398c2075a9cc3ba1c38187))  



## [0.0.271](https://www.github.com/spinnaker/deck/compare/b498d2303aa27082f46a5c6685022a2b7e2e3a2c...5f4539731094d84ab2dca98d76c1b1b6f726205c) (2018-09-27)


### Changes

chore(core): Bump to 0.0.271 [#5803](https://github.com/spinnaker/deck/pull/5803) ([5f453973](https://github.com/spinnaker/deck/commit/5f4539731094d84ab2dca98d76c1b1b6f726205c))  
feat(core): Remove ability to delete tasks from ui [#5777](https://github.com/spinnaker/deck/pull/5777) ([7c01027d](https://github.com/spinnaker/deck/commit/7c01027d9ef50ac34f7c6f5c9fc86b95583ed340))  
chore(prettier): Just Update Prettier™ [#5802](https://github.com/spinnaker/deck/pull/5802) ([acfab9c8](https://github.com/spinnaker/deck/commit/acfab9c8ea03298353ce4c7be284a96f78717129))  
fix(core/trigger): Fix react trigger reload [#5800](https://github.com/spinnaker/deck/pull/5800) ([35965d87](https://github.com/spinnaker/deck/commit/35965d87d95f6c9bc35819b689214a67b29de425))  
refactor(core/modal): Improve wizardPage types so no type param is necessary ([8aff36a9](https://github.com/spinnaker/deck/commit/8aff36a9582b775a8e1dc2344938ecf1e22f85be))  
feat(provider/cf): add deploy and delete service pipeline stages ([876fcf55](https://github.com/spinnaker/deck/commit/876fcf55f33afa1a734cbd89e72149fbe3ebac68))  



## [0.0.270](https://www.github.com/spinnaker/deck/compare/b6fdd77ba7611e1afb59a43ab791af49a1bc777b...b498d2303aa27082f46a5c6685022a2b7e2e3a2c) (2018-09-27)


### Changes

chore(core): Bump to 0.0.270 ([b498d230](https://github.com/spinnaker/deck/commit/b498d2303aa27082f46a5c6685022a2b7e2e3a2c))  
refactor(docker/pipeline): Convert docker trigger config to react ([2125de97](https://github.com/spinnaker/deck/commit/2125de975c149537e5eaeb8763016e6ce731f9ef))  
refactor(core/pipeline): Create react version of RunAsUser ([366a381c](https://github.com/spinnaker/deck/commit/366a381c8c11da05f386baae1df0a0eca00c53e0))  
feat(core/pipeline): Support trigger configs in react ([4fce3cc1](https://github.com/spinnaker/deck/commit/4fce3cc1e8291f9092ba6860fcecb23d96aa8a49))  
feat(core): Call new manual execution endpoint behind a flag [#5794](https://github.com/spinnaker/deck/pull/5794) ([c2b78583](https://github.com/spinnaker/deck/commit/c2b785836727868b61b6067a7302c9fbb71fa72f))  
fix(core): Fix error when changing execution grouping [#5793](https://github.com/spinnaker/deck/pull/5793) ([f208cbf1](https://github.com/spinnaker/deck/commit/f208cbf1b36b46004954ba7c1fd0b5c60e8b6393))  



## [0.0.269](https://www.github.com/spinnaker/deck/compare/a0c98ab711d935a2f5aaa69d15e62abbc1029de7...b6fdd77ba7611e1afb59a43ab791af49a1bc777b) (2018-09-26)


### Changes

chore(core): bump to 269 ([b6fdd77b](https://github.com/spinnaker/deck/commit/b6fdd77ba7611e1afb59a43ab791af49a1bc777b))  
refactor(core/*): Change core imports to core/module instead of @spinnaker/core ([b94fbb67](https://github.com/spinnaker/deck/commit/b94fbb67500412759f98f9d85808cb242d0e5aea))  



## [0.0.268](https://www.github.com/spinnaker/deck/compare/a5a7fae1556e0e6555d31061f829f51d84d99f08...a0c98ab711d935a2f5aaa69d15e62abbc1029de7) (2018-09-26)


### Changes

chore(core): bump to 268 [#5788](https://github.com/spinnaker/deck/pull/5788) ([a0c98ab7](https://github.com/spinnaker/deck/commit/a0c98ab711d935a2f5aaa69d15e62abbc1029de7))  
feat(core): collapse execution group account tags if there are more than 2 [#5778](https://github.com/spinnaker/deck/pull/5778) ([d30ade8d](https://github.com/spinnaker/deck/commit/d30ade8df6512e0875948f5166528035a312d3ec))  



## [0.0.267](https://www.github.com/spinnaker/deck/compare/56daf336535c91c25babe53d50b13ce3411b92f6...a5a7fae1556e0e6555d31061f829f51d84d99f08) (2018-09-25)


### Changes

chore(core): Bump to 0.0.267 ([a5a7fae1](https://github.com/spinnaker/deck/commit/a5a7fae1556e0e6555d31061f829f51d84d99f08))  
refactor(core/modal): Use `formik` prop instead of spreading.   Simplify WizardPage props. ([a1d03e0e](https://github.com/spinnaker/deck/commit/a1d03e0eeb0ea25f87cf5deac644f4f5025c2b0d))  
refactor(core): Add form-field-loading css class for loaders in forms ([9093e202](https://github.com/spinnaker/deck/commit/9093e202198c953308769dff92e16e66ffec5eb7))  
fix(core/deploy): Fix whitespace in platform health override ([e9b20c80](https://github.com/spinnaker/deck/commit/e9b20c800a3e2162bd96f323b073efb5606bb56e))  
feat(core/wizard): Support a note section at the bottom of the wizard page ([a0c8d4e9](https://github.com/spinnaker/deck/commit/a0c8d4e94c045edcb249614cb1e7a5fd6ab94b63))  
feat(core/deploy): Make templateSelectionText optional ([ba744da9](https://github.com/spinnaker/deck/commit/ba744da940c9383d9357134396f09fef3328d674))  
fix(provider/google): Deploy custom archetype fixes. [#5771](https://github.com/spinnaker/deck/pull/5771) ([1e632639](https://github.com/spinnaker/deck/commit/1e632639afd6fd04409c0526cb5cf7434e971bf1))  
fix(artifacts): Creating new expected artifact doesn't save first time [#5768](https://github.com/spinnaker/deck/pull/5768) ([f6cf8ab7](https://github.com/spinnaker/deck/commit/f6cf8ab733548c19d40172cacec3eb25980a4a84))  
feat(core): allow accounts to be parameterized [#5766](https://github.com/spinnaker/deck/pull/5766) ([1bf60db4](https://github.com/spinnaker/deck/commit/1bf60db472167a9bd87197f4ac53739ddd3171b0))  
refactor(kubernetes): convert manifest wizard to react [#5764](https://github.com/spinnaker/deck/pull/5764) ([fbc72797](https://github.com/spinnaker/deck/commit/fbc72797717a22f30923a0e7e95c0507426ce687))  
refactor(core/task): Migrated task progress bar to react [#5753](https://github.com/spinnaker/deck/pull/5753) ([e8081abd](https://github.com/spinnaker/deck/commit/e8081abde4e7ce09259e51321920626acc124cc6))  
fix(core): Projects calls interval increased to 3min from 30s [#5762](https://github.com/spinnaker/deck/pull/5762) ([785a26fe](https://github.com/spinnaker/deck/commit/785a26fe583549d9920b45ecc291e3a958aa59ab))  
fix(artifacts): use default artifact kind before match artifact kind [#5763](https://github.com/spinnaker/deck/pull/5763) ([0cd6845d](https://github.com/spinnaker/deck/commit/0cd6845d6c49bc26948ce65b98b7990249a12578))  
feat(kubernetes): allow a copy from running infrastructure into deploy manifest stage [#5751](https://github.com/spinnaker/deck/pull/5751) ([a8ec3c11](https://github.com/spinnaker/deck/commit/a8ec3c11c72e89ee476515c3d973bbfa39a4ea0b))  



## [0.0.266](https://www.github.com/spinnaker/deck/compare/35f2506501f053dbaa9fda15b98b3063b313916c...56daf336535c91c25babe53d50b13ce3411b92f6) (2018-09-19)


### Changes

chore(core/amazon): bump core to 0.0.266, amazon to 0.0.121 [#5760](https://github.com/spinnaker/deck/pull/5760) ([56daf336](https://github.com/spinnaker/deck/commit/56daf336535c91c25babe53d50b13ce3411b92f6))  
fix(core/application): Use relative import of ICluster [#5759](https://github.com/spinnaker/deck/pull/5759) ([a4975b87](https://github.com/spinnaker/deck/commit/a4975b8705d7ed234438600f54e3539017158bcc))  
refactor(formik): Use TSX generics to render Formik components ([17dc8d84](https://github.com/spinnaker/deck/commit/17dc8d84ff4ceec9376109db25a76b95819e122b))  
chore(prettier): Just Update Prettier™ [#5754](https://github.com/spinnaker/deck/pull/5754) ([709f30f6](https://github.com/spinnaker/deck/commit/709f30f6eff0c8862cb8736465e4fd152abd693c))  
feat(kayenta): show image sources [#5749](https://github.com/spinnaker/deck/pull/5749) ([ffb7a8bb](https://github.com/spinnaker/deck/commit/ffb7a8bbf2fc42f110baef75b0860ae0d92ef447))  
chore(artifacts): remove angular expected artifact selector [#5748](https://github.com/spinnaker/deck/pull/5748) ([0e576ddd](https://github.com/spinnaker/deck/commit/0e576ddd03752f2410621793c58924daf336f9a3))  
fix(kayenta): fix editing canary + baseline server groups [#5747](https://github.com/spinnaker/deck/pull/5747) ([1e98bf77](https://github.com/spinnaker/deck/commit/1e98bf77cca9fed59e8d51f0b8e15592dde0802c))  
feat(artifacts): default artifact type auto selected from match artifact [#5746](https://github.com/spinnaker/deck/pull/5746) ([d7e8ac91](https://github.com/spinnaker/deck/commit/d7e8ac916ef4bd417f9fb8d9829dd2d312ab49a3))  



## [0.0.264](https://www.github.com/spinnaker/deck/compare/0a0f71f5c14ab193b1b07c5addc2857a348573a3...35f2506501f053dbaa9fda15b98b3063b313916c) (2018-09-15)


### Changes

chore(core): Bump to 0.0.264 [#5745](https://github.com/spinnaker/deck/pull/5745) ([35f25065](https://github.com/spinnaker/deck/commit/35f2506501f053dbaa9fda15b98b3063b313916c))  
fix(amazon/instance): Fix standalone instance view [#5744](https://github.com/spinnaker/deck/pull/5744) ([e22c5c60](https://github.com/spinnaker/deck/commit/e22c5c6078f3d4825c0109f23ecaf049e5e2d60b))  
doc(core/presentation): Add docs for Forms: Input, Layout, Field components [#5741](https://github.com/spinnaker/deck/pull/5741) ([bbbb7629](https://github.com/spinnaker/deck/commit/bbbb7629474ed7419ad12540e56b1f7b4ef987ff))  
feat(kubernetes): bake manifest artifacts inline editor [#5740](https://github.com/spinnaker/deck/pull/5740) ([3e0065d9](https://github.com/spinnaker/deck/commit/3e0065d9d91a52f9d157cb6102d83de9e591ef41))  
 fix(ux): hover flicker on cluster instances [#5738](https://github.com/spinnaker/deck/pull/5738) ([50d7705f](https://github.com/spinnaker/deck/commit/50d7705f2edafe1af6361a9dc198d6d07aa92485))  
refactor(core/application): Migrate Application Data Sources to Rx streams [#5720](https://github.com/spinnaker/deck/pull/5720) ([93f9f1d5](https://github.com/spinnaker/deck/commit/93f9f1d59617b3121d6cb75b07608d18d3c3c77d))  
refactor(core/application): Rename createApplication to createApplicationForTests [#5737](https://github.com/spinnaker/deck/pull/5737) ([536e42b4](https://github.com/spinnaker/deck/commit/536e42b42bced4c1d4dfc9229813931d885ec80e))  
feat(artifacts): add inline artifact editor for appengine artifacts [#5735](https://github.com/spinnaker/deck/pull/5735) ([632f4a43](https://github.com/spinnaker/deck/commit/632f4a43603b3429bdf2feca15a0ebd9d27151a2))  
refactor(core/application): Add strong typing to ApplicationModelBuilder.createApplication() [#5732](https://github.com/spinnaker/deck/pull/5732) ([95c3420e](https://github.com/spinnaker/deck/commit/95c3420e9d569b45bf0d65ab4ec9f623328a0c9a))  
feat(google): add inline artifact editor to google deploy stage [#5731](https://github.com/spinnaker/deck/pull/5731) ([c5161cd4](https://github.com/spinnaker/deck/commit/c5161cd432e836963fd452224a78fdbc5ee5dbbc))  
fix(core/serverGroup): Guard for existence of callbacks [#5730](https://github.com/spinnaker/deck/pull/5730) ([1ac6eead](https://github.com/spinnaker/deck/commit/1ac6eead0b2b1c5440ecf2e40bc94e22f2f34b38))  
test(*): Switch karma reporter to super-dots and add mocha style error reporter [#5729](https://github.com/spinnaker/deck/pull/5729) ([d22de4a3](https://github.com/spinnaker/deck/commit/d22de4a377fa77f739cb8f81ecf5ea17055d3997))  
refactor(core): All region-select-field to use RegionSelectField.tsx [#5726](https://github.com/spinnaker/deck/pull/5726) ([9d4499d8](https://github.com/spinnaker/deck/commit/9d4499d8db2266dd6f6fbeae0a39dee08c0acaca))  
Revert "refactor(core/entityTag): Remove dataUpdated() call from DataSource onLoad" ([36527aea](https://github.com/spinnaker/deck/commit/36527aeaf996bf5c90e7afcef50b378e4d44a85a))  
feat(api): reject invalid content API responses with message [#5725](https://github.com/spinnaker/deck/pull/5725) ([d5762f77](https://github.com/spinnaker/deck/commit/d5762f7756f8427747cdceb84a33e0fedeaa2df6))  
feat(kubernetes): add inline artifact editor to patch manifest stage [#5717](https://github.com/spinnaker/deck/pull/5717) ([5e1e08e6](https://github.com/spinnaker/deck/commit/5e1e08e6f9cddd8529b55df30867a17bb3b0afde))  
refactor(core/entityTag): Remove dataUpdated() call from DataSource onLoad I verified that no current afterLoad() implementation mutates the current data source's data[], therefore calling dataUpdated() should be unnecessary. ([29ea9adc](https://github.com/spinnaker/deck/commit/29ea9adc7a989c828d07a8ccf11d92424bc96e91))  
fix(pipeline): Fixed missing MPT icon for pipelines with no executions [#5709](https://github.com/spinnaker/deck/pull/5709) ([e5083a59](https://github.com/spinnaker/deck/commit/e5083a59e3dd739ee0feeda90b08cfe8b3ed57ce))  
fix(trigger): add labels for gitlab trigger fields [#5708](https://github.com/spinnaker/deck/pull/5708) ([a25ef08f](https://github.com/spinnaker/deck/commit/a25ef08fe0ce3bf371da3e3db5bb3c0f01cc0daf))  
feat(kubernetes): use inline artifact editor for deploy manifest artifact [#5707](https://github.com/spinnaker/deck/pull/5707) ([d6bc248a](https://github.com/spinnaker/deck/commit/d6bc248a8a0bf0e618831c4568c58ddaab0a54b1))  
fix(pipeline/deploy): Fixed missing tab by reevaluating scope after initialization [#5703](https://github.com/spinnaker/deck/pull/5703) ([4d387699](https://github.com/spinnaker/deck/commit/4d3876990da518f85b6b1b9b77d2a71ce9fea0b7))  
fix(artifacts): appease tslint [#5702](https://github.com/spinnaker/deck/pull/5702) ([19bfd02f](https://github.com/spinnaker/deck/commit/19bfd02f0ea20f6753d6d191b3944a7417d236da))  
feat(artifacts): add expected artifact selector with option to create new artifacts [#5701](https://github.com/spinnaker/deck/pull/5701) ([416773dd](https://github.com/spinnaker/deck/commit/416773dd3b9e0051172125010f4998fa20be8816))  



## [0.0.263](https://www.github.com/spinnaker/deck/compare/3431d4a5df30a00d057576e6ef900eca07a53600...0a0f71f5c14ab193b1b07c5addc2857a348573a3) (2018-09-05)


### Changes

chore(core): Bump to 0.0.263 ([0a0f71f5](https://github.com/spinnaker/deck/commit/0a0f71f5c14ab193b1b07c5addc2857a348573a3))  
feat(artifacts): add inline artifact editor [#5692](https://github.com/spinnaker/deck/pull/5692) ([62d51324](https://github.com/spinnaker/deck/commit/62d51324864ec614dc9c9cc3240093dd99852dad))  
refactor(core/task): Simplify task monitoring in React Modals Some other minor modal related bug fixes ([94c30e04](https://github.com/spinnaker/deck/commit/94c30e04a797d83834a1a074149a73f886ebfcc1))  
feat(artifacts): add a select box for choosing an artifact source [#5690](https://github.com/spinnaker/deck/pull/5690) ([305ced10](https://github.com/spinnaker/deck/commit/305ced103726908e753d41dbb998c002861717ad))  
feat(artifacts): add an artifact kind select box [#5689](https://github.com/spinnaker/deck/pull/5689) ([82c96b22](https://github.com/spinnaker/deck/commit/82c96b2218a330481cb65dafdf78c12207500c19))  
feat(artifacts): add select box for artifact accounts [#5691](https://github.com/spinnaker/deck/pull/5691) ([20c721c3](https://github.com/spinnaker/deck/commit/20c721c3baa6bcbf03ac101930ab7cf9f74830a1))  
feat(artifacts): add icon helper and use stage config field where possible [#5687](https://github.com/spinnaker/deck/pull/5687) ([97a06a1c](https://github.com/spinnaker/deck/commit/97a06a1c0492abd0a05fbea397ca0d5429f1ec0b))  
feat(artifacts): provide helpers for working with sources of artifacts [#5686](https://github.com/spinnaker/deck/pull/5686) ([8fc3a4ec](https://github.com/spinnaker/deck/commit/8fc3a4ecad5ad80f6bcf57dbe77e722bf104e8ce))  
refactor(core/reactShims): Remove spread-resolves-objects-to-props router shim now that `@uirouter/react` does this natively [#5685](https://github.com/spinnaker/deck/pull/5685) ([9c9c38f4](https://github.com/spinnaker/deck/commit/9c9c38f44df28bae36a40e0a5f241fff70d2becb))  
fix(core/pipeline): Add error message to ApplySourceServerGroupCapacityDetails stage [#5684](https://github.com/spinnaker/deck/pull/5684) ([b228a0e6](https://github.com/spinnaker/deck/commit/b228a0e601c8ec554677e716dd76860219b35b4f))  
fix(core/search): Trim whitespace for global search [#5680](https://github.com/spinnaker/deck/pull/5680) ([1edd417f](https://github.com/spinnaker/deck/commit/1edd417f73be5a0288522f0108c53a1ed3104306))  
fix(core/projects): remove duplicative home.projects.project.** substates [#5682](https://github.com/spinnaker/deck/pull/5682) ([7edfe15b](https://github.com/spinnaker/deck/commit/7edfe15b5f2b73b9d7d78aefcaf88f969c4dd6d1))  
fix(core/bootstrap): Remove vis=true/vis=false from URL after toggling visualizer [#5679](https://github.com/spinnaker/deck/pull/5679) ([892e4450](https://github.com/spinnaker/deck/commit/892e4450d416d08b2014b18fd8583e866e1e799d))  



## [0.0.262](https://www.github.com/spinnaker/deck/compare/2368003c135b423a1993075cf74cffe2979d4a9f...3431d4a5df30a00d057576e6ef900eca07a53600) (2018-08-28)


### Changes

chore(core,amazon): Bump core to 0.0.262, amazon to 0.0.117 [#5677](https://github.com/spinnaker/deck/pull/5677) ([3431d4a5](https://github.com/spinnaker/deck/commit/3431d4a5df30a00d057576e6ef900eca07a53600))  
fix(amazon): Security Group cloning did not refresh fields with changes to region/account/vpc [#5647](https://github.com/spinnaker/deck/pull/5647) ([0a58daed](https://github.com/spinnaker/deck/commit/0a58daed3e79e78823542950d7e64caf3005dd27))  
fix(pipeline): fix invisible parameter when default is not in options [#5673](https://github.com/spinnaker/deck/pull/5673) ([ba70bcd7](https://github.com/spinnaker/deck/commit/ba70bcd7cba2e92afc80a8a2469dfad6f09e4358))  
fix(core/notifications): Render negative TTL correctly in ephemeral server group popover [#5676](https://github.com/spinnaker/deck/pull/5676) ([2d3ee660](https://github.com/spinnaker/deck/commit/2d3ee66012e782e91bfe444a0ce4add5e7e88d5a))  
fix(ux): deploy stage table layout ([6262d009](https://github.com/spinnaker/deck/commit/6262d009f0e64c3ba2c55ef6b8a4f7f230ba2c52))  
fix(core): fixes server group manager reload [#5661](https://github.com/spinnaker/deck/pull/5661) ([65d68cf2](https://github.com/spinnaker/deck/commit/65d68cf2193f0df98eebb727941e1c149d90c3ef))  
fix(artifacts): Fix artifact in execution history [#5656](https://github.com/spinnaker/deck/pull/5656) ([7dcf8c84](https://github.com/spinnaker/deck/commit/7dcf8c84c1bf4deca67a3fea8b9a4e6f1252a7d9))  



## [0.0.261](https://www.github.com/spinnaker/deck/compare/f9259f7502c1967a5d94449d8760687861368b3b...2368003c135b423a1993075cf74cffe2979d4a9f) (2018-08-23)


### Changes

chore(core): Bump to 0.0.261 ([2368003c](https://github.com/spinnaker/deck/commit/2368003c135b423a1993075cf74cffe2979d4a9f))  
feat(artifacts): add react implementations of artifact editors [#5652](https://github.com/spinnaker/deck/pull/5652) ([714e8b3a](https://github.com/spinnaker/deck/commit/714e8b3a63c97966e974659cc5d488a6cf2fb225))  
feat(titus): Add titus ui endpoint to the instance object ([75592564](https://github.com/spinnaker/deck/commit/75592564ae4679d60e2d48ebc27484a9c573c29e))  
feat(core): Support  different instance link sections by cloud provider ([b17a8f3e](https://github.com/spinnaker/deck/commit/b17a8f3e11610ef43da9c350d59848d191d4627e))  
fix(help): update help text for force-rebake option [#5651](https://github.com/spinnaker/deck/pull/5651) ([8a270526](https://github.com/spinnaker/deck/commit/8a270526d5b02fd3bec93d81e816448267dcc36f))  
fix(kubernetes): fix scrolling behavior for deployments in cluster view [#5649](https://github.com/spinnaker/deck/pull/5649) ([30ca7ed8](https://github.com/spinnaker/deck/commit/30ca7ed801cc8227afa1884ee5fa606173f7a264))  
feat(artifacts): move artifact creation into central service [#5648](https://github.com/spinnaker/deck/pull/5648) ([273d719b](https://github.com/spinnaker/deck/commit/273d719b1a58f0cf2073925434afc68ce938b884))  
fix(core): Long app names hide and make refresh unusable [#5640](https://github.com/spinnaker/deck/pull/5640) ([d1ff0aaa](https://github.com/spinnaker/deck/commit/d1ff0aaa7c898dbf45fb7cda9c345838a63f6eea))  



## [0.0.260](https://www.github.com/spinnaker/deck/compare/57f5082e3132c7d38c137587feb2561cbb54de15...f9259f7502c1967a5d94449d8760687861368b3b) (2018-08-21)


### Changes

chore(core): Bump to 0.0.260 [#5644](https://github.com/spinnaker/deck/pull/5644) ([f9259f75](https://github.com/spinnaker/deck/commit/f9259f7502c1967a5d94449d8760687861368b3b))  
feat(core): Add source field to pipeline interface [#5643](https://github.com/spinnaker/deck/pull/5643) ([805b7997](https://github.com/spinnaker/deck/commit/805b7997ceda640fa17a48afe41410eb2bf21436))  
fix(pipeline/create): Added workaround to address typeahead issue in react-select [#5637](https://github.com/spinnaker/deck/pull/5637) ([42ab548c](https://github.com/spinnaker/deck/commit/42ab548ca48bf05a30b925fda1e64f88cc98160d))  
fix(kayenta): fixes for kayenta stage [#5642](https://github.com/spinnaker/deck/pull/5642) ([d82d22e1](https://github.com/spinnaker/deck/commit/d82d22e15246750e614e6d20b88703e1ea7b9237))  
feat(kubernetes): new deployment representation in cluster view [#5617](https://github.com/spinnaker/deck/pull/5617) ([b97cb454](https://github.com/spinnaker/deck/commit/b97cb45459061f9301f5e59a7595b374788ed6bb))  
refactor(trigger): pull link and or text from ITrigger [#5635](https://github.com/spinnaker/deck/pull/5635) ([efdd728e](https://github.com/spinnaker/deck/commit/efdd728e166ba07ed4ad5e5d8ae0d05b9910b893))  
fix(core/mpt): Fix configure template checkbox initial state [#5634](https://github.com/spinnaker/deck/pull/5634) ([171f3b0e](https://github.com/spinnaker/deck/commit/171f3b0ea27f2ff8eb585bfa85a7bdb8c8c1affa))  
fix(trigger/webhook): fix runas user [#5630](https://github.com/spinnaker/deck/pull/5630) ([b63ae994](https://github.com/spinnaker/deck/commit/b63ae9949957a1929fd5703aa2bf30f4572f460b))  
feat(appengine): filter container image artifacts to just docker images [#5629](https://github.com/spinnaker/deck/pull/5629) ([7ff3c018](https://github.com/spinnaker/deck/commit/7ff3c0181d6a7fd027a094172b1750b0d199981c))  
fix(locking): updated LockFailureException details [#5619](https://github.com/spinnaker/deck/pull/5619) ([8ccdb515](https://github.com/spinnaker/deck/commit/8ccdb515792c062f169b462e0fd742fd41b4afbb))  
fix(pipeline): Fixed missing triggers/parameters/artifacts when inherited from template [#5624](https://github.com/spinnaker/deck/pull/5624) ([91cb04c3](https://github.com/spinnaker/deck/commit/91cb04c3478eef327489579183f3e679b7140860))  
fix(artifacts): expected artifacts can be null-ish [#5625](https://github.com/spinnaker/deck/pull/5625) ([a0366b44](https://github.com/spinnaker/deck/commit/a0366b44950efa74c38b19936f62548ef710bc4f))  



## [0.0.259](https://www.github.com/spinnaker/deck/compare/975bb49f2bd97e884e84e441bf67815c1acde6b6...57f5082e3132c7d38c137587feb2561cbb54de15) (2018-08-15)


### Changes

chore(core): Bump to 0.0.259 [#5622](https://github.com/spinnaker/deck/pull/5622) ([57f5082e](https://github.com/spinnaker/deck/commit/57f5082e3132c7d38c137587feb2561cbb54de15))  
feat(kubernetes): filter artifact types that dont make sense for a manifest [#5621](https://github.com/spinnaker/deck/pull/5621) ([4716f558](https://github.com/spinnaker/deck/commit/4716f558caa8940e177ba36699b69f30ea135d97))  
fix(core/serverGroup): Make forced deploy template selection actually work [#5620](https://github.com/spinnaker/deck/pull/5620) ([2d83c5bf](https://github.com/spinnaker/deck/commit/2d83c5bf22e9beafabd4450f988bde460b95fa4d))  
feat(artifacts): filter set of available artifact kinds by configured artifact accounts [#5612](https://github.com/spinnaker/deck/pull/5612) ([5d9776e0](https://github.com/spinnaker/deck/commit/5d9776e05b461f59d063429d86009331a7745bfb))  
refactor(instances): Convert instance-load-balancer-health to react [#5604](https://github.com/spinnaker/deck/pull/5604) ([cf567bc5](https://github.com/spinnaker/deck/commit/cf567bc581e954dd638ff8f3a025b4aaa7914e7e))  



## [0.0.258](https://www.github.com/spinnaker/deck/compare/f1befd08e6c1946b936db5bd87d2fb94aecd2eef...975bb49f2bd97e884e84e441bf67815c1acde6b6) (2018-08-14)


### Changes

chore(core): Bump to 0.0.258 ([975bb49f](https://github.com/spinnaker/deck/commit/975bb49f2bd97e884e84e441bf67815c1acde6b6))  
fix(loadBalancer): Use correct provider for titus server groups/instances [#5613](https://github.com/spinnaker/deck/pull/5613) ([060ad9cc](https://github.com/spinnaker/deck/commit/060ad9cc0eed5cf389d2923436f02911a2a25b6a))  
fix(core/executions): Fix rerun button when grouped by anything other than pipeline [#5607](https://github.com/spinnaker/deck/pull/5607) ([8358bb78](https://github.com/spinnaker/deck/commit/8358bb78cdc536d99aebeb4913cab6f2e5c825c3))  
feat(provider/kubernetes): hide artifact account selector when only one suitable account is available [#5598](https://github.com/spinnaker/deck/pull/5598) ([608267d5](https://github.com/spinnaker/deck/commit/608267d5e1c54f1a50fe481edd0096230990ded6))  



## [0.0.256](https://www.github.com/spinnaker/deck/compare/c4daa47d9f36f2f7e7de8561705848e5a0145772...f1befd08e6c1946b936db5bd87d2fb94aecd2eef) (2018-08-13)


### Changes

chore(core): Bump to 0.0.256 ([f1befd08](https://github.com/spinnaker/deck/commit/f1befd08e6c1946b936db5bd87d2fb94aecd2eef))  
fix(core/overrideRegistry): Fix copying of static methods over to @Overridable High Order Component + React.forwardRef ([4df8db0e](https://github.com/spinnaker/deck/commit/4df8db0e17995b74c491d90f3d19df594a210e0b))  
feat(wercker): feature toggle for wercker stages [#5586](https://github.com/spinnaker/deck/pull/5586) ([9ddf0705](https://github.com/spinnaker/deck/commit/9ddf0705fd821f019879748f45d836ab7111b5cc))  
refactor(core): Convert subnet-tag to react [#5584](https://github.com/spinnaker/deck/pull/5584) ([759e6ff7](https://github.com/spinnaker/deck/commit/759e6ff722c627ad01facfb5d7365a04f0fbf920))  



## [0.0.255](https://www.github.com/spinnaker/deck/compare/952b29e219b8deb6c18e7828c4793392a952ec61...c4daa47d9f36f2f7e7de8561705848e5a0145772) (2018-08-10)


### Changes

chore(core): Bump to 0.0.255 ([c4daa47d](https://github.com/spinnaker/deck/commit/c4daa47d9f36f2f7e7de8561705848e5a0145772))  



## [0.0.254](https://www.github.com/spinnaker/deck/compare/975df5db78942b3ef985533b54f909feee900e9a...952b29e219b8deb6c18e7828c4793392a952ec61) (2018-08-10)


### Changes

chore(core): bump core to 254 ([952b29e2](https://github.com/spinnaker/deck/commit/952b29e219b8deb6c18e7828c4793392a952ec61))  
fix(core/search): remove unneeded "see more" li elements ([ee6e9d7b](https://github.com/spinnaker/deck/commit/ee6e9d7b5d4ebb4addcdbcf904a6953b3563f22c))  
fix(amazon/deploy): Edit deployment cluster button did not work [#5580](https://github.com/spinnaker/deck/pull/5580) ([235e126c](https://github.com/spinnaker/deck/commit/235e126cc63016b69cd13bfeb084e6d19e75c499))  
feat(core/search): Add "show all" links to each category [#5577](https://github.com/spinnaker/deck/pull/5577) ([eccecc38](https://github.com/spinnaker/deck/commit/eccecc38dadef1e5e3914f9c03bfb92b7b0586be))  
refactor(search): Update BasicCell to allow children [#5576](https://github.com/spinnaker/deck/pull/5576) ([5ada07b7](https://github.com/spinnaker/deck/commit/5ada07b792d2e9012182520b823c1df67124da1a))  
fix(core/search): When calling a faceted backend search such as stack:int, tell clouddriver not to worry about minimum query length of 3 ([953140cf](https://github.com/spinnaker/deck/commit/953140cfa6d4345d49b74e659427a4d4051d6ac6))  



## [0.0.253](https://www.github.com/spinnaker/deck/compare/ed8117c040e17f3551087bba11a5d4ed8fde6333...975df5db78942b3ef985533b54f909feee900e9a) (2018-08-08)


### Changes

chore(core): bump package to 253 ([975df5db](https://github.com/spinnaker/deck/commit/975df5db78942b3ef985533b54f909feee900e9a))  
fix(core/overrideRegistry): Fix @Overridable + Stateless Functional Component - Upgrade uirouter/react-hybrid - Pin uirouter libs using yarn 'resolutions' - Switch to React 16.3 new context API ([46ddf34e](https://github.com/spinnaker/deck/commit/46ddf34e002ad0348e1d9395402f07e592420520))  
Merge branch 'master' into parse-docker-default-artifacts ([f4d553a0](https://github.com/spinnaker/deck/commit/f4d553a059395e8e87048c9c5de67154f4c648a5))  
fix(artifacts): Handle Docker registries with port specifications ([0c666bf5](https://github.com/spinnaker/deck/commit/0c666bf5f03b7b015571ad4cfe045f21aae10564))  



## [0.0.252](https://www.github.com/spinnaker/deck/compare/bf2d22525ef6abeab30be6bd5c160e1b4024ad89...ed8117c040e17f3551087bba11a5d4ed8fde6333) (2018-08-06)


### Changes

chore(core): Bump package to 0.0.251' [#5569](https://github.com/spinnaker/deck/pull/5569) ([ed8117c0](https://github.com/spinnaker/deck/commit/ed8117c040e17f3551087bba11a5d4ed8fde6333))  
feat(core): Export './AuthenticationInitializer' [#5568](https://github.com/spinnaker/deck/pull/5568) ([b114dc46](https://github.com/spinnaker/deck/commit/b114dc4663506a8c6f2969b01001a782ef71c169))  
refactor(core): Convert CancelModal to react [#5564](https://github.com/spinnaker/deck/pull/5564) ([e33ada56](https://github.com/spinnaker/deck/commit/e33ada5631a5a6f632acab1fe72a51e844ccadf2))  
chore(core): Disable tests for CreatePipelineModal until enzyme updates ([cb15cc47](https://github.com/spinnaker/deck/commit/cb15cc4749e9b3a3a4cf854b6f42daf1613a4525))  
refactor(core): Support react deploy dialogs ([6996c710](https://github.com/spinnaker/deck/commit/6996c7102b59271950ed6626c65d2b66924e9b52))  
refactor(core): Add imageReader, instanceTypeService, and providerServiceDelegate to react injector ([84907630](https://github.com/spinnaker/deck/commit/849076305a8d88869f0cbae12cb53832417da415))  
chore(core): Improve server group command view state interface ([95df35d2](https://github.com/spinnaker/deck/commit/95df35d22ba7c7efbf6d1fa8afa9c72bbb253260))  
refactor(core): Remove unused addWatches from clone server group ([17af347d](https://github.com/spinnaker/deck/commit/17af347dd5ede045f8ac407540888d57dc774032))  
refactor(core): Remove ngreact instance list ([236102f4](https://github.com/spinnaker/deck/commit/236102f4789e1e8c94303f0b944580a5821cbe20))  
chore(core): Export some more serverGroup types for use by other modules ([1d896d44](https://github.com/spinnaker/deck/commit/1d896d440839b78f79528d6957a5b2e0769c1837))  
feat(core/wizard): Show a tooltip of errors for each sections title ([590ff013](https://github.com/spinnaker/deck/commit/590ff013ab1f856f75fe596736c82266ef5a9bc1))  
refactor(core): Create react version of MapEditor ([5402df1e](https://github.com/spinnaker/deck/commit/5402df1e6798c6f7898801aef37961b89df114ec))  
refactor(core): Create react version of platform health override ([06cd9d0a](https://github.com/spinnaker/deck/commit/06cd9d0ad7cf25855f0107ab09f56cb4820509ec))  
refactor(core): Create react version of TaskReason ([94f2012c](https://github.com/spinnaker/deck/commit/94f2012c562054a507ce34cc742fc7d7808e2270))  
refactor(core): Convert instance archetype selector to a component ([9373259d](https://github.com/spinnaker/deck/commit/9373259ddb601c41a1c8482843521ec2c694aa54))  
refactor(core): Convert instance type selector to a component ([eb7d93de](https://github.com/spinnaker/deck/commit/eb7d93de59009d8a29578c88132fb1060045b774))  
refactor(core/overrides): Since overridable now has a forwardedRef, only support ComponentClass ([e2a8e97c](https://github.com/spinnaker/deck/commit/e2a8e97c4eef0c22d10e2787cd44d97b57f4989d))  
refactor(core): Create a react version of DeployInitializer ([d124867d](https://github.com/spinnaker/deck/commit/d124867dc04799eea7157322413ee248978c13db))  
fix(core): Make sure deploy initializer has a parentState ([fef72523](https://github.com/spinnaker/deck/commit/fef72523bdb1152290bc9351f65529080e9106f4))  
feat(tagging): Select which upstream stages to include in image search ([77bd90fc](https://github.com/spinnaker/deck/commit/77bd90fc94745bed44fddfd4b36edee496b2db35))  
feat(checklist): Support for using Map for key/value pairs in checklist ([32f79c1f](https://github.com/spinnaker/deck/commit/32f79c1f0d209afc1963fdafbac1c444fb81ed45))  
feat(artifacts): alphabetically sort artifact types by label [#5563](https://github.com/spinnaker/deck/pull/5563) ([1ff6ff85](https://github.com/spinnaker/deck/commit/1ff6ff857be4acad76c344a66397f3115ef2ad48))  
fix(authz): Fix help text for pipeline permissions. [#5562](https://github.com/spinnaker/deck/pull/5562) ([c7be4979](https://github.com/spinnaker/deck/commit/c7be4979205193bbbcfdc4345a3d5b6df5e9be59))  



## [0.0.250](https://www.github.com/spinnaker/deck/compare/ca0603a7e27429ba43c99c6669ae32884e1d9d4c...bf2d22525ef6abeab30be6bd5c160e1b4024ad89) (2018-08-02)


### Changes

chore(core): Bump package to 0.0.250 ([bf2d2252](https://github.com/spinnaker/deck/commit/bf2d22525ef6abeab30be6bd5c160e1b4024ad89))  
 feat(amazon/loadBalancers): Support overriding OIDC client and add help text [#5558](https://github.com/spinnaker/deck/pull/5558) ([a3fc126c](https://github.com/spinnaker/deck/commit/a3fc126caa70bca8e864b5ecc0fe53d0c78b53dc))  
chore(core): Update typescript and tslint dependencies [#5557](https://github.com/spinnaker/deck/pull/5557) ([311bb09e](https://github.com/spinnaker/deck/commit/311bb09e6eec8f99532439cee2fda2a66ea970c9))  



## [0.0.249](https://www.github.com/spinnaker/deck/compare/231476f998b2dbe710f88c63a09c31649fad8510...ca0603a7e27429ba43c99c6669ae32884e1d9d4c) (2018-08-01)


### Changes

chore(core): bump core package to 0.0.249 [#5556](https://github.com/spinnaker/deck/pull/5556) ([ca0603a7](https://github.com/spinnaker/deck/commit/ca0603a7e27429ba43c99c6669ae32884e1d9d4c))  
docs(artifacts): GitLab example URL typo [#5525](https://github.com/spinnaker/deck/pull/5525) ([672e47f7](https://github.com/spinnaker/deck/commit/672e47f7893eca8e68b120d960dab5451212502b))  
feat(core): Adding null check to protect against undefined config [#5546](https://github.com/spinnaker/deck/pull/5546) ([eeeb1f17](https://github.com/spinnaker/deck/commit/eeeb1f17604e58b182479485821d989376bd3e8b))  
feat(wercker): add wercker trigger and stage (#5519) [#5535](https://github.com/spinnaker/deck/pull/5535) ([20209194](https://github.com/spinnaker/deck/commit/20209194195626ac9faa72205133528da4b3bc77))  
feat(artifacts): Kubernetes V1 provider removes deleted artifacts [#5544](https://github.com/spinnaker/deck/pull/5544) ([3ace82a2](https://github.com/spinnaker/deck/commit/3ace82a2cb6d730faffc3c58c97c2d1df41338fe))  
refactor(*): Add server group configuration command to all configured command functions ([4d23c971](https://github.com/spinnaker/deck/commit/4d23c971e1d3eb5a234d3315fd166c9d37059413))  



## [0.0.248](https://www.github.com/spinnaker/deck/compare/b0a35f2d57e74de3ca6e62006fd380676d1855fd...231476f998b2dbe710f88c63a09c31649fad8510) (2018-07-23)


### Changes

chore(core): bump core package to 0.0.248 [#5543](https://github.com/spinnaker/deck/pull/5543) ([231476f9](https://github.com/spinnaker/deck/commit/231476f998b2dbe710f88c63a09c31649fad8510))  
refactor(core): Create react wrapper around deployment strategy selector ([f6567534](https://github.com/spinnaker/deck/commit/f6567534e613d911d2e777189f3a97c8362f0884))  
chore(core): account select field supports strings ([0b7fd0ae](https://github.com/spinnaker/deck/commit/0b7fd0ae7850ed921c7e6a1ae3679856e547bafe))  
fix(core): Support WizardModal not having any hidden sections ([35642d35](https://github.com/spinnaker/deck/commit/35642d358becaa58c2382c87ecbc2d91643b6fa0))  
fix(core): Modal close button sometimes would submit the form ([3ec5a7a1](https://github.com/spinnaker/deck/commit/3ec5a7a1565b3578d7b07e4168ece04bcef102f0))  
refactor(core): Improve server group command interface ([95c96b58](https://github.com/spinnaker/deck/commit/95c96b5820a093f4301dcc89962384a836f9c30c))  
feat(authz/config): Add a pipeline roles section to pipeline config. [#5536](https://github.com/spinnaker/deck/pull/5536) ([ab33610e](https://github.com/spinnaker/deck/commit/ab33610ec78dc9635fecfb7a731f2949af81412a))  
refactor(core): Make WizardModal usable in ReactModal ([882db703](https://github.com/spinnaker/deck/commit/882db7039048f0bfecad91267bcc759db222ec48))  
fix(artifacts): Remove kubernetes import from core [#5540](https://github.com/spinnaker/deck/pull/5540) ([0c105323](https://github.com/spinnaker/deck/commit/0c1053230ca87afe62412d6e752b22a3a88e50b6))  



## [0.0.247](https://www.github.com/spinnaker/deck/compare/f5967a5aebf046a3fadb8ae819f934096d8c0c6c...b0a35f2d57e74de3ca6e62006fd380676d1855fd) (2018-07-23)


### Changes

Bump core and amazon [#5531](https://github.com/spinnaker/deck/pull/5531) ([b0a35f2d](https://github.com/spinnaker/deck/commit/b0a35f2d57e74de3ca6e62006fd380676d1855fd))  
feat(kubernetes): Add consumed artifacts to Kubernetes V1 provider [#5538](https://github.com/spinnaker/deck/pull/5538) ([a99f527c](https://github.com/spinnaker/deck/commit/a99f527c161610f8966e5c66dfd42dadb8998b06))  
Revert "feat(wercker): add wercker trigger and stage (#5519)" [#5534](https://github.com/spinnaker/deck/pull/5534) ([9dac6bfb](https://github.com/spinnaker/deck/commit/9dac6bfb5062d8d155ef2262d7f803bff0774385))  
feat(wercker): add wercker trigger and stage [#5519](https://github.com/spinnaker/deck/pull/5519) ([12c36646](https://github.com/spinnaker/deck/commit/12c36646f97b25d918ba16418eddcc0aea4e6bfb))  
fix(core): Fix undefined on spel decorator [#5529](https://github.com/spinnaker/deck/pull/5529) ([9c21261a](https://github.com/spinnaker/deck/commit/9c21261abbbadad7266f18f0e522d48afa6e37b1))  
fix(core): Fix stage execution windows from undefined error [#5530](https://github.com/spinnaker/deck/pull/5530) ([c7290237](https://github.com/spinnaker/deck/commit/c7290237f9ebf615338c10c4b49e8b33d0b0f4fe))  
fix(core): Fix undefined when page navigator inits [#5528](https://github.com/spinnaker/deck/pull/5528) ([ce3a268f](https://github.com/spinnaker/deck/commit/ce3a268f536e645fde24dc35ff34ba8c41b2fdd5))  
feat(amazon/loadBalancer): Add confirmation if removing an existing oidc rule from an ALB [#5521](https://github.com/spinnaker/deck/pull/5521) ([8cec136d](https://github.com/spinnaker/deck/commit/8cec136d644273d1bf033a351e34d843ba6cdad5))  
fix(loading): Surface error connecting to gate on applications screen [#5515](https://github.com/spinnaker/deck/pull/5515) ([2736add8](https://github.com/spinnaker/deck/commit/2736add827c19d35e2d69a9d4838238850a13c9f))  



## [0.0.246](https://www.github.com/spinnaker/deck/compare/c60ec06b15ce20c61adfec5312d3ac2287b83cbe...f5967a5aebf046a3fadb8ae819f934096d8c0c6c) (2018-07-11)


### Changes

chore(core): Bump core to 0.0.246 [#5523](https://github.com/spinnaker/deck/pull/5523) ([f5967a5a](https://github.com/spinnaker/deck/commit/f5967a5aebf046a3fadb8ae819f934096d8c0c6c))  
fix(core/cluster): Do not scroll to bottom of clusters view when no cluster is selected [#5518](https://github.com/spinnaker/deck/pull/5518) ([2ee1f9d6](https://github.com/spinnaker/deck/commit/2ee1f9d682973c8ec01dfef60ae9e316952cb1d5))  
feat(instances): support name field on IInstance [#5506](https://github.com/spinnaker/deck/pull/5506) ([73240917](https://github.com/spinnaker/deck/commit/7324091773422cd65feb1f10529014237342c7d9))  



## [0.0.245](https://www.github.com/spinnaker/deck/compare/5f7099a646ec946b5b1eedcd38e1c607470c4d99...c60ec06b15ce20c61adfec5312d3ac2287b83cbe) (2018-07-06)


### Changes

chore(core): bump to 0.0.245 ([c60ec06b](https://github.com/spinnaker/deck/commit/c60ec06b15ce20c61adfec5312d3ac2287b83cbe))  
fix(docker): change link format for docker insight link ([f57a534d](https://github.com/spinnaker/deck/commit/f57a534da5361359d98baf4ac35e2ef544d6525a))  
fix(core): properties message for jenkins stage is wrong ([66da0a72](https://github.com/spinnaker/deck/commit/66da0a72ce526e5f69f37bd9e6d72bdf6355b7fb))  
fix(trigger/webhook): fix linting issue [#5510](https://github.com/spinnaker/deck/pull/5510) ([e137164b](https://github.com/spinnaker/deck/commit/e137164bfedcedf263a064facc3d4efae389f840))  
fix(trigger/webhook): add runAsUser to webhook [#5507](https://github.com/spinnaker/deck/pull/5507) ([9b546bc9](https://github.com/spinnaker/deck/commit/9b546bc98fd482ca5e799dffdde8bc433374f332))  



## [0.0.244](https://www.github.com/spinnaker/deck/compare/c608d7b2aadcc80febd703a61c6c94d484a58422...5f7099a646ec946b5b1eedcd38e1c607470c4d99) (2018-06-29)


### Changes

chore(core): bump to 0.0.244 ([5f7099a6](https://github.com/spinnaker/deck/commit/5f7099a646ec946b5b1eedcd38e1c607470c4d99))  
Merge branch 'master' into arch/fixWarnings ([d92be942](https://github.com/spinnaker/deck/commit/d92be9429aa93e4ec9016edb9a255d3ededcb3ff))  
fix(core): Warnings to publish core ([b7a9bd7b](https://github.com/spinnaker/deck/commit/b7a9bd7b49b2f0d4e5075b9029f7f68c7ec74828))  
feat(core): customizing maximum number of pipelines displayed [#5497](https://github.com/spinnaker/deck/pull/5497) ([75c4a7e8](https://github.com/spinnaker/deck/commit/75c4a7e895ed3f8ce3ea335905f5dc2350aaa012))  
fix(amazon/loadBalancer): Support order property in listener actions [#5495](https://github.com/spinnaker/deck/pull/5495) ([ca825e03](https://github.com/spinnaker/deck/commit/ca825e03edc55dfca283898feb3e127f318f9909))  
Merge branch 'master' into arch/bumpUpCore ([0c6adb69](https://github.com/spinnaker/deck/commit/0c6adb6987b6befb08f19b992223f87c690d57b0))  
feat(core): Adding option to fail pipeline on failed expressions [#5494](https://github.com/spinnaker/deck/pull/5494) ([b927e2d0](https://github.com/spinnaker/deck/commit/b927e2d0e5d60aee7b1b1b9391780e5e05ac2a07))  



## [0.0.243](https://www.github.com/spinnaker/deck/compare/e726ad50271a04060933d0e0edc447e1ee71dba6...c608d7b2aadcc80febd703a61c6c94d484a58422) (2018-06-28)


### Changes

feat(core): bump up package version to 0.0.243 ([c608d7b2](https://github.com/spinnaker/deck/commit/c608d7b2aadcc80febd703a61c6c94d484a58422))  
fix(core): Fixed cluster scroll jump ([3e305789](https://github.com/spinnaker/deck/commit/3e3057895327f476023d2520535ca1d72a56f20b))  
fix(amazon): Force user to ack removed load balancers before saving deploy config [#5485](https://github.com/spinnaker/deck/pull/5485) ([06a44949](https://github.com/spinnaker/deck/commit/06a4494978694ff7e63d874c554aa8c1bbd346b9))  
fix(pubsub): tooltip specifies constraint value is java regex [#5484](https://github.com/spinnaker/deck/pull/5484) ([677fbbc4](https://github.com/spinnaker/deck/commit/677fbbc4cb226916a6142cbb9d78370d47d5291f))  
feat(notification): Add support for Google Chat. [#5478](https://github.com/spinnaker/deck/pull/5478) ([0e984db5](https://github.com/spinnaker/deck/commit/0e984db58d5d41ca67f7b8ca98d6972977a021d2))  
fix(core): Should trim pipeline name on save [#5481](https://github.com/spinnaker/deck/pull/5481) ([7b077e6a](https://github.com/spinnaker/deck/commit/7b077e6ace0fe110f4189074f305dcf7eeb897c6))  



## [0.0.242](https://www.github.com/spinnaker/deck/compare/173ff37847087794a59cbba15786edb0a6bb43db...e726ad50271a04060933d0e0edc447e1ee71dba6) (2018-06-21)


### Changes

chore(core): Bump to 0.0.242 [#5476](https://github.com/spinnaker/deck/pull/5476) ([e726ad50](https://github.com/spinnaker/deck/commit/e726ad50271a04060933d0e0edc447e1ee71dba6))  
chore(build): remove console.log [#5475](https://github.com/spinnaker/deck/pull/5475) ([53bfd76b](https://github.com/spinnaker/deck/commit/53bfd76b7258488e3118375bdc782f7d17eb2e8e))  
fix(titus): Fix links to titus servergroups from amazon load balancer [#5474](https://github.com/spinnaker/deck/pull/5474) ([d866ad44](https://github.com/spinnaker/deck/commit/d866ad4496a449f79c99c833366fdafd84f435db))  
feat(pipeline): Support `runAsUser` for implicit pipeline triggers [#5473](https://github.com/spinnaker/deck/pull/5473) ([b0ef7105](https://github.com/spinnaker/deck/commit/b0ef7105fc05b58c4d7b9ede297de5b81112ce5a))  
feat(kubernetes): use kind mapping to determine details view [#5469](https://github.com/spinnaker/deck/pull/5469) ([d1c01ee1](https://github.com/spinnaker/deck/commit/d1c01ee115ff8c3ea958ffc1352b6100ec904ba8))  



## [0.0.241](https://www.github.com/spinnaker/deck/compare/85e32a8bf0191ba13e5a31df64a3e588e0184e9f...173ff37847087794a59cbba15786edb0a6bb43db) (2018-06-19)


### Changes

chore(core): bump package to 0.0.241 [#5468](https://github.com/spinnaker/deck/pull/5468) ([173ff378](https://github.com/spinnaker/deck/commit/173ff37847087794a59cbba15786edb0a6bb43db))  
fix(kubernetes): don't restrict runnningExecution spinner to deployManifest stages [#5466](https://github.com/spinnaker/deck/pull/5466) ([05b6d644](https://github.com/spinnaker/deck/commit/05b6d6448bcbb0473b0a1b09d9231a5f94773c48))  



## [0.0.240](https://www.github.com/spinnaker/deck/compare/13e152bc973404a806168f10854de0f8167885db...85e32a8bf0191ba13e5a31df64a3e588e0184e9f) (2018-06-18)


### Changes

chore(core): bump package to 0.0.240 [#5465](https://github.com/spinnaker/deck/pull/5465) ([85e32a8b](https://github.com/spinnaker/deck/commit/85e32a8bf0191ba13e5a31df64a3e588e0184e9f))  
feat(kubernetes): render ongoing deploy manifest executions in clusters tab [#5464](https://github.com/spinnaker/deck/pull/5464) ([979b82f4](https://github.com/spinnaker/deck/commit/979b82f4a07000203655e09ed3c3ac9da5631c1f))  
feat(provier/kubernetes): add artifact tab to bakeManifest executions [#5462](https://github.com/spinnaker/deck/pull/5462) ([d00be060](https://github.com/spinnaker/deck/commit/d00be0602bdee6038d102d2148e957262f09a849))  



## [0.0.239](https://www.github.com/spinnaker/deck/compare/5e327ad4c6e063cdce0344bd5de472498aef43a4...13e152bc973404a806168f10854de0f8167885db) (2018-06-15)


### Changes

chore(core): bump package to 0.0.239 [#5461](https://github.com/spinnaker/deck/pull/5461) ([13e152bc](https://github.com/spinnaker/deck/commit/13e152bc973404a806168f10854de0f8167885db))  
feat(core): feedback link [#5460](https://github.com/spinnaker/deck/pull/5460) ([6511d38e](https://github.com/spinnaker/deck/commit/6511d38e06b633bef86e9fc6b99789171425321e))  
feat(notification): add bearychat support [#5430](https://github.com/spinnaker/deck/pull/5430) ([bd56c087](https://github.com/spinnaker/deck/commit/bd56c087905fdb55ef1d8880ee60395ff6a1cc9a))  



## [0.0.238](https://www.github.com/spinnaker/deck/compare/0570c08ccd47b68afe15746bbe7a94f6ddc49ee3...5e327ad4c6e063cdce0344bd5de472498aef43a4) (2018-06-13)


### Changes

chore(core): Bump to 0.0.238 [#5453](https://github.com/spinnaker/deck/pull/5453) ([5e327ad4](https://github.com/spinnaker/deck/commit/5e327ad4c6e063cdce0344bd5de472498aef43a4))  
feat(google/iap): Refreshes IAP session after they expire. ([67d6f242](https://github.com/spinnaker/deck/commit/67d6f2425bbe13f89b0642bbd3c206f05160921d))  
fix(bake/manifest): fix passing namespace in helm bakery [#5456](https://github.com/spinnaker/deck/pull/5456) ([83c819a6](https://github.com/spinnaker/deck/commit/83c819a69f1b42239a15ebd3d3cc0b691ae272a7))  
fix(pipeline): Correctly handle saving pipeline templates [#5450](https://github.com/spinnaker/deck/pull/5450) ([6924cd67](https://github.com/spinnaker/deck/commit/6924cd67d8ce963ee9e883d4070dd6356eb00059))  
feat(core/presentation): use ValidationMessage for BasicLayout error/warning/preview [#5452](https://github.com/spinnaker/deck/pull/5452) ([d97c3c31](https://github.com/spinnaker/deck/commit/d97c3c3191620a482c05fb67709124d40e4be313))  
fix(core/presentation): BasicLayout: align the input and actions items [#5451](https://github.com/spinnaker/deck/pull/5451) ([70feb1f2](https://github.com/spinnaker/deck/commit/70feb1f233f4b28d026832a1ab581903931e47ab))  
fix(core/presentation): add className prop to SubmitButton.tsx, use html button (not bootstrap) [#5449](https://github.com/spinnaker/deck/pull/5449) ([485237ab](https://github.com/spinnaker/deck/commit/485237ab9c076cda8f6a04e15499fe76284cc989))  
fix(core/presentation): fix broken travis ([ead58ee1](https://github.com/spinnaker/deck/commit/ead58ee171f1f0a10ddbd5beb59b8c3484866836))  
feat(core/presentation): Add React components for Form Inputs/Layouts/Fields ([c3941708](https://github.com/spinnaker/deck/commit/c39417087b938dedc276eb55459c3572d05e9720))  
refactor(core/validation): Rename ValidationError to ValidationMessage, add 'type' prop ([0b619338](https://github.com/spinnaker/deck/commit/0b6193383880e20abd75d5e4e971e37c435e8352))  
fix(core/pipeline): Don't fail when checking Force Rebake without a trigger [#5445](https://github.com/spinnaker/deck/pull/5445) ([ab83c704](https://github.com/spinnaker/deck/commit/ab83c704e9a43439d85be084706810028b505c69))  
fix(core/application): Fix delete application modal hanging ([4616ded0](https://github.com/spinnaker/deck/commit/4616ded02eda5dca3c084c902cb9898584dee7df))  
fix(core/securityGroup): Fix links to titus server groups (in sidebar) from firewalls screen ([1fd477e3](https://github.com/spinnaker/deck/commit/1fd477e3dbc232e974c832c5c6046748ffccf93e))  
fix(pubsub): constraint alignment & help text [#5436](https://github.com/spinnaker/deck/pull/5436) ([ca091e97](https://github.com/spinnaker/deck/commit/ca091e9759cdd1d8de9aa3bc44682178171e88e3))  
fix(artifacts): only show question mark icon for custom artifacts [#5435](https://github.com/spinnaker/deck/pull/5435) ([9f56f671](https://github.com/spinnaker/deck/commit/9f56f6712f2ae7ab7cacbef051d4d0044fa51034))  



## [0.0.237](https://www.github.com/spinnaker/deck/compare/ac1cdc801eef4392495e36a7446ce77a1d2222f2...0570c08ccd47b68afe15746bbe7a94f6ddc49ee3) (2018-06-07)


### Changes

chore(core): Bump to 0.0.237 ([0570c08c](https://github.com/spinnaker/deck/commit/0570c08ccd47b68afe15746bbe7a94f6ddc49ee3))  
fix(core/cluster): Fix broken logic in task.matcher.ts ([4b7a2070](https://github.com/spinnaker/deck/commit/4b7a2070a55812d52d4b760efba73467df7aceb3))  



## [0.0.236](https://www.github.com/spinnaker/deck/compare/4ff604056837c944d04ef9d9fc1b52a78653ca9b...ac1cdc801eef4392495e36a7446ce77a1d2222f2) (2018-06-07)


### Changes

chore(core): Bump to 0.0.236 ([ac1cdc80](https://github.com/spinnaker/deck/commit/ac1cdc801eef4392495e36a7446ce77a1d2222f2))  
fix(core/cluster): check stage types both exact and lowercase for taskmatcher lookup [#5420](https://github.com/spinnaker/deck/pull/5420) ([952131e0](https://github.com/spinnaker/deck/commit/952131e0dfa7425f74c6b8b2305fe42073f3cd82))  
fix(core/pipeline): Make script stage "Path" field not required [#5428](https://github.com/spinnaker/deck/pull/5428) ([8de68eef](https://github.com/spinnaker/deck/commit/8de68eefdd6d40b8bdd27d01568c344adbcd50ad))  
feat(core/presentation): Make CollapsibleSection less style opinionated [#5427](https://github.com/spinnaker/deck/pull/5427) ([4e891fea](https://github.com/spinnaker/deck/commit/4e891fea096b56e0fd897d0a67a0bb5b2e804221))  
feat(bake/manifests): Add namespace support for Helm bakery [#5326](https://github.com/spinnaker/deck/pull/5326) ([b297eb0f](https://github.com/spinnaker/deck/commit/b297eb0fe19bf18dbc755f4a9990f9c1ab90c871))  
Add a new patch manifest stage for kubernetes V2 provider [#5417](https://github.com/spinnaker/deck/pull/5417) ([74ac6162](https://github.com/spinnaker/deck/commit/74ac6162a80ccae4b5baf2cc7bdb2c362c0f6c15))  
fix(kubernetes): server group manager button alignment [#5423](https://github.com/spinnaker/deck/pull/5423) ([160a7cf6](https://github.com/spinnaker/deck/commit/160a7cf6e37a54848e5cb55241c99f0a73245a72))  
fix(kubernetes): break line for each container image in server group pod [#5422](https://github.com/spinnaker/deck/pull/5422) ([8aa026ca](https://github.com/spinnaker/deck/commit/8aa026ca488884eb1b831e658880dfb2e8f9bfa7))  



## [0.0.235](https://www.github.com/spinnaker/deck/compare/c2ad636c49a257d1eee3aeb7b1dea56346b9e5c0...4ff604056837c944d04ef9d9fc1b52a78653ca9b) (2018-06-01)


### Changes

chore(core): Bump to 0.0.235 [#5419](https://github.com/spinnaker/deck/pull/5419) ([4ff60405](https://github.com/spinnaker/deck/commit/4ff604056837c944d04ef9d9fc1b52a78653ca9b))  
refactor(core/presentation): Add third 'modalProps' prop to ReactModal.show() [#5418](https://github.com/spinnaker/deck/pull/5418) ([337779eb](https://github.com/spinnaker/deck/commit/337779eb2c28a22ed6a91473e82d5480b43afdd1))  



## [0.0.234](https://www.github.com/spinnaker/deck/compare/ffd72217394970b9d8193dcdc05a934c05594cf1...c2ad636c49a257d1eee3aeb7b1dea56346b9e5c0) (2018-05-31)


### Changes

chore(core): bump package to 0.0.234 [#5415](https://github.com/spinnaker/deck/pull/5415) ([c2ad636c](https://github.com/spinnaker/deck/commit/c2ad636c49a257d1eee3aeb7b1dea56346b9e5c0))  
fix(core): Make force rebake checkbox actually work [#5416](https://github.com/spinnaker/deck/pull/5416) ([4b9b711e](https://github.com/spinnaker/deck/commit/4b9b711ec5f292ed15d7a048ded2743d1a82c61c))  
feat(artifacts/bitbucket): added bitbucket artifact [#5414](https://github.com/spinnaker/deck/pull/5414) ([742c5cde](https://github.com/spinnaker/deck/commit/742c5cde0dda4b9beb6982b6d477feb840111b75))  
fix(core): fix check on showAllInstances for cluster height calculations [#5413](https://github.com/spinnaker/deck/pull/5413) ([d1c91def](https://github.com/spinnaker/deck/commit/d1c91def68e7cdb878a45b79d0474d6193e19745))  



## [0.0.232](https://www.github.com/spinnaker/deck/compare/ee89420398e2a7a42e199ba89c7ac730f494be95...ffd72217394970b9d8193dcdc05a934c05594cf1) (2018-05-31)


### Changes

chore(core): bump package to 0.0.232 [#5411](https://github.com/spinnaker/deck/pull/5411) ([ffd72217](https://github.com/spinnaker/deck/commit/ffd72217394970b9d8193dcdc05a934c05594cf1))  
fix(core): recompute cluster pod heights on filter changes [#5410](https://github.com/spinnaker/deck/pull/5410) ([225c351b](https://github.com/spinnaker/deck/commit/225c351bc1f0b1ddbe11a02a4ec84a112643e37f))  
fix(core): improve pipeline graph mouse event handling for Chrome 67 [#5409](https://github.com/spinnaker/deck/pull/5409) ([71b8b695](https://github.com/spinnaker/deck/commit/71b8b6959c2c964e76485dd9a8ea795ae054524f))  
fix(provider/kubernetes): artifact tab columns compressed in small windows [#5406](https://github.com/spinnaker/deck/pull/5406) ([0a5b4e26](https://github.com/spinnaker/deck/commit/0a5b4e2696b39cfdd430a49e63ab2c1b158292dc))  
feat(core/pipeline): Make CreatePipelineModal overridable [#5395](https://github.com/spinnaker/deck/pull/5395) ([c7a16d48](https://github.com/spinnaker/deck/commit/c7a16d483ee6ae839ee8600780ef802192cfa053))  



## [0.0.231](https://www.github.com/spinnaker/deck/compare/b66b2b565268cfc33481dd55267d7ff176967e77...ee89420398e2a7a42e199ba89c7ac730f494be95) (2018-05-30)


### Changes

chore(core/kubernetes/appengine): core@0.0.231, kubernetes@0.0.14, appengine@0.0.5 [#5405](https://github.com/spinnaker/deck/pull/5405) ([ee894203](https://github.com/spinnaker/deck/commit/ee89420398e2a7a42e199ba89c7ac730f494be95))  
fix(provider/kubernetes): ensure key uniqueness in artifact icon list [#5402](https://github.com/spinnaker/deck/pull/5402) ([81e894c0](https://github.com/spinnaker/deck/commit/81e894c021ed930687b7cea32dc0946c572cd535))  
fix(artifacts): "expected artifacts" < "artifact constraints" [#5400](https://github.com/spinnaker/deck/pull/5400) ([9b66ae1c](https://github.com/spinnaker/deck/commit/9b66ae1cfd3afd27f89c86c4ad0a4360451a21fa))  
refactor(core): allow React components for stage config [#5398](https://github.com/spinnaker/deck/pull/5398) ([85a9bd5c](https://github.com/spinnaker/deck/commit/85a9bd5c995c272356715edbdb9fdaac5ae0250f))  
fix(core/presentation): move some things out of state and just use props.  Re-evaluate the expression on each render. [#5397](https://github.com/spinnaker/deck/pull/5397) ([14e27878](https://github.com/spinnaker/deck/commit/14e27878d034aacc62cd4e0e4da65529b3136c9b))  
feat(core): Support buffered executions and sort appropriately [#5394](https://github.com/spinnaker/deck/pull/5394) ([25717d8c](https://github.com/spinnaker/deck/commit/25717d8c1171209077b2accfc184cd47d3f79cec))  
fix(provider/kubernetes): config relying on name instead of labels incorrectly validates kind [#5393](https://github.com/spinnaker/deck/pull/5393) ([06015d1e](https://github.com/spinnaker/deck/commit/06015d1e0aad7c5ca3b22f8958095e781792fe45))  
chore(provider/kubernetes): reactify deployManifest execution details [#5376](https://github.com/spinnaker/deck/pull/5376) ([c6d98ac4](https://github.com/spinnaker/deck/commit/c6d98ac4f21d63d670901d9316ce93b87e543117))  



## [0.0.230](https://www.github.com/spinnaker/deck/compare/d35d8a0d70e782019d177477f9a69e0a37c5791b...b66b2b565268cfc33481dd55267d7ff176967e77) (2018-05-25)


### Changes

chore(*): package bumps: core to 230, amazon to 100, titus to 31 [#5392](https://github.com/spinnaker/deck/pull/5392) ([b66b2b56](https://github.com/spinnaker/deck/commit/b66b2b565268cfc33481dd55267d7ff176967e77))  
refactor(amazon): de-angularize services [#5391](https://github.com/spinnaker/deck/pull/5391) ([445147da](https://github.com/spinnaker/deck/commit/445147dad6ef59d0befbf33f3347d5e6f0493260))  
refactor(core) de-angularize more services [#5390](https://github.com/spinnaker/deck/pull/5390) ([ca5df990](https://github.com/spinnaker/deck/commit/ca5df990b30a9208a682831803d376781a4cba87))  
fix(core/trafficGuard): Fix unsupported accounts error message firing incorrectly [#5388](https://github.com/spinnaker/deck/pull/5388) ([0bc749a9](https://github.com/spinnaker/deck/commit/0bc749a9e0e4bb81f210a3e72acccf752267da5c))  
refactor(core): de-angularize services [#5385](https://github.com/spinnaker/deck/pull/5385) ([37a96b16](https://github.com/spinnaker/deck/commit/37a96b168cae0cb5517c269e858bc16020f753c2))  
fix(core): Show stage failure message when necessary [#5384](https://github.com/spinnaker/deck/pull/5384) ([8a2d9193](https://github.com/spinnaker/deck/commit/8a2d91936f75d29455fe95caa733b9f7d9e11cad))  



## [0.0.229](https://www.github.com/spinnaker/deck/compare/05b20919c4289ffce8aac40faaed7afceebfcc66...d35d8a0d70e782019d177477f9a69e0a37c5791b) (2018-05-24)


### Changes

Revert "fix(core/executions): Fix rapid browser hangs from rapid URL … [#5383](https://github.com/spinnaker/deck/pull/5383) ([d35d8a0d](https://github.com/spinnaker/deck/commit/d35d8a0d70e782019d177477f9a69e0a37c5791b))  



## [0.0.228](https://www.github.com/spinnaker/deck/compare/6505931b46776e39e3132d695d5ddb95e422999f...05b20919c4289ffce8aac40faaed7afceebfcc66) (2018-05-24)


### Changes

chore(core): Bump to 0.0.228 ([05b20919](https://github.com/spinnaker/deck/commit/05b20919c4289ffce8aac40faaed7afceebfcc66))  
refactor(core): De-angularize application read service ([96ddb67a](https://github.com/spinnaker/deck/commit/96ddb67a331f11ba292fd65111ef45eecdfbb0c4))  
refactor(core): de-angularize application data source registry ([90e9b2aa](https://github.com/spinnaker/deck/commit/90e9b2aaa89f8ce17b6e33b154167155bae77008))  
refactor(core): De-angularize scheduler factory ([ce1f3eaf](https://github.com/spinnaker/deck/commit/ce1f3eaf40916cbc41ded71817c0c80dd5ca54c4))  
refactor(core): De-angularize inferred application warning service ([e1308a79](https://github.com/spinnaker/deck/commit/e1308a798e9752cdd09b290e6ca52faa7d3d3450))  
refactor(core): De-angular application write service ([2823c3e6](https://github.com/spinnaker/deck/commit/2823c3e65fb355d7a8722948b0f48bd95473ef58))  
refactor(core): de-angularize services [#5377](https://github.com/spinnaker/deck/pull/5377) ([bda420f9](https://github.com/spinnaker/deck/commit/bda420f98933852e734452a40d9ab788912dbb42))  



## [0.0.226](https://www.github.com/spinnaker/deck/compare/ee8eeba3406b261ae4cf8cf2c5a41589c0b191aa...6505931b46776e39e3132d695d5ddb95e422999f) (2018-05-23)


### Changes

chore(*): bump core/amazon/titus packages [#5375](https://github.com/spinnaker/deck/pull/5375) ([6505931b](https://github.com/spinnaker/deck/commit/6505931b46776e39e3132d695d5ddb95e422999f))  
fix(core/executions): Fix rapid browser hangs from rapid URL cycles triggered by changing a pipeline filter by altering the URL and hitting enter [#5373](https://github.com/spinnaker/deck/pull/5373) ([78a791f4](https://github.com/spinnaker/deck/commit/78a791f4488233c8730d24c038ebe287d9739eb3))  
feat(artifacts): Add artifact details to GCE bake and deploy stages [#5374](https://github.com/spinnaker/deck/pull/5374) ([ca3d3415](https://github.com/spinnaker/deck/commit/ca3d34159b42e24cf7899587a37ebe4cd9e574cb))  
 refactor(artifacts): Generalize kubernetes artifact summary [#5370](https://github.com/spinnaker/deck/pull/5370) ([847359e4](https://github.com/spinnaker/deck/commit/847359e4bb5bdda7008f22afe4a010c83f8b99c1))  
refactor(core): de-angularize services [#5365](https://github.com/spinnaker/deck/pull/5365) ([5d159622](https://github.com/spinnaker/deck/commit/5d159622f43fb2aa859a46b47665c8c60165224e))  
refactor(core): convert notifier functionality to React [#5366](https://github.com/spinnaker/deck/pull/5366) ([74ce42f7](https://github.com/spinnaker/deck/commit/74ce42f781a01a763729646535ea861842d5eb18))  
fix(core): avoid NPE on manual execution modal open [#5372](https://github.com/spinnaker/deck/pull/5372) ([ba840037](https://github.com/spinnaker/deck/commit/ba840037eb86d101e53b96e4afae1151201ad07e))  
Merge branch 'master' into arch/addFormValidationForScriptstage ([9e03d010](https://github.com/spinnaker/deck/commit/9e03d010c8f788f8969ac06ea22c8dc83c6cf097))  
fix(core): Fix warnings about unused variables [#5371](https://github.com/spinnaker/deck/pull/5371) ([bd2b5b8e](https://github.com/spinnaker/deck/commit/bd2b5b8e446eb864f01ad4326b5c350e0420350f))  
feat(*/instance): add moniker info + env to instance link templates [#5367](https://github.com/spinnaker/deck/pull/5367) ([b70c6d98](https://github.com/spinnaker/deck/commit/b70c6d98a92adc3f2ddabb4d449c6ced3249480c))  
Merge branch 'master' into arch/addFormValidationForScriptstage ([d80c7954](https://github.com/spinnaker/deck/commit/d80c79546e440f37342731def93441dc4eb62dcb))  
fix(core): Added validators for script stage ([a3f6bd5d](https://github.com/spinnaker/deck/commit/a3f6bd5d2869f77fff6dba9607a97e3206500889))  
feat(artifacts): Show artifact icon in artifact.component [#5368](https://github.com/spinnaker/deck/pull/5368) ([b709361b](https://github.com/spinnaker/deck/commit/b709361b3eba67c30b5ff614c3171122e7c02b31))  



## [0.0.225](https://www.github.com/spinnaker/deck/compare/6e20b41114db7ebba7a7dabd55818349871c5595...ee8eeba3406b261ae4cf8cf2c5a41589c0b191aa) (2018-05-21)


### Changes

chore(*): bump core/amazon/titus packages [#5363](https://github.com/spinnaker/deck/pull/5363) ([ee8eeba3](https://github.com/spinnaker/deck/commit/ee8eeba3406b261ae4cf8cf2c5a41589c0b191aa))  
 fix(core): force Registry config block to run earlier [#5360](https://github.com/spinnaker/deck/pull/5360) ([68003f5c](https://github.com/spinnaker/deck/commit/68003f5c70c5a41b46be66c31810f26bdf730341))  
refactor(core): de-angularize services [#5354](https://github.com/spinnaker/deck/pull/5354) ([ab380a10](https://github.com/spinnaker/deck/commit/ab380a105abd46116de1ea0b70c560f066732644))  
fix(core): Stop assuming trigger will exist for manual executions [#5356](https://github.com/spinnaker/deck/pull/5356) ([447f1a95](https://github.com/spinnaker/deck/commit/447f1a954fe6a31542b7f68b279d904722c006a4))  
chore(provider/kubernetes): deangularizify k8s manifest services [#5358](https://github.com/spinnaker/deck/pull/5358) ([86955869](https://github.com/spinnaker/deck/commit/86955869d1ca280717b4b7599a30db415e54511f))  
feat(artifacts): add gitlab artifacts [#5357](https://github.com/spinnaker/deck/pull/5357) ([b2e4a235](https://github.com/spinnaker/deck/commit/b2e4a2353a9798cae6262db48cbdc401c29189b8))  



## [0.0.224](https://www.github.com/spinnaker/deck/compare/24b11526b12f7085d15f7e611523c2038714fedc...6e20b41114db7ebba7a7dabd55818349871c5595) (2018-05-18)


### Changes

chore(*): bump packages for amazon/appengine/core/google/k8s/titus [#5353](https://github.com/spinnaker/deck/pull/5353) ([6e20b411](https://github.com/spinnaker/deck/commit/6e20b41114db7ebba7a7dabd55818349871c5595))  
refactor(*): de-angular-ize task reader/writer/executor [#5352](https://github.com/spinnaker/deck/pull/5352) ([56ede9d2](https://github.com/spinnaker/deck/commit/56ede9d28f704926f4fadf60af42612138e5b4ce))  
fix(artifacts): ensure fieldColumns is not undefined [#5351](https://github.com/spinnaker/deck/pull/5351) ([f6d14bd7](https://github.com/spinnaker/deck/commit/f6d14bd7cde28e719aeb80a6952a199745591444))  
chore(provider/appengine): use expected-artifact-selector in place of copy-n-paste [#5350](https://github.com/spinnaker/deck/pull/5350) ([c85383d6](https://github.com/spinnaker/deck/commit/c85383d65a7bfa373d2c124e26b74a508b9e4fb4))  
feat(core/tasks): deep link to text query in tasks view [#5342](https://github.com/spinnaker/deck/pull/5342) ([95f303e6](https://github.com/spinnaker/deck/commit/95f303e60030de2f88ad4d222b395468f8a0a21e))  
chore(artifacts): de-angularize artifact reference service [#5348](https://github.com/spinnaker/deck/pull/5348) ([0acc2a3d](https://github.com/spinnaker/deck/commit/0acc2a3d6a6cc9cb0856f83847b732f725f255cb))  
fix(core): Fix travis triggers [#5347](https://github.com/spinnaker/deck/pull/5347) ([ea6b0474](https://github.com/spinnaker/deck/commit/ea6b04749d5cacd4e51d542c2e4261c8b418c06b))  
chore(provider/kubernetes): de-angularize v2 expected artifact service [#5345](https://github.com/spinnaker/deck/pull/5345) ([9423895c](https://github.com/spinnaker/deck/commit/9423895c242ab4673418dd94a1b09b5df56b5db0))  
feat(artifacts): type icons in artifact selectors [#5346](https://github.com/spinnaker/deck/pull/5346) ([db4b3716](https://github.com/spinnaker/deck/commit/db4b37161622081f560b808fe93206fbb0b8d387))  



## [0.0.223](https://www.github.com/spinnaker/deck/compare/dc689f46a6d1c1edf48246f6d183ad7b58f6d39b...24b11526b12f7085d15f7e611523c2038714fedc) (2018-05-17)


### Changes

chore(*): Bump core/amazon/docker/titus/kayenta [#5344](https://github.com/spinnaker/deck/pull/5344) ([24b11526](https://github.com/spinnaker/deck/commit/24b11526b12f7085d15f7e611523c2038714fedc))  
refactor(*): De-angular pipelineConfigProvider and rename to PipelineRegistry [#5340](https://github.com/spinnaker/deck/pull/5340) ([40d11f8c](https://github.com/spinnaker/deck/commit/40d11f8c5a48284bca56e639e46cf846311a5dd4))  
feat(artifacts): show artifact type as icon in execution summary [#5341](https://github.com/spinnaker/deck/pull/5341) ([3e9956d6](https://github.com/spinnaker/deck/commit/3e9956d67965e3e8f5102aedad32cd11f8e60b78))  



## [0.0.222](https://www.github.com/spinnaker/deck/compare/6f2242bfe0a33e29c942dc3b6fce5b88bd38cf6a...dc689f46a6d1c1edf48246f6d183ad7b58f6d39b) (2018-05-16)


### Changes

chore(core): Bump to 0.0.222 [#5339](https://github.com/spinnaker/deck/pull/5339) ([dc689f46](https://github.com/spinnaker/deck/commit/dc689f46a6d1c1edf48246f6d183ad7b58f6d39b))  
fix(core/clusters): prevent scroll reset on instance clicks; better no rows messaging [#5333](https://github.com/spinnaker/deck/pull/5333) ([5b0c1f82](https://github.com/spinnaker/deck/commit/5b0c1f828388220b1d124285988b7a75464036e3))  
fix(core): Cleanup manual trigger template state when switching pipelines [#5338](https://github.com/spinnaker/deck/pull/5338) ([4504666e](https://github.com/spinnaker/deck/commit/4504666e6cdba76613bfc966d95f1f9ec7bbc3e1))  



## [0.0.221](https://www.github.com/spinnaker/deck/compare/0dd1915b6103528f6d9118dbd0a63aeb1305e6cf...6f2242bfe0a33e29c942dc3b6fce5b88bd38cf6a) (2018-05-16)


### Changes

chore(core/docker): bump core to 0.0.221, docker to 0.0.10 [#5337](https://github.com/spinnaker/deck/pull/5337) ([6f2242bf](https://github.com/spinnaker/deck/commit/6f2242bfe0a33e29c942dc3b6fce5b88bd38cf6a))  
fix(core): Fix the triggers to reload when multiple triggers of same type [#5335](https://github.com/spinnaker/deck/pull/5335) ([6ea8ac45](https://github.com/spinnaker/deck/commit/6ea8ac450c84b01dc444ec55e8cbb2c33f72ede3))  



## [0.0.220](https://www.github.com/spinnaker/deck/compare/c4ee1bfe0f1c7ed32e7a66c6d3a0fd23a5b89012...0dd1915b6103528f6d9118dbd0a63aeb1305e6cf) (2018-05-16)


### Changes

chore(core/docker): bump core to 0.0.220, docker to 0.0.9 [#5336](https://github.com/spinnaker/deck/pull/5336) ([0dd1915b](https://github.com/spinnaker/deck/commit/0dd1915b6103528f6d9118dbd0a63aeb1305e6cf))  
fix(core): Fix jenkins trigger selection for manual trigger [#5334](https://github.com/spinnaker/deck/pull/5334) ([bf115444](https://github.com/spinnaker/deck/commit/bf1154446547092d87ba999e7c58f5bfbe10bdc0))  
feat(webhook): override webhook timeout [#5330](https://github.com/spinnaker/deck/pull/5330) ([344f7809](https://github.com/spinnaker/deck/commit/344f78096e6f33e178cce4ba0cc92735bbcfe471))  
perf(core): Support loading an application with `?expand=false` [#5329](https://github.com/spinnaker/deck/pull/5329) ([b99d403b](https://github.com/spinnaker/deck/commit/b99d403b2a1bbcbda33fdf416d0f67e647d88c1c))  



## [0.0.218](https://www.github.com/spinnaker/deck/compare/7958ddbd12076fffc48f181d1f4cee099a19e352...c4ee1bfe0f1c7ed32e7a66c6d3a0fd23a5b89012) (2018-05-16)


### Changes

chore(core): Bump to 0.0.218 [#5328](https://github.com/spinnaker/deck/pull/5328) ([c4ee1bfe](https://github.com/spinnaker/deck/commit/c4ee1bfe0f1c7ed32e7a66c6d3a0fd23a5b89012))  
feat(ttl): Clearly identify ephemeral server groups [#5325](https://github.com/spinnaker/deck/pull/5325) ([f4a801d0](https://github.com/spinnaker/deck/commit/f4a801d0f11afdde0dca3d4d055d572d9d3bfa75))  
feat(artifacts): List artifacts consumed / produced by executions [#5322](https://github.com/spinnaker/deck/pull/5322) ([897818f6](https://github.com/spinnaker/deck/commit/897818f6453e2dbfb066091643238cbf0b3a309f))  



## [0.0.217](https://www.github.com/spinnaker/deck/compare/bff1fba45e1ad61aef29965bbb7e95d679790126...7958ddbd12076fffc48f181d1f4cee099a19e352) (2018-05-15)


### Changes

chore(core): Bump to 0.0.217 ([7958ddbd](https://github.com/spinnaker/deck/commit/7958ddbd12076fffc48f181d1f4cee099a19e352))  
refactor(core): Convert travis trigger template to react ([e67a8063](https://github.com/spinnaker/deck/commit/e67a8063e72a3c67e29b19a231637d42826d284f))  
refactor(core): Convert pipeline trigger template to react ([aa879a1c](https://github.com/spinnaker/deck/commit/aa879a1c6756b887436e3ac843bc302de915e9c6))  
refactor(docker): Convert docker trigger template to react ([3d44b13c](https://github.com/spinnaker/deck/commit/3d44b13c1ea9be2f9f2881e432bd5df0364cd8cc))  
refactor(core): convert jenkins trigger template to react ([d05100eb](https://github.com/spinnaker/deck/commit/d05100eb08f94b08d6d85e7bdf74d54271a0c12e))  
chore(core): Add a TetheredSelect component to tether dropdown to body ([185a5f47](https://github.com/spinnaker/deck/commit/185a5f47707b03d8f33d8e79c802d7e5bdee1a58))  
refactor(core): Make manual execution templates need to be react ([5c977ac2](https://github.com/spinnaker/deck/commit/5c977ac207089c4b55530a9bacfee3a0a2bcc769))  
refactor(*): De-angular retry service ([f73d1292](https://github.com/spinnaker/deck/commit/f73d129247657aedf3d5a6e06d8be6f296281ab2))  



## [0.0.216](https://www.github.com/spinnaker/deck/compare/2ac62c04d6a30571125a5977b1abdb9a68215d25...bff1fba45e1ad61aef29965bbb7e95d679790126) (2018-05-14)


### Changes

chore(core): Bump to 0.0.216 ([bff1fba4](https://github.com/spinnaker/deck/commit/bff1fba45e1ad61aef29965bbb7e95d679790126))  
refactor(core): Convert load balancer filters to react ([8c9e97af](https://github.com/spinnaker/deck/commit/8c9e97afd8923a4d48f3f1e1d47e39caf2b73cde))  
refactor(core): Create a react filter collapse button ([4fbf6940](https://github.com/spinnaker/deck/commit/4fbf69403f3e63db574322959667ae996e2f7e9d))  
refactor(core): Create react component for cloud provider label ([d0ffadaf](https://github.com/spinnaker/deck/commit/d0ffadaf1aa48e643503f9a549d07ddd77f6d01f))  
refactor(core): De-angular dependentFilter.service ([8885a07c](https://github.com/spinnaker/deck/commit/8885a07c79ba1b78a5f948f73e8e53ce073ba2bc))  
refactor(core): Collapse load balancer dependent filter helper ([b5ec78f1](https://github.com/spinnaker/deck/commit/b5ec78f1ae367e2197048b2acdd67d74a0510cea))  
 feat(artifacts): Either string or artifact is sufficient for bake stage [#5316](https://github.com/spinnaker/deck/pull/5316) ([91ea6344](https://github.com/spinnaker/deck/commit/91ea6344382c6225e543209bc6fb45d906690ae8))  



## [0.0.215](https://www.github.com/spinnaker/deck/compare/ca35afcaf9b7d7d9a776d540bb3e160813c8dc8c...2ac62c04d6a30571125a5977b1abdb9a68215d25) (2018-05-14)


### Changes

chore(core): Bump to 0.0.215 ([2ac62c04](https://github.com/spinnaker/deck/commit/2ac62c04d6a30571125a5977b1abdb9a68215d25))  
refactor(core): De-angular pipelineConfig.service [#5306](https://github.com/spinnaker/deck/pull/5306) ([bf797263](https://github.com/spinnaker/deck/commit/bf797263217544f29c888dc4293b13dd027ee872))  
fix(core/pipelines): fix execution graph overflow in Firefox [#5314](https://github.com/spinnaker/deck/pull/5314) ([277a33ac](https://github.com/spinnaker/deck/commit/277a33acdb827b2e197755a26e2512e6dde5f0a6))  



## [0.0.214](https://www.github.com/spinnaker/deck/compare/d8c5a6ea9c4ac12cbb377ce17468bb65289a6dfa...ca35afcaf9b7d7d9a776d540bb3e160813c8dc8c) (2018-05-11)


### Changes

chore(core): bump to 0.0.214 ([ca35afca](https://github.com/spinnaker/deck/commit/ca35afcaf9b7d7d9a776d540bb3e160813c8dc8c))  
feat(titus): make on demand cluster ui more obvious ([cf3d4db4](https://github.com/spinnaker/deck/commit/cf3d4db4108f86e8dfa37d4c656cda149b529f14))  
fix(core/sms): loosen validation of SMS input for notifications [#5309](https://github.com/spinnaker/deck/pull/5309) ([6249b86f](https://github.com/spinnaker/deck/commit/6249b86f3545912f05bf94a3f3ad014186ddae94))  
feat(core/tasks): If no user for the task, show the authenticated user [#5308](https://github.com/spinnaker/deck/pull/5308) ([0e00877c](https://github.com/spinnaker/deck/commit/0e00877cbc9644afc4ab0c393d9c8dbb41456d6b))  
refactor(core): Remove unused instanceList.filter [#5307](https://github.com/spinnaker/deck/pull/5307) ([47c5b39d](https://github.com/spinnaker/deck/commit/47c5b39d67f40696ba94aabacea96cb45f5588c1))  



## [0.0.213](https://www.github.com/spinnaker/deck/compare/67edc5840a3e1216324138d1aee7d134cb5f8413...d8c5a6ea9c4ac12cbb377ce17468bb65289a6dfa) (2018-05-09)


### Changes

chore(core): bump package to 0.0.213 [#5303](https://github.com/spinnaker/deck/pull/5303) ([d8c5a6ea](https://github.com/spinnaker/deck/commit/d8c5a6ea9c4ac12cbb377ce17468bb65289a6dfa))  
fix(core/executions): do not overwrite hydrated executions [#5302](https://github.com/spinnaker/deck/pull/5302) ([1e564153](https://github.com/spinnaker/deck/commit/1e5641531d6ec75fe954243d4e7bfdf8899266b8))  
fix(core/pipelines): fix this binding on pipeline template controller [#5301](https://github.com/spinnaker/deck/pull/5301) ([ad4f1176](https://github.com/spinnaker/deck/commit/ad4f1176c8a50d00f52e7ba682d510505b0b0385))  
refactor(core): handle TS issue with interface extension [#5300](https://github.com/spinnaker/deck/pull/5300) ([9bcc2627](https://github.com/spinnaker/deck/commit/9bcc2627c1faae3849eacd37c1c1a2727b85d8ca))  
fix(core): fix path to entity source pipeline [#5299](https://github.com/spinnaker/deck/pull/5299) ([f6d946ad](https://github.com/spinnaker/deck/commit/f6d946ad49a29e18f68b359e4e75f1ab62486504))  



## [0.0.212](https://www.github.com/spinnaker/deck/compare/b1ed8d69a4021c97ab20eb06d251da57000818df...67edc5840a3e1216324138d1aee7d134cb5f8413) (2018-05-09)


### Changes

chore(core): Bump to 0.0.212 ([67edc584](https://github.com/spinnaker/deck/commit/67edc5840a3e1216324138d1aee7d134cb5f8413))  
chore(*): Update to react 16.3.2 [#5295](https://github.com/spinnaker/deck/pull/5295) ([0c851b54](https://github.com/spinnaker/deck/commit/0c851b54208cfe2f4fc9df636a628c8a051172e1))  
chore(*): Keep dependencies up to date [#5294](https://github.com/spinnaker/deck/pull/5294) ([18f40953](https://github.com/spinnaker/deck/commit/18f40953e599ad6c5cff0e10d194f0c1cd6fd264))  
refactor(core): rename Security Groups to Firewalls [#5284](https://github.com/spinnaker/deck/pull/5284) ([d9291085](https://github.com/spinnaker/deck/commit/d929108509898833b535d20be01179dffaf187bf))  



## [0.0.211](https://www.github.com/spinnaker/deck/compare/3af8342352ac51001a7b610d818231b8f1a96636...b1ed8d69a4021c97ab20eb06d251da57000818df) (2018-05-08)


### Changes

chore(core): bump package to 0.0.211 [#5293](https://github.com/spinnaker/deck/pull/5293) ([b1ed8d69](https://github.com/spinnaker/deck/commit/b1ed8d69a4021c97ab20eb06d251da57000818df))  
fix(core/executions): ensure group count is set; sync before setting hydrated flag [#5292](https://github.com/spinnaker/deck/pull/5292) ([ab8f48c0](https://github.com/spinnaker/deck/commit/ab8f48c03e40d2e9538ed537e41bb691b1f5e1b7))  



## [0.0.210](https://www.github.com/spinnaker/deck/compare/08dc2c5ebf3ab576b3a22409fe290ae2d3c749c9...3af8342352ac51001a7b610d818231b8f1a96636) (2018-05-08)


### Changes

chore(core): bump package to 0.0.210 [#5291](https://github.com/spinnaker/deck/pull/5291) ([3af83423](https://github.com/spinnaker/deck/commit/3af8342352ac51001a7b610d818231b8f1a96636))  
Execution fixes [#5290](https://github.com/spinnaker/deck/pull/5290) ([77481987](https://github.com/spinnaker/deck/commit/77481987dfc4f716dbc9b1e626b59daa2358a4c9))  



## [0.0.209](https://www.github.com/spinnaker/deck/compare/b4eee9b02ea414de317b05665f4ee663a0401b0a...08dc2c5ebf3ab576b3a22409fe290ae2d3c749c9) (2018-05-08)


### Changes

chore(core): bump package to 0.0.209 [#5288](https://github.com/spinnaker/deck/pull/5288) ([08dc2c5e](https://github.com/spinnaker/deck/commit/08dc2c5ebf3ab576b3a22409fe290ae2d3c749c9))  
fix(core/pipelines): display comments on stage details [#5287](https://github.com/spinnaker/deck/pull/5287) ([81bfa573](https://github.com/spinnaker/deck/commit/81bfa573edc2ed465fc2a77d9233358f96f12e33))  
fix(provider/gce): Warn user if using default instanceType storage. [#5285](https://github.com/spinnaker/deck/pull/5285) ([4f83aa30](https://github.com/spinnaker/deck/commit/4f83aa303e74b2e630344d4ba1ba755e0a8f3fb5))  
refactor(titus): convert run job details to React [#5282](https://github.com/spinnaker/deck/pull/5282) ([2cef0469](https://github.com/spinnaker/deck/commit/2cef04697c9ca7f9232a3478abed4ac40252875d))  
fix(pubsub): Filter displayed list of Pub/Sub Subscription Names by Pub/Sub System Type. [#5281](https://github.com/spinnaker/deck/pull/5281) ([5bd56cef](https://github.com/spinnaker/deck/commit/5bd56cef4ed6c2702db4b79a0b38f0e95cf1a486))  
feat(provider/gce): Surface all available GCE instanceTypes. [#5278](https://github.com/spinnaker/deck/pull/5278) ([fcd4ed91](https://github.com/spinnaker/deck/commit/fcd4ed919a188b7b426a1c43083eaafa01aee6f0))  



## [0.0.208](https://www.github.com/spinnaker/deck/compare/5de32bfc0f7c093206db3a4694137c1923f183fc...b4eee9b02ea414de317b05665f4ee663a0401b0a) (2018-05-05)


### Changes

chore(core): bump package to 0.0.208 [#5276](https://github.com/spinnaker/deck/pull/5276) ([b4eee9b0](https://github.com/spinnaker/deck/commit/b4eee9b02ea414de317b05665f4ee663a0401b0a))  
fix(core): do not use arrow functions for inline ng controllers [#5275](https://github.com/spinnaker/deck/pull/5275) ([fee4a6af](https://github.com/spinnaker/deck/commit/fee4a6af2c94b62ba6015261b4f4b70c8e7f4ca4))  
feat(artifacts): Allow artifacts to be selected in bake config [#5270](https://github.com/spinnaker/deck/pull/5270) ([f9e7c50c](https://github.com/spinnaker/deck/commit/f9e7c50c97adb48e5e5f16c7d18e6632e36b0bf0))  



## [0.0.207](https://www.github.com/spinnaker/deck/compare/09d274c334f5cadf358f5f86dbe129fa523eec35...5de32bfc0f7c093206db3a4694137c1923f183fc) (2018-05-04)


### Changes

chore(core/amazon/titus): bump packages [#5267](https://github.com/spinnaker/deck/pull/5267) ([5de32bfc](https://github.com/spinnaker/deck/commit/5de32bfc0f7c093206db3a4694137c1923f183fc))  
fix(artifacts): method-syntax functions cant be used as constructors [#5269](https://github.com/spinnaker/deck/pull/5269) ([5aee5c06](https://github.com/spinnaker/deck/commit/5aee5c06333104f3cbaf954a4a340f88db101efc))  
perf(*): transpile to latest two modern browsers only [#5260](https://github.com/spinnaker/deck/pull/5260) ([caf1a8a8](https://github.com/spinnaker/deck/commit/caf1a8a84139fb4e5fe4c12959e02a9309d4a7db))  
fix(core): trim pipeline name when checking for duplicates [#5262](https://github.com/spinnaker/deck/pull/5262) ([2a2ce3ee](https://github.com/spinnaker/deck/commit/2a2ce3ee4e5813a22b64a1d8a2af953fcbc37097))  
docs(tootltip): update docker trigger tooltip [#5264](https://github.com/spinnaker/deck/pull/5264) ([57c20761](https://github.com/spinnaker/deck/commit/57c2076125445219bbc2dac44794a37ee5da9232))  
refactor(google): Refactor config fields to use consistent directive [#5263](https://github.com/spinnaker/deck/pull/5263) ([e9f0d246](https://github.com/spinnaker/deck/commit/e9f0d2462ca3d8038f6559cc8a1c34851827beee))  
fix(core): remove tooltip if needed when unmounting instances [#5258](https://github.com/spinnaker/deck/pull/5258) ([436c4eba](https://github.com/spinnaker/deck/commit/436c4eba1b9637a5cec117ab2db9d6eebacac9d9))  



## [0.0.206](https://www.github.com/spinnaker/deck/compare/75842c4b6d46309ee0f015c6783481ebffac8b91...09d274c334f5cadf358f5f86dbe129fa523eec35) (2018-05-02)


### Changes

chore(core): update to version 0.206 ([09d274c3](https://github.com/spinnaker/deck/commit/09d274c334f5cadf358f5f86dbe129fa523eec35))  
feat(core): send includeDetails=false for server groups [#5255](https://github.com/spinnaker/deck/pull/5255) ([1a7edb6a](https://github.com/spinnaker/deck/commit/1a7edb6a9ea3ca1f4f6f93d64a62730f9a8e9224))  



## [0.0.205](https://www.github.com/spinnaker/deck/compare/2720ad92ccc07889d854781329d4f086590359a5...75842c4b6d46309ee0f015c6783481ebffac8b91) (2018-05-01)


### Changes

chore(core): bump package to 0.0.205 [#5253](https://github.com/spinnaker/deck/pull/5253) ([75842c4b](https://github.com/spinnaker/deck/commit/75842c4b6d46309ee0f015c6783481ebffac8b91))  
fix(core): fix task matcher region check for rollbackServerGroup [#5251](https://github.com/spinnaker/deck/pull/5251) ([5046af1f](https://github.com/spinnaker/deck/commit/5046af1f00e3ed7b53f57b2969867cccb69682ff))  



## [0.0.204](https://www.github.com/spinnaker/deck/compare/ca9595bdbffc5dc1a772bcb22f8e1af0ebde72ce...2720ad92ccc07889d854781329d4f086590359a5) (2018-05-01)


### Changes

chore(core): bump package to 0.0.204 [#5250](https://github.com/spinnaker/deck/pull/5250) ([2720ad92](https://github.com/spinnaker/deck/commit/2720ad92ccc07889d854781329d4f086590359a5))  



## [0.0.203](https://www.github.com/spinnaker/deck/compare/e59a0bf1d33fc0133b238cde92c142e615b55b03...ca9595bdbffc5dc1a772bcb22f8e1af0ebde72ce) (2018-05-01)


### Changes

chore(core): bump package to 0.0.203 [#5249](https://github.com/spinnaker/deck/pull/5249) ([ca9595bd](https://github.com/spinnaker/deck/commit/ca9595bdbffc5dc1a772bcb22f8e1af0ebde72ce))  
fix(core/pipeline): Simplify cache field naming to avoid confusion [#5245](https://github.com/spinnaker/deck/pull/5245) ([cc1cfadb](https://github.com/spinnaker/deck/commit/cc1cfadb41136a7f9fda22c3fd8ed2635b051a25))  
feat(pipeline_templates) Allow Pipelines to Inherit Pipeline Template Configuration [#5214](https://github.com/spinnaker/deck/pull/5214) ([e1e43cf4](https://github.com/spinnaker/deck/commit/e1e43cf4f3dbc097fc84fe58fc2f2e70b4614376))  
fix(core/pipeline): Retain show stage durations even if filter applied [#5244](https://github.com/spinnaker/deck/pull/5244) ([e0377d11](https://github.com/spinnaker/deck/commit/e0377d116911aafecc11288aebff77d99155ea4e))  
fix(core): Fix ordering for executions in triggers [#5242](https://github.com/spinnaker/deck/pull/5242) ([a237b1f5](https://github.com/spinnaker/deck/commit/a237b1f5a930f2ee5bfdd5c578e7dc2b9e710811))  
fix(artifacts): limit list of artifacts in execution to those consumed by pipeline [#5238](https://github.com/spinnaker/deck/pull/5238) ([c7b56a8e](https://github.com/spinnaker/deck/commit/c7b56a8e5d8d6bcd42399dceffdc4be6a387468a))  
feat(artifacts) Let pipeline stages emit artifacts [#5193](https://github.com/spinnaker/deck/pull/5193) ([38819033](https://github.com/spinnaker/deck/commit/38819033e8370c1db14b46667481d9302356caa4))  
feat(artifacts): Support Jenkins stages emitting artifacts [#5174](https://github.com/spinnaker/deck/pull/5174) ([8e4aceb0](https://github.com/spinnaker/deck/commit/8e4aceb0e2b394512431369a7e69d3246a4e6048))  
Fixes an "invalid regex" JS error. [#5236](https://github.com/spinnaker/deck/pull/5236) ([06c0c1a3](https://github.com/spinnaker/deck/commit/06c0c1a35d94e197214743cb7c55c637e23e0f37))  
fix(bake/manifest): attach UUID to expected artifact [#5235](https://github.com/spinnaker/deck/pull/5235) ([387e0703](https://github.com/spinnaker/deck/commit/387e07032f28e86b2dbed48764c9fb932d45c7be))  
feat(bake/manifest): allow ui-specified value artifacts [#5232](https://github.com/spinnaker/deck/pull/5232) ([ed2e7eb8](https://github.com/spinnaker/deck/commit/ed2e7eb8dc1c9815ed844e0770167d6439851add))  



## [0.0.202](https://www.github.com/spinnaker/deck/compare/be48c44433d0a99c160d839026f20a84d750a937...e59a0bf1d33fc0133b238cde92c142e615b55b03) (2018-04-25)


### Changes

chore(core): Bump package to 0.0.202 [#5231](https://github.com/spinnaker/deck/pull/5231) ([e59a0bf1](https://github.com/spinnaker/deck/commit/e59a0bf1d33fc0133b238cde92c142e615b55b03))  
fix(core/pipeline): Only list decorated artifacts [#5229](https://github.com/spinnaker/deck/pull/5229) ([b668b303](https://github.com/spinnaker/deck/commit/b668b303bdb319b44916d2688adbe25a7ab2288c))  
chore(bake/manifest): clarifying comment on first artifact [#5223](https://github.com/spinnaker/deck/pull/5223) ([308002dc](https://github.com/spinnaker/deck/commit/308002dcef953c30ffa654f979706eb5187d780f))  
fix(core) Keep correct stage in summary object for future operations [#5230](https://github.com/spinnaker/deck/pull/5230) ([5b1d32be](https://github.com/spinnaker/deck/commit/5b1d32be404439cd46c6b473916b0c9341eaa6c4))  
fix(core/executions): store execution count filter per application [#5228](https://github.com/spinnaker/deck/pull/5228) ([1d248a1f](https://github.com/spinnaker/deck/commit/1d248a1fa614e059649de17e30395b37b2c216d7))  



## [0.0.201](https://www.github.com/spinnaker/deck/compare/d02946c11d91db6bdb6afb4ec18d8d5a00ba1216...be48c44433d0a99c160d839026f20a84d750a937) (2018-04-23)


### Changes

chore(core): Bump package to 0.0.201 ([be48c444](https://github.com/spinnaker/deck/commit/be48c44433d0a99c160d839026f20a84d750a937))  
fix(*): Fix tests ([c7d93679](https://github.com/spinnaker/deck/commit/c7d936796cafec33b2b81f01949b06d58d6da22d))  
refactor(*): De-angularize account service ([cc6d3332](https://github.com/spinnaker/deck/commit/cc6d333254159ab713a83bc89f13938d4c98e256))  
refactor(*): De-angularize API service ([cc8adc9d](https://github.com/spinnaker/deck/commit/cc8adc9df3f191ff2590a0bb5eea3f794cc85544))  
fix(core/tests): Make sure angular-ui-bootstrap is available for necessary tests ([974ab5d7](https://github.com/spinnaker/deck/commit/974ab5d7d162ef62f46a77a088bca6bf94aa0200))  
refactor(core): De-angularize authentication initializer ([c192909a](https://github.com/spinnaker/deck/commit/c192909ac1d22e70d54565473ef10e41d2076536))  
refactor(core): Convert logged out modal to react ([4b695749](https://github.com/spinnaker/deck/commit/4b69574907424ec20ba6c2a7496a7fca4165c0dd))  
refactor(*): De-angularize authentication service ([a4d96cd3](https://github.com/spinnaker/deck/commit/a4d96cd340b49203f453afafd8d92512da6c831b))  
refactor(*): De-angularize cloud provider registry ([5aaf40d8](https://github.com/spinnaker/deck/commit/5aaf40d8599e372b3f49ba2db3dffbd711bf437e))  
fix(core): sort global search results by ranking [#5225](https://github.com/spinnaker/deck/pull/5225) ([bc1035ba](https://github.com/spinnaker/deck/commit/bc1035ba1c200fd8a71b2736695facb2d1d746ad))  
fix(core): attach instance ID tooltip to body [#5224](https://github.com/spinnaker/deck/pull/5224) ([b90234fa](https://github.com/spinnaker/deck/commit/b90234faf65ccbb31c4e11e9637b17bf2c6e7804))  
feat(bake/manifest): helm values artifacts [#5222](https://github.com/spinnaker/deck/pull/5222) ([5f728802](https://github.com/spinnaker/deck/commit/5f728802954dcf5d1ed7f13395d649b3e70d0301))  



## [0.0.199](https://www.github.com/spinnaker/deck/compare/ca74878e2b49d0085046afa0c2e8b74bcf3f7195...d02946c11d91db6bdb6afb4ec18d8d5a00ba1216) (2018-04-20)


### Changes

chore(core): bump package to 0.0.199 [#5219](https://github.com/spinnaker/deck/pull/5219) ([d02946c1](https://github.com/spinnaker/deck/commit/d02946c11d91db6bdb6afb4ec18d8d5a00ba1216))  
fix(core/cluster): fix infinite loop toggling listInstances true/false [#5218](https://github.com/spinnaker/deck/pull/5218) ([e16e5275](https://github.com/spinnaker/deck/commit/e16e52754e2aa9c46ea34ee9274f4e4ccbdde297))  
refactor(core): Consolidate securty group dependent filter helper [#5212](https://github.com/spinnaker/deck/pull/5212) ([a2646747](https://github.com/spinnaker/deck/commit/a26467478ea26c53191416172f138ab4de31aba7))  
fix(core/serverGroup): Default to decoding User Data as text [#5210](https://github.com/spinnaker/deck/pull/5210) ([cd3fe200](https://github.com/spinnaker/deck/commit/cd3fe200c959ad0659e60f3a2b77f6d51a4fb734))  



## [0.0.198](https://www.github.com/spinnaker/deck/compare/7ea8923712c37b87ce2ac709fee83bfceea7bf28...ca74878e2b49d0085046afa0c2e8b74bcf3f7195) (2018-04-18)


### Changes

fix(core): fix HelpField export [#5204](https://github.com/spinnaker/deck/pull/5204) ([ca74878e](https://github.com/spinnaker/deck/commit/ca74878e2b49d0085046afa0c2e8b74bcf3f7195))  



## [0.0.197](https://www.github.com/spinnaker/deck/compare/8e8cee4bb137cb3b302b6aaa9a154b0abf7eeb18...7ea8923712c37b87ce2ac709fee83bfceea7bf28) (2018-04-17)


### Changes

chore(core): bump package to 0.0.197 [#5202](https://github.com/spinnaker/deck/pull/5202) ([7ea89237](https://github.com/spinnaker/deck/commit/7ea8923712c37b87ce2ac709fee83bfceea7bf28))  
fix(core): remove artificial dehydration code from execution service [#5201](https://github.com/spinnaker/deck/pull/5201) ([58688959](https://github.com/spinnaker/deck/commit/586889591597d75c5062b6b68ce081cd876cb43e))  



## [0.0.196](https://www.github.com/spinnaker/deck/compare/dd38b469cae044739fceca27503ec3a4f444f0ba...8e8cee4bb137cb3b302b6aaa9a154b0abf7eeb18) (2018-04-17)


### Changes

chore(*): bump packages for de-angularized help contents [#5200](https://github.com/spinnaker/deck/pull/5200) ([8e8cee4b](https://github.com/spinnaker/deck/commit/8e8cee4bb137cb3b302b6aaa9a154b0abf7eeb18))  
refactor(*): de-angularize help contents/registry [#5199](https://github.com/spinnaker/deck/pull/5199) ([d6bfa5c2](https://github.com/spinnaker/deck/commit/d6bfa5c22c2196942230721ecc38ddb68e56874f))  
fix(core): ExecutionBuildTitle sometimes gets an undefined execution [#5198](https://github.com/spinnaker/deck/pull/5198) ([37918dbe](https://github.com/spinnaker/deck/commit/37918dbe76870c4fe2b7f7644346d45d23a72e50))  
fix(core): allow auto-navigation on single search result in V2 [#5196](https://github.com/spinnaker/deck/pull/5196) ([56363e51](https://github.com/spinnaker/deck/commit/56363e515c32746ffc7632f07cde53563bdb01f3))  



## [0.0.195](https://www.github.com/spinnaker/deck/compare/091c23e18c44148d3617fa692c3acf378a056c4e...dd38b469cae044739fceca27503ec3a4f444f0ba) (2018-04-16)


### Changes

chore(core): bump package to 0.0.195 [#5187](https://github.com/spinnaker/deck/pull/5187) ([dd38b469](https://github.com/spinnaker/deck/commit/dd38b469cae044739fceca27503ec3a4f444f0ba))  
refactor(core): use running executions to refresh in-place executions [#5186](https://github.com/spinnaker/deck/pull/5186) ([b57d7656](https://github.com/spinnaker/deck/commit/b57d7656e6b5146e92accd847487f58d3f0cff53))  
fix(core): fix refresh on execution patches; default option on judgment [#5183](https://github.com/spinnaker/deck/pull/5183) ([7491cab7](https://github.com/spinnaker/deck/commit/7491cab70774cf1694f49e18c9cd9745014c5e5d))  
refactor(core): Remove angular from security groups filter service ([13537a8f](https://github.com/spinnaker/deck/commit/13537a8f4d95c4150562b65f6107c008bc6a7c93))  
refactor(core): Remove angular from load balancer filter service ([8d732a50](https://github.com/spinnaker/deck/commit/8d732a50a8990a01908ebc1a44e5a7e5571ae066))  
refactor(core): Remove angular from cluster filter service ([76f0d5ec](https://github.com/spinnaker/deck/commit/76f0d5ec4f112bee40b591e744cd3dc1e936b2e2))  
refactor(core): Remove angular from security groups filter model ([ce241183](https://github.com/spinnaker/deck/commit/ce241183ecb1ffb9f6bf7129f4f6c12ce1b6c674))  
refactor(core): Remove angular from multiselect model ([15f13e64](https://github.com/spinnaker/deck/commit/15f13e6423ec9d02a621e8e9091ab71c6e5611c6))  
refactor(core): Convert multiselect model to TS ([d545f83a](https://github.com/spinnaker/deck/commit/d545f83a475e8f1a1a74245f88180d0cfe7f2de5))  
refactor(core): Remove angular from execution filter model ([9ceddd45](https://github.com/spinnaker/deck/commit/9ceddd45a400b58f2a726e171d4e3989f965580d))  
refactor(core): Remove angular from cluster filter model ([fdf714db](https://github.com/spinnaker/deck/commit/fdf714dbb6af26a84e486f93f6857e3998991458))  
refactor(core): Remove angular from load balancer filter model ([6c085e0b](https://github.com/spinnaker/deck/commit/6c085e0b7d5ea11f2d13f6beef52563ce6e0e8dd))  
test(filterModel): Make sure 'ui.router' is initialized before tests which depend on it. ([0c50397b](https://github.com/spinnaker/deck/commit/0c50397b02fc19c6e0da24f17c2e64c495776224))  
refactor(core): Remove angular from filter model service ([e024c161](https://github.com/spinnaker/deck/commit/e024c161515540d2993272cd5b5ec6d83bcc74d6))  



## [0.0.194](https://www.github.com/spinnaker/deck/compare/3ac539e6f720985916c0d9c618bd4d08d4697b2e...091c23e18c44148d3617fa692c3acf378a056c4e) (2018-04-16)


### Changes

chore(core): bump package to 0.0.194 [#5178](https://github.com/spinnaker/deck/pull/5178) ([091c23e1](https://github.com/spinnaker/deck/commit/091c23e18c44148d3617fa692c3acf378a056c4e))  
feat(core): show execution timestamp on hover [#5177](https://github.com/spinnaker/deck/pull/5177) ([cc4a3314](https://github.com/spinnaker/deck/commit/cc4a33142bc2ef65e376bf4e98cc029617cc88e3))  
fix(core/jenkins): show long choice params on multiple lines in dropdown [#5175](https://github.com/spinnaker/deck/pull/5175) ([da4d1dc1](https://github.com/spinnaker/deck/commit/da4d1dc15898a00c042cab1c09b79d53727dc6fd))  
chore(npmignore): remove yalc workaround for 'history' and 'changes' [#5173](https://github.com/spinnaker/deck/pull/5173) ([071275f1](https://github.com/spinnaker/deck/commit/071275f104aec44378fd6275db0e7035938f45d1))  
fix(core/execution) fix wrong parameter passed to getExectionsForConfigIds [#5171](https://github.com/spinnaker/deck/pull/5171) ([3affdc7d](https://github.com/spinnaker/deck/commit/3affdc7d3e914f0597683d7bcac3c060d93b7603))  
feat(redblack): UI option for 'Rollback on Failure' [#5102](https://github.com/spinnaker/deck/pull/5102) ([0470561c](https://github.com/spinnaker/deck/commit/0470561cacb3179b70126b229e12df9c51e24ac8))  
feat(bake/manifest): support overrides [#5172](https://github.com/spinnaker/deck/pull/5172) ([9da44589](https://github.com/spinnaker/deck/commit/9da445897106de9364bbf3d297d2e656da706301))  



## [0.0.193](https://www.github.com/spinnaker/deck/compare/b44c5cea4c79dfa68c904284d7ca961da1f7a1b4...3ac539e6f720985916c0d9c618bd4d08d4697b2e) (2018-04-12)


### Changes

chore(core): bump package to 0.0.193 [#5170](https://github.com/spinnaker/deck/pull/5170) ([3ac539e6](https://github.com/spinnaker/deck/commit/3ac539e6f720985916c0d9c618bd4d08d4697b2e))  
fix(core): fix awaiting judgment overflow, prevent flicker on active stages [#5169](https://github.com/spinnaker/deck/pull/5169) ([ff9aa054](https://github.com/spinnaker/deck/commit/ff9aa054939e19e3948f8d65ca05285508818c6f))  



## [0.0.192](https://www.github.com/spinnaker/deck/compare/597607f927c6cd9ec2c0eceb42985dbb6c97e7e4...b44c5cea4c79dfa68c904284d7ca961da1f7a1b4) (2018-04-12)


### Changes

chore(core): bump package to 0.0.192 [#5168](https://github.com/spinnaker/deck/pull/5168) ([b44c5cea](https://github.com/spinnaker/deck/commit/b44c5cea4c79dfa68c904284d7ca961da1f7a1b4))  
fix(core/canary): do not transform lazy canaries; smarter stage hydration [#5167](https://github.com/spinnaker/deck/pull/5167) ([da34c5fa](https://github.com/spinnaker/deck/commit/da34c5fa1bdbc3e954e6a095ef07d7fac7a1f28e))  



## [0.0.191](https://www.github.com/spinnaker/deck/compare/d460e74ed5008b4202a22848a24a819127718f4f...597607f927c6cd9ec2c0eceb42985dbb6c97e7e4) (2018-04-12)


### Changes

chore(core): bump package to 0.0.191 [#5165](https://github.com/spinnaker/deck/pull/5165) ([597607f9](https://github.com/spinnaker/deck/commit/597607f927c6cd9ec2c0eceb42985dbb6c97e7e4))  
fix(core): fix execution rendering on lazy executions [#5164](https://github.com/spinnaker/deck/pull/5164) ([80f533a9](https://github.com/spinnaker/deck/commit/80f533a9094369e3faa8148f3a6b2ff4a5853645))  
fix(core): Fix pipeline configurer for new cache format [#5163](https://github.com/spinnaker/deck/pull/5163) ([f196cf4a](https://github.com/spinnaker/deck/commit/f196cf4afeb3a46045f1345ca01b69d6320413d5))  
refactor(*): De-angularize caches [#5161](https://github.com/spinnaker/deck/pull/5161) ([2f654733](https://github.com/spinnaker/deck/commit/2f6547336c43fdf5ced72dc029700e214d07c1b9))  
fix(core): extract target accounts from config [#5155](https://github.com/spinnaker/deck/pull/5155) ([8c1293fa](https://github.com/spinnaker/deck/commit/8c1293fa0411cd611c9713d6b639ea872f26ec0d))  



## [0.0.190](https://www.github.com/spinnaker/deck/compare/1f4055c0de4d0384f6f99eb44e44d5e303375d55...d460e74ed5008b4202a22848a24a819127718f4f) (2018-04-11)


### Changes

chore(core): Bump to 0.0.190 ([d460e74e](https://github.com/spinnaker/deck/commit/d460e74ed5008b4202a22848a24a819127718f4f))  
refactor(core/entityTag): Convert clusterTargetBuilder.service to plain JS ([378f228a](https://github.com/spinnaker/deck/commit/378f228aec8cdb4a02990785505a67c1b9ad2adc))  
refactor(core/naming): Convert angular naming.service to plain NameUtils ([d9f313bd](https://github.com/spinnaker/deck/commit/d9f313bd36508961f2a6be8d32c5155e0b5b893d))  



## [0.0.189](https://www.github.com/spinnaker/deck/compare/dd32e241bab28a785268b6270931734d9d621971...1f4055c0de4d0384f6f99eb44e44d5e303375d55) (2018-04-10)


### Changes

chore(core): bump package to 0.0.189 [#5153](https://github.com/spinnaker/deck/pull/5153) ([1f4055c0](https://github.com/spinnaker/deck/commit/1f4055c0de4d0384f6f99eb44e44d5e303375d55))  
fix(core): fix manual judgment render, active execution refresh [#5152](https://github.com/spinnaker/deck/pull/5152) ([9089ba89](https://github.com/spinnaker/deck/commit/9089ba89d48598704b14548224cf26ca72497be2))  



## [0.0.188](https://www.github.com/spinnaker/deck/compare/3d37206ecd6f0c86a0cb4612948801c2f2f6e13d...dd32e241bab28a785268b6270931734d9d621971) (2018-04-10)


### Changes

chore(core): bump package to 0.0.188 [#5150](https://github.com/spinnaker/deck/pull/5150) ([dd32e241](https://github.com/spinnaker/deck/commit/dd32e241bab28a785268b6270931734d9d621971))  
refactor(core): lazy load execution details [#5141](https://github.com/spinnaker/deck/pull/5141) ([ce35bb63](https://github.com/spinnaker/deck/commit/ce35bb63b1647ba90e87d143636b3f2e11615d85))  
fix(core/pipeline): Fix rendering of execution graphs after tslint --fix [#5148](https://github.com/spinnaker/deck/pull/5148) ([88269803](https://github.com/spinnaker/deck/commit/88269803453c3e82c39117e8fb8ee2ec18f2ea4b))  
fix(core/pipeline): Fix rendering of pipeline graphs after tslint --fix [#5147](https://github.com/spinnaker/deck/pull/5147) ([4af3b708](https://github.com/spinnaker/deck/commit/4af3b708096e0629936e21ec4f6e9322b5aadcb7))  
fix(core/nav): use 'undefined' instead of 'null' for empty select value [#5146](https://github.com/spinnaker/deck/pull/5146) ([fb2295d4](https://github.com/spinnaker/deck/commit/fb2295d49ea3b72002ba63b20051eb8eacf4ab13))  
fix(core/wizard): Fix header icons at smaller widths ([71d978f0](https://github.com/spinnaker/deck/commit/71d978f0d201ee223730e054cbcac1e09401dcbd))  
chore(tslint): manually fix lint errors that don't have --fix ([be938ab2](https://github.com/spinnaker/deck/commit/be938ab296d0dbec2a888f38086f8ec232232e17))  
chore(tslint): ❯ npx tslint --fix -p tsconfig.json ([b1ddb67c](https://github.com/spinnaker/deck/commit/b1ddb67c2c7a74f451baac070a65c985c2b6fb8e))  
chore(tslint): Add prettier-tslint rules, manually fix lint errors that don't have --fix ([e74be825](https://github.com/spinnaker/deck/commit/e74be825f0f0c3e8ed24717188b0e76d6cc99bd8))  
Just Use Prettier™ ([532ab778](https://github.com/spinnaker/deck/commit/532ab7784ca93569308c8f2ab80a18d313b910f9))  
feat(bake/manifest): bake manifest stage config [#5128](https://github.com/spinnaker/deck/pull/5128) ([2a908d13](https://github.com/spinnaker/deck/commit/2a908d130bfacc8f631177eec706b7c7336bbbbb))  



## [0.0.187](https://www.github.com/spinnaker/deck/compare/b6ca1f32e1afa603ec35601c6f92d3baeca1446e...3d37206ecd6f0c86a0cb4612948801c2f2f6e13d) (2018-04-09)


### Changes

chore(core): bump package to 0.0.187 [#5140](https://github.com/spinnaker/deck/pull/5140) ([3d37206e](https://github.com/spinnaker/deck/commit/3d37206ecd6f0c86a0cb4612948801c2f2f6e13d))  
fix(core): skin selection [#5139](https://github.com/spinnaker/deck/pull/5139) ([7b797d24](https://github.com/spinnaker/deck/commit/7b797d24fc01268c0bcbb68c973f389ebed88550))  



## [0.0.186](https://www.github.com/spinnaker/deck/compare/52412bb88f5757b8b3ca45d45a31cd099917cbdb...b6ca1f32e1afa603ec35601c6f92d3baeca1446e) (2018-04-08)


### Changes

chore(core): bump package to 0.0.186 [#5137](https://github.com/spinnaker/deck/pull/5137) ([b6ca1f32](https://github.com/spinnaker/deck/commit/b6ca1f32e1afa603ec35601c6f92d3baeca1446e))  
fix(core): make nav category headings clickable in Firefox [#5136](https://github.com/spinnaker/deck/pull/5136) ([e9d721b1](https://github.com/spinnaker/deck/commit/e9d721b1128585dc06ecb9c27931d2db82a12be7))  
fix(core): do not cache non-json responses in local storage [#5124](https://github.com/spinnaker/deck/pull/5124) ([3bc5b150](https://github.com/spinnaker/deck/commit/3bc5b1506b760af070f14ee5608d93ad5414b9a3))  
fix(artifacts): deleting expected artifact removes stale references [#5107](https://github.com/spinnaker/deck/pull/5107) ([bd912257](https://github.com/spinnaker/deck/commit/bd912257caa91660d3ec92be37cb64b142d35f68))  



## [0.0.185](https://www.github.com/spinnaker/deck/compare/edc9280594c8185b2529821383c63126e4826a46...52412bb88f5757b8b3ca45d45a31cd099917cbdb) (2018-04-05)


### Changes

chore(core): bump package to 0.0.185 [#5121](https://github.com/spinnaker/deck/pull/5121) ([52412bb8](https://github.com/spinnaker/deck/commit/52412bb88f5757b8b3ca45d45a31cd099917cbdb))  
fix(core): remove :focus styling on nav dropdowns [#5119](https://github.com/spinnaker/deck/pull/5119) ([ac520145](https://github.com/spinnaker/deck/commit/ac5201457218b99b74e32087c179c129eacd3566))  
fix(core): toggle disabled to false if needed on application refresh [#5114](https://github.com/spinnaker/deck/pull/5114) ([b935d9aa](https://github.com/spinnaker/deck/commit/b935d9aa96cdb082fe17e7dc163315e2e4e739ce))  
feat(aws): offer rollback when user enables an older server group [#5109](https://github.com/spinnaker/deck/pull/5109) ([555e7466](https://github.com/spinnaker/deck/commit/555e7466fe8bec03bb0b5dc53a7fde73a2dfd99a))  
feat(provider/kubernetes): pipeline manifest events [#5096](https://github.com/spinnaker/deck/pull/5096) ([fa436486](https://github.com/spinnaker/deck/commit/fa436486ebc7c5fd87ba042375f527401766d25a))  
feat(artifacts) Implement S3 artifacts [#5099](https://github.com/spinnaker/deck/pull/5099) ([c119e9db](https://github.com/spinnaker/deck/commit/c119e9dbffb37a76941a585dda5af612b9d3460c))  



## [0.0.184](https://www.github.com/spinnaker/deck/compare/40317f8f83fee1017017a52d8b0cf297e713dab5...edc9280594c8185b2529821383c63126e4826a46) (2018-03-29)


### Changes

chore(core): Bump to 0.0.184 [#5104](https://github.com/spinnaker/deck/pull/5104) ([edc92805](https://github.com/spinnaker/deck/commit/edc9280594c8185b2529821383c63126e4826a46))  
feat(core): Add Pager UI for finding and paging application owners [#5103](https://github.com/spinnaker/deck/pull/5103) ([103d7090](https://github.com/spinnaker/deck/commit/103d7090c6338bd1c154bd3a03a8a637273830b5))  
feat(webhooks): support artifact production [#5090](https://github.com/spinnaker/deck/pull/5090) ([5e64470f](https://github.com/spinnaker/deck/commit/5e64470fe098b4c725dab263a33f4b51e9e83a04))  



## [0.0.183](https://www.github.com/spinnaker/deck/compare/9b44d15ac5697a1e87059c3146cd003649e928ac...40317f8f83fee1017017a52d8b0cf297e713dab5) (2018-03-29)


### Changes

chore(core): bump package to 0.0.183 [#5095](https://github.com/spinnaker/deck/pull/5095) ([40317f8f](https://github.com/spinnaker/deck/commit/40317f8f83fee1017017a52d8b0cf297e713dab5))  
refactor(core): allow dataSources to specify a required dataSource [#5094](https://github.com/spinnaker/deck/pull/5094) ([e995e1e9](https://github.com/spinnaker/deck/commit/e995e1e928993f0c2e2b643613feeebbc94ac4f6))  
style(core/navigation): remove dead zones from nav dropdown [#5087](https://github.com/spinnaker/deck/pull/5087) ([7e662195](https://github.com/spinnaker/deck/commit/7e662195d5122273786d611c8aa54bf8783fea34))  
Updating to use auto-generated files from icomoon.app [#5086](https://github.com/spinnaker/deck/pull/5086) ([c96f74b7](https://github.com/spinnaker/deck/commit/c96f74b720624631b65405e847593c0e52b4a5fb))  
fix(core/search): dont throw when SETTINGS.defaultProviders is null-ish [#5093](https://github.com/spinnaker/deck/pull/5093) ([e4f5f3e9](https://github.com/spinnaker/deck/commit/e4f5f3e99274bb2a48bd0ce34348b4c9b28a9783))  



## [0.0.182](https://www.github.com/spinnaker/deck/compare/56737bbc95731b8a9d81067164e79eb1efb80307...9b44d15ac5697a1e87059c3146cd003649e928ac) (2018-03-28)


### Changes

chore(core): bump to 0.0.182 [#5089](https://github.com/spinnaker/deck/pull/5089) ([9b44d15a](https://github.com/spinnaker/deck/commit/9b44d15ac5697a1e87059c3146cd003649e928ac))  
feat(core/pagerDuty): Add pagerDuty feature to enable/disable adding pd keys [#5088](https://github.com/spinnaker/deck/pull/5088) ([17727c23](https://github.com/spinnaker/deck/commit/17727c2377efafdae2a95a7abc66160184ab7632))  
feat(kubernetes): use v2 load balancer and security group transformers [#5085](https://github.com/spinnaker/deck/pull/5085) ([81c0307a](https://github.com/spinnaker/deck/commit/81c0307a8e579f4e789a28f315312dcdd9efa6ad))  
fix(artifacts): Set the name field on the default github artifacts [#5083](https://github.com/spinnaker/deck/pull/5083) ([342685ba](https://github.com/spinnaker/deck/commit/342685badb4a155cf6ce82127a6b8cdfbe40b8d0))  



## [0.0.181](https://www.github.com/spinnaker/deck/compare/44ae6fd86ad48fb4df9ba919c6ca7c914b2784c3...56737bbc95731b8a9d81067164e79eb1efb80307) (2018-03-28)


### Changes

chore(core): bump package to 0.0.181 [#5084](https://github.com/spinnaker/deck/pull/5084) ([56737bbc](https://github.com/spinnaker/deck/commit/56737bbc95731b8a9d81067164e79eb1efb80307))  
feat(core): move from provider version UI implementation to skins [#5080](https://github.com/spinnaker/deck/pull/5080) ([dc6d3fdc](https://github.com/spinnaker/deck/commit/dc6d3fdc912faf17db4f0ef8cf37ae7de9e6ca84))  



## [0.0.180](https://www.github.com/spinnaker/deck/compare/3435ed2c352c061056ea5be7c4d6b6186b2e9a5c...44ae6fd86ad48fb4df9ba919c6ca7c914b2784c3) (2018-03-27)


### Changes

chore(core): bump package to 0.0.180 [#5079](https://github.com/spinnaker/deck/pull/5079) ([44ae6fd8](https://github.com/spinnaker/deck/commit/44ae6fd86ad48fb4df9ba919c6ca7c914b2784c3))  
fix(core): set initial state of skipWaitCustomText on wait stage [#5078](https://github.com/spinnaker/deck/pull/5078) ([aa05331d](https://github.com/spinnaker/deck/commit/aa05331dce52af5d2b095d0e5456b2dcd1860880))  



## [0.0.179](https://www.github.com/spinnaker/deck/compare/fc85d34b3ee33c2145356176f970b2b6816a7efa...3435ed2c352c061056ea5be7c4d6b6186b2e9a5c) (2018-03-26)


### Changes

chore(core/amazon): bump packages to 0.0.179, 0.0.86 [#5076](https://github.com/spinnaker/deck/pull/5076) ([3435ed2c](https://github.com/spinnaker/deck/commit/3435ed2c352c061056ea5be7c4d6b6186b2e9a5c))  
fix(core): fix submenu alignment, sync running badges [#5075](https://github.com/spinnaker/deck/pull/5075) ([cf54d8fb](https://github.com/spinnaker/deck/commit/cf54d8fb4aa9e7b53afcd5b0536a38c99f4251af))  
feat(core): allow custom warning when users skip wait stage [#5069](https://github.com/spinnaker/deck/pull/5069) ([84d35a83](https://github.com/spinnaker/deck/commit/84d35a83e962f3d8e6039664c76a690846dd4c29))  
fix(provider/kubernetes): v2 incorrectly showing runjob stages [#5072](https://github.com/spinnaker/deck/pull/5072) ([af6a1b10](https://github.com/spinnaker/deck/commit/af6a1b10b88d579d47c4495b055b93c58584485f))  



## [0.0.178](https://www.github.com/spinnaker/deck/compare/f78bb703432c41d0c9d44dec9171a41c92461137...fc85d34b3ee33c2145356176f970b2b6816a7efa) (2018-03-26)


### Changes

chore(core): bump package to 0.0.178 [#5071](https://github.com/spinnaker/deck/pull/5071) ([fc85d34b](https://github.com/spinnaker/deck/commit/fc85d34b3ee33c2145356176f970b2b6816a7efa))  
feat(core): navigate to first item in nav dropdown by default on click [#5070](https://github.com/spinnaker/deck/pull/5070) ([69ff3458](https://github.com/spinnaker/deck/commit/69ff345873623b7d653c43d19fb8284165d85862))  



## [0.0.177](https://www.github.com/spinnaker/deck/compare/0cd5bbc4931cdb66241404be778daa00362161c7...f78bb703432c41d0c9d44dec9171a41c92461137) (2018-03-25)


### Changes

chore(core): bump package to 0.0.177 [#5068](https://github.com/spinnaker/deck/pull/5068) ([f78bb703](https://github.com/spinnaker/deck/commit/f78bb703432c41d0c9d44dec9171a41c92461137))  
style(core): add running-count style to third-level nav badge [#5067](https://github.com/spinnaker/deck/pull/5067) ([854aed60](https://github.com/spinnaker/deck/commit/854aed60a5295a262145c2f5011306cbe4f11c51))  



## [0.0.176](https://www.github.com/spinnaker/deck/compare/1289e4c32cbcc9767709c89b754fd740dc93e7e8...0cd5bbc4931cdb66241404be778daa00362161c7) (2018-03-25)


### Changes

chore(core): bump package to 0.0.176 [#5066](https://github.com/spinnaker/deck/pull/5066) ([0cd5bbc4](https://github.com/spinnaker/deck/commit/0cd5bbc4931cdb66241404be778daa00362161c7))  
style(core): tweak navigation icon sizes/placement [#5065](https://github.com/spinnaker/deck/pull/5065) ([faebfb21](https://github.com/spinnaker/deck/commit/faebfb21d84e5cd0659848069c2532ed8d242fb3))  



## [0.0.175](https://www.github.com/spinnaker/deck/compare/906a30e692064800e44ed566c19191678715ccc2...1289e4c32cbcc9767709c89b754fd740dc93e7e8) (2018-03-25)


### Changes

chore(core): bump package to 0.0.175 [#5064](https://github.com/spinnaker/deck/pull/5064) ([1289e4c3](https://github.com/spinnaker/deck/commit/1289e4c32cbcc9767709c89b754fd740dc93e7e8))  
feat(core): implement categorized navigation [#5063](https://github.com/spinnaker/deck/pull/5063) ([9d59e048](https://github.com/spinnaker/deck/commit/9d59e048ccfe08021ea3045b1791a46e77cdadc1))  
refactor(core): move app refresher, pager duty buttons to components [#5061](https://github.com/spinnaker/deck/pull/5061) ([c128b915](https://github.com/spinnaker/deck/commit/c128b915a5d7ae23b46f8a9283518ddd2a625756))  
fix(core-package): Add explicit do-not-ignores to .npmignore so yalc doesn't exclude them [#5060](https://github.com/spinnaker/deck/pull/5060) ([1c297b6e](https://github.com/spinnaker/deck/commit/1c297b6e0a50498e9294ebb9239b13c1419a0e74))  
chore(package): minify package bundles in production mode only ([a5bde826](https://github.com/spinnaker/deck/commit/a5bde826f2c641c6075fbb3900f740050892eb72))  
feat(core): add categories for application data sources [#5058](https://github.com/spinnaker/deck/pull/5058) ([7f42310f](https://github.com/spinnaker/deck/commit/7f42310f80b288a1467deeb98a8d7510d547adea))  



## [0.0.174](https://www.github.com/spinnaker/deck/compare/fb7c39867fe26875022b751be0e33e82fec34f29...906a30e692064800e44ed566c19191678715ccc2) (2018-03-23)


### Changes

chore(core): bump package to 0.0.174 [#5057](https://github.com/spinnaker/deck/pull/5057) ([906a30e6](https://github.com/spinnaker/deck/commit/906a30e692064800e44ed566c19191678715ccc2))  
Oneliners [#5055](https://github.com/spinnaker/deck/pull/5055) ([3e1d5c3c](https://github.com/spinnaker/deck/commit/3e1d5c3cb070cd173d77d3358d57d4965798171b))  
feat(titus): Support specifying a percentage of instances to relaunch [#5054](https://github.com/spinnaker/deck/pull/5054) ([507fc5e2](https://github.com/spinnaker/deck/commit/507fc5e2b01f84f7b651584e88d714f1d63bcf52))  
fix(core): avoid overflow on Applications actions menu [#5051](https://github.com/spinnaker/deck/pull/5051) ([ab4413e1](https://github.com/spinnaker/deck/commit/ab4413e1fa95801f1a3f858a86ecf0c807b81aa7))  
fix(core/securityGroups): ensure security groups view renders when ready [#5052](https://github.com/spinnaker/deck/pull/5052) ([deb1bbdd](https://github.com/spinnaker/deck/commit/deb1bbdd3cabba811c7ee70d1ea05a2029625ed1))  
fix(core): avoid excessive rerender on popovers with templates [#5049](https://github.com/spinnaker/deck/pull/5049) ([8aa42713](https://github.com/spinnaker/deck/commit/8aa42713333d40051565a74eca12deebdec2c9a6))  
fix(core/entityTag): Fix notifications popover re-mounting during each render. ([5c3b1b9c](https://github.com/spinnaker/deck/commit/5c3b1b9c5b5adbd0597bef77e9a4abb0155d3b05))  



## [0.0.173](https://www.github.com/spinnaker/deck/compare/95aebb16cd693534a3e88b5d5b46af286022beba...fb7c39867fe26875022b751be0e33e82fec34f29) (2018-03-21)


### Changes

chore(core/amazon): bump core to 0.0.173, amazon to 0.0.85 [#5042](https://github.com/spinnaker/deck/pull/5042) ([fb7c3986](https://github.com/spinnaker/deck/commit/fb7c39867fe26875022b751be0e33e82fec34f29))  
fix(*): update icons to font-awesome 5 equivalents [#5040](https://github.com/spinnaker/deck/pull/5040) ([3a5e51da](https://github.com/spinnaker/deck/commit/3a5e51dab4950cc768503eb45eb8fb6f0922e089))  
style(core): override icon for sitemap [#5038](https://github.com/spinnaker/deck/pull/5038) ([8b532810](https://github.com/spinnaker/deck/commit/8b53281045a9d6a5ee06002319c5d914d22c46b1))  
fix(core/pipeline): Update cancel execution icon for FA-v5. ([708415a0](https://github.com/spinnaker/deck/commit/708415a063047ff7ae38e0e664ee7c9557384a5d))  
chore(karma): fix lint errors in tests ([68db0ea6](https://github.com/spinnaker/deck/commit/68db0ea60ba1d8213c3ae5299dc5dac3c3a752eb))  
chore(webpack): update webpack configurations for webpack 4 ([40981eae](https://github.com/spinnaker/deck/commit/40981eae4c404cd833cf186a9df50d3a56b5c927))  



## [0.0.172](https://www.github.com/spinnaker/deck/compare/843c4be98f9efdcf0bdce5a1added5a9ded8aa66...95aebb16cd693534a3e88b5d5b46af286022beba) (2018-03-21)


### Changes

chore(core): bump package to 0.0.172 [#5035](https://github.com/spinnaker/deck/pull/5035) ([95aebb16](https://github.com/spinnaker/deck/commit/95aebb16cd693534a3e88b5d5b46af286022beba))  
fix(executions): fontawesome renamed repeat icon to redo [#5034](https://github.com/spinnaker/deck/pull/5034) ([080e83cb](https://github.com/spinnaker/deck/commit/080e83cb522b402b2dd9c2663d50a491f0253893))  



## [0.0.171](https://www.github.com/spinnaker/deck/compare/17db3421940370d291ad6cff82f501d62b46c3f6...843c4be98f9efdcf0bdce5a1added5a9ded8aa66) (2018-03-20)


### Changes

chore(core/amazon): bump core to 0.0.171, amazon to 0.0.84 [#5033](https://github.com/spinnaker/deck/pull/5033) ([843c4be9](https://github.com/spinnaker/deck/commit/843c4be98f9efdcf0bdce5a1added5a9ded8aa66))  
fix(artifacts): remove unreachable error markup [#5031](https://github.com/spinnaker/deck/pull/5031) ([d9caaab5](https://github.com/spinnaker/deck/commit/d9caaab5db1c0b4e8011ee12cce9bf600771ad73))  
fix(core): do not render application list if all are filtered out [#5032](https://github.com/spinnaker/deck/pull/5032) ([fa56cb7a](https://github.com/spinnaker/deck/commit/fa56cb7a3b8c69bc5cdb9a0315f7d050f7d80c60))  
chore(core): upgrade to font-awesome 5 [#5029](https://github.com/spinnaker/deck/pull/5029) ([c2bdbf72](https://github.com/spinnaker/deck/commit/c2bdbf727746223e1c9e0a1d7fc56018a0e81736))  
fix(bake): Fix JavaScript error on bake stage load [#5027](https://github.com/spinnaker/deck/pull/5027) ([64e2ac95](https://github.com/spinnaker/deck/commit/64e2ac95cdf2326f41e9f102c1052e2539e28900))  
feat(artifacts): Support embedded/base64 artifact type [#5021](https://github.com/spinnaker/deck/pull/5021) ([71239717](https://github.com/spinnaker/deck/commit/71239717f86858c437167a4e2d453503abdd9b58))  
fix(tooltip): updating conditional on expression tooltip) [#5028](https://github.com/spinnaker/deck/pull/5028) ([b25c9326](https://github.com/spinnaker/deck/commit/b25c93269feafa78288901fb7e0d103da05f3b5e))  
fix(core/pipeline): Fix closing of "new pipeline" modal using X or Cancel buttons [#5026](https://github.com/spinnaker/deck/pull/5026) ([c22580e0](https://github.com/spinnaker/deck/commit/c22580e02d371b3c946f8cac9e6ae267e96e5d60))  



## [0.0.170](https://www.github.com/spinnaker/deck/compare/597b68c245048bd717cbfc369eb17884bbbe7d1d...17db3421940370d291ad6cff82f501d62b46c3f6) (2018-03-19)


### Changes

chore(core): bump package to 0.0.170 [#5023](https://github.com/spinnaker/deck/pull/5023) ([17db3421](https://github.com/spinnaker/deck/commit/17db3421940370d291ad6cff82f501d62b46c3f6))  
refactor(core/task+amazon/common): Reactify UserVerification and AwsModalFooter [#5015](https://github.com/spinnaker/deck/pull/5015) ([5bd7e6d2](https://github.com/spinnaker/deck/commit/5bd7e6d2a9c897a9976cd549362306019c02ea16))  
fix(amazon/deploy): Do not destroy SpEL based load balancers in deploy config [#5016](https://github.com/spinnaker/deck/pull/5016) ([be99e7fe](https://github.com/spinnaker/deck/commit/be99e7fe3315d20279559a2677bd7e408b32303d))  
feat(provider/kubernetes): trim runJob logs from executions until explicitly requested [#5014](https://github.com/spinnaker/deck/pull/5014) ([cab2cec4](https://github.com/spinnaker/deck/commit/cab2cec4328dfe509a5e30e84942654caeef822f))  
fix(core): count down remaining time in wait stage details [#5009](https://github.com/spinnaker/deck/pull/5009) ([a1978294](https://github.com/spinnaker/deck/commit/a19782949bc6db0de92d2a83264a6c7b9fb996da))  



## [0.0.169](https://www.github.com/spinnaker/deck/compare/7923da734d8fb329c3637ef68adaedde0d949511...597b68c245048bd717cbfc369eb17884bbbe7d1d) (2018-03-14)


### Changes

chore(core): bump package to 0.0.169 [#5006](https://github.com/spinnaker/deck/pull/5006) ([597b68c2](https://github.com/spinnaker/deck/commit/597b68c245048bd717cbfc369eb17884bbbe7d1d))  
feat(core): Support warning message type for stages [#5005](https://github.com/spinnaker/deck/pull/5005) ([760b08ad](https://github.com/spinnaker/deck/commit/760b08adf5ac11225d4bf004f0c481e405edf45f))  
fix(core): render remaining wait on wait stage popover [#5004](https://github.com/spinnaker/deck/pull/5004) ([c0d8cf51](https://github.com/spinnaker/deck/commit/c0d8cf5114f49a116d8b74f9bde0331a6313cf73))  
fix(core): ensure stage comments are in string format [#5003](https://github.com/spinnaker/deck/pull/5003) ([e010be07](https://github.com/spinnaker/deck/commit/e010be07d20545da410036d541f9151bbee56391))  
fix(core): allow expressions in pipeline stage application field [#5002](https://github.com/spinnaker/deck/pull/5002) ([164d32e3](https://github.com/spinnaker/deck/commit/164d32e3a5ac36e4307132bbe44ac3a9261d0bae))  
fix(core/help): Allow target="_blank" in links in help popover [#5000](https://github.com/spinnaker/deck/pull/5000) ([17c8eef5](https://github.com/spinnaker/deck/commit/17c8eef559129eeea9db529a3f19250b4c22f10f))  
fix(core): fix build link to parent pipeline [#4999](https://github.com/spinnaker/deck/pull/4999) ([274690d8](https://github.com/spinnaker/deck/commit/274690d8901222c9c623ec2be143427646a54310))  
feat(core): omit unsupported cloud provider data from clusters [#4998](https://github.com/spinnaker/deck/pull/4998) ([b2d60891](https://github.com/spinnaker/deck/commit/b2d608919e54bb212165855c11ecfebb5ff46cae))  
refactor(amazon/core): move targetHealthyPercentage component to core [#4997](https://github.com/spinnaker/deck/pull/4997) ([bb33107f](https://github.com/spinnaker/deck/commit/bb33107f424c483ea77b1a447b3f1693c2a0c6bb))  
fix(core): fix linting [#4996](https://github.com/spinnaker/deck/pull/4996) ([69310120](https://github.com/spinnaker/deck/commit/69310120bc97f6699f4aadfbf85301f6511360fd))  



## [0.0.168](https://www.github.com/spinnaker/deck/compare/5d4485b74e6d4f2d2f178c646829235b9334c043...7923da734d8fb329c3637ef68adaedde0d949511) (2018-03-13)


### Changes

chore(core): bump package to 0.0.168 [#4995](https://github.com/spinnaker/deck/pull/4995) ([7923da73](https://github.com/spinnaker/deck/commit/7923da734d8fb329c3637ef68adaedde0d949511))  
feat(core): adds ability to define a dockerInsight link [#4994](https://github.com/spinnaker/deck/pull/4994) ([c3342e3b](https://github.com/spinnaker/deck/commit/c3342e3b5c6c1d50866fba6364a74c9bce5a316f))  
feat(core/presentation): Provide `hidePopover()` prop to popover contents component [#4992](https://github.com/spinnaker/deck/pull/4992) ([75a8d729](https://github.com/spinnaker/deck/commit/75a8d729551bb76e770baee4d2e10a068b7a5206))  



## [0.0.167](https://www.github.com/spinnaker/deck/compare/cca2f070ec0ad1466775892015ca85e39f215b4b...5d4485b74e6d4f2d2f178c646829235b9334c043) (2018-03-13)


### Changes

chore(core): bump package to 0.0.167 [#4991](https://github.com/spinnaker/deck/pull/4991) ([5d4485b7](https://github.com/spinnaker/deck/commit/5d4485b74e6d4f2d2f178c646829235b9334c043))  
fix(core): avoid rendering wizard pages before they have registered [#4990](https://github.com/spinnaker/deck/pull/4990) ([931f8a39](https://github.com/spinnaker/deck/commit/931f8a396d6109898e78ed57830161bc9afbd654))  
feat(core/pipeline): Allow overriding of default timeout for "Script stage" from UI [#4989](https://github.com/spinnaker/deck/pull/4989) ([33d70fee](https://github.com/spinnaker/deck/commit/33d70fee194ab886c7cc91ca782ca101c069828f))  



## [0.0.166](https://www.github.com/spinnaker/deck/compare/fda1f45a1aa99fc0ee43d542c0cd5787d7eee228...cca2f070ec0ad1466775892015ca85e39f215b4b) (2018-03-12)


### Changes

chore(*): Bump core and amazon [#4987](https://github.com/spinnaker/deck/pull/4987) ([cca2f070](https://github.com/spinnaker/deck/commit/cca2f070ec0ad1466775892015ca85e39f215b4b))  
feat(artifacts): Enable GCE deploy stage to deploy an artifact [#4986](https://github.com/spinnaker/deck/pull/4986) ([d4e61e5c](https://github.com/spinnaker/deck/commit/d4e61e5c456a8f7eacaf5ae96b05ce67146346d0))  
feat(core): allow markdown in stage-level comments [#4988](https://github.com/spinnaker/deck/pull/4988) ([7cc0a153](https://github.com/spinnaker/deck/commit/7cc0a15396d3bf7b210f673d2a6c0a6eb3e164cf))  
feat(core): support markdown in tooltips and popovers [#4985](https://github.com/spinnaker/deck/pull/4985) ([58d15274](https://github.com/spinnaker/deck/commit/58d1527426affd5ed5ce46f05078cc2b123915a7))  
feat(core/triggers): More relevant info when selecting execution [#4983](https://github.com/spinnaker/deck/pull/4983) ([53395ac8](https://github.com/spinnaker/deck/commit/53395ac820c78a86f879f5cbae0f32a2a475cdd2))  



## [0.0.165](https://www.github.com/spinnaker/deck/compare/e04770a3ced7b0d6aed6441ab2bfb3033d44bc37...fda1f45a1aa99fc0ee43d542c0cd5787d7eee228) (2018-03-08)


### Changes

chore(core): bump package to 0.0.165 [#4982](https://github.com/spinnaker/deck/pull/4982) ([fda1f45a](https://github.com/spinnaker/deck/commit/fda1f45a1aa99fc0ee43d542c0cd5787d7eee228))  
fix(core): omit colon if there is no server group sequence [#4976](https://github.com/spinnaker/deck/pull/4976) ([e69526dd](https://github.com/spinnaker/deck/commit/e69526dd9a722d6fd1114b9d470cd0d70234b881))  
feat(rollback): Support rollback on failure for Rolling Red/Black [#4981](https://github.com/spinnaker/deck/pull/4981) ([29e544fa](https://github.com/spinnaker/deck/commit/29e544fa39dc7e664000df53a6a084ad459ae9b7))  
feat(core/serverGroup): Use flexbox instead of bootstrap grid to layout running tasks [#4980](https://github.com/spinnaker/deck/pull/4980) ([9f7d6a3c](https://github.com/spinnaker/deck/commit/9f7d6a3ceaf917412f0ff34f1eee39c28bef132c))  
fix(core/presentation): Increase specificity of flex-pull-right within flex-container-h [#4979](https://github.com/spinnaker/deck/pull/4979) ([0684cad7](https://github.com/spinnaker/deck/commit/0684cad762251623a606d537f7c944989b204b30))  
feat(core/presentation): Add margin selector for flex-container-h layout [#4978](https://github.com/spinnaker/deck/pull/4978) ([e9cacb83](https://github.com/spinnaker/deck/commit/e9cacb83542ef9ce68202fcfe32701184fc311d0))  
fix(core/entityTag): Fix background colors of notification categories ([40e77678](https://github.com/spinnaker/deck/commit/40e77678e855b2d97eaecb4c52c88ebb2cfa3caa))  
fix(pipeline_templates): prevent [object Object] when object's defaultValue is provided [#4975](https://github.com/spinnaker/deck/pull/4975) ([93a20c57](https://github.com/spinnaker/deck/commit/93a20c5723c9cf68e117446d796712cd9c85efda))  



## [0.0.164](https://www.github.com/spinnaker/deck/compare/eba54f535bfe65c843ac42cb3db055f0cfed4ae2...e04770a3ced7b0d6aed6441ab2bfb3033d44bc37) (2018-03-08)


### Changes

chore(core): bump package to 0.0.164 [#4974](https://github.com/spinnaker/deck/pull/4974) ([e04770a3](https://github.com/spinnaker/deck/commit/e04770a3ced7b0d6aed6441ab2bfb3033d44bc37))  
feat(core): expose spel evaluator in spinnaker/core [#4972](https://github.com/spinnaker/deck/pull/4972) ([873cb702](https://github.com/spinnaker/deck/commit/873cb7024c2cd941cf04da0816b8bcf7b0fb943a))  
feat(core): remove 'N/A' if no server group sequence exists [#4973](https://github.com/spinnaker/deck/pull/4973) ([5a7eed5d](https://github.com/spinnaker/deck/commit/5a7eed5dd289da23796dbeec3270a9b9e28adf56))  
fixed a spinner that was off [#4965](https://github.com/spinnaker/deck/pull/4965) ([fab805f3](https://github.com/spinnaker/deck/commit/fab805f3e38f24e6c0ea9a99694b9027918787d7))  
fix(core/overrideRegistry): fix lint error ([11aa394e](https://github.com/spinnaker/deck/commit/11aa394e77cb59fff6ba1b97eb2c2cfb5bcc09bf))  



## [0.0.163](https://www.github.com/spinnaker/deck/compare/85b5626eea2e92e75017ac65a79e2f8702704969...eba54f535bfe65c843ac42cb3db055f0cfed4ae2) (2018-03-07)


### Changes

chore(core): bump package to 0.0.163 ([eba54f53](https://github.com/spinnaker/deck/commit/eba54f535bfe65c843ac42cb3db055f0cfed4ae2))  
fix(core/overrideRegistry): Copy static class properties to Overridable component ([5cf8c4b4](https://github.com/spinnaker/deck/commit/5cf8c4b48fcd9d98e6e2e1c0efe884e8fd9768f8))  



## [0.0.162](https://www.github.com/spinnaker/deck/compare/67ce54b9830c2eea3b74e2f78b6b45f3cddccd60...85b5626eea2e92e75017ac65a79e2f8702704969) (2018-03-07)


### Changes

chore(core): bump package to 0.0.162 ([85b5626e](https://github.com/spinnaker/deck/commit/85b5626eea2e92e75017ac65a79e2f8702704969))  
fix(core/application): fix scrolling of applications list [#4966](https://github.com/spinnaker/deck/pull/4966) ([c832ff9d](https://github.com/spinnaker/deck/commit/c832ff9d47453eb7b13767950d60d7246c9104dc))  
feat(core/serverGroup): Refactor to smaller components and make them Overridable [#4941](https://github.com/spinnaker/deck/pull/4941) ([73e002cd](https://github.com/spinnaker/deck/commit/73e002cddcc9dd0512e870c06f805570411f7459))  
fix(core): Fix dismissal of new server group template modal [#4954](https://github.com/spinnaker/deck/pull/4954) ([f52d61a7](https://github.com/spinnaker/deck/commit/f52d61a7c1e440259c0b9addce8fb4e9183ff792))  



## [0.0.161](https://www.github.com/spinnaker/deck/compare/bb7d45bb5c484f256939be5db12df7dfffc74d57...67ce54b9830c2eea3b74e2f78b6b45f3cddccd60) (2018-03-05)


### Changes

chore(core): Bump to 0.0.161 [#4960](https://github.com/spinnaker/deck/pull/4960) ([67ce54b9](https://github.com/spinnaker/deck/commit/67ce54b9830c2eea3b74e2f78b6b45f3cddccd60))  
fix(core/pagerduty): Fix tests for new service interface [#4957](https://github.com/spinnaker/deck/pull/4957) ([c5c93ec4](https://github.com/spinnaker/deck/commit/c5c93ec4a7802228508f0d18f9d60501f1f30d74))  
chore(core/pagerDuty): Add status to IPagerDutyService [#4956](https://github.com/spinnaker/deck/pull/4956) ([3af44c96](https://github.com/spinnaker/deck/commit/3af44c969e4837d20a7e60372b355633acf321f4))  



## [0.0.160](https://www.github.com/spinnaker/deck/compare/fd028b12337793da9b924682aa04e55831cd9174...bb7d45bb5c484f256939be5db12df7dfffc74d57) (2018-03-03)


### Changes

chore(core): bump package to 0.0.160 [#4953](https://github.com/spinnaker/deck/pull/4953) ([bb7d45bb](https://github.com/spinnaker/deck/commit/bb7d45bb5c484f256939be5db12df7dfffc74d57))  
feat(core): allow custom warning when users skip an execution window [#4952](https://github.com/spinnaker/deck/pull/4952) ([10e5dfe2](https://github.com/spinnaker/deck/commit/10e5dfe278d791a4187863f6425d5ed7d97c6a1f))  
feat(core): allow parameters to be hidden based on other params [#4948](https://github.com/spinnaker/deck/pull/4948) ([d4696478](https://github.com/spinnaker/deck/commit/d4696478db16d75a6c4222e2f7a76ffdb2b43185))  



## [0.0.159](https://www.github.com/spinnaker/deck/compare/615bff3f025c4be5b5882f920a7028b466dbaa88...fd028b12337793da9b924682aa04e55831cd9174) (2018-03-02)


### Changes

chore(core): bump to 0.0.159 ([fd028b12](https://github.com/spinnaker/deck/commit/fd028b12337793da9b924682aa04e55831cd9174))  
chore(*): Prepare for upgrading to react 16 [#4947](https://github.com/spinnaker/deck/pull/4947) ([0b9ebbaa](https://github.com/spinnaker/deck/commit/0b9ebbaacd9635efba39758f214e9e562d5efc2a))  
fix(core/search): Get rid of useless prompt, hide 'Recently Viewed' header if no history [#4937](https://github.com/spinnaker/deck/pull/4937) ([a960ed53](https://github.com/spinnaker/deck/commit/a960ed53c86115fbfc2f6836fdf5ba40f519b56d))  
fix(core): correct category on recently viewed analytics events [#4933](https://github.com/spinnaker/deck/pull/4933) ([8ef9acca](https://github.com/spinnaker/deck/commit/8ef9acca33f929b75c380b1bf699876578427f63))  
fix(artifacts): Fix no expected artifacts message ([9a8f9b3d](https://github.com/spinnaker/deck/commit/9a8f9b3dbdaaa492176395d190e877cc59df3704))  



## [0.0.158](https://www.github.com/spinnaker/deck/compare/72479f279c9c5462953172b07741d10b0ab45338...615bff3f025c4be5b5882f920a7028b466dbaa88) (2018-02-28)


### Changes

chore(core): bump package to 0.0.158 ([615bff3f](https://github.com/spinnaker/deck/commit/615bff3f025c4be5b5882f920a7028b466dbaa88))  
fix(core/instance): Fix instance details loading on first click ([a035097c](https://github.com/spinnaker/deck/commit/a035097cccc36e49eb0ff6254c7b3d0a643d19cc))  
feat(core/notification): Add a quick one-off slack channel affordance [#4930](https://github.com/spinnaker/deck/pull/4930) ([990cd107](https://github.com/spinnaker/deck/commit/990cd107242b69294f6bc0a29e783f87f74a24c2))  



## [0.0.157](https://www.github.com/spinnaker/deck/compare/d88b5934ed51d1694676b3a3e1fa9ed055767699...72479f279c9c5462953172b07741d10b0ab45338) (2018-02-28)


### Changes

chore(core): Bump to 0.0.157 [#4928](https://github.com/spinnaker/deck/pull/4928) ([72479f27](https://github.com/spinnaker/deck/commit/72479f279c9c5462953172b07741d10b0ab45338))  
fix(core/pipelines): force refresh pipeline configs after save completes [#4925](https://github.com/spinnaker/deck/pull/4925) ([b470ff89](https://github.com/spinnaker/deck/commit/b470ff89a69f2dcccf7c45683fbe645a5b40f933))  
feat(provider/kubernetes): delineate default artifacts in executions [#4924](https://github.com/spinnaker/deck/pull/4924) ([9578dc8c](https://github.com/spinnaker/deck/commit/9578dc8c4942a11a2ca9048d0ea517891cb65c94))  
fix(core/instance): Update InstanceDetails when props change ([7174a99f](https://github.com/spinnaker/deck/commit/7174a99f08a569db1b53cf117ca21a45357d8b23))  
Making sure styleguide renders in deck [#4922](https://github.com/spinnaker/deck/pull/4922) ([2c9a101e](https://github.com/spinnaker/deck/commit/2c9a101e66302695c0423a6cdec0212552c2cd03))  
chore(core): add analytics to recently viewed items [#4920](https://github.com/spinnaker/deck/pull/4920) ([4310381d](https://github.com/spinnaker/deck/commit/4310381d432f91df59ae2febea678bf59f3d2a30))  
feat(core/wizard): Support a waiting state for sections ([abfc180a](https://github.com/spinnaker/deck/commit/abfc180a1a8920812cf9885835a32f5859be78dc))  



## [0.0.156](https://www.github.com/spinnaker/deck/compare/5134200b3faf44f72f139ba7d8c69221d6e6d01a...d88b5934ed51d1694676b3a3e1fa9ed055767699) (2018-02-28)


### Changes

chore(core): bump package to 0.0.156 [#4919](https://github.com/spinnaker/deck/pull/4919) ([d88b5934](https://github.com/spinnaker/deck/commit/d88b5934ed51d1694676b3a3e1fa9ed055767699))  
feat(pubsub): specify run-as-user for pubsub triggers [#4918](https://github.com/spinnaker/deck/pull/4918) ([890b942c](https://github.com/spinnaker/deck/commit/890b942cd0335db1595d6f1037491e20114447b3))  



## [0.0.155](https://www.github.com/spinnaker/deck/compare/d90ba77743b99b9a0b0674ebc4ba91af93f071c5...5134200b3faf44f72f139ba7d8c69221d6e6d01a) (2018-02-27)


### Changes

chore(core): bump package to 0.0.155 [#4914](https://github.com/spinnaker/deck/pull/4914) ([5134200b](https://github.com/spinnaker/deck/commit/5134200b3faf44f72f139ba7d8c69221d6e6d01a))  
style(core): wrap Overridable expressions in parens [#4913](https://github.com/spinnaker/deck/pull/4913) ([f3874dcc](https://github.com/spinnaker/deck/commit/f3874dccc39aedd1b0f463911f5a2b553621050f))  
fix(artifacts): removing artifact from 'produces artifacts' throws exception [#4911](https://github.com/spinnaker/deck/pull/4911) ([3ab15066](https://github.com/spinnaker/deck/commit/3ab15066109521def936d90f02c739c82a846757))  
feat(core/overrideRegistry): Pass the original component to the overriding component as a prop [#4910](https://github.com/spinnaker/deck/pull/4910) ([6a89eb96](https://github.com/spinnaker/deck/commit/6a89eb9678995e84daaa3742891a2caafbdcf26e))  
style(core): postioned the entity notifications to the right [#4907](https://github.com/spinnaker/deck/pull/4907) ([6f22937d](https://github.com/spinnaker/deck/commit/6f22937d93088ee5fb8902eeba8fb4a8e427f722))  
feat(core/cluster): Allow task matchers to be configured from deck-customized [#4909](https://github.com/spinnaker/deck/pull/4909) ([01096fe1](https://github.com/spinnaker/deck/commit/01096fe1c8bf5486f42574127c2aaedc24953bae))  
feat(core/pipelines): allow user-friendly labels on pipeline parameters [#4912](https://github.com/spinnaker/deck/pull/4912) ([4c435f7c](https://github.com/spinnaker/deck/commit/4c435f7c9b62f42dfeb45999f589b188b63e639f))  
feat(core) add a help menu with links to docs and community [#4903](https://github.com/spinnaker/deck/pull/4903) ([4849bdfa](https://github.com/spinnaker/deck/commit/4849bdfa1a9d6860527f0e9fbc099d30b459a606))  
chore(*): Update typescript and tslint dependencies to latest [#4905](https://github.com/spinnaker/deck/pull/4905) ([e8d39739](https://github.com/spinnaker/deck/commit/e8d39739d750d392371465dc88e0b459ab95cd66))  



## [0.0.154](https://www.github.com/spinnaker/deck/compare/3ac16f910eb074df74d7d9def26635c39dd8a012...d90ba77743b99b9a0b0674ebc4ba91af93f071c5) (2018-02-22)


### Changes

chore(core): Bump package to 0.0.154 [#4904](https://github.com/spinnaker/deck/pull/4904) ([d90ba777](https://github.com/spinnaker/deck/commit/d90ba77743b99b9a0b0674ebc4ba91af93f071c5))  
refactor(core): Remove formsy; replace-ish with formik [#4902](https://github.com/spinnaker/deck/pull/4902) ([5ae40835](https://github.com/spinnaker/deck/commit/5ae40835bd8999c43a545e851b458edc2748cc51))  



## [0.0.153](https://www.github.com/spinnaker/deck/compare/1ba48c6a6fca5b7f6c97a63b7e09aea7902409a6...3ac16f910eb074df74d7d9def26635c39dd8a012) (2018-02-21)


### Changes

chore(core): bump package to 0.0.153 [#4900](https://github.com/spinnaker/deck/pull/4900) ([3ac16f91](https://github.com/spinnaker/deck/commit/3ac16f910eb074df74d7d9def26635c39dd8a012))  
fix(core/pipelines): force data refresh on pipeline creation [#4897](https://github.com/spinnaker/deck/pull/4897) ([272b89e1](https://github.com/spinnaker/deck/commit/272b89e1204514817cbccb293053bf5c1c373c82))  
style(core): replace nano spinners on inline buttons [#4898](https://github.com/spinnaker/deck/pull/4898) ([ad0cda4d](https://github.com/spinnaker/deck/commit/ad0cda4def9357ad7a1aea0cf761fd92fcce58d1))  
fix(core): allow grouping by time on cancelled executions [#4896](https://github.com/spinnaker/deck/pull/4896) ([6a79063e](https://github.com/spinnaker/deck/commit/6a79063e546c110069e464b9ca31eda0f88a5de6))  
style(all): Updated references to styleguide package [#4869](https://github.com/spinnaker/deck/pull/4869) ([211647b7](https://github.com/spinnaker/deck/commit/211647b77ee73d49fa8f94fc008daf44c8a49470))  



## [0.0.152](https://www.github.com/spinnaker/deck/compare/721e00bff4002b1cde7ff76a2ee60842a7998b14...1ba48c6a6fca5b7f6c97a63b7e09aea7902409a6) (2018-02-21)


### Changes

chore(core): bump package to 0.0.152 [#4892](https://github.com/spinnaker/deck/pull/4892) ([1ba48c6a](https://github.com/spinnaker/deck/commit/1ba48c6a6fca5b7f6c97a63b7e09aea7902409a6))  
fix(core/reactShims): Fix first render when  receiving props before $scope is set [#4890](https://github.com/spinnaker/deck/pull/4890) ([0bb815eb](https://github.com/spinnaker/deck/commit/0bb815ebe9ee391075ba234de862668737109ca3))  
fix(core/pipeline): Fix pipeline config - add stage that uses BaseProviderStageCtrl [#4885](https://github.com/spinnaker/deck/pull/4885) ([59783aea](https://github.com/spinnaker/deck/commit/59783aea42e290ca4c1e7e16fc4d91b3fb80005c))  
fix(core/overrideRegistry): Use account name (not id) when choosing overriding component [#4889](https://github.com/spinnaker/deck/pull/4889) ([a2033a82](https://github.com/spinnaker/deck/commit/a2033a82c832a637c2288d3d122cb0e832b95f1e))  



## [0.0.151](https://www.github.com/spinnaker/deck/compare/5d570b162945a94caea82b0ee41ecbcd1921b8c3...721e00bff4002b1cde7ff76a2ee60842a7998b14) (2018-02-20)


### Changes

chore(core): bump package to 0.0.151 [#4887](https://github.com/spinnaker/deck/pull/4887) ([721e00bf](https://github.com/spinnaker/deck/commit/721e00bff4002b1cde7ff76a2ee60842a7998b14))  
feat(core/executions): allow pipeline rerun with same parameters [#4872](https://github.com/spinnaker/deck/pull/4872) ([d75df6f8](https://github.com/spinnaker/deck/commit/d75df6f8bc3aa93fb9abcab504bd85f7617d5159))  
feat(core): update permissions help text to include warning about cache delay [#4883](https://github.com/spinnaker/deck/pull/4883) ([84846f4b](https://github.com/spinnaker/deck/commit/84846f4b84361c771013995fcce8c25c0d649f52))  
fix(core/presentation): Fix react modal wrapper [#4884](https://github.com/spinnaker/deck/pull/4884) ([747cc545](https://github.com/spinnaker/deck/commit/747cc545357eb062c6301f83aba2bbb52bb0f626))  
refactor(core/presentation): Change how ReactModal works [#4874](https://github.com/spinnaker/deck/pull/4874) ([895fb3fe](https://github.com/spinnaker/deck/commit/895fb3fe7bb434778bc733c1f8d26e90bddf4165))  
fix(core/entityTag): Fix error message after successful entity tag updates [#4873](https://github.com/spinnaker/deck/pull/4873) ([56cd1c01](https://github.com/spinnaker/deck/commit/56cd1c0130a055a4454341aba0a8e68c37a42dd0))  
fix(artifacts): show artifact execution config [#4870](https://github.com/spinnaker/deck/pull/4870) ([ed5ff510](https://github.com/spinnaker/deck/commit/ed5ff510b47c2ca9f2b801dd533009e3ad398490))  
fix(core/overrideRegistry): Handle Override component synchronously, when possible [#4868](https://github.com/spinnaker/deck/pull/4868) ([d4a75982](https://github.com/spinnaker/deck/commit/d4a7598275a9bee9b89b8cdb3c79cca1c94d578d))  
fix(core/healthCounts): Propagate css class to health counts in either case [#4867](https://github.com/spinnaker/deck/pull/4867) ([2ab4270c](https://github.com/spinnaker/deck/commit/2ab4270c7fea3593f159b439368d614e9746153a))  
feat(core/angular): Improve error message when angular module dep is undefined. Add overload to $q for normalizing a real promise into a fake angularjs promise. [#4866](https://github.com/spinnaker/deck/pull/4866) ([c67cd0eb](https://github.com/spinnaker/deck/commit/c67cd0eb97f4e5de000e6fbdd6e0170e1042b929))  
fix(provider/kubernetes): exclude manifest stages from v1 [#4863](https://github.com/spinnaker/deck/pull/4863) ([f0a8d6b9](https://github.com/spinnaker/deck/commit/f0a8d6b96c2b0dcea6db9aebc65ca108874b2f7b))  



## [0.0.150](https://www.github.com/spinnaker/deck/compare/11eb47f63d3381bd0aad632255cac4c685c6af60...5d570b162945a94caea82b0ee41ecbcd1921b8c3) (2018-02-15)


### Changes

chore(core): bump package to 0.0.150 [#4864](https://github.com/spinnaker/deck/pull/4864) ([5d570b16](https://github.com/spinnaker/deck/commit/5d570b162945a94caea82b0ee41ecbcd1921b8c3))  
feat(pubsub): pubsub providers list pulled from settings [#4861](https://github.com/spinnaker/deck/pull/4861) ([7953dd6a](https://github.com/spinnaker/deck/commit/7953dd6ada8cf36db0a76a4dc972d0a43cf00454))  
feat(artifacts): allow specific stages to 'produce' artifacts [#4858](https://github.com/spinnaker/deck/pull/4858) ([388953db](https://github.com/spinnaker/deck/commit/388953dbfea640477845f4d9aac455fe382a31d7))  



## [0.0.149](https://www.github.com/spinnaker/deck/compare/92ea222308e6dc925760ddcfee82140568dda828...11eb47f63d3381bd0aad632255cac4c685c6af60) (2018-02-13)


### Changes

chore(core): bump package to 0.0.149 [#4852](https://github.com/spinnaker/deck/pull/4852) ([11eb47f6](https://github.com/spinnaker/deck/commit/11eb47f63d3381bd0aad632255cac4c685c6af60))  
fix(core): allow overflow on details panel for menus [#4850](https://github.com/spinnaker/deck/pull/4850) ([902b3c48](https://github.com/spinnaker/deck/commit/902b3c4856afd660c25f07d166ed17175a785d5f))  
refactor(core): convert security group filter service to TS [#4849](https://github.com/spinnaker/deck/pull/4849) ([8bb33ef5](https://github.com/spinnaker/deck/commit/8bb33ef5baa3e8ac189fe00986f6ad41364c3360))  
refactor(core/filterModel): Refactor filter.model.service to a typescript class [#4846](https://github.com/spinnaker/deck/pull/4846) ([38bddeb8](https://github.com/spinnaker/deck/commit/38bddeb87743a3fb623e460122e34588e1fda5fc))  
fix(core): restore pod header widths to fill container [#4845](https://github.com/spinnaker/deck/pull/4845) ([dc0eaa1f](https://github.com/spinnaker/deck/commit/dc0eaa1f2d1e871d4d547a412f4c39db44fdd16e))  
fix(core/stages): Hide excluded providers [#4831](https://github.com/spinnaker/deck/pull/4831) ([d6268c6c](https://github.com/spinnaker/deck/commit/d6268c6c4aa8e70ac00a2931a12301a394e8208e))  
feat(core/pipelines): allow triggering of execution via deep link [#4844](https://github.com/spinnaker/deck/pull/4844) ([6fc700cb](https://github.com/spinnaker/deck/commit/6fc700cb2c62e4bbb1537d62df2b7b337ec6138c))  
fix(core): avoid scrollbars in cluster pod header [#4836](https://github.com/spinnaker/deck/pull/4836) ([9e6a40a5](https://github.com/spinnaker/deck/commit/9e6a40a56e074421da94939d892b2489730fa7b1))  
fix(core/accounts): fix versioned account lookup [#4837](https://github.com/spinnaker/deck/pull/4837) ([c5eecf77](https://github.com/spinnaker/deck/commit/c5eecf775d4a054932bb619308e8a01d98e24dee))  
refactor(amazon): remove local storage caches: instance types, load balancers [#4777](https://github.com/spinnaker/deck/pull/4777) ([4cf3239d](https://github.com/spinnaker/deck/commit/4cf3239d43703d52a33f045fc9c41512f035b9a2))  
fix(core): add authorized field to test accounts [#4832](https://github.com/spinnaker/deck/pull/4832) ([6cb1e9ab](https://github.com/spinnaker/deck/commit/6cb1e9abedeaa073ccfb641ad1ff8e513129453b))  
fix(provider/kubernetes): allow stages to exclude specific provider versions [#4825](https://github.com/spinnaker/deck/pull/4825) ([9ce1e64d](https://github.com/spinnaker/deck/commit/9ce1e64d73638a5548f5ed48d7bc3ac1bbcfa305))  



## [0.0.148](https://www.github.com/spinnaker/deck/compare/651a88af2fc1051d34b68dfab1446fe55d945e22...92ea222308e6dc925760ddcfee82140568dda828) (2018-02-09)


### Changes

chore(core): Bump to 0.0.148 [#4830](https://github.com/spinnaker/deck/pull/4830) ([92ea2223](https://github.com/spinnaker/deck/commit/92ea222308e6dc925760ddcfee82140568dda828))  
fix(core/*): Fix viewing of account badges, and servergroup/instance/foo details for accounts you don't have access to [#4829](https://github.com/spinnaker/deck/pull/4829) ([9cc1dfdb](https://github.com/spinnaker/deck/commit/9cc1dfdba24ebba939de9863e178b78ab7a7d3e6))  
feat(dryrun): some style changes [#4826](https://github.com/spinnaker/deck/pull/4826) ([96a8e9ef](https://github.com/spinnaker/deck/commit/96a8e9ef50700cdacdfd4e6dc5ccf5435238bba9))  
Fix a couple server group details issues [#4828](https://github.com/spinnaker/deck/pull/4828) ([ca478a05](https://github.com/spinnaker/deck/commit/ca478a05b7a24f2caba38e2eceb8f85d248384c0))  
chore(help): Swap server groups for ASGs in Chaos Monkey tooltip. [#4824](https://github.com/spinnaker/deck/pull/4824) ([eb59137d](https://github.com/spinnaker/deck/commit/eb59137d9236451251c2f185359c405cad7fbd40))  
feat(provider/kubernetes): v2 Add find artifacts from resource stage [#4732](https://github.com/spinnaker/deck/pull/4732) ([99567048](https://github.com/spinnaker/deck/commit/99567048150be33fa435b5be6507b0762fbdaa55))  
Added variables to the zindex styleguide file [#4796](https://github.com/spinnaker/deck/pull/4796) ([faa2e727](https://github.com/spinnaker/deck/commit/faa2e72704546c381dae6157c9632694a2f90757))  



## [0.0.147](https://www.github.com/spinnaker/deck/compare/e6be1ce10607efbbaf71999c4d97b2377e0c89d7...651a88af2fc1051d34b68dfab1446fe55d945e22) (2018-02-08)


### Changes

chore(core): Bump to 0.0.147 ([651a88af](https://github.com/spinnaker/deck/commit/651a88af2fc1051d34b68dfab1446fe55d945e22))  
feat(artifacts): better default artifact behavior [#4817](https://github.com/spinnaker/deck/pull/4817) ([7391f45a](https://github.com/spinnaker/deck/commit/7391f45afbb953131843f3c13d2aa70e01693109))  
fix(core/serverGroup): Fallback to no providerVersion if user does not have account permission [#4818](https://github.com/spinnaker/deck/pull/4818) ([6af1dd5e](https://github.com/spinnaker/deck/commit/6af1dd5efbec28e36fa16b5447c68be8909e1519))  



## [0.0.146](https://www.github.com/spinnaker/deck/compare/37cb614d0c55f00a6b88d3d916e4805cb50b282f...e6be1ce10607efbbaf71999c4d97b2377e0c89d7) (2018-02-07)


### Changes

bump version [#4816](https://github.com/spinnaker/deck/pull/4816) ([e6be1ce1](https://github.com/spinnaker/deck/commit/e6be1ce10607efbbaf71999c4d97b2377e0c89d7))  
feat(provider/kubernetes): List trigger artifacts in execution status [#4813](https://github.com/spinnaker/deck/pull/4813) ([b00cdc04](https://github.com/spinnaker/deck/commit/b00cdc049d923996116f896deec7b875199c94ea))  
fix(core): Don't use '@spinnaker/core' imports inside core package [#4815](https://github.com/spinnaker/deck/pull/4815) ([10d3a178](https://github.com/spinnaker/deck/commit/10d3a178849c022f386b7134e02dd8e938633d1b))  
fix(amazon/serverGroup): stop closing details panel after it was closed once [#4812](https://github.com/spinnaker/deck/pull/4812) ([dea2000b](https://github.com/spinnaker/deck/commit/dea2000b053787ff394cffa4f541d8e5e58d1ba0))  
feat(dryrun): distinguish dry run pipelines in execution view ([933c20fc](https://github.com/spinnaker/deck/commit/933c20fc2ff26da1c57e82afe77dd78196b1c659))  
fix(core/serverGroup): Add key to running tasks in server group details [#4806](https://github.com/spinnaker/deck/pull/4806) ([62b98eef](https://github.com/spinnaker/deck/commit/62b98eef1a05befb361e0b1a07fc095acce556c3))  



## [0.0.145](https://www.github.com/spinnaker/deck/compare/7d39abf8e3b53f4eebce2fc62cc038bb8d914cd7...37cb614d0c55f00a6b88d3d916e4805cb50b282f) (2018-02-06)


### Changes

chore(core): Bump to 0.0.145 ([37cb614d](https://github.com/spinnaker/deck/commit/37cb614d0c55f00a6b88d3d916e4805cb50b282f))  



## [0.0.144](https://www.github.com/spinnaker/deck/compare/09bcb66e70249b2acb03b1a7d538a09a9a4f55c8...7d39abf8e3b53f4eebce2fc62cc038bb8d914cd7) (2018-02-06)


### Changes

chore(core): bump to 0.0.144 ([7d39abf8](https://github.com/spinnaker/deck/commit/7d39abf8e3b53f4eebce2fc62cc038bb8d914cd7))  
feat(core/presentation): Add helper components/functions for form validation ([4b7919e3](https://github.com/spinnaker/deck/commit/4b7919e3834574fa768ee25663ca27775c3e58b5))  
Aligning react inputs to spinnaker select styles [#4802](https://github.com/spinnaker/deck/pull/4802) ([810aa7a0](https://github.com/spinnaker/deck/commit/810aa7a005fb06d540963e6b8440decd7d23ccca))  
fix(core/task): Set PlatformOverrideHealthMessage state properly [#4797](https://github.com/spinnaker/deck/pull/4797) ([c99ea2ca](https://github.com/spinnaker/deck/commit/c99ea2ca3d00ccee7d9bc3b61dcbe4d92495e51d))  
fix(core/pipeline): When deselecting the last filter, update executions properly [#4794](https://github.com/spinnaker/deck/pull/4794) ([4c71a3dd](https://github.com/spinnaker/deck/commit/4c71a3dd5bc25aa528a9478163f38d221adcb386))  



## [0.0.143](https://www.github.com/spinnaker/deck/compare/c20036a7c736c6b173e4fadd6aa953443cd0e290...09bcb66e70249b2acb03b1a7d538a09a9a4f55c8) (2018-02-05)


### Changes

chore(core): Bump module to 0.0.143 [#4793](https://github.com/spinnaker/deck/pull/4793) ([09bcb66e](https://github.com/spinnaker/deck/commit/09bcb66e70249b2acb03b1a7d538a09a9a4f55c8))  



## [0.0.142](https://www.github.com/spinnaker/deck/compare/7ce502554c29ff5516a72f7c99c378a302ef9b48...c20036a7c736c6b173e4fadd6aa953443cd0e290) (2018-02-05)


### Changes

chore(core): Bump module to 0.0.142 [#4792](https://github.com/spinnaker/deck/pull/4792) ([c20036a7](https://github.com/spinnaker/deck/commit/c20036a7c736c6b173e4fadd6aa953443cd0e290))  
feat(dryrun): enable/disable dry run with a feature flag ([6410fff6](https://github.com/spinnaker/deck/commit/6410fff6b71e1a821f3e088a57599b9cb8add33a))  
fix(core/pipeline): Sometimes executions do not have stages; still show details [#4781](https://github.com/spinnaker/deck/pull/4781) ([bd151f7d](https://github.com/spinnaker/deck/commit/bd151f7dd77ec18096b8b7691771f215cd05a5b7))  
fix(core/search): Select first search tab with >0 results, if no tab is already selected. [#4785](https://github.com/spinnaker/deck/pull/4785) ([7fd0dc6a](https://github.com/spinnaker/deck/commit/7fd0dc6a6fbd0f3813de3cca563bb842a2f15876))  
fix(core): parameter descrption and default arent required [#4786](https://github.com/spinnaker/deck/pull/4786) ([a7482433](https://github.com/spinnaker/deck/commit/a748243309c540813ea08f7e0358f0b59f7a969e))  



## [0.0.141](https://www.github.com/spinnaker/deck/compare/27eab28e8a8f0675f8c4d3317ccaa6d86515beba...7ce502554c29ff5516a72f7c99c378a302ef9b48) (2018-02-05)


### Changes

chore(core): Bump module to 0.0.141 [#4784](https://github.com/spinnaker/deck/pull/4784) ([7ce50255](https://github.com/spinnaker/deck/commit/7ce502554c29ff5516a72f7c99c378a302ef9b48))  
feat(dryrun): temporarily disable dry run until Orca goes out ([98ae07c3](https://github.com/spinnaker/deck/commit/98ae07c32306d4e386c355e1cc46a0fc4ba06d4b))  



## [0.0.140](https://www.github.com/spinnaker/deck/compare/d3a886c33fa8544ca837f759e816b18c87f410f4...27eab28e8a8f0675f8c4d3317ccaa6d86515beba) (2018-02-04)


### Changes

chore(core): Bump module to 0.0.140 [#4780](https://github.com/spinnaker/deck/pull/4780) ([27eab28e](https://github.com/spinnaker/deck/commit/27eab28e8a8f0675f8c4d3317ccaa6d86515beba))  
feat(core/search): Consolidate search registries and v1/v2 data fetching [#4775](https://github.com/spinnaker/deck/pull/4775) ([0d2fbf84](https://github.com/spinnaker/deck/commit/0d2fbf846cb12a8d38bdae3ccdfb91d1d4a3ecbf))  
fix(core): restore auto-scroll on deep linked server group render [#4776](https://github.com/spinnaker/deck/pull/4776) ([389afa5e](https://github.com/spinnaker/deck/commit/389afa5e9266e46ec141a6869ec752c6d9db9322))  
fix(core): Fix missing wizard section labels [#4778](https://github.com/spinnaker/deck/pull/4778) ([2a788540](https://github.com/spinnaker/deck/commit/2a78854071f25cb2708128e8d4ac61833e8f0212))  
refactor(core/search): Clean up GlobalSearch, make state mgmt. way less flaky [#4760](https://github.com/spinnaker/deck/pull/4760) ([63937b89](https://github.com/spinnaker/deck/commit/63937b8926cc750fc306a755ba97ee03a3b6705a))  
fix(core/loadBalancer): Fix create load balancer button for multiple providers. ([9ee937e1](https://github.com/spinnaker/deck/commit/9ee937e12a60fae0f796e6d716babbb88d893da6))  
fix(core): avoid flickering scrollbars in clusters view [#4774](https://github.com/spinnaker/deck/pull/4774) ([dbca16a0](https://github.com/spinnaker/deck/commit/dbca16a0f0335cc0f191558846dff12b55dbf4d6))  
fix(rxjs): switch RxJS deep imports to `import { Foo } from 'rxjs'` [#4772](https://github.com/spinnaker/deck/pull/4772) ([d52067cc](https://github.com/spinnaker/deck/commit/d52067cc819c743c9e7539c98933e4d84f97bacb))  
fix(core): shrink tag marker to fit better in cluster header [#4773](https://github.com/spinnaker/deck/pull/4773) ([94bfe9f5](https://github.com/spinnaker/deck/commit/94bfe9f59e1f141276e5bd1b1068db3de26419f7))  
feat(core/application): reactify applications search [#4759](https://github.com/spinnaker/deck/pull/4759) ([c159511a](https://github.com/spinnaker/deck/commit/c159511a05ceb0e3512e0b4ea018490058b60247))  
fix(core/search): Remove angular2react bridging of native react component [#4763](https://github.com/spinnaker/deck/pull/4763) ([c030658e](https://github.com/spinnaker/deck/commit/c030658e01b59997192318f7f8e9a9c24075032b))  
fix(core): handle challengeDestructiveActions being undefined in API response [#4766](https://github.com/spinnaker/deck/pull/4766) ([a6ec9840](https://github.com/spinnaker/deck/commit/a6ec9840732405d507ce5960b79ececfcd4ab353))  
fix(core/cluster): Vertically align icons and make them the same size [#4764](https://github.com/spinnaker/deck/pull/4764) ([ea99db18](https://github.com/spinnaker/deck/commit/ea99db182f8a25f85f889c34341e172a063c4343))  



## [0.0.139](https://www.github.com/spinnaker/deck/compare/476020834bfa68cf1af2ec46b7a3f679ddd163f7...d3a886c33fa8544ca837f759e816b18c87f410f4) (2018-02-01)


### Changes

chore(core): Bump module to 0.0.139 ([d3a886c3](https://github.com/spinnaker/deck/commit/d3a886c33fa8544ca837f759e816b18c87f410f4))  
feat(core/serverGroup): Prepare for Reactification of provider server group details ([6339aa01](https://github.com/spinnaker/deck/commit/6339aa01cadcac3a030ba0110244aa9aabfe665f))  
fix(core/search): Vertically center spinners and 'no results found' messages [#4758](https://github.com/spinnaker/deck/pull/4758) ([804e9dd6](https://github.com/spinnaker/deck/commit/804e9dd697ccb8d72badca6b31b7f2f24c7ed004))  
feat(dryrun): made dry run a flag on the trigger rather than a different type [#4761](https://github.com/spinnaker/deck/pull/4761) ([abaa849d](https://github.com/spinnaker/deck/commit/abaa849dd2ef3ed3dcfb134aeaae556140b8fa36))  
fix(core): consistent button sizing in details panels [#4757](https://github.com/spinnaker/deck/pull/4757) ([20e4edbc](https://github.com/spinnaker/deck/commit/20e4edbce8ecba2f3e650c44339d0e77019069b6))  



## [0.0.138](https://www.github.com/spinnaker/deck/compare/32218ed928f44a03f03c605a721b400f10d4a3ba...476020834bfa68cf1af2ec46b7a3f679ddd163f7) (2018-02-01)


### Changes

chore(core): bump package to 0.0.138 [#4755](https://github.com/spinnaker/deck/pull/4755) ([47602083](https://github.com/spinnaker/deck/commit/476020834bfa68cf1af2ec46b7a3f679ddd163f7))  
feat(dryrun): added a checkbox to let users dry run pipelines [#4747](https://github.com/spinnaker/deck/pull/4747) ([80a69f29](https://github.com/spinnaker/deck/commit/80a69f2909071232dc7354a0b70c5b1b4e311f0b))  
refactor(core): limit calls to /credentials [#4752](https://github.com/spinnaker/deck/pull/4752) ([7d7a1bc4](https://github.com/spinnaker/deck/commit/7d7a1bc4d49d217861d0409b2ca30fa195fe7c10))  
fix(core): eagerly fetch more than one cluster grouping pod [#4753](https://github.com/spinnaker/deck/pull/4753) ([b9cdd00d](https://github.com/spinnaker/deck/commit/b9cdd00d2b1269742828b9a5c8d170d9b8f6bbe9))  
feat(details): Migrate the following to @Overridable() decorator: [#4749](https://github.com/spinnaker/deck/pull/4749) ([ca2f5a47](https://github.com/spinnaker/deck/commit/ca2f5a4711d049e9c30bf2719bfc3fd16fcb3d39))  
feat(core/overrideRegistry): Add react component decorators to enable UI overrides [#4741](https://github.com/spinnaker/deck/pull/4741) ([6ae7e905](https://github.com/spinnaker/deck/commit/6ae7e905cb2eca5be1691679a3080987e79a35c7))  
fix(core): allow multiselect toggling on instance checkbox click [#4745](https://github.com/spinnaker/deck/pull/4745) ([261f605b](https://github.com/spinnaker/deck/commit/261f605b44e0546d39eaf4598ec973f21c7c5ad7))  
style(pipelines): Remove explicit use of $q.defer [#4746](https://github.com/spinnaker/deck/pull/4746) ([e6439e91](https://github.com/spinnaker/deck/commit/e6439e91c9d4fa613701740bad5f8ba61c3a9f77))  
fix(details): allow details dropdowns to wrap (at smaller widths). remove clearfix [#4748](https://github.com/spinnaker/deck/pull/4748) ([46c774d8](https://github.com/spinnaker/deck/commit/46c774d81fdd40edf0ce03f0735d81ceee30f995))  
feat(core): allow hidden (but enable-able) data sources [#4738](https://github.com/spinnaker/deck/pull/4738) ([b6a2af7b](https://github.com/spinnaker/deck/commit/b6a2af7b143a24e593259bccc4b9ab30907b7536))  
fix(core/search): Export the new GlobalSearch react component [#4750](https://github.com/spinnaker/deck/pull/4750) ([960e913b](https://github.com/spinnaker/deck/commit/960e913bead6f9e8a15f14acb689b6c281a4129d))  
fix(pipelines/help): explains when concurrent pipelines get canceled [#4744](https://github.com/spinnaker/deck/pull/4744) ([9028eef8](https://github.com/spinnaker/deck/commit/9028eef8379429b88c3b2108a5e9bfef8ae14289))  
fix(pipelines): Fix polling for manual execution [#4743](https://github.com/spinnaker/deck/pull/4743) ([2d3791ef](https://github.com/spinnaker/deck/commit/2d3791ef1be0febfd325c8fc2250decf1fba40d4))  
feat(core/search): Require 3 chars for global search, move to React [#4737](https://github.com/spinnaker/deck/pull/4737) ([7d268514](https://github.com/spinnaker/deck/commit/7d26851442376eac816830e9013604511fdc1049))  



## [0.0.137](https://www.github.com/spinnaker/deck/compare/81ed1882f3d92066ec872cfb0606c604a2fd9e65...32218ed928f44a03f03c605a721b400f10d4a3ba) (2018-01-29)


### Changes

chore(chore): bump package to 0.0.137 ([32218ed9](https://github.com/spinnaker/deck/commit/32218ed928f44a03f03c605a721b400f10d4a3ba))  
fix(core/instance): Make navigating to instance details work again post-React-rewrite [#4735](https://github.com/spinnaker/deck/pull/4735) ([94c7e79f](https://github.com/spinnaker/deck/commit/94c7e79f9b2a4cebc4d0bcf2a58aa04028e493e0))  



## [0.0.136](https://www.github.com/spinnaker/deck/compare/f680ff47afcbc7bdd6fa29a789b6ccf88c2670bf...81ed1882f3d92066ec872cfb0606c604a2fd9e65) (2018-01-29)


### Changes

chore(core): bump package to 0.0.136 [#4734](https://github.com/spinnaker/deck/pull/4734) ([81ed1882](https://github.com/spinnaker/deck/commit/81ed1882f3d92066ec872cfb0606c604a2fd9e65))  
refactor(core): convert instance list body to React [#4733](https://github.com/spinnaker/deck/pull/4733) ([0cd07c01](https://github.com/spinnaker/deck/commit/0cd07c01e611fd9e3e56ff9011123268354fb947))  
style(all): Added grids to the main container of the app [#4691](https://github.com/spinnaker/deck/pull/4691) ([97e52e30](https://github.com/spinnaker/deck/commit/97e52e3089acdfc71fd5f98464f786c3246f2603))  
fix(core): append pipeline dropdown to body [#4727](https://github.com/spinnaker/deck/pull/4727) ([7c92be39](https://github.com/spinnaker/deck/commit/7c92be39ed6ad602b4c551a58a2d48ad311d35e4))  
feat(core): Add support for react versions of stage details [#4731](https://github.com/spinnaker/deck/pull/4731) ([1d657532](https://github.com/spinnaker/deck/commit/1d65753264843f86cd3af1906e9e74c8d20e0279))  
feat(core/reactShims): Add angularJS template/controller adapter [#4730](https://github.com/spinnaker/deck/pull/4730) ([b1839549](https://github.com/spinnaker/deck/commit/b1839549f78e3112ed25587aac6f394cad60abb7))  
fix(core): Improved pipeline template tooltip copy [#4729](https://github.com/spinnaker/deck/pull/4729) ([5e48a3c4](https://github.com/spinnaker/deck/commit/5e48a3c4e17083c4bc5dae961db1f171828913d9))  
docs(provider/kubernetes) - Adding additional tool tips around kubernetes v2 provider [#4723](https://github.com/spinnaker/deck/pull/4723) ([e63429e4](https://github.com/spinnaker/deck/commit/e63429e45d58dd559173ec49abdf52d998bce9e5))  
feat(core): Indicate templated pipelines [#4728](https://github.com/spinnaker/deck/pull/4728) ([99e80b41](https://github.com/spinnaker/deck/commit/99e80b41b6aa5028c8c4040eb09e0cc39769f23b))  



## [0.0.135](https://www.github.com/spinnaker/deck/compare/50c59fb1f9f0080a9d765381f1720fbff8d92a76...f680ff47afcbc7bdd6fa29a789b6ccf88c2670bf) (2018-01-24)


### Changes

chore(core): bump package version [#4722](https://github.com/spinnaker/deck/pull/4722) ([f680ff47](https://github.com/spinnaker/deck/commit/f680ff47afcbc7bdd6fa29a789b6ccf88c2670bf))  
feat(core): map editor hidden keys [#4721](https://github.com/spinnaker/deck/pull/4721) ([decec00a](https://github.com/spinnaker/deck/commit/decec00a52aa6adb6e877a5a2755b459ff083eca))  
feat(amazon): Convert create load balancer modal to react [#4705](https://github.com/spinnaker/deck/pull/4705) ([20b42665](https://github.com/spinnaker/deck/commit/20b42665fd50728109393d631142a58ddf37f076))  



## [0.0.134](https://www.github.com/spinnaker/deck/compare/6d1523f878699aec3547e6baabeb47704545ba81...50c59fb1f9f0080a9d765381f1720fbff8d92a76) (2018-01-24)


### Changes

chore(core): bump package to 0.0.134 [#4718](https://github.com/spinnaker/deck/pull/4718) ([50c59fb1](https://github.com/spinnaker/deck/commit/50c59fb1f9f0080a9d765381f1720fbff8d92a76))  
fix(core): Add styleguide.html back since it breaks the docker build [#4716](https://github.com/spinnaker/deck/pull/4716) ([0aca2d66](https://github.com/spinnaker/deck/commit/0aca2d66570daae972ce2170d1ef2a2881361c6b))  
chore(core): Ignore styleguide.html changes since it is generated [#4714](https://github.com/spinnaker/deck/pull/4714) ([74ff03bd](https://github.com/spinnaker/deck/commit/74ff03bd6da9e59b76230d0f4f6f89803cc60ba3))  
reverting to cogs for the pipeline saving button [#4715](https://github.com/spinnaker/deck/pull/4715) ([e5a3a7dc](https://github.com/spinnaker/deck/commit/e5a3a7dc9a0f887c9932ca5e4716a553db65fc45))  
fix(core/amazon): wrap spinner size in quotes [#4710](https://github.com/spinnaker/deck/pull/4710) ([6838ab02](https://github.com/spinnaker/deck/commit/6838ab0212fcb4cbda0a15313de4ee61719a2438))  
fix(core): handle load failures in entityTags datasource [#4708](https://github.com/spinnaker/deck/pull/4708) ([c508514f](https://github.com/spinnaker/deck/commit/c508514f5280cd7a3a182b4c780f2f389f008187))  
fix(core): Fix module creation by not using internal type [#4712](https://github.com/spinnaker/deck/pull/4712) ([d858ca2a](https://github.com/spinnaker/deck/commit/d858ca2af8ceb29bb2bd58df0e779b64d0a73a2a))  
Added disabled states for button + a button group for buttons to be arranged [#4704](https://github.com/spinnaker/deck/pull/4704) ([e4349d58](https://github.com/spinnaker/deck/commit/e4349d58ae25a68bfc4a73d4a4c040fba0c1209a))  
chore(core): upgrade jQuery to 3.3.1 [#4703](https://github.com/spinnaker/deck/pull/4703) ([b97e0f89](https://github.com/spinnaker/deck/commit/b97e0f895175055daec4e2df94e091c666148c66))  
fix(core/account): Fix account tag color rendering during scroll/filter operation [#4706](https://github.com/spinnaker/deck/pull/4706) ([08e0f215](https://github.com/spinnaker/deck/commit/08e0f215a3947916c61f8bde0a3a6deba7a48e77))  
feat(core): Create a react modal wizard [#4695](https://github.com/spinnaker/deck/pull/4695) ([d768df5f](https://github.com/spinnaker/deck/commit/d768df5f2b152b7eb7dd80bb19d3f8f0cf5c2cc5))  



## [0.0.133](https://www.github.com/spinnaker/deck/compare/ce16f01d93e8bbd8c2153fcd5ad1448bda7ecebc...6d1523f878699aec3547e6baabeb47704545ba81) (2018-01-22)


### Changes

chore(chore): bump package to 0.0.133 [#4701](https://github.com/spinnaker/deck/pull/4701) ([6d1523f8](https://github.com/spinnaker/deck/commit/6d1523f878699aec3547e6baabeb47704545ba81))  
fix(amazon): show load balancer actions menu [#4700](https://github.com/spinnaker/deck/pull/4700) ([67dae2b1](https://github.com/spinnaker/deck/commit/67dae2b12b802625360ffee903f7b8fb59d16ff7))  
fix(core): reset cluster measure cache when groups update [#4698](https://github.com/spinnaker/deck/pull/4698) ([501ada0c](https://github.com/spinnaker/deck/commit/501ada0c418af0c0ac33c1f3ac351723d07a306c))  
fix(core): use small loader instead of nano on task monitor [#4699](https://github.com/spinnaker/deck/pull/4699) ([f036dfaf](https://github.com/spinnaker/deck/commit/f036dfaf90965ed3cd661da6a4bb1248e37c4d48))  



## [0.0.132](https://www.github.com/spinnaker/deck/compare/43385bee3827fc858ffd5d0df7040249770d3019...ce16f01d93e8bbd8c2153fcd5ad1448bda7ecebc) (2018-01-22)


### Changes

chore(core): bump package to 0.0.132 ([ce16f01d](https://github.com/spinnaker/deck/commit/ce16f01d93e8bbd8c2153fcd5ad1448bda7ecebc))  
fix(core/search): Fix external search types for v1 search/global search ([95d52095](https://github.com/spinnaker/deck/commit/95d520956ee0797b6c3402d65677a28f63013bdd))  



## [0.0.131](https://www.github.com/spinnaker/deck/compare/b073a64fd9bfe4fe162d6d925a0f193a56500752...43385bee3827fc858ffd5d0df7040249770d3019) (2018-01-19)


### Changes

chore(core): bump package to 0.0.131 ([43385bee](https://github.com/spinnaker/deck/commit/43385bee3827fc858ffd5d0df7040249770d3019))  
refactor(core/search): Use flex classes ([864ecc62](https://github.com/spinnaker/deck/commit/864ecc623539d043fff1660b4ca8b4fa61b56c16))  
refactor(core/search): Switch to ul/li in search tabs. Convert AccountTag to react. ([f9d75280](https://github.com/spinnaker/deck/commit/f9d75280b8a721f2d6d66f5b15daf8f472ff4679))  
feat(core/search): Show search results as they arrive -- do not wait for all to complete ([1fbf6c11](https://github.com/spinnaker/deck/commit/1fbf6c115bc8bedbee93c1b909d726a021c11a19))  



## [0.0.130](https://www.github.com/spinnaker/deck/compare/1aee32dcd5439bd5b4bf50dc42c728c12ee954c5...b073a64fd9bfe4fe162d6d925a0f193a56500752) (2018-01-19)


### Changes

chore(core): bump package to 0.0.130 [#4693](https://github.com/spinnaker/deck/pull/4693) ([b073a64f](https://github.com/spinnaker/deck/commit/b073a64fd9bfe4fe162d6d925a0f193a56500752))  
style(pipeline): Fit contents in the debugging section [#4622](https://github.com/spinnaker/deck/pull/4622) ([e996df46](https://github.com/spinnaker/deck/commit/e996df463943d01731da60bb41c2db635058d6b9))  
refactor(core): remove redundant div from cluster pod wrapper [#4690](https://github.com/spinnaker/deck/pull/4690) ([756d6399](https://github.com/spinnaker/deck/commit/756d6399a9d3f4b6127744661cd379ed7aa2bd95))  
fix(core): Add loadBalancerWriter to react injector ([8f59ab58](https://github.com/spinnaker/deck/commit/8f59ab583d7d69cf0ba8ae9e1e9ae21377cfb22c))  
feat(core/validation): Add react component for form validation errors ([b351c0ea](https://github.com/spinnaker/deck/commit/b351c0ea8498753e25fe9a6cef51b77d31d79873))  
feat(core/entityTag): Create react wrapper for add entity tag links ([6657a542](https://github.com/spinnaker/deck/commit/6657a542c675a1d9863165bc77414a4dbe9f5cda))  
feat(core/region): Create react version of region select field ([4e10a758](https://github.com/spinnaker/deck/commit/4e10a75899a7cfc4a280d55eefccbe0db065c457))  
feat(core/account): Create react wrapper for account select field ([0befdc0f](https://github.com/spinnaker/deck/commit/0befdc0f22a57dfb8f97bd6c3a3484216256fbdf))  
feat(core/forms): Create a react version of the checklist component ([953e52ee](https://github.com/spinnaker/deck/commit/953e52ee679d5d8933271eb3991a5b4c9e5bb923))  
refactor(core): Remove unused function in waypoint service ([049d5f97](https://github.com/spinnaker/deck/commit/049d5f97ecc370e37be6a2d122196bdc7f3a98fd))  
perf(core/clusters): use ReactVirtualized to render clusters [#4688](https://github.com/spinnaker/deck/pull/4688) ([c8d9d6dd](https://github.com/spinnaker/deck/commit/c8d9d6dd1e1dabe21681f0f493b4c39727be63a9))  
refactor(core/search): Reactify SearchV2 ([a86f8c53](https://github.com/spinnaker/deck/commit/a86f8c53d6ed674396b1ea4b854d845e4598d9e5))  
refactor(core/search): Use router state to drive search results. ([6211deb2](https://github.com/spinnaker/deck/commit/6211deb272395aa9241376a44d6482131826e7af))  
refactor(core/search): Refactor Search V2. ([a9f83748](https://github.com/spinnaker/deck/commit/a9f83748ae25ce4d9a8eedaba84da5c1639c3fbd))  
fix(core/search): Fix calling setState on unmounted RecentlyViewedItems [#4680](https://github.com/spinnaker/deck/pull/4680) ([ddda974e](https://github.com/spinnaker/deck/commit/ddda974ea9fe4e7ce4efe52afd73d783a0ebdd2b))  
style(amazon/application/projects/pipeline/google/kubernetes): Replacing fa-cog icons with new spinner [#4630](https://github.com/spinnaker/deck/pull/4630) ([c1f63e87](https://github.com/spinnaker/deck/commit/c1f63e879791473e86d0ecc0a316c3e94a9ba8de))  
fix(core): surface app data status in views without active states [#4675](https://github.com/spinnaker/deck/pull/4675) ([580356dd](https://github.com/spinnaker/deck/commit/580356dde7ed86b1de376341c2851728c74ac8f5))  
fix(core/presentation): Add padding to filter sidebar as a scrolling affordance [#4679](https://github.com/spinnaker/deck/pull/4679) ([faa1687c](https://github.com/spinnaker/deck/commit/faa1687ce2f440437940eb81be3f11a910103d6f))  
fix(webhooks): default parameters to empty list [#4678](https://github.com/spinnaker/deck/pull/4678) ([a7628df9](https://github.com/spinnaker/deck/commit/a7628df9a91e53f8abaf1b035c351b3db9a3b7b3))  
fix(core/search): Fix instance searches [#4670](https://github.com/spinnaker/deck/pull/4670) ([5c32bfe5](https://github.com/spinnaker/deck/commit/5c32bfe589405bf929d700203b2c519aad8a16be))  
fix(core/vis): Fix visualizer toggle to clean up old copies of the visualizer [#4672](https://github.com/spinnaker/deck/pull/4672) ([61bef170](https://github.com/spinnaker/deck/commit/61bef1704c13959e6ceb70d9afaabed0a0c0c852))  
fix(core): fix restore to this version behavior on pipeline config [#4671](https://github.com/spinnaker/deck/pull/4671) ([7d5663be](https://github.com/spinnaker/deck/commit/7d5663beec34135faa37f38790afdb0561044533))  
chore(provider/kubernetes): bump kubernetes package version [#4676](https://github.com/spinnaker/deck/pull/4676) ([ed9470db](https://github.com/spinnaker/deck/commit/ed9470db4e656a18ee93c3d7b8863dc632ee7e81))  
feat(provider/kubernetes): export k8s server group interface [#4674](https://github.com/spinnaker/deck/pull/4674) ([88e36864](https://github.com/spinnaker/deck/commit/88e368647a4452ea7b07ef0a1c51cfb4ad6f714a))  
feat(webhooks): preconfigured webhook params [#4669](https://github.com/spinnaker/deck/pull/4669) ([2311696e](https://github.com/spinnaker/deck/commit/2311696e737d7d191e04da320c83484b2458d3d0))  



## [0.0.129](https://www.github.com/spinnaker/deck/compare/e0b48a26349b52e7157e88acbbe063bb85b05ca9...1aee32dcd5439bd5b4bf50dc42c728c12ee954c5) (2018-01-12)


### Changes

chore(core): bump package to 0.0.129 [#4666](https://github.com/spinnaker/deck/pull/4666) ([1aee32dc](https://github.com/spinnaker/deck/commit/1aee32dcd5439bd5b4bf50dc42c728c12ee954c5))  
fix(core): remove lazy datasource config on security groups, load balancers [#4665](https://github.com/spinnaker/deck/pull/4665) ([cece4a5c](https://github.com/spinnaker/deck/commit/cece4a5cb218f7b64e9aed5e08f27156a9ae5b77))  



## [0.0.128](https://www.github.com/spinnaker/deck/compare/69054621050ef01b0a06ce41400ad8ef2723222f...e0b48a26349b52e7157e88acbbe063bb85b05ca9) (2018-01-11)


### Changes

chore(core): bump package to 0.0.128 [#4663](https://github.com/spinnaker/deck/pull/4663) ([e0b48a26](https://github.com/spinnaker/deck/commit/e0b48a26349b52e7157e88acbbe063bb85b05ca9))  
perf(core): lazy load data for load balancers, security groups [#4661](https://github.com/spinnaker/deck/pull/4661) ([cd391e42](https://github.com/spinnaker/deck/commit/cd391e42c900b24dbd4e13c6b1de4125a0382897))  
fix(core): fix link to deployed server group in tasks view [#4662](https://github.com/spinnaker/deck/pull/4662) ([690f6da4](https://github.com/spinnaker/deck/commit/690f6da4aa643116286e53cafc65876a58f52dd1))  
fix(core/pipelines): do not show no pipelines message while initializing [#4660](https://github.com/spinnaker/deck/pull/4660) ([7e287991](https://github.com/spinnaker/deck/commit/7e287991a1c0bcdcbe56c2c2475551d22f7c8ae9))  
fix(core): handle execution window expressions gracefully [#4656](https://github.com/spinnaker/deck/pull/4656) ([912bffb1](https://github.com/spinnaker/deck/commit/912bffb16cb9f4ea1e96fa106eda08e0a4e997a5))  
feat(deck) - Add gitlab as a gitSource and allow gitSources to be configured via settings.js [#4657](https://github.com/spinnaker/deck/pull/4657) ([6dbf23ef](https://github.com/spinnaker/deck/commit/6dbf23ef3f18f9f2ece0ab3eba1cadc3b58efed2))  



## [0.0.127](https://www.github.com/spinnaker/deck/compare/7622072220056ab87af19292b35308ede2c76a22...69054621050ef01b0a06ce41400ad8ef2723222f) (2018-01-09)


### Changes

chore(core): bump package to 0.0.127 [#4649](https://github.com/spinnaker/deck/pull/4649) ([69054621](https://github.com/spinnaker/deck/commit/69054621050ef01b0a06ce41400ad8ef2723222f))  
feat(core): enable highlighting of invalid pristine fields [#4648](https://github.com/spinnaker/deck/pull/4648) ([f818cb90](https://github.com/spinnaker/deck/commit/f818cb900b02633ab80fbd663b78ac22f6fc7a54))  
fix(core): allow side filters to be collapsed [#4629](https://github.com/spinnaker/deck/pull/4629) ([55382285](https://github.com/spinnaker/deck/commit/553822858692e8cfd70106e340485008df62fa76))  



## [0.0.126](https://www.github.com/spinnaker/deck/compare/5da17b2290ec7ee5133a7dd08dbdc1369c257edc...7622072220056ab87af19292b35308ede2c76a22) (2018-01-04)


### Changes

chore(core): bump package to 0.0.126 [#4625](https://github.com/spinnaker/deck/pull/4625) ([76220722](https://github.com/spinnaker/deck/commit/7622072220056ab87af19292b35308ede2c76a22))  
fix(core): remove "0" from filter list when no pipelines present [#4623](https://github.com/spinnaker/deck/pull/4623) ([5624b0b9](https://github.com/spinnaker/deck/commit/5624b0b9d9ac1184cfd4ff2a3558f2ae17e28fc5))  
fix(core/search): Fix redirect from 'home.infrastructure' to 'home.search' ([64af8a3e](https://github.com/spinnaker/deck/commit/64af8a3ec212cf1b756a753d3b42cb81c3ea2d94))  
feat(provider/google): Clarify help messages for pubsub fields. [#4613](https://github.com/spinnaker/deck/pull/4613) ([d81d65e4](https://github.com/spinnaker/deck/commit/d81d65e4a4d303a494ad99f0f6f10a63acf79eb8))  
style(amazon/azure/cloudfoundry/core/dcos/google/kubernetes/openstack/oracle): Added new spinners per designs [#4611](https://github.com/spinnaker/deck/pull/4611) ([47b809c3](https://github.com/spinnaker/deck/commit/47b809c3445b606d6c668ab1657811bf2924ca74))  
feat(provider/kubernetes): pick artifacts to deploy [#4595](https://github.com/spinnaker/deck/pull/4595) ([3363cff0](https://github.com/spinnaker/deck/commit/3363cff077e63309d1c4f59f0cefb8928b92310f))  
style(core): Pods CSS bug fix + addition of bold variants to type [#4608](https://github.com/spinnaker/deck/pull/4608) ([a38dfa48](https://github.com/spinnaker/deck/commit/a38dfa48078816ef1aced0595e37a321be18e2af))  
fix(core): keep restrict execution checkbox checked if its set [#4605](https://github.com/spinnaker/deck/pull/4605) ([d2524e6f](https://github.com/spinnaker/deck/commit/d2524e6fb0c4244c53d723ce6ec41c344fc2a545))  
fix(core/reactShims): Add catch block to reactShims state.go() calls ([4f4b284c](https://github.com/spinnaker/deck/commit/4f4b284c43e0e63df560be8e9f0839af8c7882ab))  
fix(core/pipeline) Check to see if .stage.restrictedExecutionWindow exists [#4616](https://github.com/spinnaker/deck/pull/4616) ([f6824843](https://github.com/spinnaker/deck/commit/f682484367740e55191b1b270d445511bd5463f7))  
fix(core/pipeline): Fix execution graph of groups when MPT partial contains only one stage [#4615](https://github.com/spinnaker/deck/pull/4615) ([44d2e38b](https://github.com/spinnaker/deck/commit/44d2e38bc14552d3abf13ecd4f37242fad009767))  
fix(core): add cluster level tags to server groups [#4612](https://github.com/spinnaker/deck/pull/4612) ([65e2e8c9](https://github.com/spinnaker/deck/commit/65e2e8c97459d66b2df1eaf0260317da0235f25e))  
Set custom parameters using the UI [#4596](https://github.com/spinnaker/deck/pull/4596) ([a44ceb82](https://github.com/spinnaker/deck/commit/a44ceb82334c351147161431088af7ae56b966fc))  
feat(provider/gce): Adds UI for pubsub attribute constraints. [#4599](https://github.com/spinnaker/deck/pull/4599) ([a3626f21](https://github.com/spinnaker/deck/commit/a3626f211bd43815b5b3c2942928b99ea9bfd685))  



## [0.0.125](https://www.github.com/spinnaker/deck/compare/88a42c14c66a4fc286da219c117f5087405834ae...5da17b2290ec7ee5133a7dd08dbdc1369c257edc) (2017-12-20)


### Changes

chore(core): bump to 0.0.125 [#4601](https://github.com/spinnaker/deck/pull/4601) ([5da17b22](https://github.com/spinnaker/deck/commit/5da17b2290ec7ee5133a7dd08dbdc1369c257edc))  
fix(core): allow HTML in search result display [#4600](https://github.com/spinnaker/deck/pull/4600) ([5ca9da74](https://github.com/spinnaker/deck/commit/5ca9da74562c09148f1684674d3fa921c90c4071))  
feat(core/pipelines): add jitter to exec windows [#4598](https://github.com/spinnaker/deck/pull/4598) ([046dc8a9](https://github.com/spinnaker/deck/commit/046dc8a98cf57dc17161e77755a06122f2de56fb))  
feat(provider/aws): Pipeline support for rolling back a cluster [#4590](https://github.com/spinnaker/deck/pull/4590) ([36335e33](https://github.com/spinnaker/deck/commit/36335e33b6ee4708a81a09e7c6892efcc97bd045))  
feat(artifacts): find artifact from execution [#4593](https://github.com/spinnaker/deck/pull/4593) ([cff74779](https://github.com/spinnaker/deck/commit/cff7477990a6fca3eda0a344c01fd58d45c38fc7))  



## [0.0.124](https://www.github.com/spinnaker/deck/compare/32f0aa712f37c423c62376d3a1f86a84f421c70c...88a42c14c66a4fc286da219c117f5087405834ae) (2017-12-16)


### Changes

chore(core): Bump to 0.0.124 ([88a42c14](https://github.com/spinnaker/deck/commit/88a42c14c66a4fc286da219c117f5087405834ae))  
feat(core/search): Add pref to setting.js for search version 1 or 2 refactor(core/search): Switch from /infrastructure to /search ([da859b7e](https://github.com/spinnaker/deck/commit/da859b7ef46febacb364fae8db317b19836a2afd))  
refactor(core/search): Reactificate recently viewed items ([ac0cc338](https://github.com/spinnaker/deck/commit/ac0cc338bc9a875645cd7e8c6d01d304aa188802))  
feat(core/presentation): add delayShow prop to Tooltip component ([2cc8c199](https://github.com/spinnaker/deck/commit/2cc8c199f8adf83da9baa87f396f1798e3d854d2))  
feat(core/presentation): re-export robotToHumanFilter ([88dd4eb8](https://github.com/spinnaker/deck/commit/88dd4eb80ca1b63de86c5af0274fdc44310b0411))  
feat(artifacts): github artifact helper [#4574](https://github.com/spinnaker/deck/pull/4574) ([0aba8e06](https://github.com/spinnaker/deck/commit/0aba8e0646306ca4471770f9674702f16f76cc32))  
feat(artifacts): artifact account selector [#4586](https://github.com/spinnaker/deck/pull/4586) ([df9d664c](https://github.com/spinnaker/deck/commit/df9d664cc49f4906e568363f9f89126f15635b45))  
fix(core): Switch notification email input type from email to text to support expressions [#4585](https://github.com/spinnaker/deck/pull/4585) ([540e67b3](https://github.com/spinnaker/deck/commit/540e67b395b30c55adeb9d99222673539b176355))  
refactor(*): Remove closeable.modal.controller [#4583](https://github.com/spinnaker/deck/pull/4583) ([2a0f1cac](https://github.com/spinnaker/deck/commit/2a0f1cac5f3237d6a97806cabe10c2f7e4b0a1ac))  
feat(provider/k8sv2): Delete/Resize manifest stage [#4567](https://github.com/spinnaker/deck/pull/4567) ([450d54c8](https://github.com/spinnaker/deck/commit/450d54c8db8541d2e7f52e18082759b9f0c4737d))  
fix(core/analytics): send correct stage label [#4577](https://github.com/spinnaker/deck/pull/4577) ([b0e4ea51](https://github.com/spinnaker/deck/commit/b0e4ea51f8a6fc044dbbc6469eec4fc70755ff98))  



## [0.0.123](https://www.github.com/spinnaker/deck/compare/ba3ba5428b3e7df50a74395f06c501ae47353d78...32f0aa712f37c423c62376d3a1f86a84f421c70c) (2017-12-11)


### Changes

chore(core): Bump to 0.0.123 [#4575](https://github.com/spinnaker/deck/pull/4575) ([32f0aa71](https://github.com/spinnaker/deck/commit/32f0aa712f37c423c62376d3a1f86a84f421c70c))  
feat(core/pipeline): Expose "Override stage timeout" UI for Pipeline Stage [#4571](https://github.com/spinnaker/deck/pull/4571) ([92bc2251](https://github.com/spinnaker/deck/commit/92bc225158e14688d96613288c093d33b62dd769))  
fix(amazon/loadBalancer): Fix target group name duplication validation [#4572](https://github.com/spinnaker/deck/pull/4572) ([dec0ad8e](https://github.com/spinnaker/deck/commit/dec0ad8e2c5030b13e8f1697b7436e74089c1142))  
fix(core/cluster): Do not wrap long servergroup stack names; use ellipsis [#4566](https://github.com/spinnaker/deck/pull/4566) ([d8d24803](https://github.com/spinnaker/deck/commit/d8d248033121a2e113061c70a9f65aa3a87a0ec0))  
fix(moniker): stop pre-constructing frigga-style cluster names [#4570](https://github.com/spinnaker/deck/pull/4570) ([8ffb8b3b](https://github.com/spinnaker/deck/commit/8ffb8b3b9ce66021a957a572f8401be8d9209457))  



## [0.0.122](https://www.github.com/spinnaker/deck/compare/1a6d34173cfa62d8ba59aa82bf8b61affaf84a2b...ba3ba5428b3e7df50a74395f06c501ae47353d78) (2017-12-08)


### Changes

chore(core): bump package to 0.0.122 [#4569](https://github.com/spinnaker/deck/pull/4569) ([ba3ba542](https://github.com/spinnaker/deck/commit/ba3ba5428b3e7df50a74395f06c501ae47353d78))  
fix(core/executions): do not auto-refresh manual judgment stages [#4568](https://github.com/spinnaker/deck/pull/4568) ([dcacdb58](https://github.com/spinnaker/deck/commit/dcacdb5824c53fd5a4408b727615a151f6c2f627))  



## [0.0.121](https://www.github.com/spinnaker/deck/compare/1571aaa0247034e72a2cb6717ac93587899b5a66...1a6d34173cfa62d8ba59aa82bf8b61affaf84a2b) (2017-12-07)


### Changes

chore(core): Bump to 0.0.121 [#4565](https://github.com/spinnaker/deck/pull/4565) ([1a6d3417](https://github.com/spinnaker/deck/commit/1a6d34173cfa62d8ba59aa82bf8b61affaf84a2b))  
fix(core/styleguide): remove black header from style guide template [#4563](https://github.com/spinnaker/deck/pull/4563) ([86975f36](https://github.com/spinnaker/deck/commit/86975f361ae258bfdc885bbfff47ee0f20ac4695))  
feat(core): Add aliases to application config [#4561](https://github.com/spinnaker/deck/pull/4561) ([354ec53a](https://github.com/spinnaker/deck/commit/354ec53a3b6ffc2d99ae479f9dbcbdd08cb296e1))  



## [0.0.120](https://www.github.com/spinnaker/deck/compare/23d0a77f2bbf846cd5eea2668964cdbb96bacafa...1571aaa0247034e72a2cb6717ac93587899b5a66) (2017-12-05)


### Changes

chore(chore): bump package to 0.0.120 [#4559](https://github.com/spinnaker/deck/pull/4559) ([1571aaa0](https://github.com/spinnaker/deck/commit/1571aaa0247034e72a2cb6717ac93587899b5a66))  
fix(core/pipelines): rerender view when first stage is removed [#4558](https://github.com/spinnaker/deck/pull/4558) ([c2673466](https://github.com/spinnaker/deck/commit/c2673466ca833a2962bfc7c9b03017ee78bce013))  
refactor(core/entityTags): retrieve tags by application [#4557](https://github.com/spinnaker/deck/pull/4557) ([ba2a3c8d](https://github.com/spinnaker/deck/commit/ba2a3c8d319279eeb9121c24aff2e30108dd955d))  
fix(core/task): Do not show tasks link if no application associated with task [#4556](https://github.com/spinnaker/deck/pull/4556) ([288fbd15](https://github.com/spinnaker/deck/commit/288fbd1559effdf5b964f2c175de6eaafcfaad23))  
feat(provider/kubernetes): deploy artifact from application [#4552](https://github.com/spinnaker/deck/pull/4552) ([7ca410fe](https://github.com/spinnaker/deck/commit/7ca410fe37eca4f3e3a0ecaf057ce969eaa9e606))  
refactor(core/pipeline): Cleanup time boundary sorting/comparison [#4551](https://github.com/spinnaker/deck/pull/4551) ([19a0d24b](https://github.com/spinnaker/deck/commit/19a0d24b8874a93c69d4e2b2b718ce6bf4b78394))  
fix(artifacts): fix typo in artifact var [#4549](https://github.com/spinnaker/deck/pull/4549) ([78136680](https://github.com/spinnaker/deck/commit/78136680e3115a06e3da263d64890655bb3a5497))  



## [0.0.119](https://www.github.com/spinnaker/deck/compare/2346e5871dedd0e210b6277cdced37e470e78892...23d0a77f2bbf846cd5eea2668964cdbb96bacafa) (2017-11-29)


### Changes

chore(core): bump package to 0.0.119 [#4544](https://github.com/spinnaker/deck/pull/4544) ([23d0a77f](https://github.com/spinnaker/deck/commit/23d0a77f2bbf846cd5eea2668964cdbb96bacafa))  
fix(core): prevent adding group stages, handle rendering gracefully [#4543](https://github.com/spinnaker/deck/pull/4543) ([9d0e4dcc](https://github.com/spinnaker/deck/commit/9d0e4dcc7a91bb0f643c2cdac357514ee980f1be))  
feat(pubsub): support constraints [#4542](https://github.com/spinnaker/deck/pull/4542) ([706b043f](https://github.com/spinnaker/deck/commit/706b043fa8d20ecd9399a5d112542f2bbfb5e34d))  



## [0.0.118](https://www.github.com/spinnaker/deck/compare/79591629f56f468ced1619ad7ce4e2dbaf6cb06d...2346e5871dedd0e210b6277cdced37e470e78892) (2017-11-29)


### Changes

chore(core): bump package to 0.0.118 [#4541](https://github.com/spinnaker/deck/pull/4541) ([2346e587](https://github.com/spinnaker/deck/commit/2346e5871dedd0e210b6277cdced37e470e78892))  
fix(core): use inline element for instance groups [#4540](https://github.com/spinnaker/deck/pull/4540) ([f01a0d16](https://github.com/spinnaker/deck/commit/f01a0d16f15b28cfaa1bb89e231e4273da4c7a5e))  



## [0.0.117](https://www.github.com/spinnaker/deck/compare/226f2831a17af0a673915113cc17090289f4c182...79591629f56f468ced1619ad7ce4e2dbaf6cb06d) (2017-11-28)


### Changes

chore(core): bump package to 0.0.117 [#4536](https://github.com/spinnaker/deck/pull/4536) ([79591629](https://github.com/spinnaker/deck/commit/79591629f56f468ced1619ad7ce4e2dbaf6cb06d))  
 refactor(core/pipeline): Convert singleExecutionDetails to react [#4530](https://github.com/spinnaker/deck/pull/4530) ([43c45af7](https://github.com/spinnaker/deck/commit/43c45af7931c1378d37fde7ead3fdb75d93eee86))  
fix(core): reduce starting instance animations [#4535](https://github.com/spinnaker/deck/pull/4535) ([f0d1c280](https://github.com/spinnaker/deck/commit/f0d1c28070896f8a8a55835c7fab746b46c1a8ff))  



## [0.0.116](https://www.github.com/spinnaker/deck/compare/23c117ae040adefff0a44ecd0c27f280f49d3e64...226f2831a17af0a673915113cc17090289f4c182) (2017-11-28)


### Changes

chore(core): bump to 0.0.116 [#4533](https://github.com/spinnaker/deck/pull/4533) ([226f2831](https://github.com/spinnaker/deck/commit/226f2831a17af0a673915113cc17090289f4c182))  
fix(core/pipeline): De-duplicate state transitions for execution details [#4531](https://github.com/spinnaker/deck/pull/4531) ([403fe5ff](https://github.com/spinnaker/deck/commit/403fe5ff5951a88cc02242f5991be686cf97787f))  
chore(core): Fix lint [#4529](https://github.com/spinnaker/deck/pull/4529) ([c0d29fbe](https://github.com/spinnaker/deck/commit/c0d29fbe8030a8c8acb8789f3549d06dc872dbb9))  
feat(webhooks): webhook trigger dialog [#4522](https://github.com/spinnaker/deck/pull/4522) ([d071e998](https://github.com/spinnaker/deck/commit/d071e99845bc1c696d89ca8070002b60504eee54))  
fix(core/pipeline): Fix undefined edge case in ScriptExecutionDetails [#4526](https://github.com/spinnaker/deck/pull/4526) ([45215b7e](https://github.com/spinnaker/deck/commit/45215b7eec16d0f097da33dd238829983fb42fb5))  
fix(*/loadBalancer): Fix a few undefined errors with load balancers [#4524](https://github.com/spinnaker/deck/pull/4524) ([ff1597bb](https://github.com/spinnaker/deck/commit/ff1597bbf7b6eb7983b11d7466cd471d87dcc0eb))  
fix(*/loadBalancer): Fix server group show/hide control [#4521](https://github.com/spinnaker/deck/pull/4521) ([727cbbea](https://github.com/spinnaker/deck/commit/727cbbea5f5938e8345f1ab4fedbf322eec4d31b))  
fix(core/pipeline): Pipeline graph overflow in firefox [#4520](https://github.com/spinnaker/deck/pull/4520) ([2794b6af](https://github.com/spinnaker/deck/commit/2794b6af48d01d2f2c8d6ea2b9c462ccce628ad1))  



## [0.0.115](https://www.github.com/spinnaker/deck/compare/9fc9dbd2c28998bf2d710f0f7971828900a1763b...23c117ae040adefff0a44ecd0c27f280f49d3e64) (2017-11-26)


### Changes

chore(core): bump package to 0.0.115 [#4517](https://github.com/spinnaker/deck/pull/4517) ([23c117ae](https://github.com/spinnaker/deck/commit/23c117ae040adefff0a44ecd0c27f280f49d3e64))  
fix(core/executions): trigger filter collapse correctly [#4515](https://github.com/spinnaker/deck/pull/4515) ([06df1103](https://github.com/spinnaker/deck/commit/06df110395800a38fa89a7b062fc10c66ee982dc))  
chore(core): fix lint warnings [#4516](https://github.com/spinnaker/deck/pull/4516) ([f0ba43d0](https://github.com/spinnaker/deck/commit/f0ba43d027ee9ee96e0b072f5be16aacd3867144))  
feat(core): console output jump to end [#4511](https://github.com/spinnaker/deck/pull/4511) ([89de607a](https://github.com/spinnaker/deck/commit/89de607a43976ecbdfb914a60462e221ca6b3d44))  
perf(core/executions): consider pipeline filters when fetching executions [#4509](https://github.com/spinnaker/deck/pull/4509) ([fbf0e807](https://github.com/spinnaker/deck/commit/fbf0e8070c6cd99da2959fd5f4f48fb93cbd5cba))  



## [0.0.114](https://www.github.com/spinnaker/deck/compare/4e6928a34f65d02a96351f0f2c90f13396677704...9fc9dbd2c28998bf2d710f0f7971828900a1763b) (2017-11-23)


### Changes

chore(core): Bump to 0.0.114 ([9fc9dbd2](https://github.com/spinnaker/deck/commit/9fc9dbd2c28998bf2d710f0f7971828900a1763b))  
chore(core/pagerDuty): Add field to the pager duty service interface ([4ccd3c1f](https://github.com/spinnaker/deck/commit/4ccd3c1f0d1e63b7c40f9a83eae236553356cb7b))  
bug(core) - add missing div close that causes stage templates to not render. [#4513](https://github.com/spinnaker/deck/pull/4513) ([dfcde92f](https://github.com/spinnaker/deck/commit/dfcde92f107a099ba9b79623d2e5976839059dbe))  
feat(artifacts): show artifacts next to manifest [#4510](https://github.com/spinnaker/deck/pull/4510) ([ca8f43d2](https://github.com/spinnaker/deck/commit/ca8f43d20e22c3f4fb3b4cad9b2c8047c709d8dc))  



## [0.0.113](https://www.github.com/spinnaker/deck/compare/a3b638e9ac82bab7505e02cd2b769f2beeacc130...4e6928a34f65d02a96351f0f2c90f13396677704) (2017-11-21)


### Changes

chore(core): Bump to 0.0.113 [#4508](https://github.com/spinnaker/deck/pull/4508) ([4e6928a3](https://github.com/spinnaker/deck/commit/4e6928a34f65d02a96351f0f2c90f13396677704))  
feat(core): Add a pagerDuty read service, tag, and select field [#4507](https://github.com/spinnaker/deck/pull/4507) ([edb12264](https://github.com/spinnaker/deck/commit/edb12264d63e24c47a81cd2e992d9939a0b5cc7e))  
fix(core/pipeline): Fix unhandled rejections with the pipeline graph [#4505](https://github.com/spinnaker/deck/pull/4505) ([f302df39](https://github.com/spinnaker/deck/commit/f302df39dae28ae43f48003ffc0b3e49366d755b))  



## [0.0.112](https://www.github.com/spinnaker/deck/compare/bfc5a0225492156102604f03d735cd7776f58984...a3b638e9ac82bab7505e02cd2b769f2beeacc130) (2017-11-20)


### Changes

chore(core): Bump to 0.0.112 [#4503](https://github.com/spinnaker/deck/pull/4503) ([a3b638e9](https://github.com/spinnaker/deck/commit/a3b638e9ac82bab7505e02cd2b769f2beeacc130))  
fix(core): typings [#4502](https://github.com/spinnaker/deck/pull/4502) ([1b34ae9e](https://github.com/spinnaker/deck/commit/1b34ae9e143154490f65fa89e4edd015dcf2a13e))  
refactor(core/pipeline): Rename ExecutionDetails to StageExecutionDetails ([ad93a12b](https://github.com/spinnaker/deck/commit/ad93a12b4f85399c0ba2d553d6cf6ba3ff4d2fb9))  
refactor(core/pipeline): Rename StageDetails to StepExecutionDetails ([a7d5617d](https://github.com/spinnaker/deck/commit/a7d5617d7fb43d088623e3bf93a2b0ab3bbaae8f))  
refactor(core/pipeline): Consolidate everything from /delivery into /pipeline ([bb0c80b6](https://github.com/spinnaker/deck/commit/bb0c80b666d781876f3ad0a3b30fc0a38ee6beff))  
fix(core): avoid NPE if security group cache does not exist [#4499](https://github.com/spinnaker/deck/pull/4499) ([6d555a3a](https://github.com/spinnaker/deck/commit/6d555a3a1702ca822d4637270e1dc083644cbffd))  
fix(core): Fix details panel cutoff at smaller sizes [#4498](https://github.com/spinnaker/deck/pull/4498) ([d17edb31](https://github.com/spinnaker/deck/commit/d17edb31cea36ecb377350bd28f1168e048e252c))  
chore(*): Update typescript and tslint and fix lint errors [#4494](https://github.com/spinnaker/deck/pull/4494) ([baa3155e](https://github.com/spinnaker/deck/commit/baa3155e710b9cde5c224e1e198b1704a6c774e4))  
refactor(core): Convert script stage execution details to react ([3efbb0bb](https://github.com/spinnaker/deck/commit/3efbb0bb46e841b9a14f64b290a3ab1a5ff6c529))  
refactor(*): Convert find ami execution details to react ([1896c7b2](https://github.com/spinnaker/deck/commit/1896c7b25fc18e57b639b6a7e9b3872d0c52b2f8))  
feat(artifact): docker artifact [#4490](https://github.com/spinnaker/deck/pull/4490) ([20e0fc6d](https://github.com/spinnaker/deck/commit/20e0fc6d4c2d6d1004e715354c2f9e77fe6da307))  
feat(core): versioned security group create [#4489](https://github.com/spinnaker/deck/pull/4489) ([ae1ab4d2](https://github.com/spinnaker/deck/commit/ae1ab4d27744b095d4996c37a9e3a3c220f83cb4))  
feat(manifest): more status banners [#4475](https://github.com/spinnaker/deck/pull/4475) ([567e132f](https://github.com/spinnaker/deck/commit/567e132f624c5f8456e56186642485458f983d91))  
fix(core): add space on Start Manual Executions button [#4485](https://github.com/spinnaker/deck/pull/4485) ([ac6023ec](https://github.com/spinnaker/deck/commit/ac6023ec994bc6bd24eb34c4077f46a65133c029))  



## [0.0.111](https://www.github.com/spinnaker/deck/compare/e120f1d250c94a5e687656f771f0a390b611b128...bfc5a0225492156102604f03d735cd7776f58984) (2017-11-17)


### Changes

chore(core): bump package to 0.0.111 [#4483](https://github.com/spinnaker/deck/pull/4483) ([bfc5a022](https://github.com/spinnaker/deck/commit/bfc5a0225492156102604f03d735cd7776f58984))  
chore(core): lint fix [#4484](https://github.com/spinnaker/deck/pull/4484) ([d1bbd3c5](https://github.com/spinnaker/deck/commit/d1bbd3c5a31e3c8ff9a9a7d93d933a6316ec7677))  
fix(core): omit moniker when caching security groups [#4482](https://github.com/spinnaker/deck/pull/4482) ([dab33173](https://github.com/spinnaker/deck/commit/dab331737b0a223b9bf41840480ec110902de5ab))  



## [0.0.110](https://www.github.com/spinnaker/deck/compare/39c5ab2ceee144702750f12d734c702c0ee16911...e120f1d250c94a5e687656f771f0a390b611b128) (2017-11-17)


### Changes

chore(core): Bump to 0.0.110 [#4480](https://github.com/spinnaker/deck/pull/4480) ([e120f1d2](https://github.com/spinnaker/deck/commit/e120f1d250c94a5e687656f771f0a390b611b128))  
feat(pagerduty): Support details field as a Map [#4473](https://github.com/spinnaker/deck/pull/4473) ([22fc713e](https://github.com/spinnaker/deck/commit/22fc713e3a456794bf0dcbd2ef7954ead470a6ce))  
feat(provider/kubernetes): surface manifest stability [#4470](https://github.com/spinnaker/deck/pull/4470) ([3c9214df](https://github.com/spinnaker/deck/commit/3c9214dfcf4cb6c583281e0d3c7d69e9f615a668))  
error message rendering for cluster locking failures ([a96bb4a6](https://github.com/spinnaker/deck/commit/a96bb4a6cbcd19d519a2e66dfca338cb38002071))  
feat(provider/kubernetes): Undo rollout [#4468](https://github.com/spinnaker/deck/pull/4468) ([46882c62](https://github.com/spinnaker/deck/commit/46882c62e6ab48e365e2679140ea20726c23e0ca))  



## [0.0.108](https://www.github.com/spinnaker/deck/compare/25d690eee9b316e283a0a2e6e3c2e429d67f01f6...39c5ab2ceee144702750f12d734c702c0ee16911) (2017-11-16)


### Changes

chore(core): bump package to 0.0.108 [#4467](https://github.com/spinnaker/deck/pull/4467) ([39c5ab2c](https://github.com/spinnaker/deck/commit/39c5ab2ceee144702750f12d734c702c0ee16911))  
fix(core/delivery): Fix jumping to same type stage when clicking details sections [#4466](https://github.com/spinnaker/deck/pull/4466) ([acb98bcf](https://github.com/spinnaker/deck/commit/acb98bcf9aa64f8ceaf4018f5d4259a6f6d10ef8))  
feat(core/search): tweak styles on v2 search [#4462](https://github.com/spinnaker/deck/pull/4462) ([42097475](https://github.com/spinnaker/deck/commit/420974754b2d7f58611506336507b397badc648f))  
feat(provider/kubernetes): render security groups [#4460](https://github.com/spinnaker/deck/pull/4460) ([e6148d3a](https://github.com/spinnaker/deck/commit/e6148d3a0529e83a2c5f18ca36dd128c9f903a18))  
fix(core): add space between icon and label on create pipeline button [#4455](https://github.com/spinnaker/deck/pull/4455) ([528744f9](https://github.com/spinnaker/deck/commit/528744f9c9fe6b06ad1a536910798347c34e8e86))  
fix(moniker): sort server groups by sequence if possible [#4459](https://github.com/spinnaker/deck/pull/4459) ([0ac9a680](https://github.com/spinnaker/deck/commit/0ac9a680cf7567f2b121d0992b164004181c7317))  
feat(provider/kubernetes): scale modal [#4458](https://github.com/spinnaker/deck/pull/4458) ([01f047dc](https://github.com/spinnaker/deck/commit/01f047dcbc70192901db6d62da0bccfd171a4492))  
feat(serverGroupManager): use fa-server icon [#4457](https://github.com/spinnaker/deck/pull/4457) ([f8219767](https://github.com/spinnaker/deck/commit/f8219767020e256b36529c46f38aa8c75f7f330c))  



## [0.0.107](https://www.github.com/spinnaker/deck/compare/5b0f95e7769e238b3f62612e2eb8d3951ae382f7...25d690eee9b316e283a0a2e6e3c2e429d67f01f6) (2017-11-15)


### Changes

chore(core): Bump package to 0.0.107 [#4456](https://github.com/spinnaker/deck/pull/4456) ([25d690ee](https://github.com/spinnaker/deck/commit/25d690eee9b316e283a0a2e6e3c2e429d67f01f6))  
refactor(core/presentation): Refactor footer css to be reusable [#4454](https://github.com/spinnaker/deck/pull/4454) ([f8e5ad55](https://github.com/spinnaker/deck/commit/f8e5ad5554e06cbd0094671dc07faacdcbc832db))  
fix(core): fix race condition in versioned cloud provider [#4453](https://github.com/spinnaker/deck/pull/4453) ([875a827e](https://github.com/spinnaker/deck/commit/875a827e05a184180904aaaac12b0f00702c0a8f))  
fix(pagerduty): Fix a paging string and tweak the task monitor config interface [#4450](https://github.com/spinnaker/deck/pull/4450) ([b5b982fc](https://github.com/spinnaker/deck/commit/b5b982fcbfe23d716f5971258414a646a7a9387e))  
feat(core): server group manager styles [#4447](https://github.com/spinnaker/deck/pull/4447) ([60dd0f7d](https://github.com/spinnaker/deck/commit/60dd0f7dc00979617ef32a9ddaa81661608d5918))  



## [0.0.106](https://www.github.com/spinnaker/deck/compare/514d1d3aa50684232309e0797164fa40b40e6c87...5b0f95e7769e238b3f62612e2eb8d3951ae382f7) (2017-11-14)


### Changes

chore(core): Bump to 0.0.106 [#4446](https://github.com/spinnaker/deck/pull/4446) ([5b0f95e7](https://github.com/spinnaker/deck/commit/5b0f95e7769e238b3f62612e2eb8d3951ae382f7))  
refactor(core/pagerduty): Convert bits to TS, support multiple applications, other stuff [#4436](https://github.com/spinnaker/deck/pull/4436) ([470e8e2d](https://github.com/spinnaker/deck/commit/470e8e2d8bce6e76095d381862885814d6c7de0e))  
fix(core/pipeline): Fix a couple undefined errors in execution details [#4445](https://github.com/spinnaker/deck/pull/4445) ([c2170de7](https://github.com/spinnaker/deck/commit/c2170de75a89e5c4866db6f7183a4aec18360d60))  



## [0.0.105](https://www.github.com/spinnaker/deck/compare/818ae098418d640db1986726a344c9bec39b4dd5...514d1d3aa50684232309e0797164fa40b40e6c87) (2017-11-14)


### Changes

chore(core): bump package to 0.0.105 [#4443](https://github.com/spinnaker/deck/pull/4443) ([514d1d3a](https://github.com/spinnaker/deck/commit/514d1d3aa50684232309e0797164fa40b40e6c87))  
feat(core/search): Add health counts to server groups search results [#4439](https://github.com/spinnaker/deck/pull/4439) ([87590ae8](https://github.com/spinnaker/deck/commit/87590ae8a799d8c3aff8de62e5f8ae441d6bcdb0))  
refactor(core/search): Create consts for ISearchResultTypes and individual search components ([08feee4a](https://github.com/spinnaker/deck/commit/08feee4a057c27967a408d58f84fee0787f894fe))  



## [0.0.104](https://www.github.com/spinnaker/deck/compare/643309d9966b4814323e38c574bf501825cdcfe1...818ae098418d640db1986726a344c9bec39b4dd5) (2017-11-13)


### Changes

chore(core): bump package to 0.0.104 [#4434](https://github.com/spinnaker/deck/pull/4434) ([818ae098](https://github.com/spinnaker/deck/commit/818ae098418d640db1986726a344c9bec39b4dd5))  
feat(core): Versioned provider lb creation [#4428](https://github.com/spinnaker/deck/pull/4428) ([facde2a4](https://github.com/spinnaker/deck/commit/facde2a40b81d495df4763e71477c3ac61926880))  
fix(core): hide jenkins failure message when controller not initialized [#4432](https://github.com/spinnaker/deck/pull/4432) ([c12b074e](https://github.com/spinnaker/deck/commit/c12b074e70a15bc92b785722fe6103d013141704))  
feat(core): server group manager button on server group pod [#4431](https://github.com/spinnaker/deck/pull/4431) ([e0f7ee87](https://github.com/spinnaker/deck/commit/e0f7ee8780ff4be46af712a7bfaf61bd6e860fc9))  
feat(core): allow pipeline parameters to be required [#4429](https://github.com/spinnaker/deck/pull/4429) ([b5eafd3f](https://github.com/spinnaker/deck/commit/b5eafd3f30bb79ea5330091e564e6352b5233537))  
Server Group Manager data source, states, and kubernetes details [#4421](https://github.com/spinnaker/deck/pull/4421) ([a8dd439f](https://github.com/spinnaker/deck/commit/a8dd439fc67f008bcbe5e12f26388f79841b9cdc))  
feat(provider/kubernetes): v2 pod logs [#4411](https://github.com/spinnaker/deck/pull/4411) ([96271b84](https://github.com/spinnaker/deck/commit/96271b84f26e70a036bf3f05af49a056ae09e2c3))  
feat(core): server group manager module, service, and typings [#4420](https://github.com/spinnaker/deck/pull/4420) ([e79aab03](https://github.com/spinnaker/deck/commit/e79aab036844952240154a9acdbeecacef1a2a74))  



## [0.0.103](https://www.github.com/spinnaker/deck/compare/6d2a3c4d7db415e2aa1f7ca42f59a71db243adab...643309d9966b4814323e38c574bf501825cdcfe1) (2017-11-09)


### Changes

chore(core): bump core to 0.0.103 ([643309d9](https://github.com/spinnaker/deck/commit/643309d9966b4814323e38c574bf501825cdcfe1))  
refactor(core/search): refactor search v2 to be more component based ([7aa7d4e2](https://github.com/spinnaker/deck/commit/7aa7d4e258cf9fc320217bc85a0da2a4a9624ef9))  



## [0.0.102](https://www.github.com/spinnaker/deck/compare/c5021a1989755c64e24510d4efb6044dea4cb64f...6d2a3c4d7db415e2aa1f7ca42f59a71db243adab) (2017-11-09)


### Changes

chore(core): bump package to 0.0.102 [#4417](https://github.com/spinnaker/deck/pull/4417) ([6d2a3c4d](https://github.com/spinnaker/deck/commit/6d2a3c4d7db415e2aa1f7ca42f59a71db243adab))  
feat(core): Support delay before scale down when red/black'ing [#4413](https://github.com/spinnaker/deck/pull/4413) ([559156e6](https://github.com/spinnaker/deck/commit/559156e67ea57c1e426314a2fbd2811f999090d9))  
 feat(pipeline_template): Better support for templated pipelines with dynamic sources - Take 2 [#4365](https://github.com/spinnaker/deck/pull/4365) ([6c6ea94e](https://github.com/spinnaker/deck/commit/6c6ea94ec416f4d1c41bfc8085033bfde60df130))  



## [0.0.101](https://www.github.com/spinnaker/deck/compare/c6570ea5393a6e4b307fdbd34522c5dab4d6fe4c...c5021a1989755c64e24510d4efb6044dea4cb64f) (2017-11-07)


### Changes

chore(core): bump package to 0.0.101 [#4395](https://github.com/spinnaker/deck/pull/4395) ([c5021a19](https://github.com/spinnaker/deck/commit/c5021a1989755c64e24510d4efb6044dea4cb64f))  
fix(core): re-enable filter on (none) for stack/detail [#4394](https://github.com/spinnaker/deck/pull/4394) ([f1d523bc](https://github.com/spinnaker/deck/commit/f1d523bcdb1753adce3773aa7016a8e21e7707a2))  



## [0.0.100](https://www.github.com/spinnaker/deck/compare/fd40ce5d4db52ab24522ecd369ad6013a249e2f4...c6570ea5393a6e4b307fdbd34522c5dab4d6fe4c) (2017-11-07)


### Changes

chore(core): bump package to 0.0.100 [#4393](https://github.com/spinnaker/deck/pull/4393) ([c6570ea5](https://github.com/spinnaker/deck/commit/c6570ea5393a6e4b307fdbd34522c5dab4d6fe4c))  
feat(core): provide simple general purpose event bus [#4390](https://github.com/spinnaker/deck/pull/4390) ([c629fdb0](https://github.com/spinnaker/deck/commit/c629fdb04da517e853e6b3acddf6df7db2d76ba2))  
fix(core): Fix a few more undefined errors from execution details conversions [#4392](https://github.com/spinnaker/deck/pull/4392) ([655e4418](https://github.com/spinnaker/deck/commit/655e441860e36c42b1d91e782e190f90026c253c))  
fix(core): Favor using `stageId` when building links to failed stages [#4391](https://github.com/spinnaker/deck/pull/4391) ([0c89da02](https://github.com/spinnaker/deck/commit/0c89da02d2df33f2f753de56387d545382c369b6))  
fix(core): Link to failed stage had incorrect name [#4388](https://github.com/spinnaker/deck/pull/4388) ([a4917caa](https://github.com/spinnaker/deck/commit/a4917caa2d9037a30026ef3d67922b571bfa4492))  



## [0.0.99](https://www.github.com/spinnaker/deck/compare/e49b525e808731fb333dabf1371c4c5e62a1ace4...fd40ce5d4db52ab24522ecd369ad6013a249e2f4) (2017-11-07)


### Changes

chore(core): bump package to 0.0.99 [#4386](https://github.com/spinnaker/deck/pull/4386) ([fd40ce5d](https://github.com/spinnaker/deck/commit/fd40ce5d4db52ab24522ecd369ad6013a249e2f4))  
fix(core): Link to child executions when no error messages available [#4383](https://github.com/spinnaker/deck/pull/4383) ([109dd04b](https://github.com/spinnaker/deck/commit/109dd04bc801e6bf4cde82c120cde11dfb023181))  
feat(core): resolve provider version for standalone instances [#4384](https://github.com/spinnaker/deck/pull/4384) ([363e5fb5](https://github.com/spinnaker/deck/commit/363e5fb50cbfbfdcb6f11e799b493ce4ffba3f2a))  



## [0.0.98](https://www.github.com/spinnaker/deck/compare/9f34bde981052dda40bbae0f4d1775d9415cd0b8...e49b525e808731fb333dabf1371c4c5e62a1ace4) (2017-11-06)


### Changes

chore(core): bump package to 0.0.98 [#4382](https://github.com/spinnaker/deck/pull/4382) ([e49b525e](https://github.com/spinnaker/deck/commit/e49b525e808731fb333dabf1371c4c5e62a1ace4))  
feat(core): Link to failing synthetic stage rather than "No reason provided." [#4381](https://github.com/spinnaker/deck/pull/4381) ([8906216c](https://github.com/spinnaker/deck/commit/8906216c2cf854e138e9d7672cbd23e97984dcd6))  
feat(moniker): Use server group moniker in multi-select [#4377](https://github.com/spinnaker/deck/pull/4377) ([d8dc3259](https://github.com/spinnaker/deck/commit/d8dc325967f5ecccd30ba75fc677f0e1c6b79047))  
fix(core): enable history comparison for strategies [#4380](https://github.com/spinnaker/deck/pull/4380) ([3fcdc0dc](https://github.com/spinnaker/deck/commit/3fcdc0dc2c8b4e482b3945acb99517bbbb2154a3))  
chore(*): placate linter [#4378](https://github.com/spinnaker/deck/pull/4378) ([0a56a6e5](https://github.com/spinnaker/deck/commit/0a56a6e54f4d29d712e21b6b309f37ee4dd029b6))  
feat: resolve provider version in instance state [#4376](https://github.com/spinnaker/deck/pull/4376) ([cf73d070](https://github.com/spinnaker/deck/commit/cf73d070acfa049cc26209a9c142d98a60530c94))  
fix(moniker): Arrange cluster by moniker.cluster if available [#4369](https://github.com/spinnaker/deck/pull/4369) ([9d7925ac](https://github.com/spinnaker/deck/commit/9d7925accd4ab5c9778e4081974768255827548d))  
feat(core): Versioned provider load balancer [#4374](https://github.com/spinnaker/deck/pull/4374) ([382278d7](https://github.com/spinnaker/deck/commit/382278d71fbe05133f915805c560cf1255a7e9a2))  
fix(core): support failed health status style [#4372](https://github.com/spinnaker/deck/pull/4372) ([1ca375ff](https://github.com/spinnaker/deck/commit/1ca375ff963e2dd7d9cd6e05450dfc0dc5429d5f))  
feat(provider/kubernetes): v2 manifest delete ctrl [#4370](https://github.com/spinnaker/deck/pull/4370) ([45880cca](https://github.com/spinnaker/deck/commit/45880cca3b19a08dbd61402a472079bec14386a7))  
fix(core/pipeline): Fix a couple undefined errors in execution details ([9128ca3b](https://github.com/spinnaker/deck/commit/9128ca3b4156a2d02959835cd5af2b297e299aaf))  



## [0.0.97](https://www.github.com/spinnaker/deck/compare/15f7b78478810d09fa62391ca77624f035d3709d...9f34bde981052dda40bbae0f4d1775d9415cd0b8) (2017-11-02)


### Changes

chore(core): bump package to 0.0.97 [#4363](https://github.com/spinnaker/deck/pull/4363) ([9f34bde9](https://github.com/spinnaker/deck/commit/9f34bde981052dda40bbae0f4d1775d9415cd0b8))  
Revert: feat(pipeline_template): Better support for templated pipelines with dynamic sources [#4362](https://github.com/spinnaker/deck/pull/4362) ([70cb6816](https://github.com/spinnaker/deck/commit/70cb681628673626904b7eb2e624df9526905ff2))  



## [0.0.96](https://www.github.com/spinnaker/deck/compare/934a78bc3263e43bb74b3f89a3636c100280f019...15f7b78478810d09fa62391ca77624f035d3709d) (2017-11-02)


### Changes

chore(core): bump package to 0.0.96 [#4360](https://github.com/spinnaker/deck/pull/4360) ([15f7b784](https://github.com/spinnaker/deck/commit/15f7b78478810d09fa62391ca77624f035d3709d))  
refactor(*/pipeline): Convert clone server group execution details to react [#4359](https://github.com/spinnaker/deck/pull/4359) ([25ff3a1a](https://github.com/spinnaker/deck/commit/25ff3a1abbdb72f6d128adba26fcaaa61d13ab90))  
chore(style/header): tweak the pixels ([95fae048](https://github.com/spinnaker/deck/commit/95fae0480edb91c7f39a136ceff8c4f2feebe870))  
fix(core): omit exception message on stopped manual judgment stages [#4357](https://github.com/spinnaker/deck/pull/4357) ([29639d58](https://github.com/spinnaker/deck/commit/29639d5862da4e99bb40a15e0c1fab02c15dbf01))  
refactor(core/pipeline): Refactor execution window and manual judgment details to react [#4355](https://github.com/spinnaker/deck/pull/4355) ([edcc225b](https://github.com/spinnaker/deck/commit/edcc225b2c7f7c919202c80a927fea730d0f7cca))  
fix(core/projects): restore refresh icon, set app name on init [#4354](https://github.com/spinnaker/deck/pull/4354) ([612495d3](https://github.com/spinnaker/deck/commit/612495d3e7fcbe2a846df1c8167784454ce80d1d))  
feat(pipeline_template): Better support for templated pipelines with dynamic sources [#4288](https://github.com/spinnaker/deck/pull/4288) ([7695c023](https://github.com/spinnaker/deck/commit/7695c023880dc2fb46531bb44b8a60d6ddb3dc73))  
refactor(core/pipeline): Convert *ClusterExecutionDetails to react ([44c9505b](https://github.com/spinnaker/deck/commit/44c9505babf43bfcd97be3d1f80868edd91cc3a2))  
refactor(core/pipeline): Convert *AsgExecutionDetails to react ([bbe7283a](https://github.com/spinnaker/deck/commit/bbe7283ac4430912f06ef386f1e9b37011a2b784))  



## [0.0.95](https://www.github.com/spinnaker/deck/compare/1c60457b4e12a436c399074e25aa704987e67ac8...934a78bc3263e43bb74b3f89a3636c100280f019) (2017-11-01)


### Changes

chore(core): bump package to 0.0.95 [#4350](https://github.com/spinnaker/deck/pull/4350) ([934a78bc](https://github.com/spinnaker/deck/commit/934a78bc3263e43bb74b3f89a3636c100280f019))  
feat(artifacts): Reference ExpectedArtifacts by id. [#4349](https://github.com/spinnaker/deck/pull/4349) ([745c33fd](https://github.com/spinnaker/deck/commit/745c33fd40ef0a863060e444ef343b084c35ecca))  
fix(amazon): Traffic should be enabled when choosing a non-custom strategy [#4348](https://github.com/spinnaker/deck/pull/4348) ([4f2a14dc](https://github.com/spinnaker/deck/commit/4f2a14dcd5a5df40dac1e07db7510d95ac8b31a7))  
fix(core): revert word-break/overflow-wrap swaps [#4344](https://github.com/spinnaker/deck/pull/4344) ([84223b20](https://github.com/spinnaker/deck/commit/84223b2076d0daccde34e99ed99674dbe92a878a))  
fix(core/runningTasks): Use popover so z-index is above all other elements ([a688387b](https://github.com/spinnaker/deck/commit/a688387b548d923ecb84a814ec4bdb618daabbd1))  
refactor(kubernetes): kubernetes is now a module [#4337](https://github.com/spinnaker/deck/pull/4337) ([5879eabe](https://github.com/spinnaker/deck/commit/5879eabe6d724250ec0c182d50270900dcf1524c))  
refactor(core/pipeline): Convert a few execution source details to react [#4339](https://github.com/spinnaker/deck/pull/4339) ([4d9e8c4f](https://github.com/spinnaker/deck/commit/4d9e8c4f0cbd7d08f89a752e751c4b4cdcf4eb5a))  



## [0.0.94](https://www.github.com/spinnaker/deck/compare/2cd74c03a046f63bffb990fc5bd51052d8cd1e93...1c60457b4e12a436c399074e25aa704987e67ac8) (2017-10-28)


### Changes

chore(core): bump package to 0.0.94 ([1c60457b](https://github.com/spinnaker/deck/commit/1c60457b4e12a436c399074e25aa704987e67ac8))  
fix(canary): Fix moniker for baseline/canary clusters ([00367ebb](https://github.com/spinnaker/deck/commit/00367ebb176582aa57e3c34b3473e2b57f1dd403))  
fix(core): Fix unhandled rejection on auth recheck [#4335](https://github.com/spinnaker/deck/pull/4335) ([525323e0](https://github.com/spinnaker/deck/commit/525323e04b017705dc14917ffd7ab869a8e60502))  
fix(core): replace word-break CSS with overflow-wrap [#4334](https://github.com/spinnaker/deck/pull/4334) ([abfbb321](https://github.com/spinnaker/deck/commit/abfbb321fd7656168e9986fa02f903ab91c05779))  
fix(core): Fix groups from breaking executions view [#4336](https://github.com/spinnaker/deck/pull/4336) ([ee646f8c](https://github.com/spinnaker/deck/commit/ee646f8cacd1b22aa0b0052c1f04a75f594de672))  
chore(core): Remove console.log statement. [#4333](https://github.com/spinnaker/deck/pull/4333) ([bc33c761](https://github.com/spinnaker/deck/commit/bc33c7617482290406d55377b11f483252d15f33))  
chore(core): remove happypack in favor of thread-loader/cache-loader [#4330](https://github.com/spinnaker/deck/pull/4330) ([c661dccf](https://github.com/spinnaker/deck/commit/c661dccfe04fb44f78f64bcbd2a05debb8d46d43))  
fix(core): Stop grouping groups with only one stage [#4332](https://github.com/spinnaker/deck/pull/4332) ([3432b937](https://github.com/spinnaker/deck/commit/3432b937ce9bb536aa356a76943092d04a834b72))  
feat(trigger/pubsub): Suggest subscriptions from echo configuration. [#4328](https://github.com/spinnaker/deck/pull/4328) ([a3825a04](https://github.com/spinnaker/deck/commit/a3825a0477b7a8a8511e29c24cdadbafa79a95ec))  



## [0.0.93](https://www.github.com/spinnaker/deck/compare/6804721b7bdeecbd0345797861db31ce35a1265b...2cd74c03a046f63bffb990fc5bd51052d8cd1e93) (2017-10-24)


### Changes

chore(core): bump package to 0.0.93 [#4326](https://github.com/spinnaker/deck/pull/4326) ([2cd74c03](https://github.com/spinnaker/deck/commit/2cd74c03a046f63bffb990fc5bd51052d8cd1e93))  
fix(core): handle spel expressions in map editors [#4325](https://github.com/spinnaker/deck/pull/4325) ([23e5d35a](https://github.com/spinnaker/deck/commit/23e5d35a076af11e7caa9218d51120d61f453121))  
refactor(*): More execution details refactoring [#4324](https://github.com/spinnaker/deck/pull/4324) ([ababde6d](https://github.com/spinnaker/deck/commit/ababde6dea29347e4bff81840c7e3b1fa685aaa0))  
fix(core/executions): tweak padding on details tabs, status glyph [#4323](https://github.com/spinnaker/deck/pull/4323) ([ce13de57](https://github.com/spinnaker/deck/commit/ce13de571cfdd15ede3e00ed0ce270a69fb838d2))  
fix(core): Fix lint [#4321](https://github.com/spinnaker/deck/pull/4321) ([00e76b76](https://github.com/spinnaker/deck/commit/00e76b76a7b9004aafbdce4af5b5bf7282b212ad))  



## [0.0.92](https://www.github.com/spinnaker/deck/compare/64b53f09824e55386cf023b0224da74b35975d3e...6804721b7bdeecbd0345797861db31ce35a1265b) (2017-10-24)


### Changes

chore(core): bump package to 0.0.92 ([6804721b](https://github.com/spinnaker/deck/commit/6804721b7bdeecbd0345797861db31ce35a1265b))  
feat(core/modal): Silence all rejection warning in console when ui-bootstrap modals are closed/cancelled. ([b2bcb5d2](https://github.com/spinnaker/deck/commit/b2bcb5d213dc6cd2fcde1854b304e5142d72590e))  
fix(core): Fix wait stage task time updating [#4320](https://github.com/spinnaker/deck/pull/4320) ([10895692](https://github.com/spinnaker/deck/commit/10895692a92d66c8264e20a8ba865b338b4e33d9))  
fix(core): handle running execution fetch failure [#4319](https://github.com/spinnaker/deck/pull/4319) ([a82a4d4d](https://github.com/spinnaker/deck/commit/a82a4d4d6449805ba8bae24cfe161e820e2502d9))  



## [0.0.91](https://www.github.com/spinnaker/deck/compare/4f34fca5715de414f178233420b2d02a32b38a47...64b53f09824e55386cf023b0224da74b35975d3e) (2017-10-24)


### Changes

chore(core): Bump core to 0.0.91 [#4316](https://github.com/spinnaker/deck/pull/4316) ([64b53f09](https://github.com/spinnaker/deck/commit/64b53f09824e55386cf023b0224da74b35975d3e))  
refactor(*): Remove duplicate execution details templates [#4314](https://github.com/spinnaker/deck/pull/4314) ([de1524a8](https://github.com/spinnaker/deck/commit/de1524a8ae7897441d5fd150d5e4189dc67891a2))  
fix(core/pipeline): Show errors in time window stage execution details [#4315](https://github.com/spinnaker/deck/pull/4315) ([61c96ecf](https://github.com/spinnaker/deck/commit/61c96ecf6f833834fbc46c29416e9e7ab849d1b7))  



## [0.0.90](https://www.github.com/spinnaker/deck/compare/503b81923930a7f59418c542e78bc93c168984ec...4f34fca5715de414f178233420b2d02a32b38a47) (2017-10-23)


### Changes

chore(core): bump package to 0.0.90 [#4313](https://github.com/spinnaker/deck/pull/4313) ([4f34fca5](https://github.com/spinnaker/deck/commit/4f34fca5715de414f178233420b2d02a32b38a47))  
feat(core): add detail filter to cluster/lb/sg views [#4311](https://github.com/spinnaker/deck/pull/4311) ([67fdca8a](https://github.com/spinnaker/deck/commit/67fdca8a349397bf64fa49f5e6f1e4da3505ad19))  
feat(rrb): Allow for specifying pipelines to run before disable [#4308](https://github.com/spinnaker/deck/pull/4308) ([c957208e](https://github.com/spinnaker/deck/commit/c957208e59ada7c389098cf4d4db5ce0f71fadc4))  
fix(core): Navigate to the first stage if passed in stage does not exist [#4309](https://github.com/spinnaker/deck/pull/4309) ([3f3258f9](https://github.com/spinnaker/deck/commit/3f3258f98daea1bdb2c2f7aea1b017333d649ac6))  
refactor(*): Consistent bracket spacing [#4307](https://github.com/spinnaker/deck/pull/4307) ([484c91a3](https://github.com/spinnaker/deck/commit/484c91a34374fe06a4c4f52642f204b8f2fa0f78))  
refactor(core/delivery): Convert waitExecutionDetails to react [#4297](https://github.com/spinnaker/deck/pull/4297) ([9dcc554e](https://github.com/spinnaker/deck/commit/9dcc554e55b83b74093528ce1e43de2721034cb0))  
fix(core): Fix lint [#4306](https://github.com/spinnaker/deck/pull/4306) ([26aad5ed](https://github.com/spinnaker/deck/commit/26aad5edff326745eae7cbd0ea8d50d3a5b936c5))  
fix(core/loadBalancer): Actually check for all the changes to props [#4305](https://github.com/spinnaker/deck/pull/4305) ([473ba906](https://github.com/spinnaker/deck/commit/473ba9067b224741bdfecc1cf269a34549561dc4))  
refactor(*): Fix all the postcss-color warnings except the hard one [#4304](https://github.com/spinnaker/deck/pull/4304) ([db2275bb](https://github.com/spinnaker/deck/commit/db2275bb48c3371e1f90bf2b83ff59e5ff8af488))  
fix(core/loadBalancer): Modify shouldComponentUpdate to allow for more specific updates [#4302](https://github.com/spinnaker/deck/pull/4302) ([899d7e0e](https://github.com/spinnaker/deck/commit/899d7e0eb1924d3faae2c829dcf185693e1eb3cc))  



## [0.0.89](https://www.github.com/spinnaker/deck/compare/4048cee49aa6ea82616c0408ee6fee9feff618f6...503b81923930a7f59418c542e78bc93c168984ec) (2017-10-20)


### Changes

fix(core/amazon): fix application name on server group command [#4298](https://github.com/spinnaker/deck/pull/4298) ([503b8192](https://github.com/spinnaker/deck/commit/503b81923930a7f59418c542e78bc93c168984ec))  
refactor(core/delivery): Convert stageFailureMessage to react [#4296](https://github.com/spinnaker/deck/pull/4296) ([43dfeb38](https://github.com/spinnaker/deck/commit/43dfeb38790ac1b4344cac7d000cdd67ad4ba993))  



## [0.0.88](https://www.github.com/spinnaker/deck/compare/aa548f7a683d4b9c77e38a30d8551428a1ac0f77...4048cee49aa6ea82616c0408ee6fee9feff618f6) (2017-10-19)


### Changes

chore(core): bump package to 0.0.88 [#4295](https://github.com/spinnaker/deck/pull/4295) ([4048cee4](https://github.com/spinnaker/deck/commit/4048cee49aa6ea82616c0408ee6fee9feff618f6))  
chore(core/amazon): make moniker changes library-friendly [#4294](https://github.com/spinnaker/deck/pull/4294) ([f9d0ed52](https://github.com/spinnaker/deck/commit/f9d0ed52586ec0b68a45f9ac34a5a7b12dac3d0d))  



## [0.0.87](https://www.github.com/spinnaker/deck/compare/aed2797919ea6c8b7ab931b148eebb3ae633435c...aa548f7a683d4b9c77e38a30d8551428a1ac0f77) (2017-10-19)


### Changes

chore(core): bump package to 0.0.87 [#4292](https://github.com/spinnaker/deck/pull/4292) ([aa548f7a](https://github.com/spinnaker/deck/commit/aa548f7a683d4b9c77e38a30d8551428a1ac0f77))  
refactor(core/delivery): Convert ExecutionDetails to react [#4282](https://github.com/spinnaker/deck/pull/4282) ([5ca919a6](https://github.com/spinnaker/deck/commit/5ca919a67c847672844dd4f97088a1925b921d14))  
fix(core): render reason as HTML in tasks view [#4290](https://github.com/spinnaker/deck/pull/4290) ([ca972f14](https://github.com/spinnaker/deck/commit/ca972f143be5c2c60042d5c0306f8a75dd817c52))  
feat(provider/gae): Specify artifact in GAE deploy from GCS. [#4280](https://github.com/spinnaker/deck/pull/4280) ([e8f33c3a](https://github.com/spinnaker/deck/commit/e8f33c3ac33cb437e904c30a96fc1a970059e4f9))  
feat(entitytags): Show replaced server group details in popover [#4284](https://github.com/spinnaker/deck/pull/4284) ([76ae6e6a](https://github.com/spinnaker/deck/commit/76ae6e6aa8b6f52783a50a95bc82f551200855db))  
feat(provider/kubernetes): v2 resize modal [#4279](https://github.com/spinnaker/deck/pull/4279) ([f2bb3f81](https://github.com/spinnaker/deck/commit/f2bb3f812e5f04c23c1ecc3f18c0a2b14eba3772))  



## [0.0.86](https://www.github.com/spinnaker/deck/compare/180fa94346b52759b71e228395d80463ea82d99c...aed2797919ea6c8b7ab931b148eebb3ae633435c) (2017-10-16)


### Changes

chore(core): bump package to 0.0.86 [#4277](https://github.com/spinnaker/deck/pull/4277) ([aed27979](https://github.com/spinnaker/deck/commit/aed2797919ea6c8b7ab931b148eebb3ae633435c))  
feat(moniker) - adding monikers to load balancers [#4278](https://github.com/spinnaker/deck/pull/4278) ([9141d193](https://github.com/spinnaker/deck/commit/9141d19377c2da19b5336ab5fd8a3af4600ba04b))  
fix(core): Fix configure pipeline links when details is open [#4276](https://github.com/spinnaker/deck/pull/4276) ([375cf3e5](https://github.com/spinnaker/deck/commit/375cf3e5c4e2149ddc2c0737249bf96cc769773e))  
fix(core): catch exceptions on server group source when pipeline 404s [#4275](https://github.com/spinnaker/deck/pull/4275) ([dabbafb9](https://github.com/spinnaker/deck/commit/dabbafb9b33074ee07db131fda60f5f9a3d0e78e))  
 refactor(core): Convert executionDetailsSectionNav, executionStepDetails, statusGlyph to react [#4273](https://github.com/spinnaker/deck/pull/4273) ([85ad05af](https://github.com/spinnaker/deck/commit/85ad05af5a9326e9db5e98e38b22a292d1303c24))  
feat(moniker) - adding monikers to the deploy stage [#4268](https://github.com/spinnaker/deck/pull/4268) ([963e3f0d](https://github.com/spinnaker/deck/commit/963e3f0d3890020b9d0f0e6499c3ee2647e5888e))  
fix(artifact): s/ul/ol [#4271](https://github.com/spinnaker/deck/pull/4271) ([dac415a4](https://github.com/spinnaker/deck/commit/dac415a47fe9dddbfc46e5f6208bf0ca1306a689))  
feat(artifact): Custom artifact helpers [#4267](https://github.com/spinnaker/deck/pull/4267) ([b11c2291](https://github.com/spinnaker/deck/commit/b11c2291786975ed9ab027e9773feb6977820152))  
chore(core): Remove unused components [#4269](https://github.com/spinnaker/deck/pull/4269) ([7b14c879](https://github.com/spinnaker/deck/commit/7b14c879bf081a9e7ae3e329bf235ed08eb2cdf5))  



## [0.0.85](https://www.github.com/spinnaker/deck/compare/d85c61814f5d7a2071526a39e465e3eb8591b62a...180fa94346b52759b71e228395d80463ea82d99c) (2017-10-14)


### Changes

chore(core): Bump module to 0.0.85 [#4265](https://github.com/spinnaker/deck/pull/4265) ([180fa943](https://github.com/spinnaker/deck/commit/180fa94346b52759b71e228395d80463ea82d99c))  
feat(artifacts): Simplify expected artifacts [#4266](https://github.com/spinnaker/deck/pull/4266) ([1ce92060](https://github.com/spinnaker/deck/commit/1ce92060835fa672f82f10e31e18031ce0434bb1))  
fix(core): Fix rendering executions that have JSON in the parameters [#4264](https://github.com/spinnaker/deck/pull/4264) ([08183404](https://github.com/spinnaker/deck/commit/08183404eeca25301e4c79f6e0c698b63919ca98))  
fix(pipelines): add validator to webhook stage "method" field [#4263](https://github.com/spinnaker/deck/pull/4263) ([f2401496](https://github.com/spinnaker/deck/commit/f2401496ee9b1598794148e3964eea367b150de3))  
refactor(core): Convert executions to react [#4260](https://github.com/spinnaker/deck/pull/4260) ([a4cfb59e](https://github.com/spinnaker/deck/commit/a4cfb59e203f4fef9f34705febf08ebdd7b1a893))  



## [0.0.84](https://www.github.com/spinnaker/deck/compare/e183d9a480bb89364784ab72a05b9ac1349860b5...d85c61814f5d7a2071526a39e465e3eb8591b62a) (2017-10-13)


### Changes

chore(core): bump package to 0.0.84 [#4259](https://github.com/spinnaker/deck/pull/4259) ([d85c6181](https://github.com/spinnaker/deck/commit/d85c61814f5d7a2071526a39e465e3eb8591b62a))  
fix(core/datasource): Possibly unhandled rejection: undefined [#4257](https://github.com/spinnaker/deck/pull/4257) ([346348ff](https://github.com/spinnaker/deck/commit/346348ff9e5bd9a1ab675d039799aa07b6704c1b))  
refactore(core): Convert ExecutionStatus to react [#4254](https://github.com/spinnaker/deck/pull/4254) ([66dfb837](https://github.com/spinnaker/deck/commit/66dfb837a04567b3b777bf69cee63a17e4c97993))  
refactor(core/search): Remove client-side fetch of servergroups in favor of culling missing entities on the server ([fe3e9f09](https://github.com/spinnaker/deck/commit/fe3e9f093184c6b0c18e1c1f001cb6716dbab575))  
feat(search): add server groups to clusters ([9febcf97](https://github.com/spinnaker/deck/commit/9febcf97eb11876880af857aa702c5358efcefc1))  



## [0.0.83](https://www.github.com/spinnaker/deck/compare/4e719ee8c2c7d8e4ea3bf1c3076bcadd418a1141...e183d9a480bb89364784ab72a05b9ac1349860b5) (2017-10-12)


### Changes

chore(core): bump package to 0.0.83 [#4251](https://github.com/spinnaker/deck/pull/4251) ([e183d9a4](https://github.com/spinnaker/deck/commit/e183d9a480bb89364784ab72a05b9ac1349860b5))  
fix(core): catch dismiss of confirmation/cancel modals [#4250](https://github.com/spinnaker/deck/pull/4250) ([eccc3cfe](https://github.com/spinnaker/deck/commit/eccc3cfe63109a5664d3f0b12ac1f35d3df0482f))  
refactor(core): convert create pipeline to react [#4248](https://github.com/spinnaker/deck/pull/4248) ([4de48ba8](https://github.com/spinnaker/deck/commit/4de48ba88c08e890a8f06d01601b6b4593c40df1))  
fix(pipelines): guard against missing info on deploy stage [#4246](https://github.com/spinnaker/deck/pull/4246) ([0e5957b8](https://github.com/spinnaker/deck/commit/0e5957b888ad012bb09aa1a82428a4ecfc485daa))  



## [0.0.81](https://www.github.com/spinnaker/deck/compare/cf68c9c13ff22efadc922eb5c23ac545d08ee5e7...4e719ee8c2c7d8e4ea3bf1c3076bcadd418a1141) (2017-10-10)


### Changes

chore(core): bump package to 0.0.81 [#4244](https://github.com/spinnaker/deck/pull/4244) ([4e719ee8](https://github.com/spinnaker/deck/commit/4e719ee8c2c7d8e4ea3bf1c3076bcadd418a1141))  
fix(core): catch modal dismiss [#4242](https://github.com/spinnaker/deck/pull/4242) ([f2f14b3e](https://github.com/spinnaker/deck/commit/f2f14b3e1bedec1e6dbd3ce61deeef388c802e98))  
chore(core): Remove some old files and convert a test to ts [#4243](https://github.com/spinnaker/deck/pull/4243) ([3bac0248](https://github.com/spinnaker/deck/commit/3bac02489533fe84a4f2bc0f3135dc555790b863))  
feat(core): Version server group transformer delegate [#4237](https://github.com/spinnaker/deck/pull/4237) ([4dec85df](https://github.com/spinnaker/deck/commit/4dec85dfa1166a3b040aa1cb3c6c924f48485f05))  
feat(pipelines): use textarea for expression entry [#4240](https://github.com/spinnaker/deck/pull/4240) ([f85d987f](https://github.com/spinnaker/deck/commit/f85d987f1b3b46e55b1e30dc8003f3a9ec82d5bf))  



## [0.0.80](https://www.github.com/spinnaker/deck/compare/1eb68647090cd456f6e8dfcaff1c07047d39a2d1...cf68c9c13ff22efadc922eb5c23ac545d08ee5e7) (2017-10-09)


### Changes

chore(core): bump package to 0.0.80 [#4239](https://github.com/spinnaker/deck/pull/4239) ([cf68c9c1](https://github.com/spinnaker/deck/commit/cf68c9c13ff22efadc922eb5c23ac545d08ee5e7))  
fix(network): include backoff, max number of retries in network recovery [#4238](https://github.com/spinnaker/deck/pull/4238) ([d0c3d2d4](https://github.com/spinnaker/deck/commit/d0c3d2d498825b9cef8a40a8e88db0307ea48164))  



## [0.0.79](https://www.github.com/spinnaker/deck/compare/ecf3cf5d1ba69b4b0a8d1f58945ad438614032bc...1eb68647090cd456f6e8dfcaff1c07047d39a2d1) (2017-10-09)


### Changes

chore(core): bump package to 0.0.79 [#4236](https://github.com/spinnaker/deck/pull/4236) ([1eb68647](https://github.com/spinnaker/deck/commit/1eb68647090cd456f6e8dfcaff1c07047d39a2d1))  
fix(core/http): retry http calls failing due to network issues [#4234](https://github.com/spinnaker/deck/pull/4234) ([68210010](https://github.com/spinnaker/deck/commit/682100108ea77c336e71ac016cb2c6b832dbb997))  
fix(core/modal): avoid throwing errors on modal $dismiss [#4233](https://github.com/spinnaker/deck/pull/4233) ([ed9c20fb](https://github.com/spinnaker/deck/commit/ed9c20fb61a17b21a0aff8a81706996416dfa796))  



## [0.0.78](https://www.github.com/spinnaker/deck/compare/0bb6484bfb647eea55629f58a8da0a29cfc2358b...ecf3cf5d1ba69b4b0a8d1f58945ad438614032bc) (2017-10-06)


### Changes

chore(core): bump package to 0.0.78 [#4230](https://github.com/spinnaker/deck/pull/4230) ([ecf3cf5d](https://github.com/spinnaker/deck/commit/ecf3cf5d1ba69b4b0a8d1f58945ad438614032bc))  
feat(provider/kubernetes): Create manifest [#4228](https://github.com/spinnaker/deck/pull/4228) ([12c0575c](https://github.com/spinnaker/deck/commit/12c0575c45a46f6e4fa0b2e08c7b229695a310db))  
feat(moniker): adds monikers to stages that includes cluster-selects [#4220](https://github.com/spinnaker/deck/pull/4220) ([2cd995c9](https://github.com/spinnaker/deck/commit/2cd995c93633f9874c4dcdfe5aaead882614e313))  
feat(script,jenkins): show contents of properties file [#4227](https://github.com/spinnaker/deck/pull/4227) ([e455df08](https://github.com/spinnaker/deck/commit/e455df08ca27dc4ad10df10a7ae71cac42ced5d2))  



## [0.0.77](https://www.github.com/spinnaker/deck/compare/8335a0be798592e1b58d711f7ce4e5eb9dd49b2e...0bb6484bfb647eea55629f58a8da0a29cfc2358b) (2017-10-05)


### Changes

chore(core): bump package to 0.0.77 [#4226](https://github.com/spinnaker/deck/pull/4226) ([0bb6484b](https://github.com/spinnaker/deck/commit/0bb6484bfb647eea55629f58a8da0a29cfc2358b))  
fix(executions): fix header alignment [#4225](https://github.com/spinnaker/deck/pull/4225) ([65c4054d](https://github.com/spinnaker/deck/commit/65c4054d42b89f8c19e3fca820b95e5820efc1c5))  
refactor(core/delivery): Convert execution filters to React [#4197](https://github.com/spinnaker/deck/pull/4197) ([e3384e3d](https://github.com/spinnaker/deck/commit/e3384e3d27d4c05b347de7c02a05727ccb957577))  
feat(redblack): Expose `delayBeforeDisableSec` [#4223](https://github.com/spinnaker/deck/pull/4223) ([ba3bc8fa](https://github.com/spinnaker/deck/commit/ba3bc8fa453ebe9d38f6c3f96fc8cbc75ffe410e))  
chore(tests): enable all tests [#4217](https://github.com/spinnaker/deck/pull/4217) ([0dc97546](https://github.com/spinnaker/deck/commit/0dc975462e8572a8a3fd1f98bd3a2512b7566223))  



## [0.0.75](https://www.github.com/spinnaker/deck/compare/215082a6e23499d9169d86484e9f3d80105d02c0...8335a0be798592e1b58d711f7ce4e5eb9dd49b2e) (2017-10-04)


### Changes

chore(chore): bump package to 0.0.75 [#4218](https://github.com/spinnaker/deck/pull/4218) ([8335a0be](https://github.com/spinnaker/deck/commit/8335a0be798592e1b58d711f7ce4e5eb9dd49b2e))  
style(core/amazon/google/kubernetes): Fixed adhoc hexcode colors to use spinnaker palette [#4206](https://github.com/spinnaker/deck/pull/4206) ([bd5c5c61](https://github.com/spinnaker/deck/commit/bd5c5c6191e37505967f759eaf44e0cc1ed7446b))  
fix(pipelines): show indicator when deleting pipeline config [#4216](https://github.com/spinnaker/deck/pull/4216) ([8cf8f0d1](https://github.com/spinnaker/deck/commit/8cf8f0d132e68534a98d17abfe2fddafc0940159))  
chore(modules): Use webpack-node-externals to exclude node_modules from @spinnaker/* bundles [#4215](https://github.com/spinnaker/deck/pull/4215) ([2a3202f7](https://github.com/spinnaker/deck/commit/2a3202f7931405a57f745b428ded3b616c463905))  
refactor(moniker): application -> app [#4213](https://github.com/spinnaker/deck/pull/4213) ([6be05518](https://github.com/spinnaker/deck/commit/6be05518c405e723517ebcba23ce30b1fd74b20a))  
naming service for sequence only uses moniker now [#4189](https://github.com/spinnaker/deck/pull/4189) ([fbbd0b79](https://github.com/spinnaker/deck/commit/fbbd0b79a23615670d51789afbc9be536bab48aa))  
feat(core): Version account lookup [#4212](https://github.com/spinnaker/deck/pull/4212) ([25b98bf4](https://github.com/spinnaker/deck/commit/25b98bf42002aeb094f8bf42215fe1e52ba9189a))  
chore(search): update badge count label [#4207](https://github.com/spinnaker/deck/pull/4207) ([19c5c7e6](https://github.com/spinnaker/deck/commit/19c5c7e60f99195caf41a0a11ee62a720fa9dc35))  
fix(core): correct access modifier from local variable in versionSelector [#4210](https://github.com/spinnaker/deck/pull/4210) ([a675542b](https://github.com/spinnaker/deck/commit/a675542b53c08e1e84fda4ea9dbc30ebfbe8fd63))  
style(all): Removed all less color variables and using CSS4 consolidated colors [#4204](https://github.com/spinnaker/deck/pull/4204) ([3c3eccc9](https://github.com/spinnaker/deck/commit/3c3eccc9c74277576cebbc3e8c5a883d01ebce8e))  
feat(core): Versioned cloud provider deploy select [#4201](https://github.com/spinnaker/deck/pull/4201) ([fbb90cb9](https://github.com/spinnaker/deck/commit/fbb90cb9d561b08ab7c2cacb33662e0f73386137))  
fix(provider/amazon) Enable & fix existing "Create LB" stage [#4184](https://github.com/spinnaker/deck/pull/4184) ([bfb90687](https://github.com/spinnaker/deck/commit/bfb90687893f7c475f8813ecb61b70503090c4fe))  
fix(artifacts): Get rid of 'unused' linter errors. [#4205](https://github.com/spinnaker/deck/pull/4205) ([0cc54798](https://github.com/spinnaker/deck/commit/0cc54798bff281b83723d63fed409c0e455e52aa))  
feat(pipeline): Change Artifact UI to use ExpectedArtifact model. [#4202](https://github.com/spinnaker/deck/pull/4202) ([97deac3a](https://github.com/spinnaker/deck/commit/97deac3acb7e62e352fb11ac876b35b361dc8390))  
fix(core/pipeline): Fix configure view state callback for MPT [#4203](https://github.com/spinnaker/deck/pull/4203) ([70b10abd](https://github.com/spinnaker/deck/commit/70b10abd6b1ef395157fcacacdef18ca24cd04c7))  



## [0.0.74](https://www.github.com/spinnaker/deck/compare/45fe3edf2fa92fffab808eb8f0a82034b2c9a6b3...215082a6e23499d9169d86484e9f3d80105d02c0) (2017-10-02)


### Changes

chore(core): bump package to 0.0.74 [#4199](https://github.com/spinnaker/deck/pull/4199) ([215082a6](https://github.com/spinnaker/deck/commit/215082a6e23499d9169d86484e9f3d80105d02c0))  
fix(pipelines): properly sync plan/pipeline/renderablePipeline [#4198](https://github.com/spinnaker/deck/pull/4198) ([ff094e2c](https://github.com/spinnaker/deck/commit/ff094e2ccf25fea5e75f67bab644943e39048cb1))  
adds filter to only retrieve target cluster [#4196](https://github.com/spinnaker/deck/pull/4196) ([25669481](https://github.com/spinnaker/deck/commit/25669481200507693d951ff43a28e21c1c041f1d))  



## [0.0.73](https://www.github.com/spinnaker/deck/compare/af4455dbe5bd595c84797b1b079cab1d9b578e47...45fe3edf2fa92fffab808eb8f0a82034b2c9a6b3) (2017-10-01)


### Changes

chore(core): bump package to 0.0.72 [#4194](https://github.com/spinnaker/deck/pull/4194) ([45fe3edf](https://github.com/spinnaker/deck/commit/45fe3edf2fa92fffab808eb8f0a82034b2c9a6b3))  
fix(search): fix advanced search page title [#4193](https://github.com/spinnaker/deck/pull/4193) ([2ecf0350](https://github.com/spinnaker/deck/commit/2ecf03506b78a5cb7a300b19228a7d0b593ddcc7))  
refactor(clusterMatch): tweak cluster match component args, export more in lib [#4191](https://github.com/spinnaker/deck/pull/4191) ([50400e79](https://github.com/spinnaker/deck/commit/50400e79aee53b176bac7f00daabd7c7c970c215))  
updating server group writer to use moniker [#4185](https://github.com/spinnaker/deck/pull/4185) ([c036848c](https://github.com/spinnaker/deck/commit/c036848cff93a008bbaf9ba7329e6190e639aa3e))  
fix(pipelines): correctly rerender when editing JSON [#4192](https://github.com/spinnaker/deck/pull/4192) ([014a6ae1](https://github.com/spinnaker/deck/commit/014a6ae181c4644b10598963c4fa83081e52f6b9))  
style(core/amazon/oracle): Updated spinners to use new designs [#4190](https://github.com/spinnaker/deck/pull/4190) ([8574f53b](https://github.com/spinnaker/deck/commit/8574f53b60ce958778f50a6cf5b72c575dde6d23))  
fix(stage): fix default timeout for deploy stage [#4186](https://github.com/spinnaker/deck/pull/4186) ([9d7ccb48](https://github.com/spinnaker/deck/commit/9d7ccb48b3550e8b4cd4e2d352b8a8bf81f4edff))  
fix(pipeline_template): Cast numeric variable types during plan [#4187](https://github.com/spinnaker/deck/pull/4187) ([60e1f1e4](https://github.com/spinnaker/deck/commit/60e1f1e47e3217b79d4383e4365cf0021d10aa99))  
feat(core/pipeline): Scroll grouped stages popover [#4182](https://github.com/spinnaker/deck/pull/4182) ([ed98e7d1](https://github.com/spinnaker/deck/commit/ed98e7d129e82ea119fceeda272001f79e3f6136))  
clone stage now uses moniker [#4166](https://github.com/spinnaker/deck/pull/4166) ([720db5f3](https://github.com/spinnaker/deck/commit/720db5f3d67ac57f83af93a2de219eef7731a4b7))  
feat(core): versioned cloud provider service [#4168](https://github.com/spinnaker/deck/pull/4168) ([19c420f9](https://github.com/spinnaker/deck/commit/19c420f9ebbf7b1437d8f721684339fa87cb3029))  
fix: Make sure jarDiffs has a default to prevent calling Object.keys on null [#4179](https://github.com/spinnaker/deck/pull/4179) ([d7068168](https://github.com/spinnaker/deck/commit/d7068168b02c162a065ab3ea4f66a0d3eb719690))  



## [0.0.71](https://www.github.com/spinnaker/deck/compare/6695a0a2680d31a5f6f0ec56c94a2e6603cf81f6...af4455dbe5bd595c84797b1b079cab1d9b578e47) (2017-09-28)


### Changes

chore(core): bump package to 0.0.71 [#4178](https://github.com/spinnaker/deck/pull/4178) ([af4455db](https://github.com/spinnaker/deck/commit/af4455dbe5bd595c84797b1b079cab1d9b578e47))  
fix(versionCheck): swallow exception if version.json fetch fails [#4177](https://github.com/spinnaker/deck/pull/4177) ([39538b62](https://github.com/spinnaker/deck/commit/39538b62ad3dcc3e1e2cf8a1ce6784b4587b36d3))  
fix(projects): restore project header width to 100% [#4176](https://github.com/spinnaker/deck/pull/4176) ([9452c105](https://github.com/spinnaker/deck/commit/9452c105e078f898a1496f4a56ef040798c4561c))  
feat(sourceMaps): Embed sources in sourcemaps for lib builds [#4175](https://github.com/spinnaker/deck/pull/4175) ([14818c96](https://github.com/spinnaker/deck/commit/14818c96450d5d4a96d87cde068944719a5d83ae))  
feat(core): Make HoverablePopover flip sides if there is not room to render on the provided side [#4173](https://github.com/spinnaker/deck/pull/4173) ([0d00fc82](https://github.com/spinnaker/deck/commit/0d00fc82cecd3e5c6811a4212556fe0c206fef89))  
fix(core): Fix undefined for getting length of commits [#4172](https://github.com/spinnaker/deck/pull/4172) ([e5a075ce](https://github.com/spinnaker/deck/commit/e5a075ce0db967abe36ce2700244089aae85261f))  
style(development only): Added linting for colors [#4165](https://github.com/spinnaker/deck/pull/4165) ([ed4ee5dd](https://github.com/spinnaker/deck/commit/ed4ee5dd07f4d2974c55b98eb2dbd9e46071dcd6))  
(docs) Update Tooltip: Bake Configuration -> Base AMI [#4161](https://github.com/spinnaker/deck/pull/4161) ([5b0a3337](https://github.com/spinnaker/deck/commit/5b0a33379901eb1ba91117891a3bfc3754920db5))  



## [0.0.70](https://www.github.com/spinnaker/deck/compare/6804841f50688dc3173cfdceeba8568ce2fbf29d...6695a0a2680d31a5f6f0ec56c94a2e6603cf81f6) (2017-09-27)


### Changes

chore(core): bump package to 0.0.70 [#4163](https://github.com/spinnaker/deck/pull/4163) ([6695a0a2](https://github.com/spinnaker/deck/commit/6695a0a2680d31a5f6f0ec56c94a2e6603cf81f6))  
fix(travis) prefer complete buildInfoUrl over composing it. [#4143](https://github.com/spinnaker/deck/pull/4143) ([c1edc5b7](https://github.com/spinnaker/deck/commit/c1edc5b7d6e4e9433768de68bd1a8b03beb06cd6))  
chore(search): tweak CSS styles per feedback [#4152](https://github.com/spinnaker/deck/pull/4152) ([d8aa7849](https://github.com/spinnaker/deck/commit/d8aa7849495ce549ffea78e4cb1ffee497936d88))  
refactor(*): Replace class-autobind-decorator with lodash-decorators BindAll [#4150](https://github.com/spinnaker/deck/pull/4150) ([ecc40304](https://github.com/spinnaker/deck/commit/ecc403046e8e556c1892a69acb944c6cc7e04034))  
refactor(*): Remove angular-loader in favor of using `.name` explicitly [#4157](https://github.com/spinnaker/deck/pull/4157) ([f6669e57](https://github.com/spinnaker/deck/commit/f6669e5759cd43ea9e30471c6923945027078aed))  
Update Tooltip: Pipeline Config -> Property File [#4156](https://github.com/spinnaker/deck/pull/4156) ([76bd61cf](https://github.com/spinnaker/deck/commit/76bd61cf3c52f5d63e0ac2d10a11403da67a78fb))  
chore(imports): remove unused import [#4160](https://github.com/spinnaker/deck/pull/4160) ([50935273](https://github.com/spinnaker/deck/commit/50935273da4dea25560b1a9e5e8e5b5169e5af6a))  
fix revision history colors, tweak loading screen [#4153](https://github.com/spinnaker/deck/pull/4153) ([5ad93344](https://github.com/spinnaker/deck/commit/5ad93344ad07e14916b542d72a3a86031a8122bf))  
fix(react): Do not suppress unhandled rejections in promises. [#4155](https://github.com/spinnaker/deck/pull/4155) ([64d385fa](https://github.com/spinnaker/deck/commit/64d385fab6ec93f71208b5888bbcf3f8b52c537e))  
feat(provider/amazon): Show NLBs in the Load Balancer screen and allow NLB target groups to be selected when deploying [#4149](https://github.com/spinnaker/deck/pull/4149) ([1e95bef1](https://github.com/spinnaker/deck/commit/1e95bef13cf39af324aeaf0fec01ef12b14648f7))  
chore(search): update project icon [#4151](https://github.com/spinnaker/deck/pull/4151) ([946bad02](https://github.com/spinnaker/deck/commit/946bad02ae6ac13611f1c90c633d97af8d2f7078))  



## [0.0.69](https://www.github.com/spinnaker/deck/compare/bf2da8c40a9bf2f116908973966118615d846669...6804841f50688dc3173cfdceeba8568ce2fbf29d) (2017-09-25)


### Changes

chore(core): bump package to 0.0.69 [#4148](https://github.com/spinnaker/deck/pull/4148) ([6804841f](https://github.com/spinnaker/deck/commit/6804841f50688dc3173cfdceeba8568ce2fbf29d))  
fix(search): deduplicate cluster results by name [#4145](https://github.com/spinnaker/deck/pull/4145) ([401cd803](https://github.com/spinnaker/deck/commit/401cd803cfd271a5842d036069cb2c399d6b4cad))  
feat(core/presentation): Add client side SpEL evaluator and Input Validator [#4140](https://github.com/spinnaker/deck/pull/4140) ([67664072](https://github.com/spinnaker/deck/commit/67664072ff3408db5b2de60b204c03a28ea18c88))  
fix(search): add default method value [#4142](https://github.com/spinnaker/deck/pull/4142) ([53266b64](https://github.com/spinnaker/deck/commit/53266b64616c1dcad738c14c7c49ab8015809375))  



## [0.0.68](https://www.github.com/spinnaker/deck/compare/69a6a4e83e241556b38e0030d99a8a2fdfb97277...bf2da8c40a9bf2f116908973966118615d846669) (2017-09-24)


### Changes

chore(core): bump package to 0.0.68 [#4141](https://github.com/spinnaker/deck/pull/4141) ([bf2da8c4](https://github.com/spinnaker/deck/commit/bf2da8c40a9bf2f116908973966118615d846669))  
fix(search): add supplemental searching capability [#4133](https://github.com/spinnaker/deck/pull/4133) ([a4e33533](https://github.com/spinnaker/deck/commit/a4e33533b308739ac25552d3f4e731f0de40b882))  
feat(core/application): Add 'autoActivate' toggle for DataSources [#4139](https://github.com/spinnaker/deck/pull/4139) ([6e180bdb](https://github.com/spinnaker/deck/commit/6e180bdb5b8721260d99dcd0d0fe77446f738e9a))  
refactor(core/formsy): Refactor formsy, create react app-config saver [#4132](https://github.com/spinnaker/deck/pull/4132) ([31fa0737](https://github.com/spinnaker/deck/commit/31fa0737d486bc09f46106ce134189679249b979))  



## [0.0.67](https://www.github.com/spinnaker/deck/compare/c9bdcb925fad65a09f1af9e6e248efaca17df3e0...69a6a4e83e241556b38e0030d99a8a2fdfb97277) (2017-09-22)


### Changes

chore(core): bump package to 0.0.67 [#4138](https://github.com/spinnaker/deck/pull/4138) ([69a6a4e8](https://github.com/spinnaker/deck/commit/69a6a4e83e241556b38e0030d99a8a2fdfb97277))  
fix(pipelines): show loading message while fetching version history [#4131](https://github.com/spinnaker/deck/pull/4131) ([d0b68335](https://github.com/spinnaker/deck/commit/d0b68335f2499ee96da9e44778a865a954efecda))  
refactor(cluster): allow cluster pod header to be customized [#4127](https://github.com/spinnaker/deck/pull/4127) ([fba6ad8b](https://github.com/spinnaker/deck/commit/fba6ad8b382d256bf427d6f7bd1896c5d123e104))  
fix(pipelines): refresh relative start time on interval [#4129](https://github.com/spinnaker/deck/pull/4129) ([2efc97fb](https://github.com/spinnaker/deck/commit/2efc97fb66823e7e2bda5661316263489e6023b1))  
fix(pipelines): enlarge conditional expression input [#4130](https://github.com/spinnaker/deck/pull/4130) ([ab3b9567](https://github.com/spinnaker/deck/commit/ab3b95678c4d57e879ed97648d165ff3e7598ffd))  
fix(pipelines): fix back link when execution cannot be found [#4125](https://github.com/spinnaker/deck/pull/4125) ([6dd0cab3](https://github.com/spinnaker/deck/commit/6dd0cab3f80a0bdf39e7331d81895b3d0365f0ca))  
Fixed colors for the containers of server groups [#4128](https://github.com/spinnaker/deck/pull/4128) ([98dbecca](https://github.com/spinnaker/deck/commit/98dbecca5a3db894dadd5120db7bd7ac9c58898b))  
feat(pipeline): Add pipeline config section for artifacts. [#4118](https://github.com/spinnaker/deck/pull/4118) ([0bf5fc3c](https://github.com/spinnaker/deck/commit/0bf5fc3ce6c69fbd30894392665735c8bfdd3663))  
style(core): Found and replaced with closest colors for variables in color.less  [#4120](https://github.com/spinnaker/deck/pull/4120) ([c5a20c98](https://github.com/spinnaker/deck/commit/c5a20c98d82d2a72c39b2685df6eca0b3e768961))  
fix(core): Seatbelt optionalStage directive to make sure stage exists [#4121](https://github.com/spinnaker/deck/pull/4121) ([472aae47](https://github.com/spinnaker/deck/commit/472aae476241c8332fa6b110dd95fd45eb733725))  



## [0.0.66](https://www.github.com/spinnaker/deck/compare/9606606b1b0bd2c6c29bd4d0c5a7d3484a493c51...c9bdcb925fad65a09f1af9e6e248efaca17df3e0) (2017-09-20)


### Changes

chore(*): Bump core and amazon module versions [#4119](https://github.com/spinnaker/deck/pull/4119) ([c9bdcb92](https://github.com/spinnaker/deck/commit/c9bdcb925fad65a09f1af9e6e248efaca17df3e0))  
feat(core/pipeline): Support grouping stages that have a 'group' property [#4117](https://github.com/spinnaker/deck/pull/4117) ([c4b00573](https://github.com/spinnaker/deck/commit/c4b005739a0209bcc7effe1b24ed18295b118111))  
fix(pipeline): remove pipeline refresh after del [#4115](https://github.com/spinnaker/deck/pull/4115) ([9c376687](https://github.com/spinnaker/deck/commit/9c376687430f7e097fe41566426e1f56753409be))  
fix(pipeline): auto focus input field [#4116](https://github.com/spinnaker/deck/pull/4116) ([0ea780d2](https://github.com/spinnaker/deck/commit/0ea780d2382f264ec4d896abf864c03ef4468643))  
chore(spinner): fix react warning for missing key [#4113](https://github.com/spinnaker/deck/pull/4113) ([e83dd755](https://github.com/spinnaker/deck/commit/e83dd7551e81db3e7f7c57c5a10d056d0ce56eb0))  
fix(style): fix small regressions on charts, history views [#4112](https://github.com/spinnaker/deck/pull/4112) ([47f70be8](https://github.com/spinnaker/deck/commit/47f70be8bfd51eabf5e4a60f232c270ffe235a26))  
Fixing colors throughout core with colors defined for styleguide [#4111](https://github.com/spinnaker/deck/pull/4111) ([76e876c4](https://github.com/spinnaker/deck/commit/76e876c496bbf3fb2b941ee873ac628ee1d77dad))  
style(styleguide): Added additional spinnaker colors [#4110](https://github.com/spinnaker/deck/pull/4110) ([80e1a158](https://github.com/spinnaker/deck/commit/80e1a1585dec729da849978432eda54a8e01e38f))  
refactor(core/pipeline): Convert PipelineGraph to React [#4099](https://github.com/spinnaker/deck/pull/4099) ([eec3daa3](https://github.com/spinnaker/deck/commit/eec3daa39bcd2838120b438b671a56281cfb9dc2))  
chore(search): change spinner to styleguide loader [#4108](https://github.com/spinnaker/deck/pull/4108) ([8aa1df8d](https://github.com/spinnaker/deck/commit/8aa1df8d3fc383fdb1073549847a0217f2117d6a))  
chore(search): move enabled filters [#4107](https://github.com/spinnaker/deck/pull/4107) ([59ed290a](https://github.com/spinnaker/deck/commit/59ed290ab0cb96fe851b1060351f0bef8f9f04d9))  
Adding badges with squared borders that Adam can use [#4105](https://github.com/spinnaker/deck/pull/4105) ([198bf19f](https://github.com/spinnaker/deck/commit/198bf19f97e69546231675813f2fef68b300ed68))  
fix(timeouts): updating help text to reflect new timeout behavior [#4106](https://github.com/spinnaker/deck/pull/4106) ([52d64a64](https://github.com/spinnaker/deck/commit/52d64a642506c70fd4e8fa2a211f9b296da6a7bd))  
feat(search): add ability to search by type [#4104](https://github.com/spinnaker/deck/pull/4104) ([481cea72](https://github.com/spinnaker/deck/commit/481cea72b914169bb4ec51ffdd10aa916f1d0404))  
style(all): Added new page loading spinner [#4102](https://github.com/spinnaker/deck/pull/4102) ([ab1a4055](https://github.com/spinnaker/deck/commit/ab1a40552739e82fa43562192ac8886c976a4483))  
fix(jenkins): allow duplicates in jenkins option lists [#4098](https://github.com/spinnaker/deck/pull/4098) ([d83b68a9](https://github.com/spinnaker/deck/commit/d83b68a9a6e5f151b3f170b7ab59e581a4034fc9))  



## [0.0.65](https://www.github.com/spinnaker/deck/compare/fc19d2a7003eb4828b26ad19063ff65895f8580b...9606606b1b0bd2c6c29bd4d0c5a7d3484a493c51) (2017-09-08)


### Changes

chore(core): bump package to 0.0.65 [#4097](https://github.com/spinnaker/deck/pull/4097) ([9606606b](https://github.com/spinnaker/deck/commit/9606606b1b0bd2c6c29bd4d0c5a7d3484a493c51))  
style(infrastructure): Add new spinner to infrastructure search [#4093](https://github.com/spinnaker/deck/pull/4093) ([6ac28756](https://github.com/spinnaker/deck/commit/6ac28756b37d6ba53a9a4ab2277eed60eeacde94))  
style(spinner): Applying new spinner to global search [#4083](https://github.com/spinnaker/deck/pull/4083) ([8ddd1a2f](https://github.com/spinnaker/deck/commit/8ddd1a2f27bef2d813301fa8f94c01b0f8c42a43))  
style(responsiveness): Main spinnaker nav header responsiveness [#4076](https://github.com/spinnaker/deck/pull/4076) ([f3536293](https://github.com/spinnaker/deck/commit/f353629309a07c3c420ee63b71b572fb5698edda))  
added support to show how to use spinners in react/angular [#4096](https://github.com/spinnaker/deck/pull/4096) ([1d362b41](https://github.com/spinnaker/deck/commit/1d362b41b7e1c85c83138c6e161a5a8e4a6a7849))  



## [0.0.64](https://www.github.com/spinnaker/deck/compare/d97ea4b4ff49effa5a62d7ba7dba10758f6d0cbc...fc19d2a7003eb4828b26ad19063ff65895f8580b) (2017-09-08)


### Changes

chore(core): bump package to 0.0.64 [#4094](https://github.com/spinnaker/deck/pull/4094) ([fc19d2a7](https://github.com/spinnaker/deck/commit/fc19d2a7003eb4828b26ad19063ff65895f8580b))  



## [0.0.63](https://www.github.com/spinnaker/deck/compare/dded16e64924823c5ca88100810950a61416ebb7...d97ea4b4ff49effa5a62d7ba7dba10758f6d0cbc) (2017-09-08)


### Changes

chore(core): bump package to 0.0.63 [#4092](https://github.com/spinnaker/deck/pull/4092) ([d97ea4b4](https://github.com/spinnaker/deck/commit/d97ea4b4ff49effa5a62d7ba7dba10758f6d0cbc))  
Attaching selectors to input types [#4090](https://github.com/spinnaker/deck/pull/4090) ([653e1323](https://github.com/spinnaker/deck/commit/653e1323b98927ab83e45a3bfc85971cb8ba879f))  
fix(pipelines): allow field removal when editing pipeline JSON [#4087](https://github.com/spinnaker/deck/pull/4087) ([3424baf7](https://github.com/spinnaker/deck/commit/3424baf7e209954360c4091a29ec7f28531e60e8))  
style(openStyleguide): Open styleguide for everyone to use [#4077](https://github.com/spinnaker/deck/pull/4077) ([e5799d0b](https://github.com/spinnaker/deck/commit/e5799d0bb52691a486bb6b1145b9312b05fe1ace))  
fix(pipelines): do not save changes to pipeline config on execution run [#4086](https://github.com/spinnaker/deck/pull/4086) ([22289ddb](https://github.com/spinnaker/deck/commit/22289ddbf3ce08d991cd927632f1f0955cb91f07))  
feat(webpack): Improve performance of webpack build [#4081](https://github.com/spinnaker/deck/pull/4081) ([da32e834](https://github.com/spinnaker/deck/commit/da32e834e4df71c4185919c30a72e07039e77038))  



## [0.0.62](https://www.github.com/spinnaker/deck/compare/36c92ac2e4691c44fafd90615829b11b1f9a3c5f...dded16e64924823c5ca88100810950a61416ebb7) (2017-09-07)


### Changes

chore(core): bump package to 0.0.62 [#4080](https://github.com/spinnaker/deck/pull/4080) ([dded16e6](https://github.com/spinnaker/deck/commit/dded16e64924823c5ca88100810950a61416ebb7))  
feat(search): add advanced search/filtering [#4072](https://github.com/spinnaker/deck/pull/4072) ([7b793aae](https://github.com/spinnaker/deck/commit/7b793aaeaa8435caf8401554fbdd843540c95664))  
feat(core): allow data sources to be available only for configured apps [#4078](https://github.com/spinnaker/deck/pull/4078) ([e76157da](https://github.com/spinnaker/deck/commit/e76157dae2b9b436a90ef3cac5083c6d38f2f951))  
refactor(core): Remove cruft from  ManualJudgementExecutionLabel [#4079](https://github.com/spinnaker/deck/pull/4079) ([b1783c6f](https://github.com/spinnaker/deck/commit/b1783c6f551e11e9093f8c29fb80b22a59361f3e))  



## [0.0.61](https://www.github.com/spinnaker/deck/compare/2c2833f5fb9b1d8c559f95772cf5e26ff29d44fd...36c92ac2e4691c44fafd90615829b11b1f9a3c5f) (2017-09-05)


### Changes

chore(core): bump package to 0.0.61 [#4075](https://github.com/spinnaker/deck/pull/4075) ([36c92ac2](https://github.com/spinnaker/deck/commit/36c92ac2e4691c44fafd90615829b11b1f9a3c5f))  
feat(pipelines): allow JSON editing of individual stages [#4071](https://github.com/spinnaker/deck/pull/4071) ([ca0543e0](https://github.com/spinnaker/deck/commit/ca0543e0c5b1a7a58ffed2edddfaf394eaaf2031))  



## [0.0.60](https://www.github.com/spinnaker/deck/compare/b786687f0284b91c439fb322c76bc44537689ebe...2c2833f5fb9b1d8c559f95772cf5e26ff29d44fd) (2017-09-05)


### Changes

chore(core): bump package to 0.0.60 [#4073](https://github.com/spinnaker/deck/pull/4073) ([2c2833f5](https://github.com/spinnaker/deck/commit/2c2833f5fb9b1d8c559f95772cf5e26ff29d44fd))  
chore(*): Update typescript, tslint, and react [#4070](https://github.com/spinnaker/deck/pull/4070) ([dc7f92b5](https://github.com/spinnaker/deck/commit/dc7f92b599b3ac5d33e95f99c5348cc5a91fb5b0))  
fix(core/delivery): Stop trying to set stageSummary when null [#4069](https://github.com/spinnaker/deck/pull/4069) ([699ca81e](https://github.com/spinnaker/deck/commit/699ca81e9954915f634b45961f1656644a658062))  



## [0.0.59](https://www.github.com/spinnaker/deck/compare/e5a340acd968681bace6e9e15caf904b1356cffc...b786687f0284b91c439fb322c76bc44537689ebe) (2017-09-01)


### Changes

chore(core): bump package to 0.0.59 [#4066](https://github.com/spinnaker/deck/pull/4066) ([b786687f](https://github.com/spinnaker/deck/commit/b786687f0284b91c439fb322c76bc44537689ebe))  
refactor(core): De-angularify OrchestratedItemTransformer [#4064](https://github.com/spinnaker/deck/pull/4064) ([9cd469ea](https://github.com/spinnaker/deck/commit/9cd469ea0c003a3027c356938d8ecdcb492cc0da))  
feat(core): implement cluster matcher functionality [#4056](https://github.com/spinnaker/deck/pull/4056) ([579c20c5](https://github.com/spinnaker/deck/commit/579c20c5e00028526eb790b293818dbf971d52ca))  
refactor(core/delivery): Convert executions.transformer.service to TS [#4061](https://github.com/spinnaker/deck/pull/4061) ([d7035ec4](https://github.com/spinnaker/deck/commit/d7035ec4af1fc2ec04e8bb1db886e9174aef0271))  
fix(core): fix rendering of manual judgment instructions [#4057](https://github.com/spinnaker/deck/pull/4057) ([3ebcd317](https://github.com/spinnaker/deck/commit/3ebcd3170accaf822a88d1e1a2b9dd05dde41eec))  
fix(styles): fix icons under styleguide class [#4060](https://github.com/spinnaker/deck/pull/4060) ([31b2a695](https://github.com/spinnaker/deck/commit/31b2a6957001788ee7091333a9709a7505978bc5))  
fix(core): avoid accessing data source when not present or loaded [#4058](https://github.com/spinnaker/deck/pull/4058) ([f9bf2a84](https://github.com/spinnaker/deck/commit/f9bf2a845698d07f19216157dd1ac0adfc7578d1))  
fix(core/delivery): Seatbelt for undefined on execution filter destroy [#4059](https://github.com/spinnaker/deck/pull/4059) ([d9138b6b](https://github.com/spinnaker/deck/commit/d9138b6b670e4bfd30da04c2c4f477475edad2b1))  
fix(core/executions): Fix execution details reloading every refresh [#4055](https://github.com/spinnaker/deck/pull/4055) ([2683a16f](https://github.com/spinnaker/deck/commit/2683a16f79530698d2c78adcf1c58d4f78b381a3))  



## [0.0.58](https://www.github.com/spinnaker/deck/compare/c02a7c54064b19bd699df32b58be84ad02cc4925...e5a340acd968681bace6e9e15caf904b1356cffc) (2017-08-29)


### Changes

bump core to 0.58 [#4054](https://github.com/spinnaker/deck/pull/4054) ([e5a340ac](https://github.com/spinnaker/deck/commit/e5a340acd968681bace6e9e15caf904b1356cffc))  
fix(core/executions): Remove `parallel` from Execution and Pipeline interface and usage ([847fff22](https://github.com/spinnaker/deck/commit/847fff222c2bd793fced2f3b4de281f2ef1b765b))  
feat(gce/pubsub): Adds basic UI for configuring pubsub triggers. [#4052](https://github.com/spinnaker/deck/pull/4052) ([59aa084d](https://github.com/spinnaker/deck/commit/59aa084dff7b993405e6cd87b3cb0dc21fa88071))  



## [0.0.57](https://www.github.com/spinnaker/deck/compare/4d0b654d9a8cdd2a6cc390ab3387d1ec82a97349...c02a7c54064b19bd699df32b58be84ad02cc4925) (2017-08-25)


### Changes

chore(core): bump package to 0.0.57 [#4051](https://github.com/spinnaker/deck/pull/4051) ([c02a7c54](https://github.com/spinnaker/deck/commit/c02a7c54064b19bd699df32b58be84ad02cc4925))  
fix(core): render whats new content as html [#4049](https://github.com/spinnaker/deck/pull/4049) ([dfa5e518](https://github.com/spinnaker/deck/commit/dfa5e518d58defd6ba3bdb297eaa5d053659aff1))  



## [0.0.56](https://www.github.com/spinnaker/deck/compare/fe0b56bd5dd90909e026935f34db339242abac4a...4d0b654d9a8cdd2a6cc390ab3387d1ec82a97349) (2017-08-25)


### Changes

chore(core): bump package to 0.0.56 [#4047](https://github.com/spinnaker/deck/pull/4047) ([4d0b654d](https://github.com/spinnaker/deck/commit/4d0b654d9a8cdd2a6cc390ab3387d1ec82a97349))  
feat(core): replace marked with commonmark [#4046](https://github.com/spinnaker/deck/pull/4046) ([1fe3b1c2](https://github.com/spinnaker/deck/commit/1fe3b1c207db1bcb5a4eaa5a6475802d89f1c79d))  
feat(amazon): implement preferSourceCapacity flag in deploy config [#4044](https://github.com/spinnaker/deck/pull/4044) ([63afeffc](https://github.com/spinnaker/deck/commit/63afeffc8aee5a1934a4eec580d187bfb0699ff5))  
style(core): Add new spinner styles and a react component for spinner [#4039](https://github.com/spinnaker/deck/pull/4039) ([18f4fc38](https://github.com/spinnaker/deck/commit/18f4fc38a36e7a85de907bbd12cb87798aba7f60))  



## [0.0.55](https://www.github.com/spinnaker/deck/compare/6b4af6fed8bbdada131f17688fab03de5116cca5...fe0b56bd5dd90909e026935f34db339242abac4a) (2017-08-21)


### Changes

chore(core): bump version to 0.0.55 [#4038](https://github.com/spinnaker/deck/pull/4038) ([fe0b56bd](https://github.com/spinnaker/deck/commit/fe0b56bd5dd90909e026935f34db339242abac4a))  
feat(core): allow customization of manual judgment action labels [#4037](https://github.com/spinnaker/deck/pull/4037) ([d2df4eeb](https://github.com/spinnaker/deck/commit/d2df4eeb94f97cd9063e7d9ef49eaa2b9ba7f00a))  
feat(core): include address in context for instance links [#4036](https://github.com/spinnaker/deck/pull/4036) ([73bb9f57](https://github.com/spinnaker/deck/commit/73bb9f57f260a3eec40e76d72c78e8e0157161de))  
fix(core): sort regions in account/region/cluster selector [#4032](https://github.com/spinnaker/deck/pull/4032) ([531715a0](https://github.com/spinnaker/deck/commit/531715a05c716109ee9e7c6ba23c17bb0e760e77))  
chore(styleguide): fix styleguide config paths [#4034](https://github.com/spinnaker/deck/pull/4034) ([a8c5c4de](https://github.com/spinnaker/deck/commit/a8c5c4de6d64019957179d1408c2324fa990e1ba))  
chore(core): fix build for styleguide [#4031](https://github.com/spinnaker/deck/pull/4031) ([35fd7088](https://github.com/spinnaker/deck/commit/35fd7088cebb1b0eca07e8b06a1ead3a38eca439))  



## [0.0.54](https://www.github.com/spinnaker/deck/compare/3887890dbc35c6acd66367a1d5895e3c96797bc4...6b4af6fed8bbdada131f17688fab03de5116cca5) (2017-08-16)


### Changes

chore(core): bump package to 0.0.54 [#4029](https://github.com/spinnaker/deck/pull/4029) ([6b4af6fe](https://github.com/spinnaker/deck/commit/6b4af6fed8bbdada131f17688fab03de5116cca5))  
chore(core): move /styleguide under /src [#4027](https://github.com/spinnaker/deck/pull/4027) ([15afdcda](https://github.com/spinnaker/deck/commit/15afdcdae4f466282aea72c78731c32af9a62a6f))  
chore(deck): removed executionEngine flag and force cancel option [#4028](https://github.com/spinnaker/deck/pull/4028) ([97253294](https://github.com/spinnaker/deck/commit/97253294492f142b191dc26656c4a1a059eebca7))  
feat(pipeline_templates): adds link to template json [#4026](https://github.com/spinnaker/deck/pull/4026) ([3d11d2fc](https://github.com/spinnaker/deck/commit/3d11d2fc27700965e89880ef90669fcc695303aa))  
fix(core): do not overwrite target percentages on rolling red/black [#4025](https://github.com/spinnaker/deck/pull/4025) ([c3029491](https://github.com/spinnaker/deck/commit/c3029491dc9d6807b66387419bb07f8d0c6805fe))  
refactor(core + canary): move shared canary components back to canary module [#4023](https://github.com/spinnaker/deck/pull/4023) ([720c6dd8](https://github.com/spinnaker/deck/commit/720c6dd8f7cd0503108770f8d8a6bd0d6eac1449))  
style(core/styleguide) Cataloging Spinnaker styles in to a style guide [#4014](https://github.com/spinnaker/deck/pull/4014) ([770379c0](https://github.com/spinnaker/deck/commit/770379c0072f221ee3692637cc828ad2e25697f8))  
fix(core): Fix deploy template selection when one server group and no template selection is disabled [#4022](https://github.com/spinnaker/deck/pull/4022) ([2ac1ca0d](https://github.com/spinnaker/deck/commit/2ac1ca0d203f4a44d8201814111755ba9affdd35))  



## [0.0.53](https://www.github.com/spinnaker/deck/compare/66998b821dde24f4fc710cfa77172f0b438726ac...3887890dbc35c6acd66367a1d5895e3c96797bc4) (2017-08-15)


### Changes

chore(core): bump package to 0.0.53 [#4021](https://github.com/spinnaker/deck/pull/4021) ([3887890d](https://github.com/spinnaker/deck/commit/3887890dbc35c6acd66367a1d5895e3c96797bc4))  
fix(core): rename canary score component [#4020](https://github.com/spinnaker/deck/pull/4020) ([488a7b14](https://github.com/spinnaker/deck/commit/488a7b14a3a9c931439ee1bdfd814f5d41c5a4e2))  
fix(core): add createServerGroup to list of candidate running stages [#4016](https://github.com/spinnaker/deck/pull/4016) ([c03e576e](https://github.com/spinnaker/deck/commit/c03e576e7cc87cebdc112a0de861b6def46c5408))  
fix(core/utils): Fix task running duration when longer than 31 days ([3f7154b8](https://github.com/spinnaker/deck/commit/3f7154b8dce89d37432aeb84d2de1347b7932579))  



## [0.0.52](https://www.github.com/spinnaker/deck/compare/20f33b796593d51abe6eb344fd881e57838c4e00...66998b821dde24f4fc710cfa77172f0b438726ac) (2017-08-11)


### Changes

chore(core): bump package to 0.0.52 [#4013](https://github.com/spinnaker/deck/pull/4013) ([66998b82](https://github.com/spinnaker/deck/commit/66998b821dde24f4fc710cfa77172f0b438726ac))  
feat(canary): configurable help fields for canary scores component [#4007](https://github.com/spinnaker/deck/pull/4007) ([bab94dd7](https://github.com/spinnaker/deck/commit/bab94dd7e898ba1ad827b49fa5cb19b7572e7e8f))  
fix(pipeline_graph): sort graph nodes lexicographically by refId if refId is a string [#4012](https://github.com/spinnaker/deck/pull/4012) ([cb0b5f69](https://github.com/spinnaker/deck/commit/cb0b5f69cc98e8b57884e190cdd9fe51b66eb490))  



## [0.0.51](https://www.github.com/spinnaker/deck/compare/a505f496cdad77e89882d0502a1dfee448420d53...20f33b796593d51abe6eb344fd881e57838c4e00) (2017-08-10)


### Changes

chore(core): bump package to 0.0.51 [#4009](https://github.com/spinnaker/deck/pull/4009) ([20f33b79](https://github.com/spinnaker/deck/commit/20f33b796593d51abe6eb344fd881e57838c4e00))  
fix(pipelines): always show running executions and keep count in sync [#4003](https://github.com/spinnaker/deck/pull/4003) ([7d607b28](https://github.com/spinnaker/deck/commit/7d607b2884918508f7f4d3f0b0c466b5598f360f))  
fix(pipelines): do not exponentially load single execution details [#4008](https://github.com/spinnaker/deck/pull/4008) ([56711002](https://github.com/spinnaker/deck/commit/56711002bc64d43c9911ca8959bc5bdf5d23830b))  



## [0.0.50](https://www.github.com/spinnaker/deck/compare/1b9719f0e815129e9b4548c4bb9125e95b39dcb4...a505f496cdad77e89882d0502a1dfee448420d53) (2017-08-10)


### Changes

chore(core): bump package to 0.0.50 [#4005](https://github.com/spinnaker/deck/pull/4005) ([a505f496](https://github.com/spinnaker/deck/commit/a505f496cdad77e89882d0502a1dfee448420d53))  



## [0.0.49](https://www.github.com/spinnaker/deck/compare/e5bd3e1f5d3fde2cc2982b87e31ff0fd8ced8645...1b9719f0e815129e9b4548c4bb9125e95b39dcb4) (2017-08-09)


### Changes

chore(core): bump package to 0.0.49 [#4002](https://github.com/spinnaker/deck/pull/4002) ([1b9719f0](https://github.com/spinnaker/deck/commit/1b9719f0e815129e9b4548c4bb9125e95b39dcb4))  
refactor(canary): convert canary scores component to React [#3999](https://github.com/spinnaker/deck/pull/3999) ([3d3ac3c7](https://github.com/spinnaker/deck/commit/3d3ac3c71102230a0886756c18aebef252e27037))  
fix(core): remove blur handler on HoverablePopover [#4001](https://github.com/spinnaker/deck/pull/4001) ([c237136e](https://github.com/spinnaker/deck/commit/c237136efdfec2939567692bfdfc6bc6e7dc768f))  
fix(core/loadBalancer): Filter nulls from loadbalancers list [#4000](https://github.com/spinnaker/deck/pull/4000) ([19b8b502](https://github.com/spinnaker/deck/commit/19b8b502c5e633cc4e456e3a927e424852391ef6))  



## [0.0.48](https://www.github.com/spinnaker/deck/compare/7db6b1064341db7f860bc396b60a2bf28ced1af9...e5bd3e1f5d3fde2cc2982b87e31ff0fd8ced8645) (2017-08-08)


### Changes

 chore(core): bump package to 0.0.48 ([e5bd3e1f](https://github.com/spinnaker/deck/commit/e5bd3e1f5d3fde2cc2982b87e31ff0fd8ced8645))  
fix(core/cluster): Fix sort order of accounts in clusters view [#3997](https://github.com/spinnaker/deck/pull/3997) ([2b943cae](https://github.com/spinnaker/deck/commit/2b943cae82b0597c40d0efcef404c9f84a502845))  



## [0.0.47](https://www.github.com/spinnaker/deck/compare/7a34f367c8cf46c7a8bcbd680a25ac6263ac8a53...7db6b1064341db7f860bc396b60a2bf28ced1af9) (2017-08-07)


### Changes

chore(core): bump package to 0.0.47 [#3993](https://github.com/spinnaker/deck/pull/3993) ([7db6b106](https://github.com/spinnaker/deck/commit/7db6b1064341db7f860bc396b60a2bf28ced1af9))  
fix(core/loadBalancers): Fix z-index issues by converting load balancer list to a popover ([8a951be1](https://github.com/spinnaker/deck/commit/8a951be1f80f911075f6f149476eba9a99814d7b))  
fix(aws): Change EBS optimized flag based on AWS defaults [#3991](https://github.com/spinnaker/deck/pull/3991) ([9a728f09](https://github.com/spinnaker/deck/commit/9a728f09f2654286af1dade357daca8904411dc5))  
fix(core/loadBalancers): Render LoadBalancerWrapper to switch component based on cloud provider [#3983](https://github.com/spinnaker/deck/pull/3983) ([be45acca](https://github.com/spinnaker/deck/commit/be45accaccb7399e88b6beeb15cea859515b3569))  
fix(core/reactShims): Fix runningTasksTag directive [#3982](https://github.com/spinnaker/deck/pull/3982) ([e3189015](https://github.com/spinnaker/deck/commit/e31890152c0738f1c1d70878639557bafab35c75))  
React clusters view [#3882](https://github.com/spinnaker/deck/pull/3882) ([9d8abc9a](https://github.com/spinnaker/deck/commit/9d8abc9acee1212cd2dfa7dfc765ebd510914afa))  
fix(core/loadBalancers): Move initialization from constructor to componentDidMount ([e8c8b50a](https://github.com/spinnaker/deck/commit/e8c8b50a80824d38662f4cae89a6e2b0e6295db7))  
fix(core): fix alignment of copy-to-clipboard icon [#3976](https://github.com/spinnaker/deck/pull/3976) ([e91226d8](https://github.com/spinnaker/deck/commit/e91226d8608a94d622389fce074bfd1fd7beffef))  
chore(core/search): unbreak core inf searching [#3977](https://github.com/spinnaker/deck/pull/3977) ([9110f468](https://github.com/spinnaker/deck/commit/9110f4684e84ce514a01685eeb986fcecfeacbff))  
fix(aws/loadbalancer): ensure timeout < interval [#3974](https://github.com/spinnaker/deck/pull/3974) ([a41c8d73](https://github.com/spinnaker/deck/commit/a41c8d73076357e605ef35c22705a24f6ff0861c))  
feat(core): add new tagging widget [#3966](https://github.com/spinnaker/deck/pull/3966) ([73792f44](https://github.com/spinnaker/deck/commit/73792f4430aab1edfe17b0ace9fb012d04dc1f4d))  
feat(CI/Jenkins): Add parameter type checking/mapped elements to Jenkins [#3972](https://github.com/spinnaker/deck/pull/3972) ([7f6fea47](https://github.com/spinnaker/deck/commit/7f6fea47b6aafef188a348bf92abf3353c9e8a39))  



## [0.0.46](https://www.github.com/spinnaker/deck/compare/2e687ef5e100416cff33e84006322c190fc7b912...7a34f367c8cf46c7a8bcbd680a25ac6263ac8a53) (2017-08-01)


### Changes

chore(core): bump package to 0.0.46 [#3971](https://github.com/spinnaker/deck/pull/3971) ([7a34f367](https://github.com/spinnaker/deck/commit/7a34f367c8cf46c7a8bcbd680a25ac6263ac8a53))  
Arch/fix positioning [#3969](https://github.com/spinnaker/deck/pull/3969) ([5ba08a8c](https://github.com/spinnaker/deck/commit/5ba08a8c67b6115fa4b40ea41f55ab67006b4a46))  
chore(imports): don't use alias for core imports [#3968](https://github.com/spinnaker/deck/pull/3968) ([d94104a2](https://github.com/spinnaker/deck/commit/d94104a25d0826cf07fd1f8d8333c13f6756688d))  
fix(core): provide valid ids for scrollTo clusters [#3965](https://github.com/spinnaker/deck/pull/3965) ([dd5f6a61](https://github.com/spinnaker/deck/commit/dd5f6a6163055fac6635d5cef2435290b0e1199e))  



## [0.0.45](https://www.github.com/spinnaker/deck/compare/a8677c5daa4f250f107fb7ca0f9755664855b37f...2e687ef5e100416cff33e84006322c190fc7b912) (2017-07-27)


### Changes

chore(core): bump package to 0.0.45 [#3964](https://github.com/spinnaker/deck/pull/3964) ([2e687ef5](https://github.com/spinnaker/deck/commit/2e687ef5e100416cff33e84006322c190fc7b912))  
fix(canary): show exception on STOPPED; extract deploy stages [#3963](https://github.com/spinnaker/deck/pull/3963) ([f4fac97f](https://github.com/spinnaker/deck/commit/f4fac97fe9ed8940db4c26d78ab424b3e4f6c466))  
fix(core): avoid double-load of execution, treat FAILED_CONTINUE as isFailed [#3961](https://github.com/spinnaker/deck/pull/3961) ([4fd8b12d](https://github.com/spinnaker/deck/commit/4fd8b12d80b38d55a0975d84ee747aa86b4c0e23))  
chore(core/amazon): update webpack configs for sourcemaps/externals [#3959](https://github.com/spinnaker/deck/pull/3959) ([6eb1890f](https://github.com/spinnaker/deck/commit/6eb1890fc95c81e4ba4d516327f498802dd1d83c))  



## [0.0.44](https://www.github.com/spinnaker/deck/compare/9e4c5e9711c381b7cdbb14e3478eb0a7579a4116...a8677c5daa4f250f107fb7ca0f9755664855b37f) (2017-07-25)


### Changes

chore(core): bump package to 0.0.44 [#3956](https://github.com/spinnaker/deck/pull/3956) ([a8677c5d](https://github.com/spinnaker/deck/commit/a8677c5daa4f250f107fb7ca0f9755664855b37f))  
feat(amazon): implement target tracking policy support [#3948](https://github.com/spinnaker/deck/pull/3948) ([70a03720](https://github.com/spinnaker/deck/commit/70a03720118071bf90285519d3cd0746f2132d1a))  



## [0.0.43](https://www.github.com/spinnaker/deck/compare/68b1645e396242e9ef1783d7a27f7ce4a27b5a8c...9e4c5e9711c381b7cdbb14e3478eb0a7579a4116) (2017-07-24)


### Changes

chore(core): bump package to 0.0.43 [#3954](https://github.com/spinnaker/deck/pull/3954) ([9e4c5e97](https://github.com/spinnaker/deck/commit/9e4c5e9711c381b7cdbb14e3478eb0a7579a4116))  
fix(core/servergroup): fix filter scrolling on ffx [#3953](https://github.com/spinnaker/deck/pull/3953) ([7a09f8bd](https://github.com/spinnaker/deck/commit/7a09f8bdc99512450960de42d4e7d543605d6c3d))  
fix(core): Load balancers tag popup and runnings tasks popup show up under headers [#3952](https://github.com/spinnaker/deck/pull/3952) ([cc116586](https://github.com/spinnaker/deck/commit/cc11658698a107c8751260a15c55560b61d9494d))  
fix(core): Fix undefined error in ApplicationComponent [#3951](https://github.com/spinnaker/deck/pull/3951) ([c9c99306](https://github.com/spinnaker/deck/commit/c9c993065497f447f0fcb22f5cea773668fe0048))  



## [0.0.42](https://www.github.com/spinnaker/deck/compare/ad7f0e407528a11c3688857e7c6dcd82a0c42929...68b1645e396242e9ef1783d7a27f7ce4a27b5a8c) (2017-07-21)


### Changes

chore(core): bump package to 0.0.42 [#3949](https://github.com/spinnaker/deck/pull/3949) ([68b1645e](https://github.com/spinnaker/deck/commit/68b1645e396242e9ef1783d7a27f7ce4a27b5a8c))  
fix(core): Fix activeState being null for application refresh [#3947](https://github.com/spinnaker/deck/pull/3947) ([3e596c55](https://github.com/spinnaker/deck/commit/3e596c553fe7b8566f69b4cc1dd5ba8eb55fe4b1))  
feat(provider/amazon): Support add/remove instance from target group [#3945](https://github.com/spinnaker/deck/pull/3945) ([9af5192c](https://github.com/spinnaker/deck/commit/9af5192cc531f4cf6f2394184333d1c0328bb578))  



## [0.0.41](https://www.github.com/spinnaker/deck/compare/a9d92f52dd46dda356018900c004e58b8647eb14...ad7f0e407528a11c3688857e7c6dcd82a0c42929) (2017-07-21)


### Changes

chore(core): bump package to 0.0.41 [#3944](https://github.com/spinnaker/deck/pull/3944) ([ad7f0e40](https://github.com/spinnaker/deck/commit/ad7f0e407528a11c3688857e7c6dcd82a0c42929))  
fix(core): add margin between collapsed execution group headers [#3943](https://github.com/spinnaker/deck/pull/3943) ([799e5d99](https://github.com/spinnaker/deck/commit/799e5d9941b18f9973f0bec1ae1bf40004f32266))  
fix(core): update webhook stage scope on stage change [#3942](https://github.com/spinnaker/deck/pull/3942) ([a389fc12](https://github.com/spinnaker/deck/commit/a389fc1247fc1b6bdb3a1e259be06e4b914bb880))  



## [0.0.40](https://www.github.com/spinnaker/deck/compare/d06bdd325cbe82a5fc8af5e2c034bdf086b0ad80...a9d92f52dd46dda356018900c004e58b8647eb14) (2017-07-21)


### Changes

chore(core): bump package to 0.0.40 [#3940](https://github.com/spinnaker/deck/pull/3940) ([a9d92f52](https://github.com/spinnaker/deck/commit/a9d92f52dd46dda356018900c004e58b8647eb14))  
feat(provider/amazon): Add ability to delete dependent ingress rules when deleting security group [#3939](https://github.com/spinnaker/deck/pull/3939) ([fe1d1060](https://github.com/spinnaker/deck/commit/fe1d106082bf68a42129a86e44b073b64dd2d7ba))  
fix(core): Fix refresher to show the actual current state [#3937](https://github.com/spinnaker/deck/pull/3937) ([f8453ff6](https://github.com/spinnaker/deck/commit/f8453ff6803c701bf73abcd969752855c1db688c))  



## [0.0.39](https://www.github.com/spinnaker/deck/compare/c1211dc79c0e06b05d906b9248597757aa0aa39a...d06bdd325cbe82a5fc8af5e2c034bdf086b0ad80) (2017-07-18)


### Changes

chore(core): bump version to 0.0.39 [#3934](https://github.com/spinnaker/deck/pull/3934) ([d06bdd32](https://github.com/spinnaker/deck/commit/d06bdd325cbe82a5fc8af5e2c034bdf086b0ad80))  
fix(core): preserve reset methods on settings reset calls [#3933](https://github.com/spinnaker/deck/pull/3933) ([386e97ab](https://github.com/spinnaker/deck/commit/386e97abe082e325d795b427f92790e7ace0db86))  



## [0.0.38](https://www.github.com/spinnaker/deck/compare/bac1b30372cbfc2b4782a5dce8f6071f4e9d78d2...c1211dc79c0e06b05d906b9248597757aa0aa39a) (2017-07-18)


### Changes

chore(core): bump package to 0.0.38 [#3931](https://github.com/spinnaker/deck/pull/3931) ([c1211dc7](https://github.com/spinnaker/deck/commit/c1211dc79c0e06b05d906b9248597757aa0aa39a))  
fix(core): update account tag color on account change [#3929](https://github.com/spinnaker/deck/pull/3929) ([ea0ac4e0](https://github.com/spinnaker/deck/commit/ea0ac4e02b4f037326dcdbf5a28420544e207fdd))  
feat(amazon): allow default VPC specification for security group creation [#3924](https://github.com/spinnaker/deck/pull/3924) ([a5893295](https://github.com/spinnaker/deck/commit/a5893295d4a7db7e062c4fed5306dcbda2fb454c))  
refactor(*): Update typescript to 2.4 and fix breaking changes ([072c1eff](https://github.com/spinnaker/deck/commit/072c1eff41a81fc933b2d6438d86e673e125587a))  
feat(stickyHeaders): Use pure CSS for sticky headers [#3923](https://github.com/spinnaker/deck/pull/3923) ([5c5601b4](https://github.com/spinnaker/deck/commit/5c5601b4c234ad613fdf7f172986a0f6688e2612))  
refactor(core): Update tslint to 5.5 [#3925](https://github.com/spinnaker/deck/pull/3925) ([fb276d5f](https://github.com/spinnaker/deck/commit/fb276d5f8a65b8ae8f1a393cf88c1d743d1a7d0f))  



## [0.0.37](https://www.github.com/spinnaker/deck/compare/8eb06709e1e335d6abc2ea1deef01541916e900b...bac1b30372cbfc2b4782a5dce8f6071f4e9d78d2) (2017-07-16)


### Changes

chore(core): bump package to 0.0.37 [#3928](https://github.com/spinnaker/deck/pull/3928) ([bac1b303](https://github.com/spinnaker/deck/commit/bac1b30372cbfc2b4782a5dce8f6071f4e9d78d2))  
refactor(core): use common component for deploy initialization [#3889](https://github.com/spinnaker/deck/pull/3889) ([8754fcb5](https://github.com/spinnaker/deck/commit/8754fcb559561390d7bd7ee541a28166007ecea3))  
chore(core): convert notifierService to TS, export from core [#3926](https://github.com/spinnaker/deck/pull/3926) ([04ef219b](https://github.com/spinnaker/deck/commit/04ef219bcf2743aed234e6a6b8f73d1f24624b9a))  
fix(core): avoid NPE rendering header for missing apps [#3927](https://github.com/spinnaker/deck/pull/3927) ([52b0398d](https://github.com/spinnaker/deck/commit/52b0398da462a697718c0604e1d4c0ceeab7f32c))  



## [0.0.36](https://www.github.com/spinnaker/deck/compare/2f96ad0c6037cc0cefc971a34dbb3e232e2245f9...8eb06709e1e335d6abc2ea1deef01541916e900b) (2017-07-14)


### Changes

chore(core): bump to 0.0.36 ([8eb06709](https://github.com/spinnaker/deck/commit/8eb06709e1e335d6abc2ea1deef01541916e900b))  
fix(scroll): Update react-hybrid to 0.0.12 which passes secondary-panel class through to ui-view ([0f905417](https://github.com/spinnaker/deck/commit/0f9054170809bdb5715e6115942ca1f97ea0fc33))  



## [0.0.35](https://www.github.com/spinnaker/deck/compare/344d85a4e602ff8d974798d3adf8bb877ba7f862...2f96ad0c6037cc0cefc971a34dbb3e232e2245f9) (2017-07-14)


### Changes

chore(core): bump to 0.0.35 [#3921](https://github.com/spinnaker/deck/pull/3921) ([2f96ad0c](https://github.com/spinnaker/deck/commit/2f96ad0c6037cc0cefc971a34dbb3e232e2245f9))  
fix(core): fix scrolling regression on pipeline config [#3920](https://github.com/spinnaker/deck/pull/3920) ([05c34c05](https://github.com/spinnaker/deck/commit/05c34c0539fd95572745af98d83894b417a10cbf))  
feature(core): Add ability to override application icon [#3917](https://github.com/spinnaker/deck/pull/3917) ([df1bb9f7](https://github.com/spinnaker/deck/commit/df1bb9f7ea6f8763165c726fdf38a10da11ba2cc))  
fix(core): Fix application refresher [#3916](https://github.com/spinnaker/deck/pull/3916) ([d9ae9a1c](https://github.com/spinnaker/deck/commit/d9ae9a1c51889d0c135028fcb8919dd20a4aa6a5))  
feat(pipeline_templates): adds boolean variable configuration type [#3900](https://github.com/spinnaker/deck/pull/3900) ([3b2e249c](https://github.com/spinnaker/deck/commit/3b2e249c7930fc92fd0b5f4fe6a46217723a0d90))  



## [0.0.34](https://www.github.com/spinnaker/deck/compare/e9334354b80e76b3a0faff779e59f52ddd7aeddf...344d85a4e602ff8d974798d3adf8bb877ba7f862) (2017-07-13)


### Changes

chore(core): bump version to 0.0.34 [#3914](https://github.com/spinnaker/deck/pull/3914) ([344d85a4](https://github.com/spinnaker/deck/commit/344d85a4e602ff8d974798d3adf8bb877ba7f862))  
feat(core): put application, taskWriter, executionService on window [#3911](https://github.com/spinnaker/deck/pull/3911) ([6a659dbc](https://github.com/spinnaker/deck/commit/6a659dbcdeefe45155081fd11e42aa0728aae076))  
chore(core): downgrade @types/react to 15.0.35 [#3909](https://github.com/spinnaker/deck/pull/3909) ([bef78482](https://github.com/spinnaker/deck/commit/bef7848261bb5c4670fc6223290696e8ec3754aa))  
fix(core): Fix pipelines css [#3913](https://github.com/spinnaker/deck/pull/3913) ([e1105a45](https://github.com/spinnaker/deck/commit/e1105a45d7d35920ae14e2a40d3a936c0284f5f1))  
refactor(core): Convert application component to react [#3907](https://github.com/spinnaker/deck/pull/3907) ([c14c3e46](https://github.com/spinnaker/deck/commit/c14c3e46589f46552c28c99b585d991768277412))  
fix(pipeline_templates): initialize variables if underlying template changes [#3902](https://github.com/spinnaker/deck/pull/3902) ([b144e8b2](https://github.com/spinnaker/deck/commit/b144e8b20d9b844e3297ae9002bec61b5f63f5e1))  
chore(*): update @types/react to latest [#3908](https://github.com/spinnaker/deck/pull/3908) ([21348d44](https://github.com/spinnaker/deck/commit/21348d448e4e40b1030ed6bf58161a8f22abc14b))  
chore(core): export account service, viewStateCache, add virtualized-select [#3906](https://github.com/spinnaker/deck/pull/3906) ([9b287634](https://github.com/spinnaker/deck/commit/9b287634702648ac7d00232ef81e7981bb805928))  



## [0.0.33](https://www.github.com/spinnaker/deck/compare/eb98282aea6c87d5f67d3e10e4b3ddb928bdedba...e9334354b80e76b3a0faff779e59f52ddd7aeddf) (2017-07-12)


### Changes

chore(core): bump package version [#3905](https://github.com/spinnaker/deck/pull/3905) ([e9334354](https://github.com/spinnaker/deck/commit/e9334354b80e76b3a0faff779e59f52ddd7aeddf))  
feat(core) - ui for rolling red black push [#3904](https://github.com/spinnaker/deck/pull/3904) ([7e5091bf](https://github.com/spinnaker/deck/commit/7e5091bf7fed43f009802d46377c76929d06a12e))  
chore(core/amazon/docker/google): update configs [#3893](https://github.com/spinnaker/deck/pull/3893) ([a0649c3e](https://github.com/spinnaker/deck/commit/a0649c3ed3c239900329c90536294f245139c056))  



## [0.0.32](https://www.github.com/spinnaker/deck/compare/47c40a4c764ed58ff456cf6bd845ed12713e86f2...eb98282aea6c87d5f67d3e10e4b3ddb928bdedba) (2017-07-07)


### Changes

chore(core): Version 0.0.32 ([eb98282a](https://github.com/spinnaker/deck/commit/eb98282aea6c87d5f67d3e10e4b3ddb928bdedba))  
chore(core/bundle): Add @uirouter/react and @uirouter/react-hybrid to webpack externals ([caec0e25](https://github.com/spinnaker/deck/commit/caec0e25379f2922169217b46a45fbaf9a60c03e))  
fix(traffic_guards): Allow accounts w/ namespaces to use traffic guards [#3860](https://github.com/spinnaker/deck/pull/3860) ([e8a9bfad](https://github.com/spinnaker/deck/commit/e8a9bfadb094097bfa35d46bf3e399d82487b4a6))  



## [0.0.31](https://www.github.com/spinnaker/deck/compare/3247d1ace100002d28e88152cf2fb0cb0258cd9d...47c40a4c764ed58ff456cf6bd845ed12713e86f2) (2017-07-06)


### Changes

feat(aws): allow search on LB listener cert select [#3892](https://github.com/spinnaker/deck/pull/3892) ([47c40a4c](https://github.com/spinnaker/deck/commit/47c40a4c764ed58ff456cf6bd845ed12713e86f2))  
feat(core): implement external search API in infrastructure search [#3890](https://github.com/spinnaker/deck/pull/3890) ([18a154a4](https://github.com/spinnaker/deck/commit/18a154a4c8cc61e50bafacc9100d906ccd8ae39e))  



## [0.0.30](https://www.github.com/spinnaker/deck/compare/0e5533373d00bccba73bec621191768bd3b5054c...3247d1ace100002d28e88152cf2fb0cb0258cd9d) (2017-07-06)


### Changes

feat(core/amazon): add build info to changes [#3884](https://github.com/spinnaker/deck/pull/3884) ([3247d1ac](https://github.com/spinnaker/deck/commit/3247d1ace100002d28e88152cf2fb0cb0258cd9d))  
refactor(core): convert service delegate to Typescript [#3886](https://github.com/spinnaker/deck/pull/3886) ([90226fd1](https://github.com/spinnaker/deck/commit/90226fd15ebf206b0fcb05f5a276de3deb664cc6))  
fix(core): Protect against undefined nodes in the pipeline graph [#3885](https://github.com/spinnaker/deck/pull/3885) ([1e4261fe](https://github.com/spinnaker/deck/commit/1e4261fe8462621e5bd5cf466445598f9b062ebb))  
refactor(core): Refactor security group types to make sense [#3883](https://github.com/spinnaker/deck/pull/3883) ([60ab92fe](https://github.com/spinnaker/deck/commit/60ab92fe5c975ed1d0371c46f1ce5f06360c21f0))  
Load balancer pod -- shouldComponentUpdate ([34eaf3f9](https://github.com/spinnaker/deck/commit/34eaf3f91a6f4601e3bf36b90e1e685bad37e6b7))  
feat(core/utils): Add timing debug decorators [#3880](https://github.com/spinnaker/deck/pull/3880) ([47abf81f](https://github.com/spinnaker/deck/commit/47abf81f4459010ec6e7a84b9b58953e4b74d495))  
fix(pipeline_templates): wrap variable name [#3878](https://github.com/spinnaker/deck/pull/3878) ([ac110894](https://github.com/spinnaker/deck/commit/ac110894a23deb8e8fe27f2747b51ce5b5a1bd50))  



## [0.0.29](https://www.github.com/spinnaker/deck/compare/cedf57824a785e5f76fae1dca41108ce500c47c7...0e5533373d00bccba73bec621191768bd3b5054c) (2017-06-27)


### Changes

feat(react): Route to react components; use react UISref components ([0e553337](https://github.com/spinnaker/deck/commit/0e5533373d00bccba73bec621191768bd3b5054c))  
fix(provider/amazon): Check to make sure cache types exists before showing refresh [#3875](https://github.com/spinnaker/deck/pull/3875) ([20fa7509](https://github.com/spinnaker/deck/commit/20fa75092bc56445d5bccb6182645b423a6f3700))  
feat(provider/amazon): Load certificates on demand [#3874](https://github.com/spinnaker/deck/pull/3874) ([eec53580](https://github.com/spinnaker/deck/commit/eec53580922ad8ab5a8787f6d5ccdd9f8fbb130f))  



## [0.0.28](https://www.github.com/spinnaker/deck/compare/369c718e09399fe7fdad6d583b9b3804cf7daf02...cedf57824a785e5f76fae1dca41108ce500c47c7) (2017-06-26)


### Changes

chore(amazon, core): rev packages [#3872](https://github.com/spinnaker/deck/pull/3872) ([cedf5782](https://github.com/spinnaker/deck/commit/cedf57824a785e5f76fae1dca41108ce500c47c7))  
fix(core): improve error message on script stage failure [#3868](https://github.com/spinnaker/deck/pull/3868) ([f04569b7](https://github.com/spinnaker/deck/commit/f04569b74ad85118bd0a819f1169bbd3877ef76f))  
fix(core): fix scroll on standalone views [#3866](https://github.com/spinnaker/deck/pull/3866) ([36ac32a8](https://github.com/spinnaker/deck/commit/36ac32a8bdb35deee5f003c39e0c31addbf90a6f))  
fix(core): hide judgment inputs if manual judgment succeeded [#3865](https://github.com/spinnaker/deck/pull/3865) ([6c4595b4](https://github.com/spinnaker/deck/commit/6c4595b4f5d0e684424ebf92f8ca28b4b413f1b6))  
fix(core): fixes ui-select clearable X button [#3870](https://github.com/spinnaker/deck/pull/3870) ([b5214b60](https://github.com/spinnaker/deck/commit/b5214b609397b8043abe5be0e293e2480a0253c7))  
fix(core): trim spaces from pipeline name before delete [#3867](https://github.com/spinnaker/deck/pull/3867) ([45a455e1](https://github.com/spinnaker/deck/commit/45a455e1c120422ae3bb2eb547cc56491d2cb49a))  



## [0.0.27](https://www.github.com/spinnaker/deck/compare/5d5c8ad217a434eec50fc67ca193591cb9323ac9...369c718e09399fe7fdad6d583b9b3804cf7daf02) (2017-06-22)


### Changes

fix(provider/amazon): Fix deleting ALBs [#3862](https://github.com/spinnaker/deck/pull/3862) ([369c718e](https://github.com/spinnaker/deck/commit/369c718e09399fe7fdad6d583b9b3804cf7daf02))  



## [0.0.26](https://www.github.com/spinnaker/deck/compare/3f20407ed2e25c2d6e6c7512cc24ffc4422a0740...5d5c8ad217a434eec50fc67ca193591cb9323ac9) (2017-06-21)


### Changes

chore: bump core to 0.0.26 and amazon to 0.0.10 [#3861](https://github.com/spinnaker/deck/pull/3861) ([5d5c8ad2](https://github.com/spinnaker/deck/commit/5d5c8ad217a434eec50fc67ca193591cb9323ac9))  
feat(provider/amazon): Cleanup ALB listeners CRUD UI [#3857](https://github.com/spinnaker/deck/pull/3857) ([80e98757](https://github.com/spinnaker/deck/commit/80e987573c04061f1df0ed33ddd79b01cb715e73))  
fix(core): reduce badge size in compact headers [#3855](https://github.com/spinnaker/deck/pull/3855) ([8da4b515](https://github.com/spinnaker/deck/commit/8da4b515d02c6f38ce600afe035dd1418c4e0fdd))  



## [0.0.25](https://www.github.com/spinnaker/deck/compare/6f851d2e44ddb57efc8171cabb3f2371af76aadb...3f20407ed2e25c2d6e6c7512cc24ffc4422a0740) (2017-06-19)


### Changes

feat(core): require app name in appModelBuilder.createApplication [#3850](https://github.com/spinnaker/deck/pull/3850) ([3f20407e](https://github.com/spinnaker/deck/commit/3f20407ed2e25c2d6e6c7512cc24ffc4422a0740))  
fix(core/entityTag): Clone more fields in UI when editing an entity tag [#3848](https://github.com/spinnaker/deck/pull/3848) ([19866889](https://github.com/spinnaker/deck/commit/198668892e3c5bbf92f555c5924e21d13e2f1501))  
fix(core) dedupe execution account labels [#3845](https://github.com/spinnaker/deck/pull/3845) ([c3d575fd](https://github.com/spinnaker/deck/commit/c3d575fd765f167b60578098cab1e80b7d0db635))  
fix(permissions): Prevent user from only specifying READ permission, and thus locking everyone out of that application [#3835](https://github.com/spinnaker/deck/pull/3835) ([509a6768](https://github.com/spinnaker/deck/commit/509a67684bc44b0343473d6a044452c35341c79c))  



## [0.0.24](https://www.github.com/spinnaker/deck/compare/dbbbd26080a250fad44141ecc35f61c22c0b67ab...6f851d2e44ddb57efc8171cabb3f2371af76aadb) (2017-06-15)


### Changes

chore(canary): move canary out of core to separate module [#3843](https://github.com/spinnaker/deck/pull/3843) ([6f851d2e](https://github.com/spinnaker/deck/commit/6f851d2e44ddb57efc8171cabb3f2371af76aadb))  



## [0.0.23](https://www.github.com/spinnaker/deck/compare/7dc4716d30880c627b42cfa9d3f94f97a523a73e...dbbbd26080a250fad44141ecc35f61c22c0b67ab) (2017-06-15)


### Changes

fix(core): import from base rxjs [#3842](https://github.com/spinnaker/deck/pull/3842) ([dbbbd260](https://github.com/spinnaker/deck/commit/dbbbd26080a250fad44141ecc35f61c22c0b67ab))  



## [0.0.22](https://www.github.com/spinnaker/deck/compare/13a0a2e3c7e7e7e165259a69542ca00b9a318fdc...7dc4716d30880c627b42cfa9d3f94f97a523a73e) (2017-06-15)


### Changes

chore(core): bump to 0.0.22 [#3841](https://github.com/spinnaker/deck/pull/3841) ([7dc4716d](https://github.com/spinnaker/deck/commit/7dc4716d30880c627b42cfa9d3f94f97a523a73e))  



## [0.0.21](https://www.github.com/spinnaker/deck/compare/a4327123eadafeb41ffa1212e056780844f52737...13a0a2e3c7e7e7e165259a69542ca00b9a318fdc) (2017-06-15)


### Changes

chore(core): bump package to 0.0.21 [#3839](https://github.com/spinnaker/deck/pull/3839) ([13a0a2e3](https://github.com/spinnaker/deck/commit/13a0a2e3c7e7e7e165259a69542ca00b9a318fdc))  
fix(core): correctly set default state on custom strategy params [#3836](https://github.com/spinnaker/deck/pull/3836) ([fd2e3874](https://github.com/spinnaker/deck/commit/fd2e3874439952c4345618f66d9929c15c1d0667))  
feat(core/entityTag): optionally render titles on alerts ([9c1e3d42](https://github.com/spinnaker/deck/commit/9c1e3d427655a46d514c058b7e865fcc943f5161))  
fix(core/entityTag): delete unused code ([f2ff60bb](https://github.com/spinnaker/deck/commit/f2ff60bb9a47eb816c24ffd682c931caf5fa160c))  
feat(core/entityTag): Tweak alert category descriptions (again) ([a745f022](https://github.com/spinnaker/deck/commit/a745f02263e0b0b6c57a0833a67f541e88e20091))  
feat(core/entityTag): Wait 100ms before showing entity tag popover ([dc9cd02e](https://github.com/spinnaker/deck/commit/dc9cd02e4120e7cb9c4a09b6ea2a7a999d27e414))  
feat(core/entityTag): Tweak alert category descriptions [#3830](https://github.com/spinnaker/deck/pull/3830) ([86a0b820](https://github.com/spinnaker/deck/commit/86a0b820a45d24911367c5d39f1f31574da73de2))  
fix(core/executions): Show durations in execution details/permalink [#3809](https://github.com/spinnaker/deck/pull/3809) ([d85896ef](https://github.com/spinnaker/deck/commit/d85896ef78af68ee7e541677f6fd8d05c8cdc076))  
feat(provider/kubernetes): Run Job extended config [#3823](https://github.com/spinnaker/deck/pull/3823) ([dbe4bfd2](https://github.com/spinnaker/deck/commit/dbe4bfd2691487485c83f90de89104f377844726))  
feat(core): move canary/aca to core, remove netflix references ([9de4a080](https://github.com/spinnaker/deck/commit/9de4a080bfbbdd0de85ef734397929d284ec417b))  



## [0.0.20](https://www.github.com/spinnaker/deck/compare/d3b94ebd812ebd6fcacfe4897ef4d48ac6787fb0...a4327123eadafeb41ffa1212e056780844f52737) (2017-06-13)


### Changes

fix(core): include "feature" on spinnaker component ctrl ([a4327123](https://github.com/spinnaker/deck/commit/a4327123eadafeb41ffa1212e056780844f52737))  
fix(pipelines): fix float on health counts ([e6557573](https://github.com/spinnaker/deck/commit/e6557573daa56d074a7285b7b85b27a998bcb518))  
fix(pipeline-template): follow angular module name conventions for pipeline template plan errors component ([ec835bf6](https://github.com/spinnaker/deck/commit/ec835bf6373ea5959b439bad165f6c2abbedcca5))  
fix(pipeline-templates) fix rendering issues for errors and help fields [#3819](https://github.com/spinnaker/deck/pull/3819) ([da2e379c](https://github.com/spinnaker/deck/commit/da2e379cffbc2ea5ef26220eea62e8a36841b0c2))  



## [0.0.19](https://www.github.com/spinnaker/deck/compare/8fdaa28fa21a2b6d0206d33c0607e03a91a88ac5...d3b94ebd812ebd6fcacfe4897ef4d48ac6787fb0) (2017-06-12)


### Changes

refactor(aws): make scaling policies customizable ([d3b94ebd](https://github.com/spinnaker/deck/commit/d3b94ebd812ebd6fcacfe4897ef4d48ac6787fb0))  
refactor(provider/amazon): convert createApplicationLoadBalancer to TS [#3816](https://github.com/spinnaker/deck/pull/3816) ([a2d75789](https://github.com/spinnaker/deck/commit/a2d75789e380c36675cfe3a2a16839af58751d2f))  
Bug fix where preconfigured webhook is not sending HTTP headers ([e71e9f73](https://github.com/spinnaker/deck/commit/e71e9f7381ac713338dbd58a431746464434b6ee))  
fix(kubernetes): Search not working if result contained K8S load balancer ([65d40ec1](https://github.com/spinnaker/deck/commit/65d40ec124b8b27cd36c677807114bf595747ff6))  
fix(provider/amazon): Fix key collisions when an ALB and classic LB existed with the same name ([afc4267a](https://github.com/spinnaker/deck/commit/afc4267a6336591f6a6802bcbed87c70dc523d45))  
refactor(provider/amazon): Separate load balancer types [#3810](https://github.com/spinnaker/deck/pull/3810) ([b66dc0c3](https://github.com/spinnaker/deck/commit/b66dc0c3766b863ed1422721b872619e6cf5e1c1))  
feat(core): include description in applications list view ([c286db71](https://github.com/spinnaker/deck/commit/c286db7132e6bfc4f5e9913c08f551954bffe446))  
refactor(provider/amazon): Convert load balancer transformer to TS [#3806](https://github.com/spinnaker/deck/pull/3806) ([af9afa1c](https://github.com/spinnaker/deck/commit/af9afa1cb7fd32dcaeeac35653887a52207289a7))  
feat(provider/amazon): CRUD for ALBs [#3803](https://github.com/spinnaker/deck/pull/3803) ([dd5abf4b](https://github.com/spinnaker/deck/commit/dd5abf4bada440aa6870fbcf644d63dd056479d1))  
feat(core): automatically scroll to server group on deep link ([59ecdeeb](https://github.com/spinnaker/deck/commit/59ecdeebb739f147fe659c43758c7cfaa92aa5a1))  



## [0.0.18](https://www.github.com/spinnaker/deck/compare/709f58cde7ba62bb148235599d896e4f0c135402...8fdaa28fa21a2b6d0206d33c0607e03a91a88ac5) (2017-06-07)


### Changes

fix(pipelines): correct diff on stages, smaller stringVal, dedupes ([8fdaa28f](https://github.com/spinnaker/deck/commit/8fdaa28fa21a2b6d0206d33c0607e03a91a88ac5))  



## [0.0.17](https://www.github.com/spinnaker/deck/compare/d8288c470cdebe1e83fb2172e035fe9345f0bfc3...709f58cde7ba62bb148235599d896e4f0c135402) (2017-06-07)


### Changes

chore(core) rev package to 0.0.17 ([709f58cd](https://github.com/spinnaker/deck/commit/709f58cde7ba62bb148235599d896e4f0c135402))  
fix(core): initialize strategy selector ([02c42173](https://github.com/spinnaker/deck/commit/02c42173c913728b4a6792f115c55886a5f6cb67))  
fix(core/bootstrap): Add missing imports ([e5041cce](https://github.com/spinnaker/deck/commit/e5041cce0af62c1be88d76621cd10008f790bdd2))  
refactor(core): Rename core.module to typescript - Remove uirouter.stateEvents.shim.js - Refactor app initialization blocks to separate files ([fff02626](https://github.com/spinnaker/deck/commit/fff026263f96980fc815df9ffc61bf2068df4f86))  
chore(core): Update core/tsconfig.json to include all typescript files ([d42e0f2e](https://github.com/spinnaker/deck/commit/d42e0f2e459216719ed369121817ff7483b24dd3))  
feat(pipeline-templates): config view [#3787](https://github.com/spinnaker/deck/pull/3787) ([30d64393](https://github.com/spinnaker/deck/commit/30d6439329599ff1cda3c70fe2783a2bf11e6c28))  
chore(core): Update to @uirouter/angularjs@1.0.3 ([d6e7e3d8](https://github.com/spinnaker/deck/commit/d6e7e3d86d342f08d186e268713dce3194def491))  
fix(firefox): Fix application config scrolling in firefox [#3785](https://github.com/spinnaker/deck/pull/3785) ([111715fe](https://github.com/spinnaker/deck/commit/111715fe8ca8ee1e9fc79f4752dc6ec73287b60d))  



## [0.0.16](https://www.github.com/spinnaker/deck/compare/1de912153f2b5438f75c5b8a537537c8292b900c...d8288c470cdebe1e83fb2172e035fe9345f0bfc3) (2017-06-01)


### Changes

chore(core): Bump version to 0.0.16 ([d8288c47](https://github.com/spinnaker/deck/commit/d8288c470cdebe1e83fb2172e035fe9345f0bfc3))  
fix(Markdown): Do not render when `message` is empty ([9831d6d3](https://github.com/spinnaker/deck/commit/9831d6d398d4031283c9c7a90d7110425686323b))  



## [0.0.15](https://www.github.com/spinnaker/deck/compare/5a743c3700ebd753db9427b90c491bd7dd9e570b...1de912153f2b5438f75c5b8a537537c8292b900c) (2017-06-01)


### Changes

chore(core): Bump version to 0.0.15 [#3783](https://github.com/spinnaker/deck/pull/3783) ([1de91215](https://github.com/spinnaker/deck/commit/1de912153f2b5438f75c5b8a537537c8292b900c))  
feat(core/entityTag): Render tagline for alerts, if present [#3779](https://github.com/spinnaker/deck/pull/3779) ([449137a5](https://github.com/spinnaker/deck/commit/449137a5e33f21e6459b8408271596def92ece4e))  
feat(core/entityTag): Add categories to alerts popovers [#3773](https://github.com/spinnaker/deck/pull/3773) ([20fd6a13](https://github.com/spinnaker/deck/commit/20fd6a1343260e0708bd63303d061f1cea4da9ce))  
chore(expression-docs): change expression docs link [#3778](https://github.com/spinnaker/deck/pull/3778) ([da1bd281](https://github.com/spinnaker/deck/commit/da1bd281ac738aabce1804263bc4400c3a7079ab))  



## [0.0.14](https://www.github.com/spinnaker/deck/compare/0d4ae53aeae8b2a3333bf1be93aceb0a1fc81cf1...5a743c3700ebd753db9427b90c491bd7dd9e570b) (2017-05-30)


### Changes

chore(core): rev version to 0.0.14 ([5a743c37](https://github.com/spinnaker/deck/commit/5a743c3700ebd753db9427b90c491bd7dd9e570b))  
feat(core): Common ratelimit http header [#3775](https://github.com/spinnaker/deck/pull/3775) ([ba877e7e](https://github.com/spinnaker/deck/commit/ba877e7e40ff48866c9463e3c73f1c37e726f56c))  
refactor(provider/amazon): Convert serverGroupConfiguration.service to TS [#3774](https://github.com/spinnaker/deck/pull/3774) ([b1a390fd](https://github.com/spinnaker/deck/commit/b1a390fd4a3da8a9c989a57fba301c014242dafa))  
refactor(strategies): make strategies provider-pluggable, convert to TS ([63c19be3](https://github.com/spinnaker/deck/commit/63c19be32ce38a0ecc188532c5252bb58c9b1b77))  
feat(core): include help on instance port, align checkboxes ([b5ddad06](https://github.com/spinnaker/deck/commit/b5ddad0675aeaae6dbc74382d3a02ce417739fb8))  



## [0.0.13](https://www.github.com/spinnaker/deck/compare/1d5bc36ab0b386ffb5bdcfd3656d26ad33ecfbc6...0d4ae53aeae8b2a3333bf1be93aceb0a1fc81cf1) (2017-05-26)


### Changes

fix(entityTags): do not pre-optimize tag retrieval in data source ([0d4ae53a](https://github.com/spinnaker/deck/commit/0d4ae53aeae8b2a3333bf1be93aceb0a1fc81cf1))  



## [0.0.12](https://www.github.com/spinnaker/deck/compare/e4f10f653914a4df5eab85c389e2d2601b012161...1d5bc36ab0b386ffb5bdcfd3656d26ad33ecfbc6) (2017-05-26)


### Changes

fix(core): remove application accounts before saving ([1d5bc36a](https://github.com/spinnaker/deck/commit/1d5bc36ab0b386ffb5bdcfd3656d26ad33ecfbc6))  



## [0.0.10](https://www.github.com/spinnaker/deck/compare/84836eeaacfad1fc529170fbf96bf618f9ac17a6...e4f10f653914a4df5eab85c389e2d2601b012161) (2017-05-26)


### Changes

chore(core): rev package ([e4f10f65](https://github.com/spinnaker/deck/commit/e4f10f653914a4df5eab85c389e2d2601b012161))  
fix(applications): shrink header for long application names ([a573a125](https://github.com/spinnaker/deck/commit/a573a1252840924f4e610ef1f6b7254868d03bf0))  
feat(application): remove account field from create/edit application modals ([8470415b](https://github.com/spinnaker/deck/commit/8470415b1c5d48a392ad2d0ddb90faf7b3bce3d7))  



## [0.0.9](https://www.github.com/spinnaker/deck/compare/369530efee3ca0802ec902b241dfa9c79edc0bd2...84836eeaacfad1fc529170fbf96bf618f9ac17a6) (2017-05-26)


### Changes

chore(*): Bump amazon and core package versions and fix a couple things to fix module publishing [#3761](https://github.com/spinnaker/deck/pull/3761) ([84836eea](https://github.com/spinnaker/deck/commit/84836eeaacfad1fc529170fbf96bf618f9ac17a6))  
feat:(amazon): Add ALB Support [#3757](https://github.com/spinnaker/deck/pull/3757) ([d98ab724](https://github.com/spinnaker/deck/commit/d98ab724b1a77f06657a4191095713f191e0c664))  
feat(core) permissions configurer [#3756](https://github.com/spinnaker/deck/pull/3756) ([a44088a2](https://github.com/spinnaker/deck/commit/a44088a232428da57060de86faa1d924841d7ccb))  



## [0.0.7](https://www.github.com/spinnaker/deck/compare/81dc32621ed3ea3b2266ef0545d92246de43b400...369530efee3ca0802ec902b241dfa9c79edc0bd2) (2017-05-25)


### Changes

chore(core): rev core version ([369530ef](https://github.com/spinnaker/deck/commit/369530efee3ca0802ec902b241dfa9c79edc0bd2))  
fix(pipelines): allow stage to declare executionAlias value ([56fd5aff](https://github.com/spinnaker/deck/commit/56fd5aff75ea7faf19cb9d027b3f5c537b211639))  
fix(core): avoid NPE when disabling autorefresh ([87ca1ecb](https://github.com/spinnaker/deck/commit/87ca1ecb3356cb80004037e489ff23946218e054))  



## [0.0.6](https://www.github.com/spinnaker/deck/compare/cb836c8f588eb325322d0b23f720e8df99139cad...81dc32621ed3ea3b2266ef0545d92246de43b400) (2017-05-25)


### Changes

chore(core): rev version ([81dc3262](https://github.com/spinnaker/deck/commit/81dc32621ed3ea3b2266ef0545d92246de43b400))  
fix(entitytags): include maxResults parameter when retrieving tags [#3753](https://github.com/spinnaker/deck/pull/3753) ([0535c00d](https://github.com/spinnaker/deck/commit/0535c00d48d47bddb4077bd49820422947e8819a))  
fix(core/instance): fix alignment of health indicators in multi-selected server group [#3752](https://github.com/spinnaker/deck/pull/3752) ([d4672918](https://github.com/spinnaker/deck/commit/d46729189616ab3cb6333d660c5614c32e4e270e))  
feat(core): add a button to navigate to the pipeline configuration from execution details view [#3748](https://github.com/spinnaker/deck/pull/3748) ([bc4aa925](https://github.com/spinnaker/deck/commit/bc4aa9258930c92744bf41773992d4adebe4bc12))  



## [0.0.5](https://www.github.com/spinnaker/deck/compare/6adae673c74ba9077e5f3672e895a60aa814500a...cb836c8f588eb325322d0b23f720e8df99139cad) (2017-05-23)


### Changes

fix(pipelines): inject app, set on scope in pipeline configurer ([cb836c8f](https://github.com/spinnaker/deck/commit/cb836c8f588eb325322d0b23f720e8df99139cad))  



## [0.0.4](https://www.github.com/spinnaker/deck/compare/ad2e8f2e72bba75df06a9ff682b35037f837ea56...6adae673c74ba9077e5f3672e895a60aa814500a) (2017-05-23)


### Changes

fix(core): unique package for analytics service; external version.json ([6adae673](https://github.com/spinnaker/deck/commit/6adae673c74ba9077e5f3672e895a60aa814500a))  
feat(webhooks): Add preconfigurable webhook support ([73b19f88](https://github.com/spinnaker/deck/commit/73b19f88a6b2120b15ffe143572bae2a6fb0bd10))  
fix(webhooks): Checkbox moving around when clicked and not in line with text ([add199c3](https://github.com/spinnaker/deck/commit/add199c36ee920d25758fb8aa3f496085965cac4))  



## [0.0.3](https://www.github.com/spinnaker/deck/compare/d93efd99028be0a032ab68dc9845087132f3aad4...ad2e8f2e72bba75df06a9ff682b35037f837ea56) (2017-05-23)


### Changes

chore(core): bump core package version ([ad2e8f2e](https://github.com/spinnaker/deck/commit/ad2e8f2e72bba75df06a9ff682b35037f837ea56))  
refactor(core): convert applicationCtrl to TS, executions to component ([54305963](https://github.com/spinnaker/deck/commit/54305963cde5b753245cea111eccef0cb7407279))  
fix(application): avoid refresh icon wobble on narrow screens ([0f1483d1](https://github.com/spinnaker/deck/commit/0f1483d195f7ac9c9fd54635f0f8b74c10e6b9ca))  
fix(application): avoid URL flashing when application not found ([d6ef2062](https://github.com/spinnaker/deck/commit/d6ef2062c4553da06e73bdcf31614ce1b9e48b15))  
fix(applications): use front50 application name instead of state param ([d4e9c7a2](https://github.com/spinnaker/deck/commit/d4e9c7a2b6aed89567f4a6f0737146a54d21bfe1))  
feat(pipelines): auto-focus stage type field on new stage ([e58ae3ee](https://github.com/spinnaker/deck/commit/e58ae3eed9de36fd25470a7a3bba93aaa2812fd7))  
refactor(netflix/titus): remove netflix/titus modules ([43894353](https://github.com/spinnaker/deck/commit/438943536f1ae25b914b42f2b2ce1a3ccca617de))  



## [0.0.2](https://www.github.com/spinnaker/deck/compare/d40d2082c45a5a229a26f3bf7d4f47624a3f5f1d...d93efd99028be0a032ab68dc9845087132f3aad4) (2017-05-22)


### Changes

chore(core/amazon): rev package.json versions ([d93efd99](https://github.com/spinnaker/deck/commit/d93efd99028be0a032ab68dc9845087132f3aad4))  
fix(pipelines): remove refId param after resolving deep link ([8541fab8](https://github.com/spinnaker/deck/commit/8541fab856dcdea4f058fbcc7f689dbc8e7757f3))  
fix(core): prevents unnecessarily defaulting to AWS for stage configs ([deeb469c](https://github.com/spinnaker/deck/commit/deeb469c4d4808d2c2ef55a56e185af856fe7da6))  
fix(modals): fix style on modal headings ([72aba19a](https://github.com/spinnaker/deck/commit/72aba19a897f29f8999c0e233864a40c6700385e))  
chore(*): convert refresh icons to fa-refresh ([c4ca0c5c](https://github.com/spinnaker/deck/commit/c4ca0c5c111fffbacd333c3dcecf3ff70fdc8631))  
refactor(*): replace glyphicon-asterisk with fa-cog ([04807cb4](https://github.com/spinnaker/deck/commit/04807cb4a3d3410cbe7c31c2e03d09df30a6c02f))  
chore(*): replace some glyphicons with font-awesome ([070fb88a](https://github.com/spinnaker/deck/commit/070fb88a8c8f67ac34dbbac8a316cab8ef7bf05a))  
chore(misc): clean up imports ([2c9ab0ce](https://github.com/spinnaker/deck/commit/2c9ab0ce496e2f8bbf9d5df1087a4382512613d6))  
fix(core): assign unique namespaces to waypoint* directives ([bc72c9a0](https://github.com/spinnaker/deck/commit/bc72c9a069f7127bf0158ccbd3b172dad3bb135d))  
refactor(core/entityTag): Refactor Entity Tags to React [#3717](https://github.com/spinnaker/deck/pull/3717) ([e96db46f](https://github.com/spinnaker/deck/commit/e96db46fbc654f7e505096e300ae51846cd59a88))  
refactor(core): flatten select2, misc lib build fixes ([d1ce78a0](https://github.com/spinnaker/deck/commit/d1ce78a0d0acff4f59f403b50006b9f98fab3f95))  
fix(core): add entityTags module ([f47597c1](https://github.com/spinnaker/deck/commit/f47597c1481f9d9c2d48f04879cb3ed53c2d13c7))  
refactor(aws): enable amazon module build with sourcemaps ([57557f5f](https://github.com/spinnaker/deck/commit/57557f5f016d6233f6ac72e3989bbe6752269c72))  
refactor(aws): move amazon code to src directory ([48fc5cb6](https://github.com/spinnaker/deck/commit/48fc5cb675f3e84ea904278e4440171d6dcb3cc0))  
refactor(amazon): prepare amazon for library build ([a050ddb4](https://github.com/spinnaker/deck/commit/a050ddb4618010520bef4d959ce441dcb808f554))  
chore(core): move angular2react components to separate class ([bd97d099](https://github.com/spinnaker/deck/commit/bd97d0993cb24bb2b1d49a9bf3414f702b58e170))  
fix(aws): restore scaling activities link ([4893a0c9](https://github.com/spinnaker/deck/commit/4893a0c948fa365d7605dff641474f0344be19d2))  
refactor(docker): prepare docker for lib packaging ([b5b2c903](https://github.com/spinnaker/deck/commit/b5b2c903d8b901756b9c887315b2c09d768e4514))  
refactor(help): split help into provider modules ([76766da2](https://github.com/spinnaker/deck/commit/76766da28c6180c45f6a06e1e7ed9fb836b98818))  
fix(clusters): fix search result heading for clusters ([6a7c9eef](https://github.com/spinnaker/deck/commit/6a7c9eef5955c6a4e9fbf84ae9c23476208f39bf))  
fix(deploymentstrategy): include requires for deployment strategies ([ceb81ff3](https://github.com/spinnaker/deck/commit/ceb81ff334c6ffa235be8810eccb3885dbd25e35))
