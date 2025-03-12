# Change Log

All notable changes to this project will be documented in this file.
See [Conventional Commits](https://conventionalcommits.org) for commit guidelines.

## [0.14.5](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.14.4...@spinnaker/amazon@0.14.5) (2024-06-10)


### Bug Fixes

* **lambda:** Export LambdaRoute stage on aws module ([#10116](https://github.com/spinnaker/deck/issues/10116)) ([1f6d2c1](https://github.com/spinnaker/deck/commit/1f6d2c1a69bfae5fd8b6bb9f5fbf0b7fb86930d3))





## [0.14.4](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.14.3...@spinnaker/amazon@0.14.4) (2024-05-10)


### Bug Fixes

* **lambda:** Invoke stage excludedArtifactTypes not including the embedded-artifact type ([#10097](https://github.com/spinnaker/deck/issues/10097)) ([9374f06](https://github.com/spinnaker/deck/commit/9374f0630afb6a174bacf64e9f2ced750bbf4f1d))
* **lambdaStages:** Exporting Lambda stages based on the feature flag settings ([#10085](https://github.com/spinnaker/deck/issues/10085)) ([93bab65](https://github.com/spinnaker/deck/commit/93bab656555fabd539e186587a40dd8a0358dbd9))





## [0.14.3](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.14.2...@spinnaker/amazon@0.14.3) (2023-12-07)


### Bug Fixes

* **amazon:** Allow scaling bounds to use floats between input steps ([#10059](https://github.com/spinnaker/deck/issues/10059)) ([5c1ebfd](https://github.com/spinnaker/deck/commit/5c1ebfdf924e73aa6877943cb008c216177b8256))
* **lambda:** available Runtimes shared between Deploy stage and Functions tab ([#10050](https://github.com/spinnaker/deck/issues/10050)) ([889d769](https://github.com/spinnaker/deck/commit/889d769c600e298917ec2471cd88a4bdd808ed91))





## [0.14.2](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.14.1...@spinnaker/amazon@0.14.2) (2023-10-16)


### Bug Fixes

* **publish:** set access config in deck libraries ([#10049](https://github.com/spinnaker/deck/issues/10049)) ([2a5ebe2](https://github.com/spinnaker/deck/commit/2a5ebe25662eeb9d41b5071749266bf9d6d51104))





## [0.14.1](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.14.0...@spinnaker/amazon@0.14.1) (2023-09-06)


### Bug Fixes

* Scaling bounds should parse float not int ([#10026](https://github.com/spinnaker/deck/issues/10026)) ([b763cae](https://github.com/spinnaker/deck/commit/b763cae826039df46b8dbe019689316ff5034e33))





# [0.14.0](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.13.9...@spinnaker/amazon@0.14.0) (2023-07-20)


### Features

* **lambda:** Migrate Lambda plugin to OSS ([#9988](https://github.com/spinnaker/deck/issues/9988)) ([11f1cab](https://github.com/spinnaker/deck/commit/11f1cabb8efe8d7e034faf06ae3cb455eef6369a)), closes [#9984](https://github.com/spinnaker/deck/issues/9984)





## [0.13.9](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.13.8...@spinnaker/amazon@0.13.9) (2023-06-02)

**Note:** Version bump only for package @spinnaker/amazon





## [0.13.8](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.13.7...@spinnaker/amazon@0.13.8) (2023-05-11)

**Note:** Version bump only for package @spinnaker/amazon





## [0.13.7](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.13.6...@spinnaker/amazon@0.13.7) (2023-05-03)

**Note:** Version bump only for package @spinnaker/amazon





## [0.13.6](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.13.5...@spinnaker/amazon@0.13.6) (2023-04-03)

**Note:** Version bump only for package @spinnaker/amazon





## [0.13.5](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.13.4...@spinnaker/amazon@0.13.5) (2023-02-02)


### Bug Fixes

* **timeout:** Added feature flag for rollback timeout ui input. ([#9937](https://github.com/spinnaker/deck/issues/9937)) ([e239be3](https://github.com/spinnaker/deck/commit/e239be3dbf63dacca84de25901eec708353d3490))





## [0.13.4](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.13.3...@spinnaker/amazon@0.13.4) (2023-02-01)


### Bug Fixes

* **aws:** Fixing AWS AZ auto rebalancing section by setting the default zones ([#9902](https://github.com/spinnaker/deck/issues/9902)) ([10bec86](https://github.com/spinnaker/deck/commit/10bec860ade8de2a4c838746ff3b5b12dd0b4cfd))
* **aws:** Fixing bugs related to clone CX when instance types are incompatible with image/region ([#9901](https://github.com/spinnaker/deck/issues/9901)) ([d7290c4](https://github.com/spinnaker/deck/commit/d7290c4d61d880c0b5dfa3abb1573e1b0550603e))
* **aws:** Guard against missing launchConfig.instanceMonitoring ([#9917](https://github.com/spinnaker/deck/issues/9917)) ([b103ed0](https://github.com/spinnaker/deck/commit/b103ed087199aa33b83c59704f5e735f04e81244))





## [0.13.3](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.13.2...@spinnaker/amazon@0.13.3) (2022-10-21)


### Bug Fixes

* **aws:** fix instance type selector by allowing instance types that can't be validated. ([#9893](https://github.com/spinnaker/deck/issues/9893)) ([563b6f6](https://github.com/spinnaker/deck/commit/563b6f6cbfc72a3122d4cfe669ffc347c9dd7168))





## [0.13.2](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.13.1...@spinnaker/amazon@0.13.2) (2022-10-06)

**Note:** Version bump only for package @spinnaker/amazon





## [0.13.1](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.13.0...@spinnaker/amazon@0.13.1) (2022-08-25)

**Note:** Version bump only for package @spinnaker/amazon





# [0.13.0](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.12.13...@spinnaker/amazon@0.13.0) (2022-08-03)


### Features

* **dependencies:** Update vulnerable dependencies ([#9875](https://github.com/spinnaker/deck/issues/9875)) ([bf92932](https://github.com/spinnaker/deck/commit/bf92932c9396a88fb902050b52f504e4ac01aaa0))





## [0.12.13](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.12.12...@spinnaker/amazon@0.12.13) (2022-07-11)

**Note:** Version bump only for package @spinnaker/amazon





## [0.12.12](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.12.11...@spinnaker/amazon@0.12.12) (2022-07-01)


### Bug Fixes

* **aws:** Fix Create Server Group button ([#9865](https://github.com/spinnaker/deck/issues/9865)) ([bd937f9](https://github.com/spinnaker/deck/commit/bd937f9d958f5d28bdd2caababea1193aa26a17f))





## [0.12.11](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.12.10...@spinnaker/amazon@0.12.11) (2022-06-22)

**Note:** Version bump only for package @spinnaker/amazon





## [0.12.10](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.12.9...@spinnaker/amazon@0.12.10) (2022-05-05)

**Note:** Version bump only for package @spinnaker/amazon





## [0.12.9](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.12.8...@spinnaker/amazon@0.12.9) (2022-04-21)

**Note:** Version bump only for package @spinnaker/amazon





## [0.12.8](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.12.7...@spinnaker/amazon@0.12.8) (2022-04-09)

**Note:** Version bump only for package @spinnaker/amazon





## [0.12.7](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.12.6...@spinnaker/amazon@0.12.7) (2022-03-08)

**Note:** Version bump only for package @spinnaker/amazon





## [0.12.6](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.12.5...@spinnaker/amazon@0.12.6) (2022-01-22)

**Note:** Version bump only for package @spinnaker/amazon





## [0.12.5](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.12.4...@spinnaker/amazon@0.12.5) (2022-01-12)

**Note:** Version bump only for package @spinnaker/amazon





## [0.12.4](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.12.3...@spinnaker/amazon@0.12.4) (2021-12-11)

**Note:** Version bump only for package @spinnaker/amazon





## [0.12.3](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.12.2...@spinnaker/amazon@0.12.3) (2021-12-08)

**Note:** Version bump only for package @spinnaker/amazon





## [0.12.2](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.12.1...@spinnaker/amazon@0.12.2) (2021-12-01)

**Note:** Version bump only for package @spinnaker/amazon





## [0.12.1](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.12.0...@spinnaker/amazon@0.12.1) (2021-11-12)

**Note:** Version bump only for package @spinnaker/amazon





# [0.12.0](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.11.1...@spinnaker/amazon@0.12.0) (2021-11-03)


### Bug Fixes

* **aws:** StepSummary incorrectly rendering ([#9756](https://github.com/spinnaker/deck/issues/9756)) ([91ab3d5](https://github.com/spinnaker/deck/commit/91ab3d52be4dd9b05125c3949129de09a2cbdb9e))


### Features

* **amazon/instance:** Add typing to instance types ([35be23d](https://github.com/spinnaker/deck/commit/35be23d3c6112aab148b53e09983f37ed03b3b1e))
* **aws:** Adding support for multiple instance types and EC2 ASG MixedInstancePolicy - part 2 - child components for Instance Type wizard ([249dc62](https://github.com/spinnaker/deck/commit/249dc6293e3c9addf6c6fe968d2c03716527b92e))
* **aws:** Adding support for multiple instance types and EC2 ASG MixedInstancePolicy - part 3 - parent components for Instance Type wizard and related ([44ab1ab](https://github.com/spinnaker/deck/commit/44ab1abdb1dd25f05b2305b65eb97eed09c8bac7))
* **aws:** Adding support for multiple instance types and EC2 ASG MixedInstancePolicy - part1 - types and hard-coded values ([b43506a](https://github.com/spinnaker/deck/commit/b43506ac27259fb2c11ffe44df1a1418d7804be3))





## [0.11.1](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.11.0...@spinnaker/amazon@0.11.1) (2021-10-05)

**Note:** Version bump only for package @spinnaker/amazon





# [0.11.0](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.10.0...@spinnaker/amazon@0.11.0) (2021-10-01)


### Features

* **lambda:** add alias details to infra view ([#9730](https://github.com/spinnaker/deck/issues/9730)) ([d46a635](https://github.com/spinnaker/deck/commit/d46a635db529bf2adde4b92307ad15bf19bc3e86))





# [0.10.0](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.8.16...@spinnaker/amazon@0.10.0) (2021-09-30)


### Bug Fixes

* bump @types/react to 16.14.10 ([bb62b99](https://github.com/spinnaker/deck/commit/bb62b991514c2a81fbdf467c01f3ce7467f71718))


### Features

* **aws/infrastructure:** Hide aws ad hoc infrastructure action buttons ([#9712](https://github.com/spinnaker/deck/issues/9712)) ([7202efd](https://github.com/spinnaker/deck/commit/7202efd54ad0b048d5c1f45c24162619b25be844))





# [0.9.0](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.8.16...@spinnaker/amazon@0.9.0) (2021-09-30)


### Bug Fixes

* bump @types/react to 16.14.10 ([bb62b99](https://github.com/spinnaker/deck/commit/bb62b991514c2a81fbdf467c01f3ce7467f71718))


### Features

* **aws/infrastructure:** Hide aws ad hoc infrastructure action buttons ([#9712](https://github.com/spinnaker/deck/issues/9712)) ([7202efd](https://github.com/spinnaker/deck/commit/7202efd54ad0b048d5c1f45c24162619b25be844))





## [0.8.16](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.8.15...@spinnaker/amazon@0.8.16) (2021-09-29)


### Bug Fixes

* **amazon:** Typo in command builder ([#9706](https://github.com/spinnaker/deck/issues/9706)) ([fd93d75](https://github.com/spinnaker/deck/commit/fd93d75984d33d5c67cdcf593bd2a0061e35970c))





## [0.8.15](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.8.14...@spinnaker/amazon@0.8.15) (2021-09-24)

**Note:** Version bump only for package @spinnaker/amazon





## [0.8.14](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.8.13...@spinnaker/amazon@0.8.14) (2021-09-24)

**Note:** Version bump only for package @spinnaker/amazon





## [0.8.13](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.8.12...@spinnaker/amazon@0.8.13) (2021-09-23)


### Bug Fixes

* **amazon:** Undefined cloudprovider causing failure for scaling policy data ([#9694](https://github.com/spinnaker/deck/issues/9694)) ([5328de3](https://github.com/spinnaker/deck/commit/5328de3708427559289ce6aa44bf0a94c3c1d506))





## [0.8.12](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.8.11...@spinnaker/amazon@0.8.12) (2021-09-23)


### Bug Fixes

* **aws/lambda:** Lists Event Source details for functions ([#9679](https://github.com/spinnaker/deck/issues/9679)) ([c311cd6](https://github.com/spinnaker/deck/commit/c311cd6daea60ac08eb616d8cdbf45c1dd20ff09))
* **aws/lambda:** Lists Event Source details for functions ([#9683](https://github.com/spinnaker/deck/issues/9683)) ([ead61cd](https://github.com/spinnaker/deck/commit/ead61cde4bce43b77fe69181e763b4cb93f6f4f0))
* **aws/lambda:** revert for typo and backport for event source ([#9682](https://github.com/spinnaker/deck/issues/9682)) ([e8d533d](https://github.com/spinnaker/deck/commit/e8d533d4fbfcc17365a5885c4536d5bc1f3d6749))





## [0.8.11](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.8.10...@spinnaker/amazon@0.8.11) (2021-09-21)

**Note:** Version bump only for package @spinnaker/amazon





## [0.8.10](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.8.9...@spinnaker/amazon@0.8.10) (2021-09-18)

**Note:** Version bump only for package @spinnaker/amazon





## [0.8.9](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.8.8...@spinnaker/amazon@0.8.9) (2021-09-18)

**Note:** Version bump only for package @spinnaker/amazon





## [0.8.8](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.8.7...@spinnaker/amazon@0.8.8) (2021-09-16)


### Bug Fixes

* **amazon:** Use account or awsAccount for cloud metrics reader ([#9666](https://github.com/spinnaker/deck/issues/9666)) ([943efd7](https://github.com/spinnaker/deck/commit/943efd73fba2166c445c6a42569f38ec661ffb38))





## [0.8.7](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.8.6...@spinnaker/amazon@0.8.7) (2021-09-15)

**Note:** Version bump only for package @spinnaker/amazon





## [0.8.6](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.8.5...@spinnaker/amazon@0.8.6) (2021-09-14)


### Bug Fixes

* **amazon:** Set default metric for new scaling policy ([#9655](https://github.com/spinnaker/deck/issues/9655)) ([63e55a9](https://github.com/spinnaker/deck/commit/63e55a988f5c8638d95ab324449d04c1cf40aeac))





## [0.8.5](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.8.4...@spinnaker/amazon@0.8.5) (2021-09-07)


### Bug Fixes

* **amazon/serverGroup:** SubnetSelectField: Use default subnets from settings if no defaultSubnetTypes prop is passed ([#9643](https://github.com/spinnaker/deck/issues/9643)) ([1141bc5](https://github.com/spinnaker/deck/commit/1141bc569ca6c854614d1d51172c2545047adb5b))
* **help:** Correct And to Add ([#9617](https://github.com/spinnaker/deck/issues/9617)) ([5f38547](https://github.com/spinnaker/deck/commit/5f38547b1d7ea45337dbbf545677f5f2c1431579))





## [0.8.4](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.8.3...@spinnaker/amazon@0.8.4) (2021-09-06)


### Bug Fixes

* **titus/subnet:** Support default titus subnetType in SubnetSelectInput ([#9641](https://github.com/spinnaker/deck/issues/9641)) ([cfde788](https://github.com/spinnaker/deck/commit/cfde7889cf72986e53418b76ff8aa354cc6875a5))





## [0.8.3](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.8.2...@spinnaker/amazon@0.8.3) (2021-09-02)

**Note:** Version bump only for package @spinnaker/amazon





## [0.8.2](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.8.1...@spinnaker/amazon@0.8.2) (2021-08-30)


### Bug Fixes

* **aws:** Clarify compatible target groups ([#9626](https://github.com/spinnaker/deck/issues/9626)) ([a28013b](https://github.com/spinnaker/deck/commit/a28013b8db11ad0ed288104d624cf1ea3ed19d34))





## [0.8.1](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.8.0...@spinnaker/amazon@0.8.1) (2021-08-30)

**Note:** Version bump only for package @spinnaker/amazon





# [0.8.0](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.7.14...@spinnaker/amazon@0.8.0) (2021-08-25)


### Features

* **amazon/serverGroup:** Automatically check IMDSv2 on valid accounts ([#9607](https://github.com/spinnaker/deck/issues/9607)) ([7fcb843](https://github.com/spinnaker/deck/commit/7fcb843d2eefdbe074ce0426913946b651b8f2f3))





## [0.7.14](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.7.13...@spinnaker/amazon@0.7.14) (2021-08-20)


### Bug Fixes

* **titus:** Update props in StepPolicyAction ([#9605](https://github.com/spinnaker/deck/issues/9605)) ([f1b039c](https://github.com/spinnaker/deck/commit/f1b039cd1b4f2df750b028569b1c3f2f2925897f))





## [0.7.13](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.7.12...@spinnaker/amazon@0.7.13) (2021-08-20)


### Bug Fixes

* **amazon:** Pass in entire step policy object ([#9604](https://github.com/spinnaker/deck/issues/9604)) ([cc0a0d2](https://github.com/spinnaker/deck/commit/cc0a0d239f38ac15a44eae1df9c67fccec4806e6))





## [0.7.12](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.7.11...@spinnaker/amazon@0.7.12) (2021-08-18)

**Note:** Version bump only for package @spinnaker/amazon





## [0.7.11](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.7.10...@spinnaker/amazon@0.7.11) (2021-08-17)

**Note:** Version bump only for package @spinnaker/amazon





## [0.7.10](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.7.9...@spinnaker/amazon@0.7.10) (2021-08-13)


### Bug Fixes

* **amazon/serverGroup:** pass serverGroup name to user data dialog ([af49c35](https://github.com/spinnaker/deck/commit/af49c35c1b0019b082bfdcc3493ef01d73b4f592))
* **amazon/subnet:** pass input name when creating synthetic event ([ea4050f](https://github.com/spinnaker/deck/commit/ea4050f3a516224947d3fb4eeed3fd03dec94309))





## [0.7.9](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.7.8...@spinnaker/amazon@0.7.9) (2021-08-10)

**Note:** Version bump only for package @spinnaker/amazon





## [0.7.8](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.7.7...@spinnaker/amazon@0.7.8) (2021-08-05)


### Bug Fixes

* **amazon/titus:** Add mode to stage view state ([#9551](https://github.com/spinnaker/deck/issues/9551)) ([a0a4eb5](https://github.com/spinnaker/deck/commit/a0a4eb52b853b510a81b8068548f0198c7c458b4))





## [0.7.7](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.7.6...@spinnaker/amazon@0.7.7) (2021-08-02)

**Note:** Version bump only for package @spinnaker/amazon





## [0.7.6](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.7.5...@spinnaker/amazon@0.7.6) (2021-07-30)


### Bug Fixes

* **all:** Fix lodash global usage ([d048432](https://github.com/spinnaker/deck/commit/d048432978f0aa0bceb2b58f80ea7301de153072))
* **build:** Upgrade uirouter/react version ([cc5004b](https://github.com/spinnaker/deck/commit/cc5004bfded32642553077346c19e34820d24ae7))





## [0.7.5](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.7.4...@spinnaker/amazon@0.7.5) (2021-07-26)

**Note:** Version bump only for package @spinnaker/amazon





## [0.7.4](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.7.3...@spinnaker/amazon@0.7.4) (2021-07-22)

**Note:** Version bump only for package @spinnaker/amazon





## [0.7.3](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.7.2...@spinnaker/amazon@0.7.3) (2021-07-22)


### Bug Fixes

* sample commit to test publishing scripts ([#9505](https://github.com/spinnaker/deck/issues/9505)) ([5075ee1](https://github.com/spinnaker/deck/commit/5075ee1afe80fff98fa0728a323480b9712d8935))





## [0.7.2](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.7.0...@spinnaker/amazon@0.7.2) (2021-07-22)

**Note:** Version bump only for package @spinnaker/amazon





## [0.7.1](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.7.0...@spinnaker/amazon@0.7.1) (2021-07-22)

**Note:** Version bump only for package @spinnaker/amazon





# [0.7.0](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.6.0...@spinnaker/amazon@0.7.0) (2021-07-21)


### Features

* commit to test package bump PR creation ([#9487](https://github.com/spinnaker/deck/issues/9487)) ([554ddc3](https://github.com/spinnaker/deck/commit/554ddc3d0ba9c0db49b4e5451afb22c2af9547cc))





# [0.6.0](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.5.0...@spinnaker/amazon@0.6.0) (2021-07-20)


### Features

* sample amazon commit to test package publishin ([#9481](https://github.com/spinnaker/deck/issues/9481)) ([8d45c5e](https://github.com/spinnaker/deck/commit/8d45c5ef24a83905a6e6bca46889ea4323b4ae50))





# [0.5.0](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.0.326...@spinnaker/amazon@0.5.0) (2021-07-19)


### Features

* **amazon:** sample commit to test publish PR generation ([#9462](https://github.com/spinnaker/deck/issues/9462)) ([f38438c](https://github.com/spinnaker/deck/commit/f38438c30e169b1b52d4eb46796ab93ec577780f))





# [0.4.0](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.0.326...@spinnaker/amazon@0.4.0) (2021-07-19)


### Features

* **amazon:** sample commit to test publish PR generation ([#9462](https://github.com/spinnaker/deck/issues/9462)) ([f38438c](https://github.com/spinnaker/deck/commit/f38438c30e169b1b52d4eb46796ab93ec577780f))





# [0.3.0](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.0.326...@spinnaker/amazon@0.3.0) (2021-07-19)


### Features

* **amazon:** sample commit to test publish PR generation ([#9462](https://github.com/spinnaker/deck/issues/9462)) ([f38438c](https://github.com/spinnaker/deck/commit/f38438c30e169b1b52d4eb46796ab93ec577780f))





# [0.2.0](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.0.326...@spinnaker/amazon@0.2.0) (2021-07-19)


### Features

* **amazon:** sample commit to test publish PR generation ([#9462](https://github.com/spinnaker/deck/issues/9462)) ([f38438c](https://github.com/spinnaker/deck/commit/f38438c30e169b1b52d4eb46796ab93ec577780f))





# [0.1.0](https://github.com/spinnaker/deck/compare/@spinnaker/amazon@0.0.326...@spinnaker/amazon@0.1.0) (2021-07-19)


### Features

* **amazon:** sample commit to test publish PR generation ([#9462](https://github.com/spinnaker/deck/issues/9462)) ([f38438c](https://github.com/spinnaker/deck/commit/f38438c30e169b1b52d4eb46796ab93ec577780f))





## 0.0.326 (2021-07-17)


### Bug Fixes

* **publishing:** Auto approve instead of adding "ready to merge" label ([51f536c](https://github.com/spinnaker/deck/commit/51f536c275e77854d8f173aeec86412ffbd66b6d))






## [0.0.324](https://www.github.com/spinnaker/deck/compare/d5ce91559150b054114c8334a7006c46edc88586...86b6dc555637f8e9bfb1b01844e100e8481e394e) (2021-07-08)


### Changes

chore(amazon): publish amazon@0.0.324 ([86b6dc55](https://github.com/spinnaker/deck/commit/86b6dc555637f8e9bfb1b01844e100e8481e394e))  
fix(amazon/securityGroup): Prevent cloning security groups into regions they already exist in [#9381](https://github.com/spinnaker/deck/pull/9381) ([b3a4c9c6](https://github.com/spinnaker/deck/commit/b3a4c9c6e58e547fef366226f9a6234ae033eb7d))  
chore(*): Import TaskMonitorWrapper from core instead of NgReact [#9406](https://github.com/spinnaker/deck/pull/9406) ([cce5473b](https://github.com/spinnaker/deck/commit/cce5473b600f173f9df41c7dabe6e2fceb29999f))  



## [0.0.323](https://www.github.com/spinnaker/deck/compare/013fa570b0810561f769155bb64e262ad178afae...d5ce91559150b054114c8334a7006c46edc88586) (2021-07-01)


### Changes

chore(amazon): publish amazon@0.0.323 ([d5ce9155](https://github.com/spinnaker/deck/commit/d5ce91559150b054114c8334a7006c46edc88586))  
chore(amazon): Export subnet files [#9394](https://github.com/spinnaker/deck/pull/9394) ([acbfe30e](https://github.com/spinnaker/deck/commit/acbfe30e9251321a0ba19a637f1a180f9388a05d))  



## [0.0.322](https://www.github.com/spinnaker/deck/compare/eb9e61cf1858e891e74a8009b78e2090d751bc7e...013fa570b0810561f769155bb64e262ad178afae) (2021-07-01)


### Changes

chore(amazon): publish amazon@0.0.322 ([013fa570](https://github.com/spinnaker/deck/commit/013fa570b0810561f769155bb64e262ad178afae))  
chore(all): Remove ng template cache for webpack ([be6df680](https://github.com/spinnaker/deck/commit/be6df680689e0624b27635bc875d0b4390a3bc4a))  
chore(build): Integrate with yarn workspaces ([e30e631b](https://github.com/spinnaker/deck/commit/e30e631b128bd1c8bfef3a48643ce0b4f9935f1d))  
feat(aws): Support UDP and TLS listeners for NLB [#8657](https://github.com/spinnaker/deck/pull/8657) ([3bb6aaeb](https://github.com/spinnaker/deck/commit/3bb6aaeb5e3107c78c3a0d062a3b761a36c76966))  
chore(core): Remove deps on ngReact for EntitySource and ViewChangesLink [#9379](https://github.com/spinnaker/deck/pull/9379) ([0a64e176](https://github.com/spinnaker/deck/commit/0a64e176819c989e26a40a3512753799f42e6680))  



## [0.0.321](https://www.github.com/spinnaker/deck/compare/2988b824525d72285b4ce30cc39d4782c19633ad...eb9e61cf1858e891e74a8009b78e2090d751bc7e) (2021-06-25)


### Changes

chore(amazon): publish amazon@0.0.321 ([eb9e61cf](https://github.com/spinnaker/deck/commit/eb9e61cf1858e891e74a8009b78e2090d751bc7e))  
feat(aws): Adding support for capacity rebalance [#9369](https://github.com/spinnaker/deck/pull/9369) ([66b7ce86](https://github.com/spinnaker/deck/commit/66b7ce860e2116ea2d19e491e1268b5860fe946b))  
feat(amazon/loadBalancer): Show NLB security groups if they exist [#9371](https://github.com/spinnaker/deck/pull/9371) ([d9fd6672](https://github.com/spinnaker/deck/commit/d9fd6672141c1584638a2b33730e526841cfaac4))  



## [0.0.320](https://www.github.com/spinnaker/deck/compare/5554b39c56c2e6aed2522e90a46779d671b82c08...2988b824525d72285b4ce30cc39d4782c19633ad) (2021-06-20)


### Changes

chore(amazon): publish amazon@0.0.320 ([2988b824](https://github.com/spinnaker/deck/commit/2988b824525d72285b4ce30cc39d4782c19633ad))  
chore(*): Remove references to ButtonBusyInidicator [#9283](https://github.com/spinnaker/deck/pull/9283) ([ee2ab2f2](https://github.com/spinnaker/deck/commit/ee2ab2f2a8a4b35210f8da40318bbca857bae1aa))  
feat(aws): Adding helper text to indicate priority [#9330](https://github.com/spinnaker/deck/pull/9330) ([b423fe59](https://github.com/spinnaker/deck/commit/b423fe591246fa6583220543cf7fcb95f9fdf928))  



## [0.0.319](https://www.github.com/spinnaker/deck/compare/025eb9d13265911a03894b18f3ee532745763bef...5554b39c56c2e6aed2522e90a46779d671b82c08) (2021-06-11)


### Changes

chore(amazon): publish amazon@0.0.319 ([5554b39c](https://github.com/spinnaker/deck/commit/5554b39c56c2e6aed2522e90a46779d671b82c08))  
chore(bump): Upgrade @spinnaker/scripts ([db9f47df](https://github.com/spinnaker/deck/commit/db9f47df6eae4e87319586721c1dc95cc86290a9))  



## [0.0.318](https://www.github.com/spinnaker/deck/compare/13375fa7dc4c2acb72572e8595d00ddf51d2b3e7...025eb9d13265911a03894b18f3ee532745763bef) (2021-06-11)


### Changes

chore(amazon): publish amazon@0.0.318 ([025eb9d1](https://github.com/spinnaker/deck/commit/025eb9d13265911a03894b18f3ee532745763bef))  
chore(bump): Upgrade core ([a042c02e](https://github.com/spinnaker/deck/commit/a042c02e9e692b3efb41cc95c54bb08dd020bb17))  



## [0.0.317](https://www.github.com/spinnaker/deck/compare/9cadb5bd8ddbad3f5b24abd167df3cbfdcedf579...13375fa7dc4c2acb72572e8595d00ddf51d2b3e7) (2021-06-10)


### Changes

chore(amazon): publish amazon@0.0.317 ([13375fa7](https://github.com/spinnaker/deck/commit/13375fa7dc4c2acb72572e8595d00ddf51d2b3e7))  
chore(core/amazon): Remove ViewScalingPolicies link from NgReact [#9271](https://github.com/spinnaker/deck/pull/9271) ([0a3f56b9](https://github.com/spinnaker/deck/commit/0a3f56b94821a5d7d80bfc722ec6b2b98e5e5e85))  



## [0.0.316](https://www.github.com/spinnaker/deck/compare/669f6c0e809b36ca8dae46f9c8165827eb88d482...9cadb5bd8ddbad3f5b24abd167df3cbfdcedf579) (2021-06-09)


### Changes

chore(amazon): publish amazon@0.0.316 ([9cadb5bd](https://github.com/spinnaker/deck/commit/9cadb5bd8ddbad3f5b24abd167df3cbfdcedf579))  
chore(package-bump): Update core versions in amazon and docker ([9a9ff96a](https://github.com/spinnaker/deck/commit/9a9ff96a5f12b02759c6adbd6ee0d1fdf0d870fb))  
refactor(packages): Migrate packages to make them independent ([9da3751a](https://github.com/spinnaker/deck/commit/9da3751a3b7420eb83ee6b589c1f73b12faed572))  



## [0.0.315](https://www.github.com/spinnaker/deck/compare/648aeffb3ad66f2429efa502684d59c7320b3984...669f6c0e809b36ca8dae46f9c8165827eb88d482) (2021-06-03)


### Changes

chore(amazon): publish amazon@0.0.315 ([669f6c0e](https://github.com/spinnaker/deck/commit/669f6c0e809b36ca8dae46f9c8165827eb88d482))  
refactor(build): Fix paths for rollup ([9a9468a2](https://github.com/spinnaker/deck/commit/9a9468a2e1da465d5b95ae996395f4e59b116a09))  



## [0.0.314](https://www.github.com/spinnaker/deck/compare/78fcf62f3a50f4040ba829a57cc46cbfab9d8376...648aeffb3ad66f2429efa502684d59c7320b3984) (2021-05-27)


### Changes

chore(amazon): publish amazon@0.0.314 ([648aeffb](https://github.com/spinnaker/deck/commit/648aeffb3ad66f2429efa502684d59c7320b3984))  
feat(core): add google analytics to logger [#9246](https://github.com/spinnaker/deck/pull/9246) ([50e06cb4](https://github.com/spinnaker/deck/commit/50e06cb421402b4c36fbb0810c4704061aefcd71))  



## [0.0.313](https://www.github.com/spinnaker/deck/compare/cca0736a6f4bd1615360c277761effa7199a2bfe...78fcf62f3a50f4040ba829a57cc46cbfab9d8376) (2021-05-25)


### Changes

chore(amazon): publish amazon@0.0.313 ([78fcf62f](https://github.com/spinnaker/deck/commit/78fcf62f3a50f4040ba829a57cc46cbfab9d8376))  
fix(amazon): Add back ng template handling ([10534f5c](https://github.com/spinnaker/deck/commit/10534f5ce2663e107ce304a48743c3cdfca38399))  



## [0.0.312](https://www.github.com/spinnaker/deck/compare/6073f0a318ccefb7101c4864ac8524677acb7cef...cca0736a6f4bd1615360c277761effa7199a2bfe) (2021-05-25)


### Changes

chore(amazon): publish amazon@0.0.312 ([cca0736a](https://github.com/spinnaker/deck/commit/cca0736a6f4bd1615360c277761effa7199a2bfe))  
fix(build): Revert independent package changes ([c067090d](https://github.com/spinnaker/deck/commit/c067090dcee79a52ac7788cfb2d939b80f52423b))  



## [0.0.311](https://www.github.com/spinnaker/deck/compare/06d0bee20b7af2456b5bdd80e3cfc6906d28066e...6073f0a318ccefb7101c4864ac8524677acb7cef) (2021-05-24)


### Changes

chore(amazon): publish amazon@0.0.311 ([6073f0a3](https://github.com/spinnaker/deck/commit/6073f0a318ccefb7101c4864ac8524677acb7cef))  
chore(packages): Upgrade @spinnaker/scripts ([285c1942](https://github.com/spinnaker/deck/commit/285c19428b631318f51435f9972cafa71830dab4))  
fix(aws/lambda): Shows function details and actions menu. [#9199](https://github.com/spinnaker/deck/pull/9199) ([c39f74d3](https://github.com/spinnaker/deck/commit/c39f74d3bd5e07178903b5611a9a5c9c973438ae))  



## [0.0.310](https://www.github.com/spinnaker/deck/compare/17319b2c58e1f7609f1a395462823bd09283d863...06d0bee20b7af2456b5bdd80e3cfc6906d28066e) (2021-05-22)


### Changes

chore(amazon): publish amazon@0.0.310 ([06d0bee2](https://github.com/spinnaker/deck/commit/06d0bee20b7af2456b5bdd80e3cfc6906d28066e))  
chore(package): Upgrade @spinnaker/scripts package ([26bc67df](https://github.com/spinnaker/deck/commit/26bc67dfb113842093a1d9b57c53ae0d7744e9b3))  



## [0.0.309](https://www.github.com/spinnaker/deck/compare/2badb3b60d70f8d1f166bbc72d9e18c0edc23f40...17319b2c58e1f7609f1a395462823bd09283d863) (2021-05-21)


### Changes

chore(amazon): publish amazon@0.0.309 ([17319b2c](https://github.com/spinnaker/deck/commit/17319b2c58e1f7609f1a395462823bd09283d863))  
chore(bump): Bump packages ([fcdc3f76](https://github.com/spinnaker/deck/commit/fcdc3f76605a73a138d9db8dfa5b54a77f1d128a))  



## [0.0.308](https://www.github.com/spinnaker/deck/compare/a54b3a0cc9ce45151b7ac01982dc844913581c6a...2badb3b60d70f8d1f166bbc72d9e18c0edc23f40) (2021-05-21)


### Changes

chore(amazon): publish amazon@0.0.308 ([2badb3b6](https://github.com/spinnaker/deck/commit/2badb3b60d70f8d1f166bbc72d9e18c0edc23f40))  
feat(amazon/serverGroup): export datelinechart and MetricAlarmChart [#9211](https://github.com/spinnaker/deck/pull/9211) ([494add31](https://github.com/spinnaker/deck/commit/494add310c89f2cfbb88b3b8bf9380e2d6c82c7a))  
refactor(amazon): Make amazon package independent ([6c3838ec](https://github.com/spinnaker/deck/commit/6c3838eca864a816d16e435b0856ef9b13e9a443))  



## [0.0.307](https://www.github.com/spinnaker/deck/compare/692c6f3ad87fa93b8297703b8be38bcfd8d5a70c...a54b3a0cc9ce45151b7ac01982dc844913581c6a) (2021-05-20)


### Changes

chore(amazon): publish amazon@0.0.307 ([a54b3a0c](https://github.com/spinnaker/deck/commit/a54b3a0cc9ce45151b7ac01982dc844913581c6a))  
fix(dependency): Fix @types/react version ([5840e086](https://github.com/spinnaker/deck/commit/5840e086a9f195b99b30af41e992c5b8d66ebe59))  



## [0.0.306](https://www.github.com/spinnaker/deck/compare/bfd1cb9721d35a52c416e074377de7a9fc7dc323...692c6f3ad87fa93b8297703b8be38bcfd8d5a70c) (2021-05-13)


### Changes

chore(amazon): publish amazon@0.0.306 ([692c6f3a](https://github.com/spinnaker/deck/commit/692c6f3ad87fa93b8297703b8be38bcfd8d5a70c))  
fix(provider/aws): Fix display of server group without monitoring [#9162](https://github.com/spinnaker/deck/pull/9162) ([63ee2410](https://github.com/spinnaker/deck/commit/63ee241037ea1a296e25ba79f2191bd68eb94d83))  



## [0.0.305](https://www.github.com/spinnaker/deck/compare/3f97f3556c1b839569ddc439bb989c390ca566a4...bfd1cb9721d35a52c416e074377de7a9fc7dc323) (2021-05-12)


### Changes

chore(amazon): publish amazon@0.0.305 ([bfd1cb97](https://github.com/spinnaker/deck/commit/bfd1cb9721d35a52c416e074377de7a9fc7dc323))  
refactor(core): Convert AddEntityTagLinks to React [#9147](https://github.com/spinnaker/deck/pull/9147) ([39fa7730](https://github.com/spinnaker/deck/commit/39fa77303b75ca6e5b0af96d1e011bcff452cede))  
fix(aws): Avoid showing an incorrect 'EC2 Classic' subnet [#9151](https://github.com/spinnaker/deck/pull/9151) ([bf898427](https://github.com/spinnaker/deck/commit/bf89842715ac05cf09e888a24643560f95f536d2))  
feat(aws/lb): Internal ALBs can be dualstacked [#9144](https://github.com/spinnaker/deck/pull/9144) ([7499271c](https://github.com/spinnaker/deck/commit/7499271c1eb45f88aae3265a48f7e9148bf5d3ea))  
feat(aws/titus): Add help text to IPv6 field [#9139](https://github.com/spinnaker/deck/pull/9139) ([01aef56e](https://github.com/spinnaker/deck/commit/01aef56e4d9d645caa7f82a814b4b9be5d5d5ae1))  



## [0.0.304](https://www.github.com/spinnaker/deck/compare/84eaeee502e2b673033124eb1872e1357cdda645...3f97f3556c1b839569ddc439bb989c390ca566a4) (2021-05-06)


### Changes

chore(amazon): publish amazon@0.0.304 ([3f97f355](https://github.com/spinnaker/deck/commit/3f97f3556c1b839569ddc439bb989c390ca566a4))  
chore(eslint): yarn eslint --fix ([7360252e](https://github.com/spinnaker/deck/commit/7360252e1a44b2c76c67b9741289a523bbd799c3))  
feature(loadbalancer): Add the ability to configure 'preserve client IP' on NLBs [#9125](https://github.com/spinnaker/deck/pull/9125) ([95a3fecb](https://github.com/spinnaker/deck/commit/95a3fecb913f26d1969dae91fa1d4ee8db89cafd))  



## [0.0.303](https://www.github.com/spinnaker/deck/compare/9e23be001cfdf1989edaceaa277291ad1f3da573...84eaeee502e2b673033124eb1872e1357cdda645) (2021-04-29)


### Changes

chore(amazon): publish amazon@0.0.303 ([84eaeee5](https://github.com/spinnaker/deck/commit/84eaeee502e2b673033124eb1872e1357cdda645))  
chore(rxjs): Migrate to static combineLatest and fix typing errors ([496e44af](https://github.com/spinnaker/deck/commit/496e44af5fb9abf304d9f4e1b08a2f4f0d2a634e))  
chore(rxjs): Remove now unused imports of Observable from 'rxjs' ([a4fc97f3](https://github.com/spinnaker/deck/commit/a4fc97f3abfa4395079e70c52e856dad4d0ecc68))  
chore(rxjs): Manually migrate rxjs code in .js files ([30c3ee8d](https://github.com/spinnaker/deck/commit/30c3ee8d593973b0c6a87fd0a2dd9a15b10470f0))  
chore(rxjs): Fix combineLatest deprecated calls ([2a9e2a4b](https://github.com/spinnaker/deck/commit/2a9e2a4bbfbc07f2d5fb8cca4e67276aa3e2c8ea))  
chore(rxjs): Run rxjs 5-to-6 migration tooling ([c11835cf](https://github.com/spinnaker/deck/commit/c11835cfef079d5d6af8dcfbafa4fe416a059a3e))  
fix(redblack): Update redblack fields for angular DeploymentStrategyS… [#9105](https://github.com/spinnaker/deck/pull/9105) ([b9a8b58e](https://github.com/spinnaker/deck/commit/b9a8b58e3499702dad7381d5f19a3cab17dd3e1c))  
fix(titus/serverGroup): Fix height of metrics line chart ([8b9187bc](https://github.com/spinnaker/deck/commit/8b9187bc39a7a080edf02fd94be6ccf633cf0ebb))  



## [0.0.302](https://www.github.com/spinnaker/deck/compare/ccfb07110d006106ccb12cb79a7d1525f2ce699a...9e23be001cfdf1989edaceaa277291ad1f3da573) (2021-04-21)


### Changes

chore(amazon): publish amazon@0.0.302 ([9e23be00](https://github.com/spinnaker/deck/commit/9e23be001cfdf1989edaceaa277291ad1f3da573))  



## [0.0.301](https://www.github.com/spinnaker/deck/compare/c23b8fb66fde650ead78c0fe7d3bc514c29b66e3...ccfb07110d006106ccb12cb79a7d1525f2ce699a) (2021-04-21)


### Changes

chore(amazon): publish amazon@0.0.301 ([ccfb0711](https://github.com/spinnaker/deck/commit/ccfb07110d006106ccb12cb79a7d1525f2ce699a))  
Remove webpack modules + webpack consolidation [#9097](https://github.com/spinnaker/deck/pull/9097) ([00145566](https://github.com/spinnaker/deck/commit/001455667f2afb5c728737863f7365fc4fcbb76b))  



## [0.0.300](https://www.github.com/spinnaker/deck/compare/453444a3f1f678c08512f279b1e0ad0906ef2672...c23b8fb66fde650ead78c0fe7d3bc514c29b66e3) (2021-04-14)


### Changes

chore(amazon): publish amazon@0.0.300 ([c23b8fb6](https://github.com/spinnaker/deck/commit/c23b8fb66fde650ead78c0fe7d3bc514c29b66e3))  
feat(aws): Adding support to view details of an ASG with MixedInstancesPolicy(MIP) [#8960](https://github.com/spinnaker/deck/pull/8960) ([fb0ad572](https://github.com/spinnaker/deck/commit/fb0ad5726b1321bbc9cae378f4a50f388fe5c611))  



## [0.0.299](https://www.github.com/spinnaker/deck/compare/ed8347abbf5df6de22994fe1b468dad552f4c190...453444a3f1f678c08512f279b1e0ad0906ef2672) (2021-04-06)


### Changes

chore(amazon): publish amazon@0.0.299 ([453444a3](https://github.com/spinnaker/deck/commit/453444a3f1f678c08512f279b1e0ad0906ef2672))  
feat(titus): Finish migrating instance details to react [#9034](https://github.com/spinnaker/deck/pull/9034) ([3ae687e5](https://github.com/spinnaker/deck/commit/3ae687e51992d1294672f78bbb1a041481044fc0))  



## [0.0.298](https://www.github.com/spinnaker/deck/compare/de7498617b7d47771f9602d13d7db6e839d5058f...ed8347abbf5df6de22994fe1b468dad552f4c190) (2021-03-30)


### Changes

chore(amazon): publish amazon@0.0.298 ([ed8347ab](https://github.com/spinnaker/deck/commit/ed8347abbf5df6de22994fe1b468dad552f4c190))  
test: fix typing errors introduced by stricter types in updated @types/jasmine package ([0ff5c18c](https://github.com/spinnaker/deck/commit/0ff5c18c3fb947800460d32241e8b31d6720c760))  
test: switch some imports to `import type` to eliminate certain warnings during karma runs ([84e4fe6b](https://github.com/spinnaker/deck/commit/84e4fe6b24592c9bbb65fe45339ccf2a930b2c50))  
refactor(amazon/serverGroup): Delete unused metricAlarmChart angularjs component ([d58cb46d](https://github.com/spinnaker/deck/commit/d58cb46d9559c0c83e7642e0eb79687f9887e586))  
feat(amazon/serverGroup): Replace angular metric-alarm-chart with react MetricAlarmChart ([0861d2dc](https://github.com/spinnaker/deck/commit/0861d2dcd07135c45c8e3baf54b0ccdebff9ae6e))  
feat(amazon/serverGroup): Introduce MetricAlarmChart.tsx using Chart.js ([802cb2ee](https://github.com/spinnaker/deck/commit/802cb2eea59c858fc4cb216325b6ae78a0cd9aef))  



## [0.0.297](https://www.github.com/spinnaker/deck/compare/e35e2b67da44a5903fde88e67abd6a76e8593934...de7498617b7d47771f9602d13d7db6e839d5058f) (2021-03-24)


### Changes

chore(amazon): publish amazon@0.0.297 ([de749861](https://github.com/spinnaker/deck/commit/de7498617b7d47771f9602d13d7db6e839d5058f))  
feat(domain): Extend instance domains [#9028](https://github.com/spinnaker/deck/pull/9028) ([2a291a0b](https://github.com/spinnaker/deck/commit/2a291a0b381c574365ae88f0307905d2bdee2cb0))  
feat(amazon): Show bake warning for migrated pipelines [#9023](https://github.com/spinnaker/deck/pull/9023) ([fa681fd2](https://github.com/spinnaker/deck/commit/fa681fd264d443e18ec076a58e7fa186f3c01d88))  



## [0.0.296](https://www.github.com/spinnaker/deck/compare/065e15d02b1862842c6d3c810bdf27c55f15631e...e35e2b67da44a5903fde88e67abd6a76e8593934) (2021-03-18)


### Changes

chore(amazon): publish amazon@0.0.296 ([e35e2b67](https://github.com/spinnaker/deck/commit/e35e2b67da44a5903fde88e67abd6a76e8593934))  
fix(core): Export ConsoleOutputLink [#8998](https://github.com/spinnaker/deck/pull/8998) ([da315f59](https://github.com/spinnaker/deck/commit/da315f59ecec9bddf9e31e5af11bda349fd202c0))  



## [0.0.295](https://www.github.com/spinnaker/deck/compare/d8c58079cab9ef151ede110f7690c392a42145b3...065e15d02b1862842c6d3c810bdf27c55f15631e) (2021-03-05)


### Changes

chore(amazon): publish amazon@0.0.295 ([065e15d0](https://github.com/spinnaker/deck/commit/065e15d02b1862842c6d3c810bdf27c55f15631e))  
fix(amazon/subnet): fix NPE when AWSProviderSettings.serverGroups is undefined [#8976](https://github.com/spinnaker/deck/pull/8976) ([5c26ecc6](https://github.com/spinnaker/deck/commit/5c26ecc6a9c53e52b23f34dc1305a4286e94b0f9))  



## [0.0.294](https://www.github.com/spinnaker/deck/compare/e1e4a2f5bbf7e5d9d31843095891f55f89530079...d8c58079cab9ef151ede110f7690c392a42145b3) (2021-03-04)


### Changes

chore(amazon): publish amazon@0.0.294 ([d8c58079](https://github.com/spinnaker/deck/commit/d8c58079cab9ef151ede110f7690c392a42145b3))  
Merge branch 'master' into disable-ipv6-images ([35215bcb](https://github.com/spinnaker/deck/commit/35215bcbfbc7efd25124a2e5623aae5bf9fc939b))  
fix(amazon/serverGroup): Move spelLoadBalancers and spelTargetGroups to viewState [#8957](https://github.com/spinnaker/deck/pull/8957) ([eb4394f8](https://github.com/spinnaker/deck/commit/eb4394f8f97bb7958a23c75f3f40cd00111317b6))  
Use native .some instead of lodash ([84bd0f1f](https://github.com/spinnaker/deck/commit/84bd0f1f5ae5c6301be067b715284d91eb55c6cc))  
fix(amazon): Do not automatically assign IPv6 for disabled images ([36b7feca](https://github.com/spinnaker/deck/commit/36b7feca2dde7defd6ea1acfb7378044d7c62300))  



## [0.0.293](https://www.github.com/spinnaker/deck/compare/94b4ab9df55225b54011eb8db2adab4758d2586d...e1e4a2f5bbf7e5d9d31843095891f55f89530079) (2021-03-02)


### Changes

chore(amazon): publish amazon@0.0.293 ([e1e4a2f5](https://github.com/spinnaker/deck/commit/e1e4a2f5bbf7e5d9d31843095891f55f89530079))  
Merge branch 'master' into aws-secGroups ([c344366c](https://github.com/spinnaker/deck/commit/c344366c98ff9a4ea74f6d8e9d6793ae65bf4d31))  
fix(aws): Simplify security group extraction ([bd2f60fe](https://github.com/spinnaker/deck/commit/bd2f60fecb0143328de4bc0e3aac16c207f7f440))  
fix(amazon): Update logic for subnet warning ([a9be5385](https://github.com/spinnaker/deck/commit/a9be53854a31f3fe7d0fa5d9b5db56de7ca137d5))  
fix(amazon/loadBalancer): Fix text for cross-zone load balancing ([bbee13dc](https://github.com/spinnaker/deck/commit/bbee13dcd7851b4b0388e0c4d449456509570ac4))  
fix(amazon): Instance DNS hrefs when Instance Port is not defined ([fd516baf](https://github.com/spinnaker/deck/commit/fd516bafc785bd7e40b5f6d21ffb001af7912510))  



## [0.0.292](https://www.github.com/spinnaker/deck/compare/e9f0756057c6370d7c518d0df39f37d6a03f2dfe...94b4ab9df55225b54011eb8db2adab4758d2586d) (2021-02-22)


### Changes

chore(amazon): publish amazon@0.0.292 ([94b4ab9d](https://github.com/spinnaker/deck/commit/94b4ab9df55225b54011eb8db2adab4758d2586d))  
chore(lint): Update import statement ordering ([5a9768bc](https://github.com/spinnaker/deck/commit/5a9768bc6db2f527a73d6b1f5fb3120c101e094b))  
chore(lint): Sort import statements ([cca56eaa](https://github.com/spinnaker/deck/commit/cca56eaaeeb412b7596c68a1260eefed7fbf6fed))  
fix(aws): Update type for recommended subnets ([c5598243](https://github.com/spinnaker/deck/commit/c5598243f2149790b76da6e69e14ccc5335d608f))  
chore(prettier): Format code using prettier ([b6364c82](https://github.com/spinnaker/deck/commit/b6364c820c106ee54e5bd5770e44c81fa3af06e9))  



## [0.0.291](https://www.github.com/spinnaker/deck/compare/eb03d883c4f57c095f6119b148c321f18453ff66...e9f0756057c6370d7c518d0df39f37d6a03f2dfe) (2021-02-17)


### Changes

chore(amazon): publish amazon@0.0.291 ([e9f07560](https://github.com/spinnaker/deck/commit/e9f0756057c6370d7c518d0df39f37d6a03f2dfe))  
fix: broken link to renamed BlockDeviceConfig.groovy [#8924](https://github.com/spinnaker/deck/pull/8924) ([8bab7d06](https://github.com/spinnaker/deck/commit/8bab7d06f90838ef032a222751c00bfd1ae7fb12))  
Merge branch 'master' into titus-type-updates ([474d03da](https://github.com/spinnaker/deck/commit/474d03da6f24b14dff3620ea31e2d8c49ef0b5a9))  
Merge branch 'master' into external-vpc-warning ([c3cd53c2](https://github.com/spinnaker/deck/commit/c3cd53c23dae97470f753612a8eb1c949b977f0f))  
refactor(titus): Create component wrapper with generic functionality ([06585c58](https://github.com/spinnaker/deck/commit/06585c586e33f8c88cd191f03eefde54d7180858))  
Merge branch 'master' into ipv6-account-conditional ([908585b2](https://github.com/spinnaker/deck/commit/908585b28ef43c304724ea8a5ed459aff492de7d))  
feat(amazon): Add configurable warnings for subnets ([5c7eac6e](https://github.com/spinnaker/deck/commit/5c7eac6e2228c7f8d9c065b1e1ef768cb7438e47))  
Update app/scripts/modules/amazon/src/serverGroup/configure/wizard/pages/ServerGroupBasicSettings.tsx ([780ad579](https://github.com/spinnaker/deck/commit/780ad5792b639b81aeb512027168962fc562d75b))  
fix(amazon): Remove conditional for enabling ipv6 by default ([a29792e8](https://github.com/spinnaker/deck/commit/a29792e83d723711b1a7a5f35581b488b037be07))  



## [0.0.290](https://www.github.com/spinnaker/deck/compare/fe3f94a45c345656e4b4246da9fe10ad757fae2f...eb03d883c4f57c095f6119b148c321f18453ff66) (2021-02-10)


### Changes

chore(amazon): publish amazon@0.0.290 ([eb03d883](https://github.com/spinnaker/deck/commit/eb03d883c4f57c095f6119b148c321f18453ff66))  
fix(amazon): Update NLB dualstack constraints for instance targets ([f86c6f5c](https://github.com/spinnaker/deck/commit/f86c6f5c30194a739ff9d90abd7f4c2bb01ccf6a))  



## [0.0.289](https://www.github.com/spinnaker/deck/compare/967f180f837fdb3710952dbeb946500548e5aedc...fe3f94a45c345656e4b4246da9fe10ad757fae2f) (2021-02-09)


### Changes

chore(amazon): publish amazon@0.0.289 ([fe3f94a4](https://github.com/spinnaker/deck/commit/fe3f94a45c345656e4b4246da9fe10ad757fae2f))  
feat(domain/aws): Extend upsert command [#8895](https://github.com/spinnaker/deck/pull/8895) ([33e0fd37](https://github.com/spinnaker/deck/commit/33e0fd377c633f3288512a6630c986a53fbd56ef))  
fix(amazon): Stray 0 showing in component [#8894](https://github.com/spinnaker/deck/pull/8894) ([1ea504a2](https://github.com/spinnaker/deck/commit/1ea504a29ad9aa514bc58d6bd63854591429a02c))  



## [0.0.288](https://www.github.com/spinnaker/deck/compare/07815259108fac258367164c1914b5f282f2c18d...967f180f837fdb3710952dbeb946500548e5aedc) (2021-02-08)


### Changes

chore(amazon): publish amazon@0.0.288 ([967f180f](https://github.com/spinnaker/deck/commit/967f180f837fdb3710952dbeb946500548e5aedc))  
fix(serverGroup): Update capacity text to be more accurate [#8891](https://github.com/spinnaker/deck/pull/8891) ([1e64d909](https://github.com/spinnaker/deck/commit/1e64d9093d864476616248c60a710f0c407f686a))  
refactor(aws/titus): Convert instance DNS to react [#8884](https://github.com/spinnaker/deck/pull/8884) ([745e1bf5](https://github.com/spinnaker/deck/commit/745e1bf5592b768e733f32b6fe6d6852b955c227))  
feat(amazon/loadBalancer): Enable dualstacking in ALBs/NLBs with constraints [#8859](https://github.com/spinnaker/deck/pull/8859) ([4c661749](https://github.com/spinnaker/deck/commit/4c6617492ad0e70e913c1608241cb0c0870e4f4e))  



## [0.0.287](https://www.github.com/spinnaker/deck/compare/07c678987dfbe913f4aac6d60d5233d79e0b20db...07815259108fac258367164c1914b5f282f2c18d) (2021-01-19)


### Changes

chore(amazon): publish amazon@0.0.287 ([07815259](https://github.com/spinnaker/deck/commit/07815259108fac258367164c1914b5f282f2c18d))  
fix(amazon/securityGroup): Add stack/detail info to help text [#8852](https://github.com/spinnaker/deck/pull/8852) ([ebbb32f9](https://github.com/spinnaker/deck/commit/ebbb32f9f2782955e54e8f263358e9e2e320d717))  
Deangularize instance writer [#8834](https://github.com/spinnaker/deck/pull/8834) ([f16b0775](https://github.com/spinnaker/deck/commit/f16b0775917242a39ae70e86c5541020c898b872))  
fix(aws): Add Connection termination on deregistration flag on NLB's [#8835](https://github.com/spinnaker/deck/pull/8835) ([22c59c18](https://github.com/spinnaker/deck/commit/22c59c185ec3b46e5a50145eba09f8e1be56f8c6))  



## [0.0.286](https://www.github.com/spinnaker/deck/compare/78cfef0e1499431cc9a610f7df970f1e4c00858b...07c678987dfbe913f4aac6d60d5233d79e0b20db) (2021-01-13)


### Changes

chore(amazon): publish amazon@0.0.286 ([07c67898](https://github.com/spinnaker/deck/commit/07c678987dfbe913f4aac6d60d5233d79e0b20db))  
feat(aws): Automatically enable IPv6 in test environments when IPv6 f… [#8813](https://github.com/spinnaker/deck/pull/8813) ([ddbe1d65](https://github.com/spinnaker/deck/commit/ddbe1d65ba77aa9c2bed2501311596f69127f205))  
feat(amazon): Display updated ALB redirect config [#8844](https://github.com/spinnaker/deck/pull/8844) ([a130ca87](https://github.com/spinnaker/deck/commit/a130ca87b28fa563cccf8ed447a8395bc6561b2b))  
refactor(core): Extract server group name previewer into its own component [#8842](https://github.com/spinnaker/deck/pull/8842) ([a35e8bf2](https://github.com/spinnaker/deck/commit/a35e8bf29007e928374e79eb95eaf91d0f8d5f25))  



## [0.0.285](https://www.github.com/spinnaker/deck/compare/2309866f40acdc6cdc36bbfe6a819591b2c20338...78cfef0e1499431cc9a610f7df970f1e4c00858b) (2021-01-13)


### Changes

chore(amazon): publish amazon@0.0.285 ([78cfef0e](https://github.com/spinnaker/deck/commit/78cfef0e1499431cc9a610f7df970f1e4c00858b))  
feat(amazon): Display updated ALB redirect config [#8830](https://github.com/spinnaker/deck/pull/8830) ([169c6013](https://github.com/spinnaker/deck/commit/169c601301a2f759cefdb3e414fcd5e7038c0b21))  
fix(amazon): Security group names for standalone apps render incorrectly [#8816](https://github.com/spinnaker/deck/pull/8816) ([6904cb06](https://github.com/spinnaker/deck/commit/6904cb064db6733db513f44147a6344dc1cf89da))  
fix(amazon/loadBalancer): Fix target group health interface to match the back end, fix instance counts for target groups in the load balancer icon popover in a server group [#8828](https://github.com/spinnaker/deck/pull/8828) ([d005ea50](https://github.com/spinnaker/deck/commit/d005ea506316e413a8b044b902c58f8a772cedcb))  
fix(amazon/vpc): Fix error: "cannot setState on an unmounted component" by migrating to FC/useData [#8827](https://github.com/spinnaker/deck/pull/8827) ([34b68b8f](https://github.com/spinnaker/deck/commit/34b68b8f5a07e65fc343cfe75d06b72c6277dd59))  
feat(aws): Allow ICMPv6 type ingress rules for security groups [#8814](https://github.com/spinnaker/deck/pull/8814) ([cb30f14f](https://github.com/spinnaker/deck/commit/cb30f14f27678c4c8f945676525eec203656c011))  



## [0.0.284](https://www.github.com/spinnaker/deck/compare/eafec41cd60293e0de323dd0b48157e136a6ebec...2309866f40acdc6cdc36bbfe6a819591b2c20338) (2020-12-16)


### Changes

chore(amazon): publish amazon@0.0.284 ([2309866f](https://github.com/spinnaker/deck/commit/2309866f40acdc6cdc36bbfe6a819591b2c20338))  
refactor(REST): Prefer REST('/foo/bar') over REST().path('foo', 'bar') ([1d4320a0](https://github.com/spinnaker/deck/commit/1d4320a08f73093483cbb93784e9115c236b1f8a))  
refactor(api-deprecation): API is deprecated, switch to REST() ([97bfbf67](https://github.com/spinnaker/deck/commit/97bfbf67b5d359cc540918b62c99088ad82dfb1b))  
refactor(api-deprecation): Prefer API.path('foo', 'bar') over API.path('foo').path('bar') ([39b08e72](https://github.com/spinnaker/deck/commit/39b08e72b4baef1063a3ab9b65584e6e4e73d3e2))  
refactor(api-deprecation): Migrate from API.one/all/withParams/getList() to path/query/get() ([587db3ab](https://github.com/spinnaker/deck/commit/587db3ab20040fb5c72fe48feb36eccd7d1f297a))  
test(mock-http-client): Remove unnecessary API.baseUrl prefix in expectGET/etc calls ([807de9ad](https://github.com/spinnaker/deck/commit/807de9ada97154e3e8b699dece0f8d5ec4493942))  
test(mock-http-client): Remove no longer needed references to $httpBackend in unit tests after migration to MockHttpClient ([924e80be](https://github.com/spinnaker/deck/commit/924e80be96fcd9e72cae7381cb50b577fa2ca709))  
test(mock-http-client): Manually fix tests which didn't pass after auto-migrating using the eslint rule migrate-to-mock-http-client --fix ([e1238187](https://github.com/spinnaker/deck/commit/e1238187ffb851e2b2a6402004bc6a42e780ec9a))  
test(mock-http-client): Run eslint rule migrate-to-mock-http-client --fix ([ef5d8ea0](https://github.com/spinnaker/deck/commit/ef5d8ea0661d360661831b57d3ee8457aae0ecfd))  
feat(aws): Add scheme to load balancer details [#8797](https://github.com/spinnaker/deck/pull/8797) ([4f13518f](https://github.com/spinnaker/deck/commit/4f13518f69d883daaf94877e9387afeeecd6bbc5))  
Avoid raw "$http" usage [#8790](https://github.com/spinnaker/deck/pull/8790) ([969f4fe0](https://github.com/spinnaker/deck/commit/969f4fe0e9ab75eef2ceb0a2287643425293e209))  
fix(aws): Misc. fixes and new unit tests [#8742](https://github.com/spinnaker/deck/pull/8742) ([40ba12cf](https://github.com/spinnaker/deck/commit/40ba12cfb786972b417913f4dd1390d1653c2cd9))  



## [0.0.283](https://www.github.com/spinnaker/deck/compare/e0e6efbb172d93d48576157ee7555197862db7e4...eafec41cd60293e0de323dd0b48157e136a6ebec) (2020-12-04)


### Changes

chore(amazon): publish amazon@0.0.283 ([eafec41c](https://github.com/spinnaker/deck/commit/eafec41cd60293e0de323dd0b48157e136a6ebec))  
fix(amazon/instanceDetails): Fix typo [#8764](https://github.com/spinnaker/deck/pull/8764) ([a22f7252](https://github.com/spinnaker/deck/commit/a22f72525edb3195136b3df55bb78e499fe09dbc))  
feat(aws) added instance type options for EC2 [#8745](https://github.com/spinnaker/deck/pull/8745) ([5ae377ce](https://github.com/spinnaker/deck/commit/5ae377cedaa29df7b738db9840ef6ac72055415b))  



## [0.0.282](https://www.github.com/spinnaker/deck/compare/c9a72b53ea039021e89fa423459befee4c022e5a...e0e6efbb172d93d48576157ee7555197862db7e4) (2020-11-24)


### Changes

chore(amazon): publish amazon@0.0.282 ([e0e6efbb](https://github.com/spinnaker/deck/commit/e0e6efbb172d93d48576157ee7555197862db7e4))  
fix(titus): Disable edits of step scaling dimensions [#8747](https://github.com/spinnaker/deck/pull/8747) ([2ec9db04](https://github.com/spinnaker/deck/commit/2ec9db04ffc7e2b6c8b426e2ef13203600b29839))  
feat(aws): added the ability to modify CPU credits [#8736](https://github.com/spinnaker/deck/pull/8736) ([7a8f9394](https://github.com/spinnaker/deck/commit/7a8f9394354c4c5bb75d0bfd98feb2a76bad3a94))  



## [0.0.281](https://www.github.com/spinnaker/deck/compare/6f3be9a87c0aecf4594e7e1d76339def86cd8a8c...c9a72b53ea039021e89fa423459befee4c022e5a) (2020-11-18)


### Changes

chore(amazon): publish amazon@0.0.281 ([c9a72b53](https://github.com/spinnaker/deck/commit/c9a72b53ea039021e89fa423459befee4c022e5a))  
Titus target healthy percentage [#8734](https://github.com/spinnaker/deck/pull/8734) ([815a4864](https://github.com/spinnaker/deck/commit/815a4864d3e9acf866b9bc3220c3a282134b0fed))  



## [0.0.280](https://www.github.com/spinnaker/deck/compare/5ebef9c47f5dc1ba2d355f3217e373429010c3b4...6f3be9a87c0aecf4594e7e1d76339def86cd8a8c) (2020-11-12)


### Changes

chore(amazon): publish amazon@0.0.280 ([6f3be9a8](https://github.com/spinnaker/deck/commit/6f3be9a87c0aecf4594e7e1d76339def86cd8a8c))  
fix(amazon/targetGroups): Handle empty healthCheckPath [#8727](https://github.com/spinnaker/deck/pull/8727) ([5cd460e8](https://github.com/spinnaker/deck/commit/5cd460e824c477a8144c4cf6ddba53432f3a3a1e))  



## [0.0.279](https://www.github.com/spinnaker/deck/compare/fb051890d258d203403c821e107ce31f494a5672...5ebef9c47f5dc1ba2d355f3217e373429010c3b4) (2020-11-11)


### Changes

chore(amazon): publish amazon@0.0.279 ([5ebef9c4](https://github.com/spinnaker/deck/commit/5ebef9c47f5dc1ba2d355f3217e373429010c3b4))  
feat(aws): Automatically enable IPv6 in test clusters [#8716](https://github.com/spinnaker/deck/pull/8716) ([1bf2c95f](https://github.com/spinnaker/deck/commit/1bf2c95fa7409b7fdda8623fcb1cf37dab5e6456))  



## [0.0.278](https://www.github.com/spinnaker/deck/compare/ae19e391201487e7839f3a7ae028136c6ddccaa9...fb051890d258d203403c821e107ce31f494a5672) (2020-11-07)


### Changes

chore(amazon): publish amazon@0.0.278 ([fb051890](https://github.com/spinnaker/deck/commit/fb051890d258d203403c821e107ce31f494a5672))  
fix(amazon/securityGroup): Initialize all accounts while cloning security groups [#8710](https://github.com/spinnaker/deck/pull/8710) ([03194e73](https://github.com/spinnaker/deck/commit/03194e73aaaf3fb092ef47048f730360528574c6))  
refactor(core/instance): Create generic header for instance details panel [#8706](https://github.com/spinnaker/deck/pull/8706) ([37483823](https://github.com/spinnaker/deck/commit/37483823071dfbc76491b77004c5435479207cc6))  
refactor(core): Remove componentWillUpdate and componentWillMount methods ([d53136ad](https://github.com/spinnaker/deck/commit/d53136ad7b40fed858a17cbac878344ee10e904d))  



## [0.0.277](https://www.github.com/spinnaker/deck/compare/50539ab33378937cd1117765dcec0164da1ffa93...ae19e391201487e7839f3a7ae028136c6ddccaa9) (2020-11-03)


### Changes

chore(amazon): publish amazon@0.0.277 ([ae19e391](https://github.com/spinnaker/deck/commit/ae19e391201487e7839f3a7ae028136c6ddccaa9))  
refactor(aws/titus): Reactify instance insights [#8698](https://github.com/spinnaker/deck/pull/8698) ([deab22f3](https://github.com/spinnaker/deck/commit/deab22f34fe0cd82fc797a553732a148b72405b8))  



## [0.0.276](https://www.github.com/spinnaker/deck/compare/db3cffc9f1d8248ec595117f779cdc6f9182a9ed...50539ab33378937cd1117765dcec0164da1ffa93) (2020-10-28)


### Changes

chore(amazon): publish amazon@0.0.276 ([50539ab3](https://github.com/spinnaker/deck/commit/50539ab33378937cd1117765dcec0164da1ffa93))  
feat(aws): display capacity type (spot/ on-demand) in instance information [#8688](https://github.com/spinnaker/deck/pull/8688) ([308fb57a](https://github.com/spinnaker/deck/commit/308fb57a10406526337e35c5c0c0214fa29f2eca))  
fix(promiselike): Revert typeRoots tsconfig change, move types to src/types and add KLUDGE to expose them in the @spinnaker/core bundle ([a929d3fa](https://github.com/spinnaker/deck/commit/a929d3fa4db978aaf7b6d8ada12abc5b03403821))  
chore(PromiseLike): Migrate remaining IPromise typings to PromiseLike ([2c0d0f68](https://github.com/spinnaker/deck/commit/2c0d0f6814689d93820eab4e97e5d89f98a61cc5))  
feat(aws): Display CPU credit specification in Launch Template section [#8654](https://github.com/spinnaker/deck/pull/8654) ([d40cc6d0](https://github.com/spinnaker/deck/commit/d40cc6d0fe5a3ca9b441f379105c229388a1f7b3))  



## [0.0.275](https://www.github.com/spinnaker/deck/compare/47a8002877ce304ce51f928214e6b3b60820fc6c...db3cffc9f1d8248ec595117f779cdc6f9182a9ed) (2020-10-28)


### Changes

chore(amazon): publish amazon@0.0.275 ([db3cffc9](https://github.com/spinnaker/deck/commit/db3cffc9f1d8248ec595117f779cdc6f9182a9ed))  
chore(promiselike): Migrate more code away from angularjs IPromise to PromiseLike [#8687](https://github.com/spinnaker/deck/pull/8687) ([1df3daa8](https://github.com/spinnaker/deck/commit/1df3daa88209e885abb3d528edae4a942a060afb))  
refactor(aws/instance): Reactify instance tags and security groups [#8686](https://github.com/spinnaker/deck/pull/8686) ([d15ca2d5](https://github.com/spinnaker/deck/commit/d15ca2d56ef4bf924b089c73bb612d302b10fdd2))  
chore(titus/serverGroup): Migrate from $q.all({}) to $q.all([]) ([ab6cc5ed](https://github.com/spinnaker/deck/commit/ab6cc5edf7c0563d5a32c8a54d8a614f09ed315c))  
chore(titus/serverGroup): Migrate from $q.all({}) to $q.all([]) ([894caf73](https://github.com/spinnaker/deck/commit/894caf73e1131d3cd8dfae45a6551ff5bbb14475))  
chore(amazon/serverGroup): Migrate from $q.all({}) to $q.all([]) ([96df008b](https://github.com/spinnaker/deck/commit/96df008b5d2b5f187d07c002aa5e9b6a0c90e73c))  
chore(package): amazon@0.0.274 appengine@0.0.21 azure@0.0.259 cloudfoundry@0.0.105 core@0.0.522 docker@0.0.64 ecs@0.0.267 google@0.0.25 huaweicloud@0.0.7 kubernetes@0.0.53 oracle@0.0.14 tencentcloud@0.0.10 titus@0.0.149 [#8680](https://github.com/spinnaker/deck/pull/8680) ([47a80028](https://github.com/spinnaker/deck/commit/47a8002877ce304ce51f928214e6b3b60820fc6c))  
chore(modules): Reformat package.json with prettier [#8679](https://github.com/spinnaker/deck/pull/8679) ([0b1e2977](https://github.com/spinnaker/deck/commit/0b1e29778521da03673dc2aff083e490164ce616))  
Revert "chore(package): amazon@0.0.274 appengine@0.0.21 azure@0.0.259 cloudfoundry@0.0.105 core@0.0.522 docker@0.0.64 ecs@0.0.267 google@0.0.25 huaweicloud@0.0.7 kubernetes@0.0.53 oracle@0.0.14 tencentcloud@0.0.10 titus@0.0.149" [#8678](https://github.com/spinnaker/deck/pull/8678) ([a220af58](https://github.com/spinnaker/deck/commit/a220af588e194762757be534cce2d7ae9dc508d5))  



## [0.0.274](https://www.github.com/spinnaker/deck/compare/a220af588e194762757be534cce2d7ae9dc508d5...47a8002877ce304ce51f928214e6b3b60820fc6c) (2020-10-26)


### Changes

chore(package): amazon@0.0.274 appengine@0.0.21 azure@0.0.259 cloudfoundry@0.0.105 core@0.0.522 docker@0.0.64 ecs@0.0.267 google@0.0.25 huaweicloud@0.0.7 kubernetes@0.0.53 oracle@0.0.14 tencentcloud@0.0.10 titus@0.0.149 [#8680](https://github.com/spinnaker/deck/pull/8680) ([47a80028](https://github.com/spinnaker/deck/commit/47a8002877ce304ce51f928214e6b3b60820fc6c))  
chore(modules): Reformat package.json with prettier [#8679](https://github.com/spinnaker/deck/pull/8679) ([0b1e2977](https://github.com/spinnaker/deck/commit/0b1e29778521da03673dc2aff083e490164ce616))  
Revert "chore(package): amazon@0.0.274 appengine@0.0.21 azure@0.0.259 cloudfoundry@0.0.105 core@0.0.522 docker@0.0.64 ecs@0.0.267 google@0.0.25 huaweicloud@0.0.7 kubernetes@0.0.53 oracle@0.0.14 tencentcloud@0.0.10 titus@0.0.149" [#8678](https://github.com/spinnaker/deck/pull/8678) ([a220af58](https://github.com/spinnaker/deck/commit/a220af588e194762757be534cce2d7ae9dc508d5))  
chore(amazon): publish amazon@0.0.274 ([602021de](https://github.com/spinnaker/deck/commit/602021de4ebffc04a406d0ccd23290b548b9a9bb))  
chore(PromiseLike): Migrate code from IPromise types to PromiseLike ([d5ae512d](https://github.com/spinnaker/deck/commit/d5ae512d5025f16a99236307c2e8fb7c019c796d))  
chore(PromiseLike): Migrate code from IPromise types to PromiseLike ([553be66f](https://github.com/spinnaker/deck/commit/553be66f1c2757e0bb5ecfd595986697f245c041))  
feat(typescript): Add a new `app/types` typeRoot to all the tsconfig.json files providing `PromiseLike` and *.svg imports ([e622a534](https://github.com/spinnaker/deck/commit/e622a5348f614ee8615fab13082ac5f2fdd95960))  
chore(package): In packages, do not use webpack to typecheck [#8670](https://github.com/spinnaker/deck/pull/8670) ([8b3c134d](https://github.com/spinnaker/deck/commit/8b3c134d1ab82610611a194917cf5958047e1cc3))  
chore(package): use node_modules/.bin/* in module scripts, add 'build' script (the old 'lib' script) [#8668](https://github.com/spinnaker/deck/pull/8668) ([231f7818](https://github.com/spinnaker/deck/commit/231f7818895e7e2a12bb3591a2112559e07ee01d))  



## [0.0.273](https://www.github.com/spinnaker/deck/compare/602021de4ebffc04a406d0ccd23290b548b9a9bb...a220af588e194762757be534cce2d7ae9dc508d5) (2020-10-26)


### Changes

Revert "chore(package): amazon@0.0.274 appengine@0.0.21 azure@0.0.259 cloudfoundry@0.0.105 core@0.0.522 docker@0.0.64 ecs@0.0.267 google@0.0.25 huaweicloud@0.0.7 kubernetes@0.0.53 oracle@0.0.14 tencentcloud@0.0.10 titus@0.0.149" [#8678](https://github.com/spinnaker/deck/pull/8678) ([a220af58](https://github.com/spinnaker/deck/commit/a220af588e194762757be534cce2d7ae9dc508d5))  



## [0.0.274](https://www.github.com/spinnaker/deck/compare/ae9292ed1f1774d779a3c8ed41c5d97f76991097...602021de4ebffc04a406d0ccd23290b548b9a9bb) (2020-10-26)


### Changes

chore(package): amazon@0.0.274 appengine@0.0.21 azure@0.0.259 cloudfoundry@0.0.105 core@0.0.522 docker@0.0.64 ecs@0.0.267 google@0.0.25 huaweicloud@0.0.7 kubernetes@0.0.53 oracle@0.0.14 tencentcloud@0.0.10 titus@0.0.149 [#8680](https://github.com/spinnaker/deck/pull/8680) ([47a80028](https://github.com/spinnaker/deck/commit/47a8002877ce304ce51f928214e6b3b60820fc6c))  
chore(modules): Reformat package.json with prettier [#8679](https://github.com/spinnaker/deck/pull/8679) ([0b1e2977](https://github.com/spinnaker/deck/commit/0b1e29778521da03673dc2aff083e490164ce616))  
Revert "chore(package): amazon@0.0.274 appengine@0.0.21 azure@0.0.259 cloudfoundry@0.0.105 core@0.0.522 docker@0.0.64 ecs@0.0.267 google@0.0.25 huaweicloud@0.0.7 kubernetes@0.0.53 oracle@0.0.14 tencentcloud@0.0.10 titus@0.0.149" [#8678](https://github.com/spinnaker/deck/pull/8678) ([a220af58](https://github.com/spinnaker/deck/commit/a220af588e194762757be534cce2d7ae9dc508d5))  
chore(amazon): publish amazon@0.0.274 ([602021de](https://github.com/spinnaker/deck/commit/602021de4ebffc04a406d0ccd23290b548b9a9bb))  
chore(PromiseLike): Migrate code from IPromise types to PromiseLike ([d5ae512d](https://github.com/spinnaker/deck/commit/d5ae512d5025f16a99236307c2e8fb7c019c796d))  
chore(PromiseLike): Migrate code from IPromise types to PromiseLike ([553be66f](https://github.com/spinnaker/deck/commit/553be66f1c2757e0bb5ecfd595986697f245c041))  
feat(typescript): Add a new `app/types` typeRoot to all the tsconfig.json files providing `PromiseLike` and *.svg imports ([e622a534](https://github.com/spinnaker/deck/commit/e622a5348f614ee8615fab13082ac5f2fdd95960))  
chore(package): In packages, do not use webpack to typecheck [#8670](https://github.com/spinnaker/deck/pull/8670) ([8b3c134d](https://github.com/spinnaker/deck/commit/8b3c134d1ab82610611a194917cf5958047e1cc3))  
chore(package): use node_modules/.bin/* in module scripts, add 'build' script (the old 'lib' script) [#8668](https://github.com/spinnaker/deck/pull/8668) ([231f7818](https://github.com/spinnaker/deck/commit/231f7818895e7e2a12bb3591a2112559e07ee01d))  



## [0.0.273](https://www.github.com/spinnaker/deck/compare/4b79b4d75628bc74b91b72980f0a5e8ba479335e...ae9292ed1f1774d779a3c8ed41c5d97f76991097) (2020-10-14)


### Changes

Revert "chore(package): amazon@0.0.274 appengine@0.0.21 azure@0.0.259 cloudfoundry@0.0.105 core@0.0.522 docker@0.0.64 ecs@0.0.267 google@0.0.25 huaweicloud@0.0.7 kubernetes@0.0.53 oracle@0.0.14 tencentcloud@0.0.10 titus@0.0.149" [#8678](https://github.com/spinnaker/deck/pull/8678) ([a220af58](https://github.com/spinnaker/deck/commit/a220af588e194762757be534cce2d7ae9dc508d5))  
chore(amazon): publish amazon@0.0.274 ([602021de](https://github.com/spinnaker/deck/commit/602021de4ebffc04a406d0ccd23290b548b9a9bb))  
chore(PromiseLike): Migrate code from IPromise types to PromiseLike ([d5ae512d](https://github.com/spinnaker/deck/commit/d5ae512d5025f16a99236307c2e8fb7c019c796d))  
chore(PromiseLike): Migrate code from IPromise types to PromiseLike ([553be66f](https://github.com/spinnaker/deck/commit/553be66f1c2757e0bb5ecfd595986697f245c041))  
feat(typescript): Add a new `app/types` typeRoot to all the tsconfig.json files providing `PromiseLike` and *.svg imports ([e622a534](https://github.com/spinnaker/deck/commit/e622a5348f614ee8615fab13082ac5f2fdd95960))  
chore(package): In packages, do not use webpack to typecheck [#8670](https://github.com/spinnaker/deck/pull/8670) ([8b3c134d](https://github.com/spinnaker/deck/commit/8b3c134d1ab82610611a194917cf5958047e1cc3))  
chore(package): use node_modules/.bin/* in module scripts, add 'build' script (the old 'lib' script) [#8668](https://github.com/spinnaker/deck/pull/8668) ([231f7818](https://github.com/spinnaker/deck/commit/231f7818895e7e2a12bb3591a2112559e07ee01d))  
chore(package): amazon@0.0.273 core@0.0.520 oracle@0.0.13 [#8655](https://github.com/spinnaker/deck/pull/8655) ([ae9292ed](https://github.com/spinnaker/deck/commit/ae9292ed1f1774d779a3c8ed41c5d97f76991097))  
fix(aws): Support cross zone load balancing for NLB [#8557](https://github.com/spinnaker/deck/pull/8557) ([a7e8847d](https://github.com/spinnaker/deck/commit/a7e8847db53289a3dd8803523096ae4a91710184))  



## [0.0.272](https://www.github.com/spinnaker/deck/compare/f216cc6556bff90033b28bbe7e9f94517ddaa270...4b79b4d75628bc74b91b72980f0a5e8ba479335e) (2020-10-12)


### Changes

chore(package): amazon@0.0.272 appengine@0.0.20 azure@0.0.258 cloudfoundry@0.0.104 core@0.0.519 docker@0.0.63 ecs@0.0.266 google@0.0.24 huaweicloud@0.0.6 kubernetes@0.0.52 oracle@0.0.12 tencentcloud@0.0.9 titus@0.0.148 [#8647](https://github.com/spinnaker/deck/pull/8647) ([4b79b4d7](https://github.com/spinnaker/deck/commit/4b79b4d75628bc74b91b72980f0a5e8ba479335e))  
chore(prettier): Just Update Prettier™ [#8644](https://github.com/spinnaker/deck/pull/8644) ([8532bdd4](https://github.com/spinnaker/deck/commit/8532bdd4c08d59c38a0adde70ccac4f163c9dd97))  
 refactor(core): Component for instance actions dropdown [#8642](https://github.com/spinnaker/deck/pull/8642) ([24b1db6c](https://github.com/spinnaker/deck/commit/24b1db6ceb84d16afef74472c7e7bb6280e6e570))  
chore(lint): eslint --fix react2angular-with-error-boundary ([defaf19b](https://github.com/spinnaker/deck/commit/defaf19b5f11f8cce70e14fa1cdd52e88e6de0fd))  



## [0.0.271](https://www.github.com/spinnaker/deck/compare/5cdc7fa4494ada88702155bb91f80a0a16f3782e...f216cc6556bff90033b28bbe7e9f94517ddaa270) (2020-10-09)


### Changes

Package bump amazon 0.0.271 azure 0.0.257 cloudfoundry 0.0.103 core 0.0.518 docker 0.0.62 google 0.0.23 oracle 0.0.11 tencentcloud 0.0.8 titus 0.0.147 [#8640](https://github.com/spinnaker/deck/pull/8640) ([f216cc65](https://github.com/spinnaker/deck/commit/f216cc6556bff90033b28bbe7e9f94517ddaa270))  
fix(amazon/serverGroup): Additional null safety for networkInterfaces [#8638](https://github.com/spinnaker/deck/pull/8638) ([3fd31ffa](https://github.com/spinnaker/deck/commit/3fd31ffaa8589c166642fce38062bd6f284ab068))  
chore(eslint): eslint --fix api-no-slashes [#8631](https://github.com/spinnaker/deck/pull/8631) ([fab1a0ad](https://github.com/spinnaker/deck/commit/fab1a0ad75200cca60dfb74455d99f332e3e376f))  



## [0.0.270](https://www.github.com/spinnaker/deck/compare/8aa1e3e514703fcf0b4bf7b06dffafe01e9c27ed...5cdc7fa4494ada88702155bb91f80a0a16f3782e) (2020-10-06)


### Changes

chore(package): amazon@0.0.270 azure@0.0.256 cloudfoundry@0.0.102 core@0.0.517 docker@0.0.61 google@0.0.22 oracle@0.0.10 tencentcloud@0.0.7 [#8630](https://github.com/spinnaker/deck/pull/8630) ([5cdc7fa4](https://github.com/spinnaker/deck/commit/5cdc7fa4494ada88702155bb91f80a0a16f3782e))  
Revert "fix(appname): encodeURIComponent for app name (#8586)" [#8627](https://github.com/spinnaker/deck/pull/8627) ([885cd169](https://github.com/spinnaker/deck/commit/885cd169ad0dca8e7e6683bc96d2c131af6b3a1e))  



## [0.0.269](https://www.github.com/spinnaker/deck/compare/3d8f6268351065390bf3a42e7bfcf7cb398b236d...8aa1e3e514703fcf0b4bf7b06dffafe01e9c27ed) (2020-10-05)


### Changes

chore(package): amazon@0.0.269 azure@0.0.255 cloudfoundry@0.0.101 core@0.0.516 docker@0.0.60 google@0.0.21 oracle@0.0.9 tencentcloud@0.0.6 [#8624](https://github.com/spinnaker/deck/pull/8624) ([8aa1e3e5](https://github.com/spinnaker/deck/commit/8aa1e3e514703fcf0b4bf7b06dffafe01e9c27ed))  
fix(appname): encodeURIComponent for app name [#8586](https://github.com/spinnaker/deck/pull/8586) ([f1bb04e8](https://github.com/spinnaker/deck/commit/f1bb04e867e68e53f4e4edb22192afbcd9715d5d))  



## [0.0.268](https://www.github.com/spinnaker/deck/compare/2273dee2b3668ba105a34a062da669cdf7c207c5...3d8f6268351065390bf3a42e7bfcf7cb398b236d) (2020-09-25)


### Changes

chore(package): amazon@0.0.268 core@0.0.511 [#8597](https://github.com/spinnaker/deck/pull/8597) ([3d8f6268](https://github.com/spinnaker/deck/commit/3d8f6268351065390bf3a42e7bfcf7cb398b236d))  
fix(amazon/instance): Show instance type for amazon [#8592](https://github.com/spinnaker/deck/pull/8592) ([4a4285a7](https://github.com/spinnaker/deck/commit/4a4285a7ce28f07570a878e4917b7bb8b84d7569))  



## [0.0.267](https://www.github.com/spinnaker/deck/compare/0ced60a4961839cc5d878252f338981efd2d5f33...2273dee2b3668ba105a34a062da669cdf7c207c5) (2020-09-23)


### Changes

chore(package): amazon@0.0.267 appengine@0.0.18 azure@0.0.254 cloudfoundry@0.0.99 core@0.0.510 ecs@0.0.264 google@0.0.20 kubernetes@0.0.50 oracle@0.0.8 tencentcloud@0.0.5 titus@0.0.144 [#8591](https://github.com/spinnaker/deck/pull/8591) ([2273dee2](https://github.com/spinnaker/deck/commit/2273dee2b3668ba105a34a062da669cdf7c207c5))  
feat(core/*): Deck layout optimizations [#8556](https://github.com/spinnaker/deck/pull/8556) ([2588b7f3](https://github.com/spinnaker/deck/commit/2588b7f3e1ecbfd590e7cc87a225bbfd056449e3))  
fix(amazon/instance): Update language for IMDSv2 [#8589](https://github.com/spinnaker/deck/pull/8589) ([23a70397](https://github.com/spinnaker/deck/commit/23a703979131cfb55eeb7c05eb6a9e5b069f509b))  
refactor(amazon/titus): Reactify instance information [#8579](https://github.com/spinnaker/deck/pull/8579) ([969b404e](https://github.com/spinnaker/deck/commit/969b404e356208010a4fa9c0464b6c0a268db0a5))  



## [0.0.266](https://www.github.com/spinnaker/deck/compare/2f25b74759d27fe294eaf070059f1526a14494fd...0ced60a4961839cc5d878252f338981efd2d5f33) (2020-09-16)


### Changes

chore(package): amazon@0.0.266 core@0.0.507 [#8569](https://github.com/spinnaker/deck/pull/8569) ([0ced60a4](https://github.com/spinnaker/deck/commit/0ced60a4961839cc5d878252f338981efd2d5f33))  
fix(functions): change KMSKeyArn to match function cache data model [#8566](https://github.com/spinnaker/deck/pull/8566) ([1e6da635](https://github.com/spinnaker/deck/commit/1e6da6357891803443043c22bfafb5b18cf8dc33))  
fix(aws): expose baseLabel to Rosco-powered bake stages [#8559](https://github.com/spinnaker/deck/pull/8559) ([efee5f22](https://github.com/spinnaker/deck/commit/efee5f229206efad189e57cc9adf3303160c6d9a))  
fix(amazon/serverGroup): Enable launch templates for rolling push [#8563](https://github.com/spinnaker/deck/pull/8563) ([547780c9](https://github.com/spinnaker/deck/commit/547780c92df0f29ee3f766c281d07634ee003f55))  



## [0.0.265](https://www.github.com/spinnaker/deck/compare/9e9c4eb36f24d80255842a47548ed8390e2cb023...2f25b74759d27fe294eaf070059f1526a14494fd) (2020-09-14)


### Changes

chore(package): amazon@0.0.265 appengine@0.0.16 cloudfoundry@0.0.98 core@0.0.506 ecs@0.0.262 titus@0.0.142 [#8564](https://github.com/spinnaker/deck/pull/8564) ([2f25b747](https://github.com/spinnaker/deck/commit/2f25b74759d27fe294eaf070059f1526a14494fd))  
fix(amazon/serverGroup): Do not clone launch template userData [#8562](https://github.com/spinnaker/deck/pull/8562) ([03367a08](https://github.com/spinnaker/deck/commit/03367a08eacb947314e13e07ee9dcbd081db2f51))  
fix(aws/serverGroup): Optimize cloning logic [#8560](https://github.com/spinnaker/deck/pull/8560) ([fa8fa7f7](https://github.com/spinnaker/deck/commit/fa8fa7f7449a46f9e9243012ed61ec77f4203eb0))  
fix(amazon/alb): Update rule condition validation for edge case [#8544](https://github.com/spinnaker/deck/pull/8544) ([469a2d01](https://github.com/spinnaker/deck/commit/469a2d0169752cb8801dd742e3de2b744b346ca1))  



## [0.0.264](https://www.github.com/spinnaker/deck/compare/ced77a7453a0ffab5a14c38943288138fdcb084b...9e9c4eb36f24d80255842a47548ed8390e2cb023) (2020-09-03)


### Changes

chore(package): amazon@0.0.264 core@0.0.505 titus@0.0.141 [#8543](https://github.com/spinnaker/deck/pull/8543) ([9e9c4eb3](https://github.com/spinnaker/deck/commit/9e9c4eb36f24d80255842a47548ed8390e2cb023))  
feat(amazon/asg): Add help info and constraints for defaulting IMDSv2 [#8490](https://github.com/spinnaker/deck/pull/8490) ([95674d34](https://github.com/spinnaker/deck/commit/95674d34d0aceb8f73e4c44df805b182dc89107b))  
refactor(aws/titus): Refactor instance status section to react [#8497](https://github.com/spinnaker/deck/pull/8497) ([f358e5e8](https://github.com/spinnaker/deck/commit/f358e5e8f8615a47ba98ade8b465246054d0c917))  
fix(amazon/firewall): Ensure we use a valid default vpc instead of none [#8524](https://github.com/spinnaker/deck/pull/8524) ([0de610ed](https://github.com/spinnaker/deck/commit/0de610ed199168375752481199b15e345dc8d3d6))  



## [0.0.263](https://www.github.com/spinnaker/deck/compare/e8d1ff843841e325dcccd0faf853a58edea92366...ced77a7453a0ffab5a14c38943288138fdcb084b) (2020-08-25)


### Changes

chore(package): publish amazon 0.0.263 appengine 0.0.15 azure 0.0.253 cloudfoundry 0.0.97 core 0.0.504 docker 0.0.59 ecs 0.0.261 google 0.0.19 huaweicloud 0.0.5 kubernetes 0.0.48 oracle 0.0.7 tencentcloud 0.0.4 titus 0.0.140 [#8520](https://github.com/spinnaker/deck/pull/8520) ([ced77a74](https://github.com/spinnaker/deck/commit/ced77a7453a0ffab5a14c38943288138fdcb084b))  
chore(licenses): add license metadata to npm packages [#8512](https://github.com/spinnaker/deck/pull/8512) ([d4afa1bf](https://github.com/spinnaker/deck/commit/d4afa1bf2328cc91cf3195f810073b0b4726b3b5))  



## [0.0.262](https://www.github.com/spinnaker/deck/compare/aaa2c130cb267d68a0a2aab6424c735d74b2bbfa...e8d1ff843841e325dcccd0faf853a58edea92366) (2020-08-06)


### Changes

chore(package): publish amazon 0.0.262 core 0.0.499 [#8476](https://github.com/spinnaker/deck/pull/8476) ([e8d1ff84](https://github.com/spinnaker/deck/commit/e8d1ff843841e325dcccd0faf853a58edea92366))  
fix(functions): available lambda runtimes not up to date [#8472](https://github.com/spinnaker/deck/pull/8472) ([cf33dcbf](https://github.com/spinnaker/deck/commit/cf33dcbf3fe11da00a872f0cf7a81f6be2aaca62))  



## [0.0.261](https://www.github.com/spinnaker/deck/compare/dcb8ceff74e7013d20a85bd8ac747b453d40bfe5...aaa2c130cb267d68a0a2aab6424c735d74b2bbfa) (2020-08-05)


### Changes

chore(package): publish amazon 0.0.261 core 0.0.498 kubernetes 0.0.45 [#8471](https://github.com/spinnaker/deck/pull/8471) ([aaa2c130](https://github.com/spinnaker/deck/commit/aaa2c130cb267d68a0a2aab6424c735d74b2bbfa))  
feat(amazon/alb): Support for http-request-method rule conditions on ALBs [#8461](https://github.com/spinnaker/deck/pull/8461) ([d8ca7b2c](https://github.com/spinnaker/deck/commit/d8ca7b2c92177c0a9fc850784a5d4dbada644df8))  
feat(amazon): ASG support for features enabled by launch templates [#8326](https://github.com/spinnaker/deck/pull/8326) ([62e68787](https://github.com/spinnaker/deck/commit/62e6878756def592f1faad0c5db1b6aa62c3e20e))  



## [0.0.260](https://www.github.com/spinnaker/deck/compare/e47310cb08dd3eeb2ccbe2dbfc7cde7cad4fa22b...dcb8ceff74e7013d20a85bd8ac747b453d40bfe5) (2020-07-31)


### Changes

chore(package): publish amazon 0.0.260 appengine 0.0.14 core 0.0.497 kubernetes 0.0.44 titus 0.0.139 [#8457](https://github.com/spinnaker/deck/pull/8457) ([dcb8ceff](https://github.com/spinnaker/deck/commit/dcb8ceff74e7013d20a85bd8ac747b453d40bfe5))  
fix(amazon/rollback): Wait 500 ms before opening a second modal [#8451](https://github.com/spinnaker/deck/pull/8451) ([75f23284](https://github.com/spinnaker/deck/commit/75f23284bc35d86a14949261aae06dd5e413506b))  



## [0.0.259](https://www.github.com/spinnaker/deck/compare/b6e98d1fc71f66f2e2a5c03b6c6133de9c36b98b...e47310cb08dd3eeb2ccbe2dbfc7cde7cad4fa22b) (2020-07-28)


### Changes

chore(package): publish amazon 0.0.259 core 0.0.494 docker 0.0.57 titus 0.0.138 [#8440](https://github.com/spinnaker/deck/pull/8440) ([e47310cb](https://github.com/spinnaker/deck/commit/e47310cb08dd3eeb2ccbe2dbfc7cde7cad4fa22b))  



## [0.0.258](https://www.github.com/spinnaker/deck/compare/ad7adde60a94cafe67cbc5df50dc198fd7c20c24...b6e98d1fc71f66f2e2a5c03b6c6133de9c36b98b) (2020-07-28)


### Changes

chore(package): publish amazon 0.0.258 azure 0.0.252 core 0.0.493 titus 0.0.137 [#8439](https://github.com/spinnaker/deck/pull/8439) ([b6e98d1f](https://github.com/spinnaker/deck/commit/b6e98d1fc71f66f2e2a5c03b6c6133de9c36b98b))  
fix(amazon): Instance details is not displayed [#8416](https://github.com/spinnaker/deck/pull/8416) ([b1967d48](https://github.com/spinnaker/deck/commit/b1967d48641957deb6ede1354a1eef014ed3ed7d))  



## [0.0.257](https://www.github.com/spinnaker/deck/compare/638a6036d8a35c60121612c5c033e461626940a8...ad7adde60a94cafe67cbc5df50dc198fd7c20c24) (2020-07-21)


### Changes

chore(package): publish amazon 0.0.257 core 0.0.491 [#8422](https://github.com/spinnaker/deck/pull/8422) ([ad7adde6](https://github.com/spinnaker/deck/commit/ad7adde60a94cafe67cbc5df50dc198fd7c20c24))  
fix(amazon): ALB listener rules are not sorted properly [#8415](https://github.com/spinnaker/deck/pull/8415) ([5178de58](https://github.com/spinnaker/deck/commit/5178de58498ec75b47e057bbfb3194762f70e87d))  



## [0.0.256](https://www.github.com/spinnaker/deck/compare/6609deeebfec7441cb90bb091b19e71977333afc...638a6036d8a35c60121612c5c033e461626940a8) (2020-06-17)


### Changes

chore(package): publish amazon 0.0.256 core 0.0.480 tencentcloud 0.0.3 titus 0.0.136 [#8355](https://github.com/spinnaker/deck/pull/8355) ([638a6036](https://github.com/spinnaker/deck/commit/638a6036d8a35c60121612c5c033e461626940a8))  
fix(*): Use allowlist and denylist [#8351](https://github.com/spinnaker/deck/pull/8351) ([579560d5](https://github.com/spinnaker/deck/commit/579560d50fe6811c8525e5f287c79e12928d1b46))  
Revert "fix(aws/titus): Server group names should be lower case (#8332)" [#8335](https://github.com/spinnaker/deck/pull/8335) ([a7cd1a5f](https://github.com/spinnaker/deck/commit/a7cd1a5f23eda4e63ab57c6c671fedb3ed252691))  
fix(aws/titus): Server group names should be lower case [#8332](https://github.com/spinnaker/deck/pull/8332) ([c0789da9](https://github.com/spinnaker/deck/commit/c0789da9e0b0f43719684bb1bf9dfbfb917a31bf))  



## [0.0.255](https://www.github.com/spinnaker/deck/compare/c881a42c3516e4b69594f8055df2d3d7995292af...6609deeebfec7441cb90bb091b19e71977333afc) (2020-05-29)


### Changes

chore(package): publish amazon 0.0.255 core 0.0.479 [#8315](https://github.com/spinnaker/deck/pull/8315) ([6609deee](https://github.com/spinnaker/deck/commit/6609deeebfec7441cb90bb091b19e71977333afc))  
feat(aws): Add IPv6 and IMDSv2 to aws settings [#8306](https://github.com/spinnaker/deck/pull/8306) ([4de4aa14](https://github.com/spinnaker/deck/commit/4de4aa14deb2ee18c93ccd37df05563589f0aea6))  
fix(amazon): Include custom component form state for validation [#8302](https://github.com/spinnaker/deck/pull/8302) ([770bd6de](https://github.com/spinnaker/deck/commit/770bd6de89b18ce2e98782a7f5ca8ed5ddcc3722))  



## [0.0.254](https://www.github.com/spinnaker/deck/compare/9113047e6647a0bd8bafd7cdb20125fa91492cb5...c881a42c3516e4b69594f8055df2d3d7995292af) (2020-05-18)


### Changes

chore(package): publish amazon 0.0.254 appengine 0.0.12 core 0.0.477 ecs 0.0.258 google 0.0.14 kubernetes 0.0.40 [#8294](https://github.com/spinnaker/deck/pull/8294) ([c881a42c](https://github.com/spinnaker/deck/commit/c881a42c3516e4b69594f8055df2d3d7995292af))  
fix(bake): adding skip region detection checkbox [#8277](https://github.com/spinnaker/deck/pull/8277) ([200198fe](https://github.com/spinnaker/deck/commit/200198fec1928ba5867949a31cde18e1ea3e1e82))  
refactor(core): legacy artifacts cleanup [#8273](https://github.com/spinnaker/deck/pull/8273) ([f4d41551](https://github.com/spinnaker/deck/commit/f4d415518dd553263ca63f4641ff19facef79464))  



## [0.0.253](https://www.github.com/spinnaker/deck/compare/55855252f72d540ddef6ca6c7e2f2222a89ef376...9113047e6647a0bd8bafd7cdb20125fa91492cb5) (2020-05-11)


### Changes

chore(package): publish amazon 0.0.253 core 0.0.475 kubernetes 0.0.39 [#8270](https://github.com/spinnaker/deck/pull/8270) ([9113047e](https://github.com/spinnaker/deck/commit/9113047e6647a0bd8bafd7cdb20125fa91492cb5))  
feat(amazon): Filter SG's that are in exclusion list [#8266](https://github.com/spinnaker/deck/pull/8266) ([703fceed](https://github.com/spinnaker/deck/commit/703fceedf8e0cb2a6bed3396decbf7b110edc690))  
feat(amazon): Launch Template details section [#8265](https://github.com/spinnaker/deck/pull/8265) ([80f0c8ef](https://github.com/spinnaker/deck/commit/80f0c8ef9a7db09f7175865cf7c74938b2133483))  



## [0.0.252](https://www.github.com/spinnaker/deck/compare/f227aa8ec00fffe63e39abc75b9c504180804623...55855252f72d540ddef6ca6c7e2f2222a89ef376) (2020-05-04)


### Changes

chore(package): publish amazon 0.0.252 core 0.0.473 ecs 0.0.257 kubernetes 0.0.38 [#8247](https://github.com/spinnaker/deck/pull/8247) ([55855252](https://github.com/spinnaker/deck/commit/55855252f72d540ddef6ca6c7e2f2222a89ef376))  
fix(amazon/loadbalancer): change default deregistration delay to 300 [#8130](https://github.com/spinnaker/deck/pull/8130) ([4e8332a3](https://github.com/spinnaker/deck/commit/4e8332a3f9575058969bcfd8350e43dc9bf2e6f6))  
feat(aws/domain): Introduce launch templates to aws domain [#8225](https://github.com/spinnaker/deck/pull/8225) ([19dde3d0](https://github.com/spinnaker/deck/commit/19dde3d06fb32fedf12b91279683924605561639))  
fix(amazon/validator): remove the invalid "_" from the regex [#8211](https://github.com/spinnaker/deck/pull/8211) ([12c60d88](https://github.com/spinnaker/deck/commit/12c60d88a287a6de6690444106e3c21b9d3d5de5))  



## [0.0.251](https://www.github.com/spinnaker/deck/compare/87938bdcf53ef098fc9d6fcae0139322c5b2371a...f227aa8ec00fffe63e39abc75b9c504180804623) (2020-04-21)


### Changes

chore(package): publish amazon 0.0.251 appengine 0.0.11 azure 0.0.251 cloudfoundry 0.0.96 core 0.0.472 docker 0.0.56 ecs 0.0.256 google 0.0.13 huaweicloud 0.0.4 kubernetes 0.0.37 oracle 0.0.6 titus 0.0.135 [#8196](https://github.com/spinnaker/deck/pull/8196) ([f227aa8e](https://github.com/spinnaker/deck/commit/f227aa8ec00fffe63e39abc75b9c504180804623))  
feat(plugins): Consolidate typescript config (partially).  Do not strip comments. [#8180](https://github.com/spinnaker/deck/pull/8180) ([4434d99c](https://github.com/spinnaker/deck/commit/4434d99c4b61704c5e53f356ff9f3b31d715e593))  



## [0.0.250](https://www.github.com/spinnaker/deck/compare/93c2f284dd421b0e8b8a686fa7e61631b5139b60...87938bdcf53ef098fc9d6fcae0139322c5b2371a) (2020-04-06)


### Changes

chore(package): publish amazon 0.0.250 core 0.0.470 titus 0.0.134 [#8135](https://github.com/spinnaker/deck/pull/8135) ([87938bdc](https://github.com/spinnaker/deck/commit/87938bdcf53ef098fc9d6fcae0139322c5b2371a))  
fix(aws/loadBalancers): Add validator for multiple listeners on same port [#8127](https://github.com/spinnaker/deck/pull/8127) ([17dcc95f](https://github.com/spinnaker/deck/commit/17dcc95faa47bf98594f4dfd247b01f2dea3cf91))  



## [0.0.249](https://www.github.com/spinnaker/deck/compare/a615c0ae0b37182cf2d43ac721ef097a01a6fc1f...93c2f284dd421b0e8b8a686fa7e61631b5139b60) (2020-03-31)


### Changes

chore(package): publish amazon 0.0.249 core 0.0.468 ecs 0.0.254 google 0.0.12 kubernetes 0.0.36 [#8113](https://github.com/spinnaker/deck/pull/8113) ([93c2f284](https://github.com/spinnaker/deck/commit/93c2f284dd421b0e8b8a686fa7e61631b5139b60))  
refactor(amazon/details): Use details field from core and remove amazon specific implementation [#8100](https://github.com/spinnaker/deck/pull/8100) ([86868011](https://github.com/spinnaker/deck/commit/86868011f1e4e79987dba254babbf5d7b6529ece))  



## [0.0.248](https://www.github.com/spinnaker/deck/compare/185865b12d2040fd0c61fa2bbb103afb6f028114...a615c0ae0b37182cf2d43ac721ef097a01a6fc1f) (2020-03-27)


### Changes

chore(bump): Bump amazon to 0.0.248 [#8104](https://github.com/spinnaker/deck/pull/8104) ([a615c0ae](https://github.com/spinnaker/deck/commit/a615c0ae0b37182cf2d43ac721ef097a01a6fc1f))  
fix(amazon,ecs,titus): Match targetGroup by name and account [#8098](https://github.com/spinnaker/deck/pull/8098) ([43c8075e](https://github.com/spinnaker/deck/commit/43c8075e07564f747fe75f0d72dd440e8c766112))  



## [0.0.247](https://www.github.com/spinnaker/deck/compare/90ed419d314fa19c936444873d9a37a4875a48ef...185865b12d2040fd0c61fa2bbb103afb6f028114) (2020-03-24)


### Changes

chore(package): publish amazon 0.0.247 core 0.0.464 [#8084](https://github.com/spinnaker/deck/pull/8084) ([185865b1](https://github.com/spinnaker/deck/commit/185865b12d2040fd0c61fa2bbb103afb6f028114))  
feat(amazon/details): Pass application to details field [#8068](https://github.com/spinnaker/deck/pull/8068) ([611416b3](https://github.com/spinnaker/deck/commit/611416b3f1d4dc0d1fff5dd07201860a683f5408))  



## [0.0.246](https://www.github.com/spinnaker/deck/compare/664d174a790d16c8df06d227d0b26dfbd9a759a4...90ed419d314fa19c936444873d9a37a4875a48ef) (2020-03-19)


### Changes

chore(titus/amazon): Bump to amazon@0.0.246, titus@0.0.131 [#8062](https://github.com/spinnaker/deck/pull/8062) ([90ed419d](https://github.com/spinnaker/deck/commit/90ed419d314fa19c936444873d9a37a4875a48ef))  
fix(titus/instance): Temporary workaround to get IPv6 for Titus instances [#8054](https://github.com/spinnaker/deck/pull/8054) ([c933e898](https://github.com/spinnaker/deck/commit/c933e8984d0de9f74b04d8746ff2ee723ea0fc0a))  
refactor(svg): add SVGR loader for inlined react SVG support [#8055](https://github.com/spinnaker/deck/pull/8055) ([15e47a68](https://github.com/spinnaker/deck/commit/15e47a680a49f048860cd4a5a0688df16a0ce874))  



## [0.0.245](https://www.github.com/spinnaker/deck/compare/55a4c2f314433dc30454e1df44ff4e6f26d39780...664d174a790d16c8df06d227d0b26dfbd9a759a4) (2020-03-12)


### Changes

chore(package): publish amazon 0.0.245 appengine 0.0.9 azure 0.0.249 cloudfoundry 0.0.94 core 0.0.459 ecs 0.0.252 google 0.0.10 huaweicloud 0.0.2 kubernetes 0.0.33 oracle 0.0.4 [#8035](https://github.com/spinnaker/deck/pull/8035) ([664d174a](https://github.com/spinnaker/deck/commit/664d174a790d16c8df06d227d0b26dfbd9a759a4))  
feat(amazon/serverGroupDetails): Make server group's details field overridable [#8029](https://github.com/spinnaker/deck/pull/8029) ([afddbc25](https://github.com/spinnaker/deck/commit/afddbc2516635757cb483b00cb113767405713f2))  



## [0.0.244](https://www.github.com/spinnaker/deck/compare/27a5fc681f7759715c576f03ec349404e2219034...55a4c2f314433dc30454e1df44ff4e6f26d39780) (2020-03-11)


### Changes

chore(package): publish amazon 0.0.244 core 0.0.458 [#8027](https://github.com/spinnaker/deck/pull/8027) ([55a4c2f3](https://github.com/spinnaker/deck/commit/55a4c2f314433dc30454e1df44ff4e6f26d39780))  
fix(amazon/iamRole): Do not default iamRole in serverGroup/deploy edit scenario [#7940](https://github.com/spinnaker/deck/pull/7940) ([778b0754](https://github.com/spinnaker/deck/commit/778b0754a8f84dc53284eb1819c08dd73a1aa274))  
chore(lint): Fix all linter violations for importing from own subpackage alias ([ae375b14](https://github.com/spinnaker/deck/commit/ae375b145db815b12bd3daa1835d1107b5fd750a))  
fix(core): Explain characters are invalid in hostname [#8024](https://github.com/spinnaker/deck/pull/8024) ([479844da](https://github.com/spinnaker/deck/commit/479844daef76b564fdd1d10122e5539dbfc02dd7))  



## [0.0.243](https://www.github.com/spinnaker/deck/compare/c77c64e27e553d84c4c2707eafc0a1c1cb146728...27a5fc681f7759715c576f03ec349404e2219034) (2020-03-09)


### Changes

chore(package): publish amazon 0.0.243 core 0.0.456 [#8015](https://github.com/spinnaker/deck/pull/8015) ([27a5fc68](https://github.com/spinnaker/deck/commit/27a5fc681f7759715c576f03ec349404e2219034))  
fix(core/managed): support new group/name@version syntax for kind field [#8014](https://github.com/spinnaker/deck/pull/8014) ([405ea827](https://github.com/spinnaker/deck/commit/405ea82700fa2086277e64e1af5e9230ed5582d4))  



## [0.0.242](https://www.github.com/spinnaker/deck/compare/3c443035f75860f036be6056b8efcca2de8f9343...c77c64e27e553d84c4c2707eafc0a1c1cb146728) (2020-03-05)


### Changes

chore(bump): Amazon package bump to 0.0.242 [#8009](https://github.com/spinnaker/deck/pull/8009) ([c77c64e2](https://github.com/spinnaker/deck/commit/c77c64e27e553d84c4c2707eafc0a1c1cb146728))  
fix(amazon): Export securityGroup interfaces [#8008](https://github.com/spinnaker/deck/pull/8008) ([483f35bf](https://github.com/spinnaker/deck/commit/483f35bff80dc6695ada28937021560840e212c1))  



## [0.0.241](https://www.github.com/spinnaker/deck/compare/e72eacb6dc4989c0da1963049292bc367138e763...3c443035f75860f036be6056b8efcca2de8f9343) (2020-03-05)


### Changes

chore(bump): Bump Amazon to 0.0.241 [#8005](https://github.com/spinnaker/deck/pull/8005) ([3c443035](https://github.com/spinnaker/deck/commit/3c443035f75860f036be6056b8efcca2de8f9343))  
fix(aws): Export security group interfaces [#8001](https://github.com/spinnaker/deck/pull/8001) ([6c84ad16](https://github.com/spinnaker/deck/commit/6c84ad164daad0d38c68a465cd5473b77585188f))  
fix (amazon/target group): Remove duplicate form fields for target group health [#7998](https://github.com/spinnaker/deck/pull/7998) ([804a17db](https://github.com/spinnaker/deck/commit/804a17db29e0b936a60f0c08c131f6bd98ba8cc0))  
fix(amazon): Add brackets to IPv6 url [#7980](https://github.com/spinnaker/deck/pull/7980) ([0fd07079](https://github.com/spinnaker/deck/commit/0fd07079e8205c09bf83a49df71da3382f654908))  
fix(core): Fix missing maxRemainingAsgs [#7967](https://github.com/spinnaker/deck/pull/7967) ([f1dbb92d](https://github.com/spinnaker/deck/commit/f1dbb92de47bff9cd7532124cfb999d480eb4914))  
feat(mocks): Move test mocks to their own npm package [#7942](https://github.com/spinnaker/deck/pull/7942) ([055e430f](https://github.com/spinnaker/deck/commit/055e430f7ba7ffeea9402f8544859aa03bed5f5b))  



## [0.0.240](https://www.github.com/spinnaker/deck/compare/03dddf858cde3249cbe7ec670b147a1ac737f533...e72eacb6dc4989c0da1963049292bc367138e763) (2020-02-27)


### Changes

chore(amazon): Bump to version 0.0.240 [#7973](https://github.com/spinnaker/deck/pull/7973) ([e72eacb6](https://github.com/spinnaker/deck/commit/e72eacb6dc4989c0da1963049292bc367138e763))  
fix(aws): Add description to IPRangeRule interface [#7970](https://github.com/spinnaker/deck/pull/7970) ([fae22952](https://github.com/spinnaker/deck/commit/fae229528f227397461e5366b063e8344c89cb97))  
refactor(core/presentation): Switch the default value of formik fastField from true to false [#7968](https://github.com/spinnaker/deck/pull/7968) ([77512ecf](https://github.com/spinnaker/deck/commit/77512ecf86abab70556a2e6083a028a8a8072b5f))  
fix(aws): Support security group ip range rule description [#7951](https://github.com/spinnaker/deck/pull/7951) ([6f3665ea](https://github.com/spinnaker/deck/commit/6f3665eace69ac21db1a7f33caca967b60bf266c))  



## [0.0.239](https://www.github.com/spinnaker/deck/compare/147f1a86fd7993017013f480b81b0505a260a2e3...03dddf858cde3249cbe7ec670b147a1ac737f533) (2020-02-18)


### Changes

chore(amazon): Bump to 0.0.239 [#7911](https://github.com/spinnaker/deck/pull/7911) ([03dddf85](https://github.com/spinnaker/deck/commit/03dddf858cde3249cbe7ec670b147a1ac737f533))  
fix(amazon/loadBalancer): Fix the default dereg delay value in help text [#7894](https://github.com/spinnaker/deck/pull/7894) ([ef881d9a](https://github.com/spinnaker/deck/commit/ef881d9a53a1dbe47cf6ee7080c9b5e0c1d28bf4))  
fix(amazon): Preserve policyNames in CLB descriptions [#7897](https://github.com/spinnaker/deck/pull/7897) ([086fed31](https://github.com/spinnaker/deck/commit/086fed31b3a0351bc71205df3c401460a29698de))  
fix(amazon/instance): fix npe when applying health to instances [#7893](https://github.com/spinnaker/deck/pull/7893) ([3ddefaf4](https://github.com/spinnaker/deck/commit/3ddefaf47045edd12953672f1b39bc39861b7412))  
fix(amazon): Add a warning when no oidc conifgs are found [#7883](https://github.com/spinnaker/deck/pull/7883) ([55193883](https://github.com/spinnaker/deck/commit/55193883b167aedecc132842a9a80aa4c926b19d))  
fix(amazon): Clarify NLB healthcheck threshold requirements [#7890](https://github.com/spinnaker/deck/pull/7890) ([43c6d5dc](https://github.com/spinnaker/deck/commit/43c6d5dcafddd3c984ff4fde163255384b90e936))  



## [0.0.238](https://www.github.com/spinnaker/deck/compare/67528e7cc77fdbaab230dfa03d90749292855008...147f1a86fd7993017013f480b81b0505a260a2e3) (2020-02-13)


### Changes

chore(bump): Bump amazon to v.238 [#7888](https://github.com/spinnaker/deck/pull/7888) ([147f1a86](https://github.com/spinnaker/deck/commit/147f1a86fd7993017013f480b81b0505a260a2e3))  



## [0.0.237](https://www.github.com/spinnaker/deck/compare/c8fb7afab15ac2adc6810c4c69e5dae0a9a22201...67528e7cc77fdbaab230dfa03d90749292855008) (2020-02-12)


### Changes

chore(amazon): Bump amazon to 0.0.237 ([67528e7c](https://github.com/spinnaker/deck/commit/67528e7cc77fdbaab230dfa03d90749292855008))  
fix(aws): Fix to set right state while cloning security group ([68f25dc0](https://github.com/spinnaker/deck/commit/68f25dc051b4ece9a963799b863c8dec34579876))  



## [0.0.236](https://www.github.com/spinnaker/deck/compare/8bf7e339e896522e78b4d7e34d9b1cbe8f614d05...c8fb7afab15ac2adc6810c4c69e5dae0a9a22201) (2020-02-08)


### Changes

chore(package): bump core, amazon, docker, titus [#7856](https://github.com/spinnaker/deck/pull/7856) ([c8fb7afa](https://github.com/spinnaker/deck/commit/c8fb7afab15ac2adc6810c4c69e5dae0a9a22201))  



## [0.0.235](https://www.github.com/spinnaker/deck/compare/759894e7a7cb0b1518d52d9127003e1b54e743da...8bf7e339e896522e78b4d7e34d9b1cbe8f614d05) (2020-02-08)


### Changes

chore(package): bump core to 447, amazon to 235, docker to 51, titus to 127 [#7851](https://github.com/spinnaker/deck/pull/7851) ([8bf7e339](https://github.com/spinnaker/deck/commit/8bf7e339e896522e78b4d7e34d9b1cbe8f614d05))  
fix(packages): Preserve webpackIgnore comments when bundling for npm packages [#7850](https://github.com/spinnaker/deck/pull/7850) ([8b84eedb](https://github.com/spinnaker/deck/commit/8b84eedb2f2130fab2d261935de81a2157b2b00e))  



## [0.0.234](https://www.github.com/spinnaker/deck/compare/f6601a3ee571a8fa162baf44d7cc11106f9c702e...759894e7a7cb0b1518d52d9127003e1b54e743da) (2020-02-07)


### Changes

chore(amazon): Bump amazon to 0.0.234 [#7849](https://github.com/spinnaker/deck/pull/7849) ([759894e7](https://github.com/spinnaker/deck/commit/759894e7a7cb0b1518d52d9127003e1b54e743da))  
react(core): Introduce LinkWithClipboard component [#7847](https://github.com/spinnaker/deck/pull/7847) ([1ddc70ad](https://github.com/spinnaker/deck/commit/1ddc70ad3c2853edcd367e7efaaed0730c2f926c))  
feat(amazon): Expose addresses for IPv6 instances [#7817](https://github.com/spinnaker/deck/pull/7817) ([1b605358](https://github.com/spinnaker/deck/commit/1b60535891163f7d8544da921c26216be1b65689))  



## [0.0.233](https://www.github.com/spinnaker/deck/compare/ee608d6125127f51933c191b8f946cb7e37ee522...f6601a3ee571a8fa162baf44d7cc11106f9c702e) (2020-02-06)


### Changes

chore(package): bump amazon to 233 [#7844](https://github.com/spinnaker/deck/pull/7844) ([f6601a3e](https://github.com/spinnaker/deck/commit/f6601a3ee571a8fa162baf44d7cc11106f9c702e))  
fix(amazon): parse healthCheckPort as integer before using it [#7830](https://github.com/spinnaker/deck/pull/7830) ([654c373d](https://github.com/spinnaker/deck/commit/654c373d798293eff6b1de05233236559daafa9e))  
fix(core/confirmationModal): Supply the 'source' location when canceling [#7816](https://github.com/spinnaker/deck/pull/7816) ([411fda5e](https://github.com/spinnaker/deck/commit/411fda5eba15363a792a5dc36873246d6a0f870c))  
tests(core): Introduce entity mocks into deck [#7781](https://github.com/spinnaker/deck/pull/7781) ([b632e29b](https://github.com/spinnaker/deck/commit/b632e29b9c5624d43df4b3a312fc9fb2432d6aa1))  
fix(amazon): Prevent error if target group is undefined [#7819](https://github.com/spinnaker/deck/pull/7819) ([607a6c46](https://github.com/spinnaker/deck/commit/607a6c46519d4079e37afc6238a57bfc33a33c54))  
chore(lint): Run eslint on typescript files ([b51dce46](https://github.com/spinnaker/deck/commit/b51dce46be3df14070f06e06de874108dcf23569))  
chore(lint): Run eslint on javascript files ([38a6324a](https://github.com/spinnaker/deck/commit/38a6324aa9f116c70c7644113f5f84214fd95679))  
feat(deck): Add execution error details to Task Status in Cloud foundry Stage [#7814](https://github.com/spinnaker/deck/pull/7814) ([e15b7964](https://github.com/spinnaker/deck/commit/e15b7964ac7c3025cf4567dbaf803fbf61d28d6b))  



## [0.0.232](https://www.github.com/spinnaker/deck/compare/6864f8b1239d45dc039986158d00d0233dd6424b...ee608d6125127f51933c191b8f946cb7e37ee522) (2020-01-23)


### Changes

chore(aws): Bump aws to version v0.0.232 ([ee608d61](https://github.com/spinnaker/deck/commit/ee608d6125127f51933c191b8f946cb7e37ee522))  
fix(aws): Component for additional security group details  [#7803](https://github.com/spinnaker/deck/pull/7803) ([35fcebbc](https://github.com/spinnaker/deck/commit/35fcebbcdd2c6dea2397a90a48db975293096054))  
fix(aws): Fixing cross-app security group lookup [#7795](https://github.com/spinnaker/deck/pull/7795) ([ddb17907](https://github.com/spinnaker/deck/commit/ddb17907c879ca08bb643746ddae5e7d80165ae3))  
fix(aws): Icmp port range should default to a port in range [#7784](https://github.com/spinnaker/deck/pull/7784) ([7b7e0f58](https://github.com/spinnaker/deck/commit/7b7e0f584ded4e515b8b835e2035b3a4823774a6))  
chore(core): convert colors to stylesheet variables [#7783](https://github.com/spinnaker/deck/pull/7783) ([1b4d8746](https://github.com/spinnaker/deck/commit/1b4d87466c0ae1524178635fd1d8a4d8ec5fe2c5))  
feat(aws/cfn): Cloudformation ChangeSet execution [#7671](https://github.com/spinnaker/deck/pull/7671) ([73838f7b](https://github.com/spinnaker/deck/commit/73838f7b453df77ca466df8344844a414bb37ec2))  



## [0.0.231](https://www.github.com/spinnaker/deck/compare/d6f5c25eea46c645bd928b711bf25836f3f244d2...6864f8b1239d45dc039986158d00d0233dd6424b) (2020-01-09)


### Changes

chore(amazon): bump package to 0.0.231 [#7762](https://github.com/spinnaker/deck/pull/7762) ([6864f8b1](https://github.com/spinnaker/deck/commit/6864f8b1239d45dc039986158d00d0233dd6424b))  
refactor(*): de-angularize confirmationModalService [#7759](https://github.com/spinnaker/deck/pull/7759) ([e6c6c662](https://github.com/spinnaker/deck/commit/e6c6c662b5326fcb184772c99f2212ce4336a1cb))  
feat(core/amazon/titus): do not allow create/clone in managed clusters [#7754](https://github.com/spinnaker/deck/pull/7754) ([4302a0fc](https://github.com/spinnaker/deck/commit/4302a0fc90d2b1679dee204d15e59d3e53b3d0a0))  



## [0.0.230](https://www.github.com/spinnaker/deck/compare/d5e5586e9f2ad27bc8f41ca9da298ea020afbb97...d6f5c25eea46c645bd928b711bf25836f3f244d2) (2020-01-08)


### Changes

chore(amazon): bump package to 0.0.230 [#7752](https://github.com/spinnaker/deck/pull/7752) ([d6f5c25e](https://github.com/spinnaker/deck/commit/d6f5c25eea46c645bd928b711bf25836f3f244d2))  
feat(core/amazon/titus): restrict menu items on managed resources [#7750](https://github.com/spinnaker/deck/pull/7750) ([ff87bda7](https://github.com/spinnaker/deck/commit/ff87bda72eed677b6e1b792bc5fae346ca459336))  



## [0.0.229](https://www.github.com/spinnaker/deck/compare/cf826d542ae070aa86d2055a9bc51932d2a1698a...d5e5586e9f2ad27bc8f41ca9da298ea020afbb97) (2020-01-08)


### Changes

chore(amazon): bump package to 0.0.229 [#7747](https://github.com/spinnaker/deck/pull/7747) ([d5e5586e](https://github.com/spinnaker/deck/commit/d5e5586e9f2ad27bc8f41ca9da298ea020afbb97))  
feat(core): offer to pause managed resources before performing actions [#7728](https://github.com/spinnaker/deck/pull/7728) ([edacd084](https://github.com/spinnaker/deck/commit/edacd08419e97e7f265538c3015c6e2789bf238b))  
refactor(*): use consistent styles on modal headers ([10b34915](https://github.com/spinnaker/deck/commit/10b34915860ed46f21d0179bf87c3b456de49c56))  
feat(core): add pause/resume to managed resource menu ([9560304a](https://github.com/spinnaker/deck/commit/9560304a063e7afb3af6deca7c64c4684baf78f3))  
Apply suggestions from code review ([c76ca2f1](https://github.com/spinnaker/deck/commit/c76ca2f152d843958c8092437537e8055396c05a))  
refactor(*): favor optional chaining over lodash.get ([dc2b3d74](https://github.com/spinnaker/deck/commit/dc2b3d7419c79159a89ad346bd64e2f4cc9fde75))  
refactor(core): convert confirmation modal to react ([a59b2c32](https://github.com/spinnaker/deck/commit/a59b2c3264500080fad7caeb05054eef6f51d52c))  
refactor(core/amazon): remove unused CSS rules [#7732](https://github.com/spinnaker/deck/pull/7732) ([abf39e70](https://github.com/spinnaker/deck/commit/abf39e70e72c3231da29d4c81966aa47ad15fd87))  
refactor(core): provide wrapper for dangerously setting html [#7721](https://github.com/spinnaker/deck/pull/7721) ([65488728](https://github.com/spinnaker/deck/commit/65488728e4ef08c2034123a88a9a4b96cb0e4bd9))  
fix(ecs): Fixes AmazonLoadBalancerChoiceModal for ecs [#7741](https://github.com/spinnaker/deck/pull/7741) ([1bb3a271](https://github.com/spinnaker/deck/commit/1bb3a2712fe551c530d21c880de5ef0bd313f99b))  



## [0.0.228](https://www.github.com/spinnaker/deck/compare/497a69fc0148bfa7e5f27279a7c2e9976f6559f0...cf826d542ae070aa86d2055a9bc51932d2a1698a) (2020-01-02)


### Changes

Bump Amazon version to 0.0.228 ([cf826d54](https://github.com/spinnaker/deck/commit/cf826d542ae070aa86d2055a9bc51932d2a1698a))  
fix(amazon): Fix additionalIpRules component imports [#7737](https://github.com/spinnaker/deck/pull/7737) ([48124094](https://github.com/spinnaker/deck/commit/48124094413d72eb3fe82d74fbcff41f23c8e35c))  



## [0.0.227](https://www.github.com/spinnaker/deck/compare/34c46b615c1a944616c6e1e62cf16a5aa3cf729e...497a69fc0148bfa7e5f27279a7c2e9976f6559f0) (2020-01-02)


### Changes

chore(amazon): Bump amazon version to 0.0.227 [#7735](https://github.com/spinnaker/deck/pull/7735) ([497a69fc](https://github.com/spinnaker/deck/commit/497a69fc0148bfa7e5f27279a7c2e9976f6559f0))  
feat(aws/additionalIpRules): Add a component for aws security group a… [#7726](https://github.com/spinnaker/deck/pull/7726) ([225046c1](https://github.com/spinnaker/deck/commit/225046c14943b4c5e57e05effb7c073fedecaaa5))  



## [0.0.226](https://www.github.com/spinnaker/deck/compare/ce8f7956c3f8d1ed4459397991f9b3b94a823fb8...34c46b615c1a944616c6e1e62cf16a5aa3cf729e) (2019-12-19)


### Changes

chore(amazon): Bump amazon to 0.0.226 [#7727](https://github.com/spinnaker/deck/pull/7727) ([34c46b61](https://github.com/spinnaker/deck/commit/34c46b615c1a944616c6e1e62cf16a5aa3cf729e))  
fix(awsIngressHelpText): Add help text for cross account ingress rules [#7720](https://github.com/spinnaker/deck/pull/7720) ([82efc2fe](https://github.com/spinnaker/deck/commit/82efc2fe5cdd443873af9a705e773e5a8fd1b422))  
refactor(core): remove unused parameter options from confirmation modal [#7716](https://github.com/spinnaker/deck/pull/7716) ([d2838d80](https://github.com/spinnaker/deck/commit/d2838d80c7f14989368fc490a2d842b2d4952a42))  
Merge branch 'master' into nobuObjects ([854c194c](https://github.com/spinnaker/deck/commit/854c194c11e3a21203af4a351ccb1d148a34cd58))  
fix eslint issues ([89faa7dd](https://github.com/spinnaker/deck/commit/89faa7ddad97029d92c7d68db6946f6e6cb86f77))  
minor fixup ([8e7ab282](https://github.com/spinnaker/deck/commit/8e7ab282df7c902fa52b2b26583017f400bccd52))  
fixup ([13b73220](https://github.com/spinnaker/deck/commit/13b7322072ecd171fe9d540115a423b67c2b6066))  
feat(aws/iprules): Create react component for IPRule details ([fdc42a84](https://github.com/spinnaker/deck/commit/fdc42a843893affc37f48c487362e14dd7d75356))  
chore(core): upgrade to latest prettier [#7713](https://github.com/spinnaker/deck/pull/7713) ([6291f858](https://github.com/spinnaker/deck/commit/6291f858cb111d9c65affeb82ddd840f05c57b65))  
chore(amazon): Remove unused AmazonTemplates [#7707](https://github.com/spinnaker/deck/pull/7707) ([6bc82003](https://github.com/spinnaker/deck/commit/6bc820039aff4dd7d1d2c7d092aa04dd0f0c6b6d))  
refactor(eslint): Fix all '@spinnaker/ng-no-component-class' eslint rule violations ([8c42d8ec](https://github.com/spinnaker/deck/commit/8c42d8ec5e5f2f30c2924ab39edfa5f60c973182))  
refactor(eslint): Fix all 'prefer-const' eslint rule violations ([90aa4775](https://github.com/spinnaker/deck/commit/90aa47754bc8815eb1bdfcceb4d05c9e1cdf325f))  
refactor(eslint): Fix all 'one-var' eslint rule violations ([d070bd45](https://github.com/spinnaker/deck/commit/d070bd45ff3e185999e863e3f48c01f63eb45733))  
refactor(eslint): Fix all 'no-var' eslint rule violations ([17487016](https://github.com/spinnaker/deck/commit/174870161a5a09ab7f15c74cb84d0f3e196cd7cb))  
chore(eslint): remove tslint ([9400826b](https://github.com/spinnaker/deck/commit/9400826bcb119cf7681e1ce37092b9fdd8b76b1b))  
fix(imports): move imports out of describe() blocks ([8c241f2d](https://github.com/spinnaker/deck/commit/8c241f2d957a3e37439833a7ca70af79d8107dc1))  
refactor(angularjs): use ES6 to import angular - migrate from `const angular = require('angular')` to `import * as angular from 'angular'` - Where possible, migrate from `import angular from 'angular'; angular.module('asdf')`   to `import { module } from 'angular'; module('asdf')` ([88b8f4ae](https://github.com/spinnaker/deck/commit/88b8f4ae0b9e96ac8d8dbdeff592f3787f0617cb))  
refactor(angularjs): use ES6 imports for angularjs module deps - migrate from `require('@uirouter/angularjs').default` to import UIROUTER_ANGULARJS from '@uirouter/angularjs' - migrate from `require('angular-ui-bootstrap')` to import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap' ([a076dc12](https://github.com/spinnaker/deck/commit/a076dc1280b56affcd30cdbea68a84fb7d5ba3f1))  
refactor(angularjs): Import angularjs module dependencies by name - Migrate angularjs module dependencies to import the exported string identifier, not via require('module').name ([ac1c86eb](https://github.com/spinnaker/deck/commit/ac1c86ebbc72e6d2d83eb57d6710c6ae2651ecc0))  
refactor(angularjs): Always export the ng module name, not the module itself ([784d64b6](https://github.com/spinnaker/deck/commit/784d64b66a6410e622803b4b0519f7050e9c5f82))  
refactor(core): convert security groups views to React [#7676](https://github.com/spinnaker/deck/pull/7676) ([2b6a9411](https://github.com/spinnaker/deck/commit/2b6a9411bfe3d5fd54d815065fe9d99625747241))  
chore(typescript): Migrate most wildcard imports to javascript style imports - Migrate from "import * as foo from 'foo'" to "import foo from 'foo'" ([b6aabe18](https://github.com/spinnaker/deck/commit/b6aabe18a2c71f194087c01fd15ec369460f5e70))  
feat(typescript): enable allowJs and allowSyntheticDefaultImports ([7ef58b6c](https://github.com/spinnaker/deck/commit/7ef58b6c122f9ce91eab95d5f444622a710ff968))  
fix(titus): Remove default metricName [#7680](https://github.com/spinnaker/deck/pull/7680) ([d855a69b](https://github.com/spinnaker/deck/commit/d855a69bdb5629ca360bf14fdc2d592e4ecbb820))  
chore(typescript): update to typescript 3.7.x [#7668](https://github.com/spinnaker/deck/pull/7668) ([145f540d](https://github.com/spinnaker/deck/commit/145f540d8bab6936a6d5bfb5caf4e1cba426f215))  
refactor(*): Remove exports-loader from n3-chart import ([f0613c1b](https://github.com/spinnaker/deck/commit/f0613c1b1648f0c2ea54623cb535a6d54bea2355))  
fix(amazon/instance): don't blow up on standalone instances [#7666](https://github.com/spinnaker/deck/pull/7666) ([fc0c9d5e](https://github.com/spinnaker/deck/commit/fc0c9d5edb7a074c4beae2173f6b0f74cf62dd3e))  
fix(lint): Fixes for no-useless-escape linter rule ([9c23975e](https://github.com/spinnaker/deck/commit/9c23975e4fac47c5393119bb005353274af6148f))  
fix(angularJS): Fix all remaining non-strict angularJS DI code via @spinnaker/strictdi linter rule ([c233af0e](https://github.com/spinnaker/deck/commit/c233af0e4ab2268ab1835177ecf85122aa47e7e6))  
chore(react-select): Update react-select type modules [#7660](https://github.com/spinnaker/deck/pull/7660) ([f7edf54d](https://github.com/spinnaker/deck/commit/f7edf54dab52e377907d0ae827d840a62528de57))  
chore(tsconfig): standardize all tsconfig.json files to es2017 [#7656](https://github.com/spinnaker/deck/pull/7656) ([c1c4d423](https://github.com/spinnaker/deck/commit/c1c4d423a0af57c6a8faf135a7a7ee3eb76d5466))  
fix(amazon): Update load balancer validations to match user expectations [#7584](https://github.com/spinnaker/deck/pull/7584) ([0561762b](https://github.com/spinnaker/deck/commit/0561762b9cd8131b75f5befb255637deeec3f400))  



## [0.0.225](https://www.github.com/spinnaker/deck/compare/4b31104a8422ec5eb300480202ba160beeec249c...ce8f7956c3f8d1ed4459397991f9b3b94a823fb8) (2019-11-22)


### Changes

chore(amazon): Bump version to 0.0.225 [#7653](https://github.com/spinnaker/deck/pull/7653) ([ce8f7956](https://github.com/spinnaker/deck/commit/ce8f7956c3f8d1ed4459397991f9b3b94a823fb8))  
fix(amazon): Removing negative lookbehind [#7651](https://github.com/spinnaker/deck/pull/7651) ([981fc2dd](https://github.com/spinnaker/deck/commit/981fc2ddc580cf80b1a2efd3e79a962ec80bca8b))  



## [0.0.224](https://www.github.com/spinnaker/deck/compare/2f0817838ba0fa451df9fd74def118dbc2004f47...4b31104a8422ec5eb300480202ba160beeec249c) (2019-11-20)


### Changes

chore(amazon): Bump version to 0.0.224 [#7646](https://github.com/spinnaker/deck/pull/7646) ([4b31104a](https://github.com/spinnaker/deck/commit/4b31104a8422ec5eb300480202ba160beeec249c))  
fix(amazon): Copy capacity from current server group does not persist [#7644](https://github.com/spinnaker/deck/pull/7644) ([91456040](https://github.com/spinnaker/deck/commit/91456040f866a63350dfa6fcf6483ac4a38993bc))  
feat(aws): Allow filtering cross account ingress [#7645](https://github.com/spinnaker/deck/pull/7645) ([68f2fa88](https://github.com/spinnaker/deck/commit/68f2fa88e0016d76158127c28d284559d5d1ca2d))  
feat(provider/aws): Lambda function target support for AWS ALB [#7630](https://github.com/spinnaker/deck/pull/7630) ([5c645d9e](https://github.com/spinnaker/deck/commit/5c645d9ed90e88d562b2c6fc778e9d2785e432a4))  
feat(provider/aws): Function create/update/delete feature. [#7586](https://github.com/spinnaker/deck/pull/7586) ([956b43bc](https://github.com/spinnaker/deck/commit/956b43bc05e1bbe471c60cacb994d460af6f74df))  



## [0.0.223](https://www.github.com/spinnaker/deck/compare/4ad76ec46682473d644e4d20d0e7daec3a790bee...2f0817838ba0fa451df9fd74def118dbc2004f47) (2019-11-11)


### Changes

Bump package core to 0.0.430 and amazon to 0.0.223 [#7625](https://github.com/spinnaker/deck/pull/7625) ([2f081783](https://github.com/spinnaker/deck/commit/2f0817838ba0fa451df9fd74def118dbc2004f47))  
feat(amazon/instance): add configurable exclusion rules for families + categories [#7623](https://github.com/spinnaker/deck/pull/7623) ([3e655b9f](https://github.com/spinnaker/deck/commit/3e655b9f087c18154b59cfdcea97fe68d7c8564f))  



## [0.0.222](https://www.github.com/spinnaker/deck/compare/fe9f8b36da081a09e46cc6ede64915905ae0bb9f...4ad76ec46682473d644e4d20d0e7daec3a790bee) (2019-10-30)


### Changes

chore(amazon): Bump version to 0.0.222 ([4ad76ec4](https://github.com/spinnaker/deck/commit/4ad76ec46682473d644e4d20d0e7daec3a790bee))  
feat(managed): Update resource indicators to use new data source ([34a3b00b](https://github.com/spinnaker/deck/commit/34a3b00beea0287a68a05014862351c0f5904afa))  
feat(dataSources): widen + parameterize types, add default values ([4ed015a0](https://github.com/spinnaker/deck/commit/4ed015a07c028eb58807601a0b0fb9783b02b0d9))  
fix(core): Ensure default port used for target group healthcheck link [#7576](https://github.com/spinnaker/deck/pull/7576) ([57c7d6d0](https://github.com/spinnaker/deck/commit/57c7d6d0f9df0a4d10d9fbbeb350c130de283830))  



## [0.0.221](https://www.github.com/spinnaker/deck/compare/97ed5192c2530e4942c8432dd28f5db1a693fa5e...fe9f8b36da081a09e46cc6ede64915905ae0bb9f) (2019-10-28)


### Changes

chore(amazon): Bump version to 0.0.221 ([fe9f8b36](https://github.com/spinnaker/deck/commit/fe9f8b36da081a09e46cc6ede64915905ae0bb9f))  
fix(rosco): Re-evaluate roscoSelector on stage updates [#7577](https://github.com/spinnaker/deck/pull/7577) ([d620e057](https://github.com/spinnaker/deck/commit/d620e057e1c0d85984caa9a8ff0639d17ea9309c))  
feat(rosco): Allow optional roscoDetailUrl for roscoMode bakes [#7575](https://github.com/spinnaker/deck/pull/7575) ([dae00c87](https://github.com/spinnaker/deck/commit/dae00c87adc5e69ac232a2145d35e73072cf5766))  



## [0.0.220](https://www.github.com/spinnaker/deck/compare/e9b9d9672d6170bcb09b79c9f8efa49c946b407b...97ed5192c2530e4942c8432dd28f5db1a693fa5e) (2019-10-28)


### Changes

chore(amazon): Bump version to 0.0.220 ([97ed5192](https://github.com/spinnaker/deck/commit/97ed5192c2530e4942c8432dd28f5db1a693fa5e))  
feat(rosco): Allow roscoMode per stage/execution [#7564](https://github.com/spinnaker/deck/pull/7564) ([c2bbf20d](https://github.com/spinnaker/deck/commit/c2bbf20d83d2c82aa1442379b98d4ed71a3379a2))  
feat(provider/aws): Functions (listing and searching) [#7568](https://github.com/spinnaker/deck/pull/7568) ([ca176fc3](https://github.com/spinnaker/deck/commit/ca176fc325edc41ee90d22b2c3d1aaa041a9c434))  
Revert "feat(provider/aws): Functions (listing and searching) (#7536)" [#7567](https://github.com/spinnaker/deck/pull/7567) ([e49ffaf4](https://github.com/spinnaker/deck/commit/e49ffaf4d5896294cf66300167aefdecbf36499c))  
feat(provider/aws): Functions (listing and searching) [#7536](https://github.com/spinnaker/deck/pull/7536) ([86a365bd](https://github.com/spinnaker/deck/commit/86a365bd406125498c1bbc45de2ee4d67f9fd0d5))  



## [0.0.219](https://www.github.com/spinnaker/deck/compare/cc8c9bf8e5a3363c0792b404e9565acae597721d...e9b9d9672d6170bcb09b79c9f8efa49c946b407b) (2019-10-24)


### Changes

chore(amazon): Bump version to 0.0.219 ([e9b9d967](https://github.com/spinnaker/deck/commit/e9b9d9672d6170bcb09b79c9f8efa49c946b407b))  
fix(config): Fix typings for SpinnakerSettings [#7556](https://github.com/spinnaker/deck/pull/7556) ([230ffb5f](https://github.com/spinnaker/deck/commit/230ffb5f65a128a21475855c675c917c41cb90a3))  
feat(provider/aws): Add capabilities in cloudformation deploy stage [#7544](https://github.com/spinnaker/deck/pull/7544) ([7e742dc0](https://github.com/spinnaker/deck/commit/7e742dc03a0379b34cba639ff266895fedcc555d))  



## [0.0.218](https://www.github.com/spinnaker/deck/compare/c65721eeeff7aac0b48153b5db4d125f28fa8df2...cc8c9bf8e5a3363c0792b404e9565acae597721d) (2019-10-23)


### Changes

Bump package core to 0.0.425 and amazon to 0.0.218 [#7551](https://github.com/spinnaker/deck/pull/7551) ([cc8c9bf8](https://github.com/spinnaker/deck/commit/cc8c9bf8e5a3363c0792b404e9565acae597721d))  
feat(provider/aws): Show load balancer warning based on settings [#7542](https://github.com/spinnaker/deck/pull/7542) ([938d219b](https://github.com/spinnaker/deck/commit/938d219ba64301b184a34598d28f90081c3c1547))  
fix(awslb): Preventing edits against orphaned load balancers [#7547](https://github.com/spinnaker/deck/pull/7547) ([828f7e70](https://github.com/spinnaker/deck/commit/828f7e70834bd9417a448d2e9d8a604094de9cde))  
refactor(aws): move Resize item in the AmazonServerGroupAction dropdown into separate component ([6186e404](https://github.com/spinnaker/deck/commit/6186e404b90c035cfc28793d416c59621f17d694))  



## [0.0.217](https://www.github.com/spinnaker/deck/compare/469d659ae89c56d2943ff08981f96de4b7bd4e67...c65721eeeff7aac0b48153b5db4d125f28fa8df2) (2019-10-16)


### Changes

chore(amazon): Bump version to 0.0.217 ([c65721ee](https://github.com/spinnaker/deck/commit/c65721eeeff7aac0b48153b5db4d125f28fa8df2))  
feat(ui): Show health check url beside target group [#7520](https://github.com/spinnaker/deck/pull/7520) ([ed7c4458](https://github.com/spinnaker/deck/commit/ed7c44589cfbf482047a0f47d30df97165053aa4))  



## [0.0.216](https://www.github.com/spinnaker/deck/compare/d2dfa6025337b27bde87de1e8b3fe7d7a37bcf7a...469d659ae89c56d2943ff08981f96de4b7bd4e67) (2019-10-11)


### Changes

chore(amazon): Bump version to 0.0.216 ([469d659a](https://github.com/spinnaker/deck/commit/469d659ae89c56d2943ff08981f96de4b7bd4e67))  
feat(provider/aws): Add roleARN to the deploy cloudformation stage [#7492](https://github.com/spinnaker/deck/pull/7492) ([74e829d0](https://github.com/spinnaker/deck/commit/74e829d0d3d0de32fd2d9e624a5ee88dbbe96e28))  



## [0.0.215](https://www.github.com/spinnaker/deck/compare/907eff56beb97cb43f99c6d9f81479e6b91cd3ae...d2dfa6025337b27bde87de1e8b3fe7d7a37bcf7a) (2019-10-11)


### Changes

chore(amazon): Bump version to 0.0.215 ([d2dfa602](https://github.com/spinnaker/deck/commit/d2dfa6025337b27bde87de1e8b3fe7d7a37bcf7a))  
fix(ui): Type field missing for CLB detail view [#7504](https://github.com/spinnaker/deck/pull/7504) ([b382a227](https://github.com/spinnaker/deck/commit/b382a227bbbd9a892414a6bad63c1014076a4838))  
fix(amazon/loadBalancer): Disable CLB deletion if instances exist [#7509](https://github.com/spinnaker/deck/pull/7509) ([e4e3c46a](https://github.com/spinnaker/deck/commit/e4e3c46a33818ae48ccf9646a820d7dd606c3e27))  



## [0.0.214](https://www.github.com/spinnaker/deck/compare/9954e0f2708e5af8d04d36346f883c928f11050b...907eff56beb97cb43f99c6d9f81479e6b91cd3ae) (2019-10-07)


### Changes

chore(amazon): Bump version to 0.0.214 ([907eff56](https://github.com/spinnaker/deck/commit/907eff56beb97cb43f99c6d9f81479e6b91cd3ae))  
feat(amazon/serverGroup): add AmazonMQ CloudWatch namespace [#7489](https://github.com/spinnaker/deck/pull/7489) ([980581a9](https://github.com/spinnaker/deck/commit/980581a91cc8bc0982822f53b77f6f719475f05d))  
feat(core/presentation): Migrate ValidationMessage to new CSS styles [#7481](https://github.com/spinnaker/deck/pull/7481) ([3c08b388](https://github.com/spinnaker/deck/commit/3c08b388e6a00d4c9d14ad9babeb857e99d3d0e2))  
fix(amazon/pipeline): sort list of available bake regions [#7472](https://github.com/spinnaker/deck/pull/7472) ([f957d429](https://github.com/spinnaker/deck/commit/f957d42950ee63c9024d238e4e6a34a7759363ac))  



## [0.0.213](https://www.github.com/spinnaker/deck/compare/62e969dc75a3dee80cb3e523397fedfa0f27ade2...9954e0f2708e5af8d04d36346f883c928f11050b) (2019-10-01)


### Changes

chore(amazon): Bump version to 0.0.213 ([9954e0f2](https://github.com/spinnaker/deck/commit/9954e0f2708e5af8d04d36346f883c928f11050b))  
feat(monitored deploy): add basic monitored deploy UI [#7426](https://github.com/spinnaker/deck/pull/7426) ([b55a49a7](https://github.com/spinnaker/deck/commit/b55a49a7337ff9db8f435be7437877adc1184b79))  
feat(aws): Add copy-to-clipboard button to copy instance id [#7388](https://github.com/spinnaker/deck/pull/7388) ([cfe9731f](https://github.com/spinnaker/deck/commit/cfe9731f5a4977c12ee5f3e5d3f63263d943934c))  



## [0.0.212](https://www.github.com/spinnaker/deck/compare/31e17deafefa4ab7bc32122ace149b0760394bae...62e969dc75a3dee80cb3e523397fedfa0f27ade2) (2019-09-16)


### Changes

chore(amazon): Bump version to 0.0.212 ([62e969dc](https://github.com/spinnaker/deck/commit/62e969dc75a3dee80cb3e523397fedfa0f27ade2))  
fix(amazon): Fix compatibility when cloudProviders missing [#7410](https://github.com/spinnaker/deck/pull/7410) ([2821438c](https://github.com/spinnaker/deck/commit/2821438ce34f1676620bad6f66b13feec7b95f07))  



## [0.0.211](https://www.github.com/spinnaker/deck/compare/f2457613cb4963af8017ce4882f0219955ba2be4...31e17deafefa4ab7bc32122ace149b0760394bae) (2019-09-12)


### Changes

chore(amazon): Bump version to 0.0.211 ([31e17dea](https://github.com/spinnaker/deck/commit/31e17deafefa4ab7bc32122ace149b0760394bae))  
refactor(titus): Adding load balancer incompatibility [#7386](https://github.com/spinnaker/deck/pull/7386) ([7d65ea9e](https://github.com/spinnaker/deck/commit/7d65ea9ee7f9856ae4670f1e5462c7cb94e45890))  



## [0.0.210](https://www.github.com/spinnaker/deck/compare/192130eedebf0a482b0430e85607d0f31ee5a582...f2457613cb4963af8017ce4882f0219955ba2be4) (2019-09-05)


### Changes

chore(amazon): Bump version to 0.0.210 [#7380](https://github.com/spinnaker/deck/pull/7380) ([f2457613](https://github.com/spinnaker/deck/commit/f2457613cb4963af8017ce4882f0219955ba2be4))  
refactor(amazon): choices prop is actually optional [#7379](https://github.com/spinnaker/deck/pull/7379) ([c93f7e82](https://github.com/spinnaker/deck/commit/c93f7e82327e1eabc1b0ff3bb644dddefd692999))  



## [0.0.209](https://www.github.com/spinnaker/deck/compare/251b160139e4009f329f622ad31b3a98c364bb96...192130eedebf0a482b0430e85607d0f31ee5a582) (2019-09-05)


### Changes

Bump package core to 0.0.406 and amazon to 0.0.209 [#7378](https://github.com/spinnaker/deck/pull/7378) ([192130ee](https://github.com/spinnaker/deck/commit/192130eedebf0a482b0430e85607d0f31ee5a582))  
fix(titus): Removing NLB as it is not compatible with Titus [#7317](https://github.com/spinnaker/deck/pull/7317) ([373f581e](https://github.com/spinnaker/deck/commit/373f581e0689a0e1d3873c7b4cc6948fafadcf8e))  



## [0.0.208](https://www.github.com/spinnaker/deck/compare/c169bba9ed80645b7aae90dc9cc4a58e7b0fcd48...251b160139e4009f329f622ad31b3a98c364bb96) (2019-08-13)


### Changes

Bump package amazon to 0.0.208 and titus to 0.0.108 [#7321](https://github.com/spinnaker/deck/pull/7321) ([251b1601](https://github.com/spinnaker/deck/commit/251b160139e4009f329f622ad31b3a98c364bb96))  
refactor(core/presentation): use render props everywhere [#7316](https://github.com/spinnaker/deck/pull/7316) ([40c31972](https://github.com/spinnaker/deck/commit/40c31972129a1c74f5087fb5a8a7a181b2144c33))  



## [0.0.207](https://www.github.com/spinnaker/deck/compare/05a2448a2b385d69434c584261d939dfcc2e9e92...c169bba9ed80645b7aae90dc9cc4a58e7b0fcd48) (2019-08-07)


### Changes

chore(amazon): Bump version to 0.0.207 ([c169bba9](https://github.com/spinnaker/deck/commit/c169bba9ed80645b7aae90dc9cc4a58e7b0fcd48))  
feat(cloudformation): Make the template editor more lenient [#7290](https://github.com/spinnaker/deck/pull/7290) ([298c0125](https://github.com/spinnaker/deck/commit/298c012583e09706b47b0f8ed1df2b98a4f16176))  
feat(*/pipeline): Remove the concept of default stage timeouts, rename option [#7286](https://github.com/spinnaker/deck/pull/7286) ([abac63ce](https://github.com/spinnaker/deck/commit/abac63ce5c88b809fcf5ed1509136fe96489a051))  



## [0.0.206](https://www.github.com/spinnaker/deck/compare/b6f71aaf469d55ca984a7ff3bb95a518d3baffd3...05a2448a2b385d69434c584261d939dfcc2e9e92) (2019-07-26)


### Changes

chore(amazon): Bump version to 0.0.206 ([05a2448a](https://github.com/spinnaker/deck/commit/05a2448a2b385d69434c584261d939dfcc2e9e92))  
feat(core/presentation): Add a <Formik/> wrapper which applies fixes and opinions that we want in Spinnaker [#7272](https://github.com/spinnaker/deck/pull/7272) ([9c7885f6](https://github.com/spinnaker/deck/commit/9c7885f6e6645f7028db7ea3102edd59f2b67e76))  



## [0.0.205](https://www.github.com/spinnaker/deck/compare/f93747f2e307c26b8bb8065fef5458dd56a9c06a...b6f71aaf469d55ca984a7ff3bb95a518d3baffd3) (2019-07-19)


### Changes

chore(amazon): Bump version to 0.0.205 ([b6f71aaf](https://github.com/spinnaker/deck/commit/b6f71aaf469d55ca984a7ff3bb95a518d3baffd3))  
feat(aws): Support new artifact model for deploy cloudformation [#7180](https://github.com/spinnaker/deck/pull/7180) ([d3249c9e](https://github.com/spinnaker/deck/commit/d3249c9e2173cfd3652f6fb8a95c2fb5f2367984))  



## [0.0.204](https://www.github.com/spinnaker/deck/compare/8c0d18ac1fe20e02af1aa3f8a6ccce52fcdd4f1e...f93747f2e307c26b8bb8065fef5458dd56a9c06a) (2019-07-15)


### Changes

chore(amazon): Bump version to 0.0.204 ([f93747f2](https://github.com/spinnaker/deck/commit/f93747f2e307c26b8bb8065fef5458dd56a9c06a))  
fix(amazon): Update default internalPort for CLBs to 80 [#7220](https://github.com/spinnaker/deck/pull/7220) ([b2c0cf03](https://github.com/spinnaker/deck/commit/b2c0cf0314c2843691b301f4bc9be919934ebc75))  



## [0.0.203](https://www.github.com/spinnaker/deck/compare/fba3c4a3c11a68b6064f5ddd95722344ab2974b1...8c0d18ac1fe20e02af1aa3f8a6ccce52fcdd4f1e) (2019-07-11)


### Changes

Bump package core to 0.0.391 and amazon to 0.0.203 [#7218](https://github.com/spinnaker/deck/pull/7218) ([8c0d18ac](https://github.com/spinnaker/deck/commit/8c0d18ac1fe20e02af1aa3f8a6ccce52fcdd4f1e))  
feat({core,amazon}/pipeline): add support per-OS AWS VM type choices [#7187](https://github.com/spinnaker/deck/pull/7187) ([68ee670d](https://github.com/spinnaker/deck/commit/68ee670d5993c9649bd9cc1c6b312512132dcdac))  



## [0.0.202](https://www.github.com/spinnaker/deck/compare/6dd149b650db9f3bccd1866dffe02dfe76f68007...fba3c4a3c11a68b6064f5ddd95722344ab2974b1) (2019-07-09)


### Changes

Bump package core to 0.0.390 and amazon to 0.0.202 and titus to 0.0.104 [#7207](https://github.com/spinnaker/deck/pull/7207) ([fba3c4a3](https://github.com/spinnaker/deck/commit/fba3c4a3c11a68b6064f5ddd95722344ab2974b1))  
refactor(amazon): allow custom CLB config when ejecting from a wizard [#7206](https://github.com/spinnaker/deck/pull/7206) ([bf681187](https://github.com/spinnaker/deck/commit/bf68118738ed4dcfd2f8c9e61f8247bcccba2918))  



## [0.0.201](https://www.github.com/spinnaker/deck/compare/012f3ccc1091c44bb80b50427e839dc4cec831a8...6dd149b650db9f3bccd1866dffe02dfe76f68007) (2019-07-09)


### Changes

Bump package core to 0.0.388 and amazon to 0.0.201 [#7200](https://github.com/spinnaker/deck/pull/7200) ([6dd149b6](https://github.com/spinnaker/deck/commit/6dd149b650db9f3bccd1866dffe02dfe76f68007))  
feat(amazon): allow custom help message on scaling policy selection modal [#7199](https://github.com/spinnaker/deck/pull/7199) ([829da5a9](https://github.com/spinnaker/deck/commit/829da5a98886a6c5d2cb17cbddcca20c75b118c0))  
fix(amazon): Disallow enable instance in a disabled server group [#7197](https://github.com/spinnaker/deck/pull/7197) ([7a218bc8](https://github.com/spinnaker/deck/commit/7a218bc8d1e54037f9dfd5fd81b9d6d6af3b010f))  



## [0.0.200](https://www.github.com/spinnaker/deck/compare/4d9a192d81c2538514deb35046cea5eef3e54038...012f3ccc1091c44bb80b50427e839dc4cec831a8) (2019-07-03)


### Changes

chore(amazon): Bump version to 0.0.200 ([012f3ccc](https://github.com/spinnaker/deck/commit/012f3ccc1091c44bb80b50427e839dc4cec831a8))  
refactor(core/serverGroup): Extract capacity details components to reuse across providers [#7182](https://github.com/spinnaker/deck/pull/7182) ([6630f9bc](https://github.com/spinnaker/deck/commit/6630f9bca7bd17e99bafa174b7659bc6c63f79fd))  



## [0.0.199](https://www.github.com/spinnaker/deck/compare/4422b34071224020b983b4c8a56fc8dd043f3e4f...4d9a192d81c2538514deb35046cea5eef3e54038) (2019-07-02)


### Changes

Bump package core to 0.0.382 and amazon to 0.0.199 [#7179](https://github.com/spinnaker/deck/pull/7179) ([4d9a192d](https://github.com/spinnaker/deck/commit/4d9a192d81c2538514deb35046cea5eef3e54038))  
refactor(core/serverGroup): Extract MinMaxDesiredChanges component [#7171](https://github.com/spinnaker/deck/pull/7171) ([97c4aed4](https://github.com/spinnaker/deck/commit/97c4aed44ea05672dcbdc2316cc857fb1f96c0dc))  



## [0.0.198](https://www.github.com/spinnaker/deck/compare/d9e58aadd786e9f0a0c951972b840b419499482f...4422b34071224020b983b4c8a56fc8dd043f3e4f) (2019-06-28)


### Changes

Bump package core to 0.0.380 and docker to 0.0.43 and amazon to 0.0.198 and titus to 0.0.100 [#7163](https://github.com/spinnaker/deck/pull/7163) ([4422b340](https://github.com/spinnaker/deck/commit/4422b34071224020b983b4c8a56fc8dd043f3e4f))  
 feat(core/amazon): add a visual indicator to infra managed by Keel [#7161](https://github.com/spinnaker/deck/pull/7161) ([2fd1aa97](https://github.com/spinnaker/deck/commit/2fd1aa97195cc1bf8c26447a3d144e6d6192021f))  



## [0.0.197](https://www.github.com/spinnaker/deck/compare/a9c51633e18212e484e14c5a7ffdb3b174e241ea...d9e58aadd786e9f0a0c951972b840b419499482f) (2019-06-27)


### Changes

Bump package core to 0.0.379 and amazon to 0.0.197 [#7159](https://github.com/spinnaker/deck/pull/7159) ([d9e58aad](https://github.com/spinnaker/deck/commit/d9e58aadd786e9f0a0c951972b840b419499482f))  
fix(core): fix alignment on cards in card choices [#7158](https://github.com/spinnaker/deck/pull/7158) ([8d929f06](https://github.com/spinnaker/deck/commit/8d929f06670e27055f488c282c13aa929578eccc))  



## [0.0.196](https://www.github.com/spinnaker/deck/compare/ea94928cbb30e8543f9c6cc8eb492954b5cb43fb...a9c51633e18212e484e14c5a7ffdb3b174e241ea) (2019-06-27)


### Changes

chore(amazon): Bump version to 0.0.196 [#7157](https://github.com/spinnaker/deck/pull/7157) ([a9c51633](https://github.com/spinnaker/deck/commit/a9c51633e18212e484e14c5a7ffdb3b174e241ea))  
fix(amazon): attach instanceId as id field on standalone instance details [#7156](https://github.com/spinnaker/deck/pull/7156) ([f790fdf6](https://github.com/spinnaker/deck/commit/f790fdf69c86146d0eca0a3f1648654c5fe2dcb6))  
feat(cfn/changesets): Introduce support for CFN changesets [#7071](https://github.com/spinnaker/deck/pull/7071) ([b53ac89d](https://github.com/spinnaker/deck/commit/b53ac89ddebe0a73ee23c6c3f45764bea33dd418))  



## [0.0.195](https://www.github.com/spinnaker/deck/compare/ce194afac6673ffb49f1413bfc368ef12308df58...ea94928cbb30e8543f9c6cc8eb492954b5cb43fb) (2019-06-26)


### Changes

Bump package core to 0.0.378 and amazon to 0.0.195 [#7155](https://github.com/spinnaker/deck/pull/7155) ([ea94928c](https://github.com/spinnaker/deck/commit/ea94928cbb30e8543f9c6cc8eb492954b5cb43fb))  
fix(amazon): Fix SpEL support for load balancers [#7151](https://github.com/spinnaker/deck/pull/7151) ([8ee7e118](https://github.com/spinnaker/deck/commit/8ee7e118d2865e0629b7a18323a802eb086797ea))  



## [0.0.194](https://www.github.com/spinnaker/deck/compare/28f0c80d5b84c4d69530c985a73d4e6d989bdb1f...ce194afac6673ffb49f1413bfc368ef12308df58) (2019-06-25)


### Changes

Bump package core to 0.0.377 and docker to 0.0.42 and amazon to 0.0.194 [#7150](https://github.com/spinnaker/deck/pull/7150) ([ce194afa](https://github.com/spinnaker/deck/commit/ce194afac6673ffb49f1413bfc368ef12308df58))  
chore(package): Just Update Prettier™ ([cdd6f237](https://github.com/spinnaker/deck/commit/cdd6f2379859d3c2b13bac59aa470c08b391a865))  
fix(amazon): Support SpEL in advanced capacity [#7124](https://github.com/spinnaker/deck/pull/7124) ([7e464a9a](https://github.com/spinnaker/deck/commit/7e464a9a7d56326c57d027b96d4e567af9e5db3f))  
chore(deck): Update to Typescript 3.4 ([08e95063](https://github.com/spinnaker/deck/commit/08e950634131cd5fdd0f37cbcea68386d0a662a0))  



## [0.0.193](https://www.github.com/spinnaker/deck/compare/df8d18184e5aa59fa94c963f30ed7984708257a2...28f0c80d5b84c4d69530c985a73d4e6d989bdb1f) (2019-06-05)


### Changes

chore(amazon): Bump version to 0.0.193 ([28f0c80d](https://github.com/spinnaker/deck/commit/28f0c80d5b84c4d69530c985a73d4e6d989bdb1f))  
refactor(*): make accountExtractor return an array of strings [#7068](https://github.com/spinnaker/deck/pull/7068) ([8398d770](https://github.com/spinnaker/deck/commit/8398d7706951ce567c352e5f96351366103ef2e3))  
refactor(core/presentation): Consolidate Checklist and ChecklistInput components [#7077](https://github.com/spinnaker/deck/pull/7077) ([4e89a39e](https://github.com/spinnaker/deck/commit/4e89a39e6575954fccae660e38f48bf8b106a978))  



## [0.0.192](https://www.github.com/spinnaker/deck/compare/ec7eb0f1a8c31b7dab7c0888e016b3a40c228bb9...df8d18184e5aa59fa94c963f30ed7984708257a2) (2019-05-22)


### Changes

chore(amazon): Bump version to 0.0.192 [#7051](https://github.com/spinnaker/deck/pull/7051) ([df8d1818](https://github.com/spinnaker/deck/commit/df8d18184e5aa59fa94c963f30ed7984708257a2))  
fix(amazon): do not set target group errors to falsy values [#7050](https://github.com/spinnaker/deck/pull/7050) ([e259625d](https://github.com/spinnaker/deck/commit/e259625d239ba3246de3a06273236d4768cbfc2a))  



## [0.0.191](https://www.github.com/spinnaker/deck/compare/55469b9f51e5e3556c407c19722a83d7d078610c...ec7eb0f1a8c31b7dab7c0888e016b3a40c228bb9) (2019-05-21)


### Changes

Bump package core to 0.0.371 and docker to 0.0.40 and amazon to 0.0.191 [#7041](https://github.com/spinnaker/deck/pull/7041) ([ec7eb0f1](https://github.com/spinnaker/deck/commit/ec7eb0f1a8c31b7dab7c0888e016b3a40c228bb9))  
fix(core/amazon): validate target group healthcheck fields [#6962](https://github.com/spinnaker/deck/pull/6962) ([b55e3b81](https://github.com/spinnaker/deck/commit/b55e3b819c219ec75544c6753891b31ff92ee5d2))  
feat(amazon): remove s3 as store type option for baking [#7035](https://github.com/spinnaker/deck/pull/7035) ([a2a3bbbf](https://github.com/spinnaker/deck/commit/a2a3bbbf89724a45350e66ea54ab186b1463ed53))  



## [0.0.190](https://www.github.com/spinnaker/deck/compare/435fdb1bd5b70c07785a4e70424e275a8d2d1ff0...55469b9f51e5e3556c407c19722a83d7d078610c) (2019-05-14)


### Changes

chore(amazon): Bump version to 0.0.190 [#6990](https://github.com/spinnaker/deck/pull/6990) ([55469b9f](https://github.com/spinnaker/deck/commit/55469b9f51e5e3556c407c19722a83d7d078610c))  
fix(amazon): explicitly import d3 for scaling policy graphs [#6989](https://github.com/spinnaker/deck/pull/6989) ([e06039b9](https://github.com/spinnaker/deck/commit/e06039b98d9e4ae96b2c8849daae17c077b16237))  



## [0.0.189](https://www.github.com/spinnaker/deck/compare/b2f3c8e9c1a7d364d0a490e5eae6ed45f57b136e...435fdb1bd5b70c07785a4e70424e275a8d2d1ff0) (2019-05-13)


### Changes

Bump package core to 0.0.363 and amazon to 0.0.189 [#6985](https://github.com/spinnaker/deck/pull/6985) ([435fdb1b](https://github.com/spinnaker/deck/commit/435fdb1bd5b70c07785a4e70424e275a8d2d1ff0))  
refactor(amazon): de-angularize load balancer transformer [#6984](https://github.com/spinnaker/deck/pull/6984) ([b576fed4](https://github.com/spinnaker/deck/commit/b576fed4abd05f2cb60fc6bfbf410232679a5b8d))  
fix(aws): add NLB info to target group help text [#6977](https://github.com/spinnaker/deck/pull/6977) ([1fcdf7c5](https://github.com/spinnaker/deck/commit/1fcdf7c5451031c609865bbbf1a3e814802db00b))  
feat(cloudformation): support cfn tags [#6928](https://github.com/spinnaker/deck/pull/6928) ([52d89d6f](https://github.com/spinnaker/deck/commit/52d89d6fb55af9402dbf23258837576e25435599))  



## [0.0.188](https://www.github.com/spinnaker/deck/compare/ff7d8e2e60492b7d737969f9711a1cadb868c349...b2f3c8e9c1a7d364d0a490e5eae6ed45f57b136e) (2019-05-08)


### Changes

Bump package core to 0.0.360 and amazon to 0.0.188 and titus to 0.0.94 [#6951](https://github.com/spinnaker/deck/pull/6951) ([b2f3c8e9](https://github.com/spinnaker/deck/commit/b2f3c8e9c1a7d364d0a490e5eae6ed45f57b136e))  
feat(core): compress AWS security groups before caching [#6947](https://github.com/spinnaker/deck/pull/6947) ([954506ac](https://github.com/spinnaker/deck/commit/954506ac56629b416441455ad1bb91929df19860))  



## [0.0.187](https://www.github.com/spinnaker/deck/compare/65e0357e004203d1d7edc086b8deaa04ecce0fe8...ff7d8e2e60492b7d737969f9711a1cadb868c349) (2019-04-29)


### Changes

Bump package core to 0.0.356 and docker to 0.0.38 and amazon to 0.0.187 [#6910](https://github.com/spinnaker/deck/pull/6910) ([ff7d8e2e](https://github.com/spinnaker/deck/commit/ff7d8e2e60492b7d737969f9711a1cadb868c349))  
feat(aws): show ALB listener redirect info in details [#6904](https://github.com/spinnaker/deck/pull/6904) ([151aa2d0](https://github.com/spinnaker/deck/commit/151aa2d0e74786b7d6fdd8fb1e54764e2bc482fa))  



## [0.0.186](https://www.github.com/spinnaker/deck/compare/2abfe4ff8e4d1ecd651a4d0e8267e6f1dcae62b2...65e0357e004203d1d7edc086b8deaa04ecce0fe8) (2019-04-16)


### Changes

chore(amazon): Bump version to 0.0.186 [#6860](https://github.com/spinnaker/deck/pull/6860) ([65e0357e](https://github.com/spinnaker/deck/commit/65e0357e004203d1d7edc086b8deaa04ecce0fe8))  
refactor(amazon): export load balancer choice modal, remove customization hook [#6859](https://github.com/spinnaker/deck/pull/6859) ([384e780c](https://github.com/spinnaker/deck/commit/384e780c7648667c91ab94337924ca86319ad8fd))  



## [0.0.185](https://www.github.com/spinnaker/deck/compare/700b8f6f7b536f909a0cc10389309f8b325906e4...2abfe4ff8e4d1ecd651a4d0e8267e6f1dcae62b2) (2019-04-16)


### Changes

Bump package core to 0.0.351 and docker to 0.0.36 and amazon to 0.0.185 and titus to 0.0.87 [#6855](https://github.com/spinnaker/deck/pull/6855) ([2abfe4ff](https://github.com/spinnaker/deck/commit/2abfe4ff8e4d1ecd651a4d0e8267e6f1dcae62b2))  
refactor(core/amazon): allow custom load balancer creation flow [#6852](https://github.com/spinnaker/deck/pull/6852) ([f6c87a33](https://github.com/spinnaker/deck/commit/f6c87a338abd78d11ca1e4099fe5a2229ed6dfcb))  
fix(titus): correctly set dirty field on command viewstate [#6795](https://github.com/spinnaker/deck/pull/6795) ([6ddc4760](https://github.com/spinnaker/deck/commit/6ddc47602b701fce5890fe266429ddff38e8b0c3))  



## [0.0.184](https://www.github.com/spinnaker/deck/compare/300042078dcb0b85b9397cda3cd8432034d5babb...700b8f6f7b536f909a0cc10389309f8b325906e4) (2019-04-02)


### Changes

Bump package core to 0.0.350 and amazon to 0.0.184 [#6807](https://github.com/spinnaker/deck/pull/6807) ([700b8f6f](https://github.com/spinnaker/deck/commit/700b8f6f7b536f909a0cc10389309f8b325906e4))  
fix(amazon): hide security groups on NLBs [#6803](https://github.com/spinnaker/deck/pull/6803) ([06a7556c](https://github.com/spinnaker/deck/commit/06a7556ca5c3e7d78a4fc629b90854fae391f193))  
refactor(core): de-angularize ApplicationModelBuilder, fix project executions [#6802](https://github.com/spinnaker/deck/pull/6802) ([72e164df](https://github.com/spinnaker/deck/commit/72e164dfcf11afbac559195a8fa6a91dd77ad2b9))  



## [0.0.183](https://www.github.com/spinnaker/deck/compare/b85bd3ef21ea12ce882f28962ae1f4fb23d37597...300042078dcb0b85b9397cda3cd8432034d5babb) (2019-04-01)


### Changes

Bump package amazon to 0.0.183 and titus to 0.0.86 [#6793](https://github.com/spinnaker/deck/pull/6793) ([30004207](https://github.com/spinnaker/deck/commit/300042078dcb0b85b9397cda3cd8432034d5babb))  
fix(amazon): Added AWS/ApplicationELB namespace to cloudwatch namespaces [#6791](https://github.com/spinnaker/deck/pull/6791) ([607f386a](https://github.com/spinnaker/deck/commit/607f386a3511da4bb568a672ea2447ca2038b622))  
fix(amazon): allow deletion of scaling policies without alarms [#6779](https://github.com/spinnaker/deck/pull/6779) ([e96e7a92](https://github.com/spinnaker/deck/commit/e96e7a927c56540c998fd6d09134df8438978625))  



## [0.0.182](https://www.github.com/spinnaker/deck/compare/33986e01c7902243e71e6cffc722df4d995f9833...b85bd3ef21ea12ce882f28962ae1f4fb23d37597) (2019-03-27)


### Changes

Bump package core to 0.0.346 and amazon to 0.0.182 and titus to 0.0.81 [#6758](https://github.com/spinnaker/deck/pull/6758) ([b85bd3ef](https://github.com/spinnaker/deck/commit/b85bd3ef21ea12ce882f28962ae1f4fb23d37597))  
refactor(titus): generalize ConfigBin and export it [#6757](https://github.com/spinnaker/deck/pull/6757) ([f32aa230](https://github.com/spinnaker/deck/commit/f32aa23030642d8e403d731038eb60c97915c1d9))  
feat(amazon): show certificate upload/expiration when selected for load balancers [#6731](https://github.com/spinnaker/deck/pull/6731) ([1f58407a](https://github.com/spinnaker/deck/commit/1f58407a8f8d1c28bf14c0e372cd3b482f4bb526))  
fix(amazon): consider all target group names when creating new ones [#6727](https://github.com/spinnaker/deck/pull/6727) ([6083b3f4](https://github.com/spinnaker/deck/commit/6083b3f4ea9b7fc66c2666a929bde70c35ef2275))  



## [0.0.181](https://www.github.com/spinnaker/deck/compare/60c38a6e4990147defcf97029ae7506cb1128509...33986e01c7902243e71e6cffc722df4d995f9833) (2019-03-20)


### Changes

Bump package core to 0.0.345 and amazon to 0.0.181 and titus to 0.0.80 [#6726](https://github.com/spinnaker/deck/pull/6726) ([33986e01](https://github.com/spinnaker/deck/commit/33986e01c7902243e71e6cffc722df4d995f9833))  
chore(core): upgrade the version to formik 1.4.1 [#6705](https://github.com/spinnaker/deck/pull/6705) ([51eeba48](https://github.com/spinnaker/deck/commit/51eeba480ac1bc232c0df020e4bb788a28a8b744))  
feature(aws): Add the cloudformation stage [#6521](https://github.com/spinnaker/deck/pull/6521) ([515bccb0](https://github.com/spinnaker/deck/commit/515bccb032bc3f71d9d71b778fd9e83cc7c6cc0c))  



## [0.0.180](https://www.github.com/spinnaker/deck/compare/1deafa4550bb0e1d5f01c45af924be0de3f46186...60c38a6e4990147defcf97029ae7506cb1128509) (2019-03-13)


### Changes

chore(amazon): Bump version to 0.0.180 [#6683](https://github.com/spinnaker/deck/pull/6683) ([60c38a6e](https://github.com/spinnaker/deck/commit/60c38a6e4990147defcf97029ae7506cb1128509))  
fix(aws): do not aggressively and endlessly fetch vpcs [#6682](https://github.com/spinnaker/deck/pull/6682) ([955eac52](https://github.com/spinnaker/deck/commit/955eac52f91b5bbcac41a0f790f0f94a9dc4cf11))  



## [0.0.179](https://www.github.com/spinnaker/deck/compare/497489e47917056c5e2f171ce784324124475a0e...1deafa4550bb0e1d5f01c45af924be0de3f46186) (2019-03-12)


### Changes

Bump package core to 0.0.343 and amazon to 0.0.179 and titus to 0.0.79 [#6672](https://github.com/spinnaker/deck/pull/6672) ([1deafa45](https://github.com/spinnaker/deck/commit/1deafa4550bb0e1d5f01c45af924be0de3f46186))  
refactor(*): remove unused local storage caches [#6665](https://github.com/spinnaker/deck/pull/6665) ([e2b4d8e9](https://github.com/spinnaker/deck/commit/e2b4d8e9371d5d2f1f9b60e2a592e10b47df73e2))  
fix({core,amazon}/serverGroup): filter out empty tags, change 'tags' field type [#6645](https://github.com/spinnaker/deck/pull/6645) ([09d7fee2](https://github.com/spinnaker/deck/commit/09d7fee21f60d1f448497f806f5a599c95880514))  



## [0.0.178](https://www.github.com/spinnaker/deck/compare/28301107f3de0a574b7030bfd3cf442c7061b2e2...497489e47917056c5e2f171ce784324124475a0e) (2019-02-27)


### Changes

Bump package core to 0.0.341 and amazon to 0.0.178 [#6624](https://github.com/spinnaker/deck/pull/6624) ([497489e4](https://github.com/spinnaker/deck/commit/497489e47917056c5e2f171ce784324124475a0e))  
fix(amazon/loadBalancer): Fix AZ autobalance, subnet AZ default value, and isInternal checkbox [#6623](https://github.com/spinnaker/deck/pull/6623) ([ea0808f5](https://github.com/spinnaker/deck/commit/ea0808f57121e108072a664bed445b186820d03e))  



## [0.0.177](https://www.github.com/spinnaker/deck/compare/ac64613268a00f966515ef8c635689fa2f82b4c4...28301107f3de0a574b7030bfd3cf442c7061b2e2) (2019-02-25)


### Changes

Bump package core to 0.0.339 and amazon to 0.0.177 and titus to 0.0.77 [#6615](https://github.com/spinnaker/deck/pull/6615) ([28301107](https://github.com/spinnaker/deck/commit/28301107f3de0a574b7030bfd3cf442c7061b2e2))  
fix(amazon/serverGroup): Do not apply default AZs unless the user wants to usePreferredZones [#6609](https://github.com/spinnaker/deck/pull/6609) ([510557f7](https://github.com/spinnaker/deck/commit/510557f7ffcbc8a538195c3ba120d5cebbe37163))  



## [0.0.176](https://www.github.com/spinnaker/deck/compare/2b2ccff259cedc559b6aad5e0cc16f072a551a8a...ac64613268a00f966515ef8c635689fa2f82b4c4) (2019-02-21)


### Changes

chore(amazon): Bump version to 0.0.176 ([ac646132](https://github.com/spinnaker/deck/commit/ac64613268a00f966515ef8c635689fa2f82b4c4))  
chore(prettier): Just Use Prettier™ [#6600](https://github.com/spinnaker/deck/pull/6600) ([7d5fc346](https://github.com/spinnaker/deck/commit/7d5fc346bca54c5d53f9eb46d823cd993c102058))  
fix(html): Fix various invalid HTML [#6599](https://github.com/spinnaker/deck/pull/6599) ([04bb4a01](https://github.com/spinnaker/deck/commit/04bb4a01c2d988aab5b5b8ae6e3aadbc59214898))  
fix(html): Fix various invalid HTML [#6597](https://github.com/spinnaker/deck/pull/6597) ([64fb4892](https://github.com/spinnaker/deck/commit/64fb4892ee3e7114eccb8f6acc9d84ae652f1af3))  
chore(prettier): Just Use Prettier™ ([5cf6c79d](https://github.com/spinnaker/deck/commit/5cf6c79da63404bb7238291d38bb7f5cfd10c26b))  
chore(angularjs): Do not use .component('foo', new Foo()) ([3ffa4fb7](https://github.com/spinnaker/deck/commit/3ffa4fb7498df815014d61071e8588f0b34bf8b9))  



## [0.0.175](https://www.github.com/spinnaker/deck/compare/273e1db3b9ac21a57a4b018edda97369b59abaa2...2b2ccff259cedc559b6aad5e0cc16f072a551a8a) (2019-02-21)


### Changes

Bump package core to 0.0.337 and docker to 0.0.34 and amazon to 0.0.175 and titus to 0.0.75 [#6593](https://github.com/spinnaker/deck/pull/6593) ([2b2ccff2](https://github.com/spinnaker/deck/commit/2b2ccff259cedc559b6aad5e0cc16f072a551a8a))  
chore(angularjs): Remove all 'ngInject'; in favor of explicit DI annotation ([cc52bee0](https://github.com/spinnaker/deck/commit/cc52bee0b9956693f948806322658f225efa5546))  
chore(prettier): Just Use Prettier™ ([b6bab1e1](https://github.com/spinnaker/deck/commit/b6bab1e16bb46697fec347cd30934f00fb2e9807))  
chore(angularjs): Explicitly annotate all AngularJS injection points ([f3fd790e](https://github.com/spinnaker/deck/commit/f3fd790e20a4c3056edcb2c41282517e1cf35004))  
feat(aws): allow override of scaling policies section [#6584](https://github.com/spinnaker/deck/pull/6584) ([4f1c5c38](https://github.com/spinnaker/deck/commit/4f1c5c38cf3967238957bba7b914a9d2bd9047e8))  
fix(aws): prevent clone submit when ingress rule removal in not acked [#6577](https://github.com/spinnaker/deck/pull/6577) ([cabc6673](https://github.com/spinnaker/deck/commit/cabc6673ded7fc30b9adc4f595d2d6ab3203fb7d))  



## [0.0.174](https://www.github.com/spinnaker/deck/compare/7956ef163f87edd4e5d80d597d5a90c0e25a8aa1...273e1db3b9ac21a57a4b018edda97369b59abaa2) (2019-02-19)


### Changes

Bump package core to 0.0.336 and amazon to 0.0.174 [#6581](https://github.com/spinnaker/deck/pull/6581) ([273e1db3](https://github.com/spinnaker/deck/commit/273e1db3b9ac21a57a4b018edda97369b59abaa2))  
fix(aws): set redirectActionConfig on ALB rules [#6579](https://github.com/spinnaker/deck/pull/6579) ([ad7c5820](https://github.com/spinnaker/deck/commit/ad7c582006a70648b20a1c7b6bb466fa62fb8b6c))  



## [0.0.173](https://www.github.com/spinnaker/deck/compare/b848693156862a79314a4ab8048c6aeeaca514f2...7956ef163f87edd4e5d80d597d5a90c0e25a8aa1) (2019-02-18)


### Changes

Bump package core to 0.0.333 and amazon to 0.0.173 [#6572](https://github.com/spinnaker/deck/pull/6572) ([7956ef16](https://github.com/spinnaker/deck/commit/7956ef163f87edd4e5d80d597d5a90c0e25a8aa1))  
fix(core/amazon): Loadbalancer tags should have spinner to avoid panic [#6562](https://github.com/spinnaker/deck/pull/6562) ([3664ce2a](https://github.com/spinnaker/deck/commit/3664ce2a4b571fcda570d454f1c124191f98aaec))  
chore(amazon/instance): Promote the recommended high-memory family to r5 [#6566](https://github.com/spinnaker/deck/pull/6566) ([ce4a4ba9](https://github.com/spinnaker/deck/commit/ce4a4ba9f4874d4833eb7381bb01f0b43be749e5))  



## [0.0.172](https://www.github.com/spinnaker/deck/compare/3470a3a339cd5299835ab774e26e0e5f753ee7bb...b848693156862a79314a4ab8048c6aeeaca514f2) (2019-02-18)


### Changes

Bump package core to 0.0.332 and amazon to 0.0.172 [#6565](https://github.com/spinnaker/deck/pull/6565) ([b8486931](https://github.com/spinnaker/deck/commit/b848693156862a79314a4ab8048c6aeeaca514f2))  
fix(aws): fix security group rule updates [#6564](https://github.com/spinnaker/deck/pull/6564) ([b18a815f](https://github.com/spinnaker/deck/commit/b18a815fbc215290070e07a1152d4d916d325d6c))  
fix(eslint): Fix eslint warnings for noundef ([7692c2e3](https://github.com/spinnaker/deck/commit/7692c2e31f7a369833383de036a5b7e6dd374eec))  



## [0.0.171](https://www.github.com/spinnaker/deck/compare/66cf2556007c999861df6bb4451a3ba1ccea4a6d...3470a3a339cd5299835ab774e26e0e5f753ee7bb) (2019-02-14)


### Changes

Bump package core to 0.0.331 and amazon to 0.0.171 and titus to 0.0.74 [#6551](https://github.com/spinnaker/deck/pull/6551) ([3470a3a3](https://github.com/spinnaker/deck/commit/3470a3a339cd5299835ab774e26e0e5f753ee7bb))  
fix(aws): allow ingress creation from all accounts [#6543](https://github.com/spinnaker/deck/pull/6543) ([ee7b9364](https://github.com/spinnaker/deck/commit/ee7b9364da35ad1580a813b0e595ad47a4171d5b))  



## [0.0.170](https://www.github.com/spinnaker/deck/compare/4d689602cb1654071bd50435795ccb2ce2ac2be4...66cf2556007c999861df6bb4451a3ba1ccea4a6d) (2019-02-12)


### Changes

Bump package core to 0.0.330 and amazon to 0.0.170 [#6537](https://github.com/spinnaker/deck/pull/6537) ([66cf2556](https://github.com/spinnaker/deck/commit/66cf2556007c999861df6bb4451a3ba1ccea4a6d))  
chore(eslint): Fix some linter errors ([d7291cc4](https://github.com/spinnaker/deck/commit/d7291cc4ce28c12241dda4f7b8f9d7318824582b))  
fix(amazon): Display capacity as text if using SPEL [#6535](https://github.com/spinnaker/deck/pull/6535) ([7e4b4571](https://github.com/spinnaker/deck/commit/7e4b45711de31637d1a97aa046c6696885578a52))  
chore(eslint): Fix lint errors ([27d8a12c](https://github.com/spinnaker/deck/commit/27d8a12c6e133ba0a25d874535f3c77da431113e))  
chore(package): Just Update Prettier™ ([a8c17492](https://github.com/spinnaker/deck/commit/a8c174925f64045f70c11b2bfc11fe1fdd558660))  
fix(amazon/core): Sorting order of regions in bake stage + lint fix [#6518](https://github.com/spinnaker/deck/pull/6518) ([a2ebca0a](https://github.com/spinnaker/deck/commit/a2ebca0a272a393953f066bb43f14deb155adcfc))  



## [0.0.169](https://www.github.com/spinnaker/deck/compare/d94a24513e93a58b22314601c92f1b5aad627c13...4d689602cb1654071bd50435795ccb2ce2ac2be4) (2019-02-08)


### Changes

chore(amazon): Bump version to 0.0.169 [#6515](https://github.com/spinnaker/deck/pull/6515) ([4d689602](https://github.com/spinnaker/deck/commit/4d689602cb1654071bd50435795ccb2ce2ac2be4))  
fix(amazon/loadBalancer): Fix NLB health check port validator [#6514](https://github.com/spinnaker/deck/pull/6514) ([92f18eca](https://github.com/spinnaker/deck/commit/92f18eca7361eb725aa2f3db92b8bbe376519884))  



## [0.0.168](https://www.github.com/spinnaker/deck/compare/6cd6042fabc17724911ca4797727a6638d8609ad...d94a24513e93a58b22314601c92f1b5aad627c13) (2019-02-07)


### Changes

chore(amazon): Bump version to 0.0.168 [#6511](https://github.com/spinnaker/deck/pull/6511) ([d94a2451](https://github.com/spinnaker/deck/commit/d94a24513e93a58b22314601c92f1b5aad627c13))  
chore(amazon): fix lint [#6510](https://github.com/spinnaker/deck/pull/6510) ([e8979d04](https://github.com/spinnaker/deck/commit/e8979d040bb14978dc91afd04245bed9ff2cda15))  
fix(amazon): do not call setState inside validate [#6509](https://github.com/spinnaker/deck/pull/6509) ([65162b39](https://github.com/spinnaker/deck/commit/65162b397be1883b1f0069fe98f407474b226002))  



## [0.0.167](https://www.github.com/spinnaker/deck/compare/58975a06f7ec513029ae39f738968b4c69a18afa...6cd6042fabc17724911ca4797727a6638d8609ad) (2019-02-07)


### Changes

Bump package core to 0.0.328 and docker to 0.0.33 and amazon to 0.0.167 and titus to 0.0.73 [#6507](https://github.com/spinnaker/deck/pull/6507) ([6cd6042f](https://github.com/spinnaker/deck/commit/6cd6042fabc17724911ca4797727a6638d8609ad))  
chore(webpack): Switch to TerserPlugin.  Split bundles into ~5mb chunks ([a35088ab](https://github.com/spinnaker/deck/commit/a35088ab28cc3b25c9e6731f6fb70bf7d0e14ef0))  



## [0.0.166](https://www.github.com/spinnaker/deck/compare/60f4f345d5b4c3f1fb9ec1ae013f3a6cc48e0297...58975a06f7ec513029ae39f738968b4c69a18afa) (2019-02-06)


### Changes

chore(amazon): Bump version to 0.0.166 ([58975a06](https://github.com/spinnaker/deck/commit/58975a06f7ec513029ae39f738968b4c69a18afa))  
fix(core): filter unauthorized accounts [#6490](https://github.com/spinnaker/deck/pull/6490) ([1c55e89c](https://github.com/spinnaker/deck/commit/1c55e89c6fedaf5c0142e1ac0208295d041d1359))  



## [0.0.165](https://www.github.com/spinnaker/deck/compare/1f03ebe413b436684e99c7b9468f03b8974631d2...60f4f345d5b4c3f1fb9ec1ae013f3a6cc48e0297) (2019-02-05)


### Changes

Bump package core to 0.0.325 and docker to 0.0.32 and amazon to 0.0.165 and titus to 0.0.71 [#6491](https://github.com/spinnaker/deck/pull/6491) ([60f4f345](https://github.com/spinnaker/deck/commit/60f4f345d5b4c3f1fb9ec1ae013f3a6cc48e0297))  
chore(typescript): Switch module from 'commonjs' to 'esnext' to emit raw dynamic 'import()' ([5c49dd2a](https://github.com/spinnaker/deck/commit/5c49dd2ab3c4226295a7e8041c25dabdbeee6a2c))  



## [0.0.164](https://www.github.com/spinnaker/deck/compare/a93921f920b6c963306cf93e946045cd925be772...1f03ebe413b436684e99c7b9468f03b8974631d2) (2019-02-05)


### Changes

chore(amazon): Bump version to 0.0.164 [#6485](https://github.com/spinnaker/deck/pull/6485) ([1f03ebe4](https://github.com/spinnaker/deck/commit/1f03ebe413b436684e99c7b9468f03b8974631d2))  
fix(amazon): do not mutate nested objects on clone server group command [#6484](https://github.com/spinnaker/deck/pull/6484) ([b680b1ec](https://github.com/spinnaker/deck/commit/b680b1ec86e4e7933790f9ab473d19d463ea795b))  



## [0.0.163](https://www.github.com/spinnaker/deck/compare/a74ab2970bd97809d5a5310934aa6fb4b704aaa6...a93921f920b6c963306cf93e946045cd925be772) (2019-02-04)


### Changes

Bump package core to 0.0.324 and amazon to 0.0.163 and titus to 0.0.70 [#6482](https://github.com/spinnaker/deck/pull/6482) ([a93921f9](https://github.com/spinnaker/deck/commit/a93921f920b6c963306cf93e946045cd925be772))  
feat(amazon/loadBalancer): Rudimentary support for redirect actions [#6470](https://github.com/spinnaker/deck/pull/6470) ([01dafc7e](https://github.com/spinnaker/deck/commit/01dafc7ed9e498d3891f40e06145667dbc9b4033))  
chore(package): Add .npmignore to all packages ([0451046c](https://github.com/spinnaker/deck/commit/0451046c241b450ae4b05df0b67b61758c16acce))  



## [0.0.162](https://www.github.com/spinnaker/deck/compare/e40784d9583ab44cfcb3de8486d3b2074cd851a7...a74ab2970bd97809d5a5310934aa6fb4b704aaa6) (2019-02-02)


### Changes

Bump package core to 0.0.323 and amazon to 0.0.162 and titus to 0.0.69 [#6475](https://github.com/spinnaker/deck/pull/6475) ([a74ab297](https://github.com/spinnaker/deck/commit/a74ab2970bd97809d5a5310934aa6fb4b704aaa6))  
fix(aws): do not filter out security group ip ranges using wildcards [#6474](https://github.com/spinnaker/deck/pull/6474) ([9bec6ed1](https://github.com/spinnaker/deck/commit/9bec6ed139de88dc33091d2a3edefd177959b7f0))  
refactor(validation): Create validation directory, split up validation and validators, de-class Validation ([aa028227](https://github.com/spinnaker/deck/commit/aa0282270d4511628c5b34f03532e338a7439f0e))  
refactor(amazon/modal): Refactor amazon modals to use new WizardPage component ([8ff2bbb0](https://github.com/spinnaker/deck/commit/8ff2bbb0948c5de72af472bd56f72324ed881134))  



## [0.0.161](https://www.github.com/spinnaker/deck/compare/0b4f905d8f2cbb95f682eca0f8bebd4f67b9f15b...e40784d9583ab44cfcb3de8486d3b2074cd851a7) (2019-01-30)


### Changes

chore(amazon): Bump version to 0.0.161 ([e40784d9](https://github.com/spinnaker/deck/commit/e40784d9583ab44cfcb3de8486d3b2074cd851a7))  
fix(*): Remove all self closing tags in AngularJS templates Reference: https://github.com/angular/angular.js/issues/1953#issuecomment-13135021 ([6f608a0a](https://github.com/spinnaker/deck/commit/6f608a0ab43616eb130c7417e560bc3df780f335))  



## [0.0.160](https://www.github.com/spinnaker/deck/compare/1bd15760e11260a5c05c555251951e696600f0f9...0b4f905d8f2cbb95f682eca0f8bebd4f67b9f15b) (2019-01-28)


### Changes

chore(amazon): Bump version to 0.0.160 [#6439](https://github.com/spinnaker/deck/pull/6439) ([0b4f905d](https://github.com/spinnaker/deck/commit/0b4f905d8f2cbb95f682eca0f8bebd4f67b9f15b))  
fix(amazon/loadBalancer): Fix display of rule condition in alb config [#6437](https://github.com/spinnaker/deck/pull/6437) ([968f1672](https://github.com/spinnaker/deck/commit/968f1672b1de00bab3711347bcf11b66a32174d3))  



## [0.0.159](https://www.github.com/spinnaker/deck/compare/bfc29fe75d3b35363c9390457e7b2e3f2512f796...1bd15760e11260a5c05c555251951e696600f0f9) (2019-01-27)


### Changes

Bump package core to 0.0.319 and docker to 0.0.31 and amazon to 0.0.159 and titus to 0.0.68 [#6436](https://github.com/spinnaker/deck/pull/6436) ([1bd15760](https://github.com/spinnaker/deck/commit/1bd15760e11260a5c05c555251951e696600f0f9))  
refactor(*): Don't use js or ts file extension in require() ([35be1f08](https://github.com/spinnaker/deck/commit/35be1f0872f5958514c920ee97510d36484e33eb))  



## [0.0.158](https://www.github.com/spinnaker/deck/compare/524780fa3d45bd40a491c5fb8f50cf8c4bbc9dfc...bfc29fe75d3b35363c9390457e7b2e3f2512f796) (2019-01-24)


### Changes

Bump package core to 0.0.318 and amazon to 0.0.158 [#6429](https://github.com/spinnaker/deck/pull/6429) ([bfc29fe7](https://github.com/spinnaker/deck/commit/bfc29fe75d3b35363c9390457e7b2e3f2512f796))  
refactor(aws): move certificate config to second line on elb/alb [#6428](https://github.com/spinnaker/deck/pull/6428) ([ff8f055e](https://github.com/spinnaker/deck/commit/ff8f055eebf50253e30c9e071d055804f56cdf34))  



## [0.0.157](https://www.github.com/spinnaker/deck/compare/dfea4bc30f3be85e954cf654bd83f934f2f428d2...524780fa3d45bd40a491c5fb8f50cf8c4bbc9dfc) (2019-01-23)


### Changes

Bump package core to 0.0.314 and amazon to 0.0.157 [#6411](https://github.com/spinnaker/deck/pull/6411) ([524780fa](https://github.com/spinnaker/deck/commit/524780fa3d45bd40a491c5fb8f50cf8c4bbc9dfc))  
feat(amazon/loadBalancer): Support traffic-port for healthcheck [#6408](https://github.com/spinnaker/deck/pull/6408) ([143803c4](https://github.com/spinnaker/deck/commit/143803c4682e3316ef7d7c18da585ee0f75e2f8f))  
fix(amazon): Fixed similar image finder so concat happens [#6406](https://github.com/spinnaker/deck/pull/6406) ([b2d66829](https://github.com/spinnaker/deck/commit/b2d66829e467f1b147a11ef9a36003db1d928ba6))  
fix(amazon): Fixing load balancers isInternal flag [#6402](https://github.com/spinnaker/deck/pull/6402) ([879221b0](https://github.com/spinnaker/deck/commit/879221b0b4db1f5fd0435b24765d42c203df78dd))  



## [0.0.156](https://www.github.com/spinnaker/deck/compare/83cb727d0b7878068fb85e315cc6f9f1eda8f257...dfea4bc30f3be85e954cf654bd83f934f2f428d2) (2019-01-17)


### Changes

chore(amazon/): Bump version to 0.0.156 [#6400](https://github.com/spinnaker/deck/pull/6400) ([dfea4bc3](https://github.com/spinnaker/deck/commit/dfea4bc30f3be85e954cf654bd83f934f2f428d2))  
refactor(aws): export cert field; rename cert field prop field [#6399](https://github.com/spinnaker/deck/pull/6399) ([04b3ea53](https://github.com/spinnaker/deck/commit/04b3ea53424b3be06649e747b2844c961fcc58bd))  



## [0.0.155](https://www.github.com/spinnaker/deck/compare/4449cbfddf7880d55f76774135a9cb129ce055eb...83cb727d0b7878068fb85e315cc6f9f1eda8f257) (2019-01-17)


### Changes

chore(amazon): Bump version to 0.0.155 [#6398](https://github.com/spinnaker/deck/pull/6398) ([83cb727d](https://github.com/spinnaker/deck/commit/83cb727d0b7878068fb85e315cc6f9f1eda8f257))  
refactor(aws): export IAmazonCertificate [#6397](https://github.com/spinnaker/deck/pull/6397) ([e0d19840](https://github.com/spinnaker/deck/commit/e0d19840f68f14c5490059b0df4aef7e9cd20681))  



## [0.0.154](https://www.github.com/spinnaker/deck/compare/467cb42e89f4f082a97bc5df0112ae2c5fe8d318...4449cbfddf7880d55f76774135a9cb129ce055eb) (2019-01-17)


### Changes

Bump package core to 0.0.313 and amazon to 0.0.154 [#6396](https://github.com/spinnaker/deck/pull/6396) ([4449cbfd](https://github.com/spinnaker/deck/commit/4449cbfddf7880d55f76774135a9cb129ce055eb))  
refactor(aws): move certificate selector to separate component [#6395](https://github.com/spinnaker/deck/pull/6395) ([0fb830ed](https://github.com/spinnaker/deck/commit/0fb830ed3b7f31a90a7d26bc55bf95fa9a2713dc))  
fix(amazon): better style of react-select for certificates [#6391](https://github.com/spinnaker/deck/pull/6391) ([433e5547](https://github.com/spinnaker/deck/commit/433e5547f0754f40e90faca9b2bea270ecce0de6))  



## [0.0.153](https://www.github.com/spinnaker/deck/compare/29ca5a5a58ad0f9d8a3d47549658fa02d55f79d4...467cb42e89f4f082a97bc5df0112ae2c5fe8d318) (2019-01-16)


### Changes

chore(amazon/): Bump version to 0.0.153 [#6386](https://github.com/spinnaker/deck/pull/6386) ([467cb42e](https://github.com/spinnaker/deck/commit/467cb42e89f4f082a97bc5df0112ae2c5fe8d318))  
refactor(aws): remove certificate loading from NLB modal [#6379](https://github.com/spinnaker/deck/pull/6379) ([6e2a209d](https://github.com/spinnaker/deck/commit/6e2a209d302e5fc5604a185b929e068224f21e9f))  



## [0.0.152](https://www.github.com/spinnaker/deck/compare/752da1bdf8ae7a64e82e7be8f05411d0208774e7...29ca5a5a58ad0f9d8a3d47549658fa02d55f79d4) (2019-01-15)


### Changes

chore(amazon): Bump version to 0.0.152 ([29ca5a5a](https://github.com/spinnaker/deck/commit/29ca5a5a58ad0f9d8a3d47549658fa02d55f79d4))  
fix(amazon/deploy): Stop dying when target groups is an expression [#6374](https://github.com/spinnaker/deck/pull/6374) ([0fc0a486](https://github.com/spinnaker/deck/commit/0fc0a486a32e128cf34423c1f63fad17c690763b))  
feat(loadBalancer): Support insight actions on load balancers [#6372](https://github.com/spinnaker/deck/pull/6372) ([e32dd4f8](https://github.com/spinnaker/deck/commit/e32dd4f80a67f0647488e16dce843bb4d71a5149))  
fix(amazon/deploy): Stop dying when load balancers is an expression [#6369](https://github.com/spinnaker/deck/pull/6369) ([5b9911ff](https://github.com/spinnaker/deck/commit/5b9911ffcba7ee95662de0c84a3ef5fbc1e46416))  
feat(aws/titus): warn on scaling policy creation if min/max are identical [#6366](https://github.com/spinnaker/deck/pull/6366) ([a931df26](https://github.com/spinnaker/deck/commit/a931df26ef2e371a0630e6c2cd2661f07d9d9629))  
fix(amazon/loadBalancer): Fix availability zone selector default values [#6368](https://github.com/spinnaker/deck/pull/6368) ([558b1a66](https://github.com/spinnaker/deck/commit/558b1a66112256dc05bd3742a0b93f3e0f8d8923))  



## [0.0.151](https://www.github.com/spinnaker/deck/compare/478936eb234c71dbd94a1fa69a148945b69b283e...752da1bdf8ae7a64e82e7be8f05411d0208774e7) (2019-01-10)


### Changes

Bump package [#6346](https://github.com/spinnaker/deck/pull/6346) ([752da1bd](https://github.com/spinnaker/deck/commit/752da1bdf8ae7a64e82e7be8f05411d0208774e7))  
refactor(amazon/loadBalancer): Migrate LoadBalancerLocation and SecurityGroups to RxJS streams [#6318](https://github.com/spinnaker/deck/pull/6318) ([5edb8033](https://github.com/spinnaker/deck/commit/5edb80338ac6b387999588f8514e9ff629cb56d0))  



## [0.0.150](https://www.github.com/spinnaker/deck/compare/4eef691645f8683db2f5665615a5129d4bd142ec...478936eb234c71dbd94a1fa69a148945b69b283e) (2019-01-10)


### Changes

Bump package core to 0.0.309 and docker to 0.0.29 and amazon to 0.0.150 and titus to 0.0.66 [#6343](https://github.com/spinnaker/deck/pull/6343) ([478936eb](https://github.com/spinnaker/deck/commit/478936eb234c71dbd94a1fa69a148945b69b283e))  
fix(*): allow modal to stay open on auto-close [#6329](https://github.com/spinnaker/deck/pull/6329) ([e802c451](https://github.com/spinnaker/deck/commit/e802c4515726e74f0f2157bde292fc43a6b46271))  



## [0.0.149](https://www.github.com/spinnaker/deck/compare/672e4d3f4f21de6ea13354a030dd9ba71a18900a...4eef691645f8683db2f5665615a5129d4bd142ec) (2019-01-09)


### Changes

Bump package core to 0.0.308 and amazon to 0.0.149 [#6336](https://github.com/spinnaker/deck/pull/6336) ([4eef6916](https://github.com/spinnaker/deck/commit/4eef691645f8683db2f5665615a5129d4bd142ec))  
refactor(stages): Deriving stages that provide version info for bakes [#6328](https://github.com/spinnaker/deck/pull/6328) ([6336232c](https://github.com/spinnaker/deck/commit/6336232cdc5fdc243233d9edaf88b5065e23fa4a))  



## [0.0.148](https://www.github.com/spinnaker/deck/compare/698b633aed7eeccf96e3d9eda622790da7e3df2a...672e4d3f4f21de6ea13354a030dd9ba71a18900a) (2019-01-07)


### Changes

Bump package core to 0.0.305 and amazon to 0.0.148 and titus to 0.0.65 [#6315](https://github.com/spinnaker/deck/pull/6315) ([672e4d3f](https://github.com/spinnaker/deck/commit/672e4d3f4f21de6ea13354a030dd9ba71a18900a))  
refactor(*): use mask-image CSS for cloud provider logos [#6280](https://github.com/spinnaker/deck/pull/6280) ([86baac96](https://github.com/spinnaker/deck/commit/86baac96af19a15b1339cd5f1856ee1e78d9d800))  
fix(aws): clarify all ports/protocols on IP range ingress [#6231](https://github.com/spinnaker/deck/pull/6231) ([fb6e6c07](https://github.com/spinnaker/deck/commit/fb6e6c07f9d2d82a69351617bfd49b247ca0bfe6))  
fix(aws): provide help explanation when load balancer delete is disabled [#6236](https://github.com/spinnaker/deck/pull/6236) ([e571c95c](https://github.com/spinnaker/deck/commit/e571c95c2a072856a1447a4253227921ba2deab0))  
feat(aws): make image sort options sticky; sort by TS by default [#6267](https://github.com/spinnaker/deck/pull/6267) ([9a6d8fbe](https://github.com/spinnaker/deck/commit/9a6d8fbe0a7008d4e1f57648985571748d0cc959))  
fix(amazon/loadBalancer): Restore the "security group removed" warning when switching regions ([73952e21](https://github.com/spinnaker/deck/commit/73952e21577f565b63c7960922f1e858395cbcb0))  



## [0.0.147](https://www.github.com/spinnaker/deck/compare/51c09597ea302a2907b7d11b890f238256d9501b...698b633aed7eeccf96e3d9eda622790da7e3df2a) (2018-12-21)


### Changes

chore(amazon): Bump version to 0.0.147 ([698b633a](https://github.com/spinnaker/deck/commit/698b633aed7eeccf96e3d9eda622790da7e3df2a))  
fix(bake): Allow null extended attributes in bake stages [#6256](https://github.com/spinnaker/deck/pull/6256) ([3afbd000](https://github.com/spinnaker/deck/commit/3afbd0001ad934ef9febe87579cc3fe81bd5f3f8))  
fix(aws): do not hardcode amiName when adding pipeline cluster [#6218](https://github.com/spinnaker/deck/pull/6218) ([6f056142](https://github.com/spinnaker/deck/commit/6f0561421a9eeb5d0816c901522bc969b0edb38f))  
refactor(amazon/serverGroup): don't sort images on every render [#6200](https://github.com/spinnaker/deck/pull/6200) ([c8488c1b](https://github.com/spinnaker/deck/commit/c8488c1ba9739f8b4f7f63e05035b194774c0a46))  
refactor(amazon/subnet): Reactify SubnetSelectField [#6192](https://github.com/spinnaker/deck/pull/6192) ([b6de8f82](https://github.com/spinnaker/deck/commit/b6de8f8268b20865ee64751e999f9166c6312d8d))  



## [0.0.146](https://www.github.com/spinnaker/deck/compare/e2e18e6b0c94416ba11ddf49a75e8373d7228983...51c09597ea302a2907b7d11b890f238256d9501b) (2018-12-12)


### Changes

chore(amazon): Bump version to 0.0.146 [#6195](https://github.com/spinnaker/deck/pull/6195) ([51c09597](https://github.com/spinnaker/deck/commit/51c09597ea302a2907b7d11b890f238256d9501b))  
fix(aws): really properly sort instance types [#6191](https://github.com/spinnaker/deck/pull/6191) ([2c15170f](https://github.com/spinnaker/deck/commit/2c15170f9cc61638a38b34cf180b17543ffcfd0d))  
fix(aws): sort instance type options in server group modal [#6190](https://github.com/spinnaker/deck/pull/6190) ([75d7e334](https://github.com/spinnaker/deck/commit/75d7e334a08a232a57567ce54bcf01ff7a66a2b1))  



## [0.0.145](https://www.github.com/spinnaker/deck/compare/d1bab7906a8038417737c271aa17c8dc4d5a0f18...e2e18e6b0c94416ba11ddf49a75e8373d7228983) (2018-12-12)


### Changes

Package bump [#6183](https://github.com/spinnaker/deck/pull/6183) ([e2e18e6b](https://github.com/spinnaker/deck/commit/e2e18e6b0c94416ba11ddf49a75e8373d7228983))  
feat(aws): allow sorting of image options in server group modal [#6174](https://github.com/spinnaker/deck/pull/6174) ([590eb613](https://github.com/spinnaker/deck/commit/590eb6130f242229d1f707067587225da66e4794))  
feat(core): increase visibility of hover on clickable pods [#6146](https://github.com/spinnaker/deck/pull/6146) ([8977593d](https://github.com/spinnaker/deck/commit/8977593d4891bea6d1ff4b6383baf5146993ec53))  
test(amazon/serverGroup): Add tests for AmazonImageSelectInput ([b3046d33](https://github.com/spinnaker/deck/commit/b3046d33abd85f4374a636257e47e0366ae352cd))  
test(amazon/serverGroup): fix failing test (due to angular upgrade) by reordering initialization call ([70233a1d](https://github.com/spinnaker/deck/commit/70233a1d6f0e6748b4fd268b12922add8c281040))  
refactor(amazon/serverGroup): Refactor code to load package images ([a5924573](https://github.com/spinnaker/deck/commit/a5924573d478878ffcb74f2595e32e263534db3e))  
feat(amazon/serverGroup): Make image loading lazy in clone server group modal - Extract AmazonImageSelectInput to a separate component ([e042f579](https://github.com/spinnaker/deck/commit/e042f579bdc132659b48ce1f0e09f306d5c7baf4))  
fix(amazon/serverGroup): Fix react key in forEach ([2729fde6](https://github.com/spinnaker/deck/commit/2729fde6f31b2d2e53c9335a03b77c5c8a1e7bc6))  



## [0.0.144](https://www.github.com/spinnaker/deck/compare/dfd67288664f18e8eb334f3f48efcc2e63725103...d1bab7906a8038417737c271aa17c8dc4d5a0f18) (2018-12-07)


### Changes

chore(amazon): Bump version to 0.0.144 ([d1bab790](https://github.com/spinnaker/deck/commit/d1bab7906a8038417737c271aa17c8dc4d5a0f18))  
refactor(core/account): Refactor AccountSelectInput to use 'value' prop ([307da1b9](https://github.com/spinnaker/deck/commit/307da1b93fb7a77364f51a2f7ea49652a20bb2d7))  
refactor(core/account): rename AccountSelectField to AccountSelectInput ([6f7f5435](https://github.com/spinnaker/deck/commit/6f7f543508176aee9eeb5ae40dea2bf03aa6a9ca))  
fix(core/amazon): avoid overflow on server group modal components [#6153](https://github.com/spinnaker/deck/pull/6153) ([9e3415cb](https://github.com/spinnaker/deck/commit/9e3415cb4ef5afd01831d64cb26ce9e7650c5db7))  
fix(amazon/serverGroup): Do not remove existing target groups [#6144](https://github.com/spinnaker/deck/pull/6144) ([98cedbf4](https://github.com/spinnaker/deck/commit/98cedbf4f3ea2b02db73814a014b0c0bf415298a))  
chore(*): Add core alias to module tsconfigs ([6b8188bb](https://github.com/spinnaker/deck/commit/6b8188bb54ea2e70987841079a8aff7debd8bd66))  
chore(amazon): Remove imports directly from 'amazon' ([29ec0b09](https://github.com/spinnaker/deck/commit/29ec0b0934ccdd222739d6cdbbf6a03cfb06b7f5))  
refactor(amazon/image): Convert amazon image reader to a TS class [#6118](https://github.com/spinnaker/deck/pull/6118) ([9cef2486](https://github.com/spinnaker/deck/commit/9cef2486758bb4bcc21095f451e7dafef952fdd9))  
fix(amazon): use arrow functions in server group modal [#6140](https://github.com/spinnaker/deck/pull/6140) ([96b8d0e5](https://github.com/spinnaker/deck/commit/96b8d0e5f7cab46e048b05b78ffcad54646aa8e6))  



## [0.0.143](https://www.github.com/spinnaker/deck/compare/1c4de1af3ca113bd6ce885352660de5680463db8...dfd67288664f18e8eb334f3f48efcc2e63725103) (2018-12-03)


### Changes

Package bump [#6130](https://github.com/spinnaker/deck/pull/6130) ([dfd67288](https://github.com/spinnaker/deck/commit/dfd67288664f18e8eb334f3f48efcc2e63725103))  
style(amazon): fix load balancer label alignment in server groups modal [#6126](https://github.com/spinnaker/deck/pull/6126) ([ff11921f](https://github.com/spinnaker/deck/commit/ff11921f3c8805d7e28a4584e2ea0773a7442931))  
fix(amazon/loadBalancer) Fixed load balancer with multiple accounts [#6108](https://github.com/spinnaker/deck/pull/6108) ([cc47b69f](https://github.com/spinnaker/deck/commit/cc47b69f45e67eaa7b532ebb585e0dab566bb175))  
refactor(amazon/serverGroup): use ngimport for $q [#6107](https://github.com/spinnaker/deck/pull/6107) ([42440c25](https://github.com/spinnaker/deck/commit/42440c25665ec241d2ceca2db3aa2555419fc529))  



## [0.0.142](https://www.github.com/spinnaker/deck/compare/7c7fa26205fcfbbac5e93a6780058801fa82b14e...1c4de1af3ca113bd6ce885352660de5680463db8) (2018-11-28)


### Changes

Package bump [#6091](https://github.com/spinnaker/deck/pull/6091) ([1c4de1af](https://github.com/spinnaker/deck/commit/1c4de1af3ca113bd6ce885352660de5680463db8))  
fix(amazon/securityGroup): Select default vpc by default (neat idea) [#6078](https://github.com/spinnaker/deck/pull/6078) ([d0a71ed5](https://github.com/spinnaker/deck/commit/d0a71ed549865661baa91b1a3c5c2c26725bbd90))  



## [0.0.141](https://www.github.com/spinnaker/deck/compare/0525282912db2987ea8167eb4749e14530c47da9...7c7fa26205fcfbbac5e93a6780058801fa82b14e) (2018-11-25)


### Changes

chore(amazon): Bump version to 0.0.141 [#6075](https://github.com/spinnaker/deck/pull/6075) ([7c7fa262](https://github.com/spinnaker/deck/commit/7c7fa26205fcfbbac5e93a6780058801fa82b14e))  
fix(aws): update load balancer zones when region changes [#6074](https://github.com/spinnaker/deck/pull/6074) ([40c3efeb](https://github.com/spinnaker/deck/commit/40c3efeb6e2d62604c0c69f934a7009e08fdcffc))  



## [0.0.140](https://www.github.com/spinnaker/deck/compare/65f618348ee72c62b628470c20501a6c42f250ec...0525282912db2987ea8167eb4749e14530c47da9) (2018-11-16)


### Changes

chore(amazon): Bump version to 0.0.140 ([05252829](https://github.com/spinnaker/deck/commit/0525282912db2987ea8167eb4749e14530c47da9))  
fix(amazon/serverGroup): Show the list of changed values (min/max/desired) [#6052](https://github.com/spinnaker/deck/pull/6052) ([f52e9913](https://github.com/spinnaker/deck/commit/f52e9913377588873aeeb241d5100afaacaea75c))  
fix(amazon/serverGroup): Track min/max values to desired better [#6051](https://github.com/spinnaker/deck/pull/6051) ([e316b54a](https://github.com/spinnaker/deck/commit/e316b54ab71f280f493c648e3bc1311e02cb82fc))  
fix(aws): only send user-changed capacity fields on resize [#6040](https://github.com/spinnaker/deck/pull/6040) ([c60b256f](https://github.com/spinnaker/deck/commit/c60b256f6194c72e837a0804c14f5284ba525777))  



## [0.0.139](https://www.github.com/spinnaker/deck/compare/afe47c65f7a51873cfcc2d19b9f693aeaa7e1730...65f618348ee72c62b628470c20501a6c42f250ec) (2018-11-13)


### Changes

chore(amazon): Bump version to 0.0.139 ([65f61834](https://github.com/spinnaker/deck/commit/65f618348ee72c62b628470c20501a6c42f250ec))  



## [0.0.138](https://www.github.com/spinnaker/deck/compare/0beaa47e3e5b9da498d11538227e18304c3241e8...afe47c65f7a51873cfcc2d19b9f693aeaa7e1730) (2018-11-13)


### Changes

Package bump [#6026](https://github.com/spinnaker/deck/pull/6026) ([afe47c65](https://github.com/spinnaker/deck/commit/afe47c65f7a51873cfcc2d19b9f693aeaa7e1730))  
fix(titus): Fix target group tag in clusters view [#6032](https://github.com/spinnaker/deck/pull/6032) ([64fdb902](https://github.com/spinnaker/deck/commit/64fdb9027c85e4a111eeada283b66ceace5d6329))  
refactor(amazon): convert resize modal to react [#6013](https://github.com/spinnaker/deck/pull/6013) ([b6151455](https://github.com/spinnaker/deck/commit/b6151455434bdcf9283dae58d703606dd2991916))  
fix(amazon+titus/serverGroup): Revert custom sort logic no longer needed with react-select ([a53d5d88](https://github.com/spinnaker/deck/commit/a53d5d8823af7eac991ec6d257431f6930cd28d1))  
fix(core/presentation): Fix perf issue by not processing diacritics in React Select ([54130031](https://github.com/spinnaker/deck/commit/54130031d35709633da5cc4bc4c8cca6fe8704b2))  
fix(aws/serverGroups): always show AWS sever group settings [#6003](https://github.com/spinnaker/deck/pull/6003) ([4c80cb86](https://github.com/spinnaker/deck/commit/4c80cb867188795a5ce66029e84def46dbcb0380))  
feat(core/presentation): Support internal Validator(s) in Inputs [#5995](https://github.com/spinnaker/deck/pull/5995) ([d08b202d](https://github.com/spinnaker/deck/commit/d08b202d637333b9b505b85593b48207b69db478))  



## [0.0.135](https://www.github.com/spinnaker/deck/compare/35f0f084d5f7768f2fc57a38a4df96de85e9a392...0beaa47e3e5b9da498d11538227e18304c3241e8) (2018-11-08)


### Changes

chore(amazon): Bump version to 0.0.135 ([0beaa47e](https://github.com/spinnaker/deck/commit/0beaa47e3e5b9da498d11538227e18304c3241e8))  
feat(core/presentation): Automatically generate '${label} cannot be negative' validation messages [#5988](https://github.com/spinnaker/deck/pull/5988) ([b8a95d73](https://github.com/spinnaker/deck/commit/b8a95d7356ec1e5bd140a9854c2dcc778f535803))  



## [0.0.134](https://www.github.com/spinnaker/deck/compare/a69126056d0dc5227d43a10684d6e5cbb169b3a1...35f0f084d5f7768f2fc57a38a4df96de85e9a392) (2018-11-07)


### Changes

chore(amazon): Bump version to 0.0.134 ([35f0f084](https://github.com/spinnaker/deck/commit/35f0f084d5f7768f2fc57a38a4df96de85e9a392))  
feat(core/presentation): Create SelectInput and use in HealthCheck component ([1858eadd](https://github.com/spinnaker/deck/commit/1858eadd2882111e7001eb513d7935d9913cd0ae))  
feat(core/presentation): Add minValue/maxValue Validators and use in AdvancedSettings ([fb2272a3](https://github.com/spinnaker/deck/commit/fb2272a30aa9fa7ec70af64ad6195264de2ca5e9))  
refactor(amazon/loadBalancer): ConfigureOidcConfigModal: use FormikFormField ([bd5d3d25](https://github.com/spinnaker/deck/commit/bd5d3d25a045490e6dadb63bb2a2be6f3bf3b77f))  
feat(core/presentation): Add CheckboxInput and NumberInput and use in ALBAdvancedSettings ([b3020c13](https://github.com/spinnaker/deck/commit/b3020c1373232dde3b96af6f48dd6d67455ae05d))  
fix(amazon): distinguish inclusive/exclusive bounds on scaling policy config [#5969](https://github.com/spinnaker/deck/pull/5969) ([27cd04b5](https://github.com/spinnaker/deck/commit/27cd04b5d328351e6f5e8307f790f755ecdfa5c5))  



## [0.0.133](https://www.github.com/spinnaker/deck/compare/d9b80f18d8a5fc0705ae3cadccbda440ac10929a...a69126056d0dc5227d43a10684d6e5cbb169b3a1) (2018-11-02)


### Changes

chore(amazon): Bump version to 0.0.133 ([a6912605](https://github.com/spinnaker/deck/commit/a69126056d0dc5227d43a10684d6e5cbb169b3a1))  
fix(amazon/loadBalancer): Fix region selection after account changes ([ccca8170](https://github.com/spinnaker/deck/commit/ccca8170a4d3e3ff8e930dd2408982d6b904cd67))  



## [0.0.132](https://www.github.com/spinnaker/deck/compare/b21451995189194cab8c5a384e4aa833a329c01b...d9b80f18d8a5fc0705ae3cadccbda440ac10929a) (2018-11-02)


### Changes

chore(amazon): Bump version to 0.0.132 [#5951](https://github.com/spinnaker/deck/pull/5951) ([d9b80f18](https://github.com/spinnaker/deck/commit/d9b80f18d8a5fc0705ae3cadccbda440ac10929a))  
fix(amazon/deploy): Fix clearing invalid security groups [#5948](https://github.com/spinnaker/deck/pull/5948) ([aa204d4e](https://github.com/spinnaker/deck/commit/aa204d4e35a2073c4301f5510ee956801b03dbf3))  



## [0.0.131](https://www.github.com/spinnaker/deck/compare/d455ea2968824e0ba302c40d8708de7f01c47b02...b21451995189194cab8c5a384e4aa833a329c01b) (2018-11-01)


### Changes

chore(amazon): Bump version to 0.0.131 ([b2145199](https://github.com/spinnaker/deck/commit/b21451995189194cab8c5a384e4aa833a329c01b))  
refactor(amazon/deploy): Convert rolling push strategy config to react ([2e629471](https://github.com/spinnaker/deck/commit/2e629471afb7c5714cba1a55ca7f408236d2bc9a))  
refactor(core): Convert DeploymentStrategySelector to react ([79587138](https://github.com/spinnaker/deck/commit/795871386c51f7c5b21bac4a3d2806853759ffe7))  



## [0.0.130](https://www.github.com/spinnaker/deck/compare/c6a5937404cca4d31ba36639b7c9ba8795181c95...d455ea2968824e0ba302c40d8708de7f01c47b02) (2018-10-31)


### Changes

chore(amazon): Bump version to 0.0.130 ([d455ea29](https://github.com/spinnaker/deck/commit/d455ea2968824e0ba302c40d8708de7f01c47b02))  
 chore(core/presentation): Update formik from 0.11.11 to 1.3.1 [#5917](https://github.com/spinnaker/deck/pull/5917) ([57b1e490](https://github.com/spinnaker/deck/commit/57b1e4904c04d04cb485d2850fd0675d41c4f60c))  



## [0.0.129](https://www.github.com/spinnaker/deck/compare/614349a46d61c07573e9a6ca969f8eb699458248...c6a5937404cca4d31ba36639b7c9ba8795181c95) (2018-10-24)


### Changes

chore(aws): bump to 0.0.129 ([c6a59374](https://github.com/spinnaker/deck/commit/c6a5937404cca4d31ba36639b7c9ba8795181c95))  
fix(providers/aws): fix deletionProtection not being checked when enabled ([8e3c45e7](https://github.com/spinnaker/deck/commit/8e3c45e71f243c1d15f0551d9b20c1b30aaa5f9a))  



## [0.0.128](https://www.github.com/spinnaker/deck/compare/a62cbe3fa63171b992f35ba4be0463e22ea34d32...614349a46d61c07573e9a6ca969f8eb699458248) (2018-10-24)


### Changes

chore(aws): bump to 0.0.128 [#5889](https://github.com/spinnaker/deck/pull/5889) ([614349a4](https://github.com/spinnaker/deck/commit/614349a46d61c07573e9a6ca969f8eb699458248))  
feat(provider/aws): allow editing of idle timeout and deletion protection ([b4571185](https://github.com/spinnaker/deck/commit/b45711858994012c830b4c740911d79ed1e54a50))  
Merge branch 'master' into force_rebake_is_always_false ([47b51714](https://github.com/spinnaker/deck/commit/47b51714a4c6a7844e1bc869a02c686834cdb1cd))  
fix(amazon): Fixed cloning security group across accounts [#5858](https://github.com/spinnaker/deck/pull/5858) ([3b008331](https://github.com/spinnaker/deck/commit/3b008331025b20874f026fee835cb3013047ff99))  
Merge branch 'master' into force_rebake_is_always_false ([b5ff2b88](https://github.com/spinnaker/deck/commit/b5ff2b8871d9e8f11eca5d9e087627cd4e9a8205))  
fix(bake): Execution details Rebake was always false when force rebaking ([55987ec1](https://github.com/spinnaker/deck/commit/55987ec13568470d609901fcda4d8e44d0f33e8d))  



## [0.0.127](https://www.github.com/spinnaker/deck/compare/5f3e71ab1c3008604bbe1ad8e922ae2645dc4409...a62cbe3fa63171b992f35ba4be0463e22ea34d32) (2018-10-16)


### Changes

chore(aws): bump to 0.0.127 ([a62cbe3f](https://github.com/spinnaker/deck/commit/a62cbe3fa63171b992f35ba4be0463e22ea34d32))  
fix(aws): Http healthchecks for NLBs ([d40dec77](https://github.com/spinnaker/deck/commit/d40dec778893912fc69e1920fed3480bfeef3961))  
refactor(*): Replace all uses of wrapped AccountSelectField with react version [#5832](https://github.com/spinnaker/deck/pull/5832) ([8e23f8fa](https://github.com/spinnaker/deck/commit/8e23f8fa06433783a1cd2ad5b1f571376340792b))  
feat(rollback): Support wait before disable during an orchestrated rollback [#5851](https://github.com/spinnaker/deck/pull/5851) ([28bb7583](https://github.com/spinnaker/deck/commit/28bb75830d56d62b54884fc8ce8426f82dbeda78))  
fix(deck): Change Build Stage description [#5839](https://github.com/spinnaker/deck/pull/5839) ([4f48a3ed](https://github.com/spinnaker/deck/commit/4f48a3edf2803785ac7122f20ad72dd374e06212))  



## [0.0.126](https://www.github.com/spinnaker/deck/compare/c891a2c701eb5ca621c83e195db8d35812099aa1...5f3e71ab1c3008604bbe1ad8e922ae2645dc4409) (2018-10-02)


### Changes

chore(amazon): Bump to 0.0.126 ([5f3e71ab](https://github.com/spinnaker/deck/commit/5f3e71ab1c3008604bbe1ad8e922ae2645dc4409))  
fix(amazon/loadBalancer): Fix editing load balancer from another app ([656be4d2](https://github.com/spinnaker/deck/commit/656be4d2df084a34b64dcb141e73b519cd9b6de1))  



## [0.0.125](https://www.github.com/spinnaker/deck/compare/b7248d304a0e3f8a34f5ced0c50d323914edfdab...c891a2c701eb5ca621c83e195db8d35812099aa1) (2018-10-02)


### Changes

chore(amazon): Bump to 0.0.125 ([c891a2c7](https://github.com/spinnaker/deck/commit/c891a2c701eb5ca621c83e195db8d35812099aa1))  
fix(amazon/deploy): Pass provider to security group refresh ([77a271d8](https://github.com/spinnaker/deck/commit/77a271d842c4e04e9bcf7c5ecf02b0eebefab01d))  
fix(titus/deploy): Firewall selector did not refresh on account change ([80db6ef4](https://github.com/spinnaker/deck/commit/80db6ef44f7e550e82e4212afc649b3c2edcf378))  



## [0.0.124](https://www.github.com/spinnaker/deck/compare/e940d997e3dfd6d64bfa51ca8aee8cb801282156...b7248d304a0e3f8a34f5ced0c50d323914edfdab) (2018-10-01)


### Changes

chore(amazon): Bump to 0.0.124 ([b7248d30](https://github.com/spinnaker/deck/commit/b7248d304a0e3f8a34f5ced0c50d323914edfdab))  
refactor(amazon): Export security group controls ([f175a365](https://github.com/spinnaker/deck/commit/f175a3655a0a248c63ca37a3435af855b99e2f81))  
chore(package): prepare -> prepublishOnly for everything [#5806](https://github.com/spinnaker/deck/pull/5806) ([06f45b5c](https://github.com/spinnaker/deck/commit/06f45b5c0da71227e4f1d7bb9e7187e95231f4d2))  
refactor(core/modal): Improve wizardPage types so no type param is necessary ([8aff36a9](https://github.com/spinnaker/deck/commit/8aff36a9582b775a8e1dc2344938ecf1e22f85be))  



## [0.0.123](https://www.github.com/spinnaker/deck/compare/ad38a5aeb63ed3ff19fc5d24d2c10d02e5ca1dc9...e940d997e3dfd6d64bfa51ca8aee8cb801282156) (2018-09-26)


### Changes

chore(amazon): Bump to 0.0.123 ([e940d997](https://github.com/spinnaker/deck/commit/e940d997e3dfd6d64bfa51ca8aee8cb801282156))  
fix(amazon/loadBalancer): Stop adding default security groups when editing [#5780](https://github.com/spinnaker/deck/pull/5780) ([afe19743](https://github.com/spinnaker/deck/commit/afe197431fb3d14c40dec8aec20fc06b7856be33))  
fix(amazon/loadBalancer): Do not ask for an SSL cert unless it's an SSL protocol [#5783](https://github.com/spinnaker/deck/pull/5783) ([5e0d916a](https://github.com/spinnaker/deck/commit/5e0d916adeb93d7c594ddcc8d3c596005c236e4a))  



## [0.0.122](https://www.github.com/spinnaker/deck/compare/56daf336535c91c25babe53d50b13ce3411b92f6...ad38a5aeb63ed3ff19fc5d24d2c10d02e5ca1dc9) (2018-09-25)


### Changes

chore(amazon): Bump to 0.0.122 ([ad38a5ae](https://github.com/spinnaker/deck/commit/ad38a5aeb63ed3ff19fc5d24d2c10d02e5ca1dc9))  
refactor(core/modal): Use `formik` prop instead of spreading.   Simplify WizardPage props. ([a1d03e0e](https://github.com/spinnaker/deck/commit/a1d03e0eeb0ea25f87cf5deac644f4f5025c2b0d))  
fix(amazon/loadBalancer): Show cert input box if ssl certs not able to load [#5774](https://github.com/spinnaker/deck/pull/5774) ([a8dd5cb6](https://github.com/spinnaker/deck/commit/a8dd5cb6f102463072da4b1120acca0bba72646d))  
refactor(amazon/deploy): Use new note section in wizard pages ([d5e7aaf4](https://github.com/spinnaker/deck/commit/d5e7aaf4dd929c442b7acd50360c5ee6eafdb4cc))  
refactor(amazon/deploy): Support hiding load balancers and/or target groups in load balancer selector ([ccecdd7c](https://github.com/spinnaker/deck/commit/ccecdd7cd09b131135a86d97bdfaf4e85eebea8c))  
refactor(amazon/deploy): Clean up interfaces and comments; fix a validation error typo ([3134bfdf](https://github.com/spinnaker/deck/commit/3134bfdfe8b25ae3351fd3510eba30a1fad6cbbd))  



## [0.0.121](https://www.github.com/spinnaker/deck/compare/688bbdc8f94a3f5b67428a47f226198c135b6f98...56daf336535c91c25babe53d50b13ce3411b92f6) (2018-09-19)


### Changes

chore(core/amazon): bump core to 0.0.266, amazon to 0.0.121 [#5760](https://github.com/spinnaker/deck/pull/5760) ([56daf336](https://github.com/spinnaker/deck/commit/56daf336535c91c25babe53d50b13ce3411b92f6))  
refactor(core/modal): Use TSX generics to render WizardModal ([2fad82c8](https://github.com/spinnaker/deck/commit/2fad82c8ddfdc912e7a7d72c98913351d12aa10c))  
refactor(formik): Use TSX generics to render Formik components ([17dc8d84](https://github.com/spinnaker/deck/commit/17dc8d84ff4ceec9376109db25a76b95819e122b))  
chore(prettier): Just Update Prettier™ [#5754](https://github.com/spinnaker/deck/pull/5754) ([709f30f6](https://github.com/spinnaker/deck/commit/709f30f6eff0c8862cb8736465e4fd152abd693c))  
feat(kayenta): show image sources [#5749](https://github.com/spinnaker/deck/pull/5749) ([ffb7a8bb](https://github.com/spinnaker/deck/commit/ffb7a8bbf2fc42f110baef75b0860ae0d92ef447))  
refactor(core/application): Rename createApplication to createApplicationForTests [#5737](https://github.com/spinnaker/deck/pull/5737) ([536e42b4](https://github.com/spinnaker/deck/commit/536e42b42bced4c1d4dfc9229813931d885ec80e))  
Security groups standalone missing vpcName [#5736](https://github.com/spinnaker/deck/pull/5736) ([3a10a067](https://github.com/spinnaker/deck/commit/3a10a0673e09ff5984ba9ac7a9e7f7f2ffe1d6e0))  



## [0.0.120](https://www.github.com/spinnaker/deck/compare/8647c7be239a995f2e4ea11d84e76027f314334a...688bbdc8f94a3f5b67428a47f226198c135b6f98) (2018-09-07)


### Changes

chore(amazon): Bump to 0.0.120 [#5716](https://github.com/spinnaker/deck/pull/5716) ([688bbdc8](https://github.com/spinnaker/deck/commit/688bbdc8f94a3f5b67428a47f226198c135b6f98))  
fix(amazon/deploy): Fix instance monitoring and ebs optimized checkboxes [#5715](https://github.com/spinnaker/deck/pull/5715) ([fddfded5](https://github.com/spinnaker/deck/commit/fddfded59a531512840312a51362c99877678692))  



## [0.0.119](https://www.github.com/spinnaker/deck/compare/aa57563a64f3226a23a1b6b49ab5396c348afc39...8647c7be239a995f2e4ea11d84e76027f314334a) (2018-09-05)


### Changes

chore(amazon): Bump to 0.0.119 ([8647c7be](https://github.com/spinnaker/deck/commit/8647c7be239a995f2e4ea11d84e76027f314334a))  



## [0.0.118](https://www.github.com/spinnaker/deck/compare/3431d4a5df30a00d057576e6ef900eca07a53600...aa57563a64f3226a23a1b6b49ab5396c348afc39) (2018-09-05)


### Changes

chore(amazon): Bump to 0.0.118 ([aa57563a](https://github.com/spinnaker/deck/commit/aa57563a64f3226a23a1b6b49ab5396c348afc39))  
refactor(core/task): Simplify task monitoring in React Modals Some other minor modal related bug fixes ([94c30e04](https://github.com/spinnaker/deck/commit/94c30e04a797d83834a1a074149a73f886ebfcc1))  
fix(core): Server group details could not be opened [#5688](https://github.com/spinnaker/deck/pull/5688) ([e807e902](https://github.com/spinnaker/deck/commit/e807e9024e0c865258a40e33ac16fb4c5f5d4c83))  
fix(amazon/deploy): Allow spel expressions in ami names [#5694](https://github.com/spinnaker/deck/pull/5694) ([28548ea0](https://github.com/spinnaker/deck/commit/28548ea045d4593852dd878d8ed05fc706887bb9))  
fix(amazon): Fix availability zone selector showing correct state [#5693](https://github.com/spinnaker/deck/pull/5693) ([8f2872e1](https://github.com/spinnaker/deck/commit/8f2872e1b6e65b3919917159847d7ab8cdc980f0))  
feat(amazon/loadBalancers): Allow setting to disable manual oidc config [#5683](https://github.com/spinnaker/deck/pull/5683) ([51a70aed](https://github.com/spinnaker/deck/commit/51a70aedc2b02fc2cdc2c3e653cdd88cb1b45a4b))  
fix(amazon/bake): Cleanup labels when docker store type ([aa4a449f](https://github.com/spinnaker/deck/commit/aa4a449f8c05196116f3326fb856ed091936e364))  



## [0.0.117](https://www.github.com/spinnaker/deck/compare/965c009a88133010f3c1a849ec9f922b64dda62e...3431d4a5df30a00d057576e6ef900eca07a53600) (2018-08-28)


### Changes

chore(core,amazon): Bump core to 0.0.262, amazon to 0.0.117 [#5677](https://github.com/spinnaker/deck/pull/5677) ([3431d4a5](https://github.com/spinnaker/deck/commit/3431d4a5df30a00d057576e6ef900eca07a53600))  
fix(amazon): Security Group cloning did not refresh fields with changes to region/account/vpc [#5647](https://github.com/spinnaker/deck/pull/5647) ([0a58daed](https://github.com/spinnaker/deck/commit/0a58daed3e79e78823542950d7e64caf3005dd27))  
feat(amazon/instance): Switch General Purpose instance from m4 -> m5 [#5665](https://github.com/spinnaker/deck/pull/5665) ([686bbef5](https://github.com/spinnaker/deck/commit/686bbef5aaa60ac2ab819a0fd7ca4272dacb409e))  
fix(ux): minor text revisions for server group enable modal [#5664](https://github.com/spinnaker/deck/pull/5664) ([c64b6d78](https://github.com/spinnaker/deck/commit/c64b6d7808b234e61598a629b496f4bd3a907e17))  
fix(amazon/deploy): Use the right text for submit btn when cloning [#5636](https://github.com/spinnaker/deck/pull/5636) ([3873af51](https://github.com/spinnaker/deck/commit/3873af5192b682aff3ab03b1e142b6d05688e029))  



## [0.0.116](https://www.github.com/spinnaker/deck/compare/02696caaf254ed3cddc3c5059d3f214c3969edc4...965c009a88133010f3c1a849ec9f922b64dda62e) (2018-08-17)


### Changes

chore(amazon): Bump to 0.0.116 [#5633](https://github.com/spinnaker/deck/pull/5633) ([965c009a](https://github.com/spinnaker/deck/commit/965c009a88133010f3c1a849ec9f922b64dda62e))  
fix(amazon/loadBalancer): Re-add ability to load all load balancers [#5632](https://github.com/spinnaker/deck/pull/5632) ([b9eac807](https://github.com/spinnaker/deck/commit/b9eac8073eb26644d55a248ca1e1d082155b0629))  



## [0.0.115](https://www.github.com/spinnaker/deck/compare/d97cdfda661fed314faf5ee354173ff3e1c86c26...02696caaf254ed3cddc3c5059d3f214c3969edc4) (2018-08-14)


### Changes

chore(amazon): Bump to 0.0.115 ([02696caa](https://github.com/spinnaker/deck/commit/02696caaf254ed3cddc3c5059d3f214c3969edc4))  
fix(loadBalancer): Use correct provider for titus server groups/instances [#5613](https://github.com/spinnaker/deck/pull/5613) ([060ad9cc](https://github.com/spinnaker/deck/commit/060ad9cc0eed5cf389d2923436f02911a2a25b6a))  
fix(amazon/loadBalancer): Fix load balancer tag on server groups for same named target groups in multiple accounts [#5608](https://github.com/spinnaker/deck/pull/5608) ([af4522f2](https://github.com/spinnaker/deck/commit/af4522f254e730a270ee67a88e309b15db942c50))  



## [0.0.114](https://www.github.com/spinnaker/deck/compare/29f05a44f35b9dae2e0014badd63b539c0c9dda9...d97cdfda661fed314faf5ee354173ff3e1c86c26) (2018-08-13)


### Changes

chore(amazon): Bump to 0.0.114 ([d97cdfda](https://github.com/spinnaker/deck/commit/d97cdfda661fed314faf5ee354173ff3e1c86c26))  
fix(amazon/deploy): Fix image searching by ami [#5601](https://github.com/spinnaker/deck/pull/5601) ([ccdf0ae0](https://github.com/spinnaker/deck/commit/ccdf0ae077eab786edc173056a935e8b3ce0fb06))  
fix(amazon/deploy): Fix iamRole, userData, instanceMonitoring, and ebsOptimized fields [#5597](https://github.com/spinnaker/deck/pull/5597) ([cf361505](https://github.com/spinnaker/deck/commit/cf361505c783a3f9a5b59101b7864982e097d8ae))  
fix(amazon/deploy): Fix security group selector to show pre-selected security groups ([0e5586c0](https://github.com/spinnaker/deck/commit/0e5586c0c3bca27f1183ede35e55435320bc3447))  
fix(amazon/deploy): Stop assuming fields are filled in ([cad41ca8](https://github.com/spinnaker/deck/commit/cad41ca8cd72ee0bab977decc336e3df2b7e26c9))  



## [0.0.113](https://www.github.com/spinnaker/deck/compare/510ff80d8e704a756ac34f82118f49192a88c957...29f05a44f35b9dae2e0014badd63b539c0c9dda9) (2018-08-11)


### Changes

chore(amazon): bump amazon to 0.0.113 ([29f05a44](https://github.com/spinnaker/deck/commit/29f05a44f35b9dae2e0014badd63b539c0c9dda9))  
fix(amazon/serverGroup): Improve image dropdown UX [#5582](https://github.com/spinnaker/deck/pull/5582) ([a2d68b94](https://github.com/spinnaker/deck/commit/a2d68b94627a57179fd1cee6eb98e86493c59ecb))  
fix(amazon/deploy): Make source capacity work again, other misc. fixes [#5581](https://github.com/spinnaker/deck/pull/5581) ([8712dbe0](https://github.com/spinnaker/deck/commit/8712dbe0e91f6e3da35e46f3df06fa9ff0e2ee81))  



## [0.0.112](https://www.github.com/spinnaker/deck/compare/8bcab584f339ab19c82b932c0f81f6f79c19cb35...510ff80d8e704a756ac34f82118f49192a88c957) (2018-08-09)


### Changes

chore(amazon): bump package to 112 ([510ff80d](https://github.com/spinnaker/deck/commit/510ff80d8e704a756ac34f82118f49192a88c957))  
fix(amazon/serverGroup): Fix clone dialog's capacity inputs ([45da9f5b](https://github.com/spinnaker/deck/commit/45da9f5b23567474a2874bea21e9228843788b79))  



## [0.0.111](https://www.github.com/spinnaker/deck/compare/daf2dc6741cea663bac6eb21c8b80d02d0d81db4...8bcab584f339ab19c82b932c0f81f6f79c19cb35) (2018-08-07)


### Changes

chore(amazon): Bump package to 0.0.111 ([8bcab584](https://github.com/spinnaker/deck/commit/8bcab584f339ab19c82b932c0f81f6f79c19cb35))  
fix(amazon/deploy): Fix send traffic to new instances checkbox ([eba58709](https://github.com/spinnaker/deck/commit/eba5870990aa8fcdffa39467c87de5111146bdb0))  
refactor(amazon): Convert the deploy dialog to react ([9d3e62c8](https://github.com/spinnaker/deck/commit/9d3e62c8a96b8e45367c3978161e2c714edc2106))  
refactor(amazon): Move AvailabilityZoneSelector ([47660367](https://github.com/spinnaker/deck/commit/4766036772eafb478fbb1512c6d4ac8511f8eaab))  
feat(tagging): Select which upstream stages to include in image search ([77bd90fc](https://github.com/spinnaker/deck/commit/77bd90fc94745bed44fddfd4b36edee496b2db35))  



## [0.0.109](https://www.github.com/spinnaker/deck/compare/b0a35f2d57e74de3ca6e62006fd380676d1855fd...daf2dc6741cea663bac6eb21c8b80d02d0d81db4) (2018-08-02)


### Changes

chore(amazon): Bump package to 0.0.109 ([daf2dc67](https://github.com/spinnaker/deck/commit/daf2dc6741cea663bac6eb21c8b80d02d0d81db4))  
chore(amazon): Remove old load balancer controller [#5559](https://github.com/spinnaker/deck/pull/5559) ([273b62c6](https://github.com/spinnaker/deck/commit/273b62c6408036bbb81afb4484ab6d26b0ddf3c3))  
 feat(amazon/loadBalancers): Support overriding OIDC client and add help text [#5558](https://github.com/spinnaker/deck/pull/5558) ([a3fc126c](https://github.com/spinnaker/deck/commit/a3fc126caa70bca8e864b5ecc0fe53d0c78b53dc))  
feat(ecs): adds ability to build ecs as a module [#5549](https://github.com/spinnaker/deck/pull/5549) ([e96f2de6](https://github.com/spinnaker/deck/commit/e96f2de67ebe012a84afad610768c50f05e15c7a))  
refactor(*): Add server group configuration command to all configured command functions ([4d23c971](https://github.com/spinnaker/deck/commit/4d23c971e1d3eb5a234d3315fd166c9d37059413))  
refactor(amazon): Make LoadBalancerModal use ReactModal ([802cdaf8](https://github.com/spinnaker/deck/commit/802cdaf8ea1d4fac8a224fa79c9d8901c2bec208))  



## [0.0.108](https://www.github.com/spinnaker/deck/compare/182393edc81bcc6ff6a6f8d6d78bb51e9e787ae6...b0a35f2d57e74de3ca6e62006fd380676d1855fd) (2018-07-23)


### Changes

Bump core and amazon [#5531](https://github.com/spinnaker/deck/pull/5531) ([b0a35f2d](https://github.com/spinnaker/deck/commit/b0a35f2d57e74de3ca6e62006fd380676d1855fd))  
feat(amazon/loadBalancer): Add confirmation if removing an existing oidc rule from an ALB [#5521](https://github.com/spinnaker/deck/pull/5521) ([8cec136d](https://github.com/spinnaker/deck/commit/8cec136d644273d1bf033a351e34d843ba6cdad5))  



## [0.0.107](https://www.github.com/spinnaker/deck/compare/bf2db729c1ea2469fbb38cda7cc4cf8e5bbde483...182393edc81bcc6ff6a6f8d6d78bb51e9e787ae6) (2018-07-03)


### Changes

chore(amazon): Bump to 0.0.107 ([182393ed](https://github.com/spinnaker/deck/commit/182393edc81bcc6ff6a6f8d6d78bb51e9e787ae6))  
feat(aws): nlb support ([06ba3c5a](https://github.com/spinnaker/deck/commit/06ba3c5ad83186fd8d72d1361580d8f68b8742f7))  



## [0.0.106](https://www.github.com/spinnaker/deck/compare/ecc0a5b9ae8f86649cc28a67a3c9011f46198490...bf2db729c1ea2469fbb38cda7cc4cf8e5bbde483) (2018-07-02)


### Changes

chore(amazon): Bump to 0.0.106 [#5501](https://github.com/spinnaker/deck/pull/5501) ([bf2db729](https://github.com/spinnaker/deck/commit/bf2db729c1ea2469fbb38cda7cc4cf8e5bbde483))  
fix(amazon/loadBalancer): Support order property in listener actions [#5495](https://github.com/spinnaker/deck/pull/5495) ([ca825e03](https://github.com/spinnaker/deck/commit/ca825e03edc55dfca283898feb3e127f318f9909))  
fix(amazon/loadBalancer): Make sure oidc actions have client secret [#5491](https://github.com/spinnaker/deck/pull/5491) ([693803f9](https://github.com/spinnaker/deck/commit/693803f9eadcae0718bd8aace94f847484fd5791))  
fix(amazon): Force user to ack removed load balancers before saving deploy config [#5485](https://github.com/spinnaker/deck/pull/5485) ([06a44949](https://github.com/spinnaker/deck/commit/06a4494978694ff7e63d874c554aa8c1bbd346b9))  
fix(amazon/loadBalancer): Fix DNS link in target group to match load balancer [#5489](https://github.com/spinnaker/deck/pull/5489) ([28265616](https://github.com/spinnaker/deck/commit/28265616335dba26a3c03bc8f6228c8d57f91941))  



## [0.0.105](https://www.github.com/spinnaker/deck/compare/66afd106837616c13e833237d65133358bcde377...ecc0a5b9ae8f86649cc28a67a3c9011f46198490) (2018-06-21)


### Changes

chore(amazon): bump package to 0.0.105 [#5477](https://github.com/spinnaker/deck/pull/5477) ([ecc0a5b9](https://github.com/spinnaker/deck/commit/ecc0a5b9ae8f86649cc28a67a3c9011f46198490))  
fix(titus): Fix links to titus servergroups from amazon load balancer [#5474](https://github.com/spinnaker/deck/pull/5474) ([d866ad44](https://github.com/spinnaker/deck/commit/d866ad4496a449f79c99c833366fdafd84f435db))  



## [0.0.104](https://www.github.com/spinnaker/deck/compare/6b2b882f90fdf3bb35360568f9ad793e82ea702f...66afd106837616c13e833237d65133358bcde377) (2018-06-13)


### Changes

chore(amazon): bump package to 0.0.104 [#5459](https://github.com/spinnaker/deck/pull/5459) ([66afd106](https://github.com/spinnaker/deck/commit/66afd106837616c13e833237d65133358bcde377))  
refactor(core/validation): Rename ValidationError to ValidationMessage, add 'type' prop ([0b619338](https://github.com/spinnaker/deck/commit/0b6193383880e20abd75d5e4e971e37c435e8352))  
fix(amazon/securityGroups): Fix name validator from clearing the name ([97c9798b](https://github.com/spinnaker/deck/commit/97c9798badbccca2434dd1fa689f49356c9410c0))  



## [0.0.103](https://www.github.com/spinnaker/deck/compare/9a5ef9e8ba88eddd37d3363f2aa1e7fb334f02d4...6b2b882f90fdf3bb35360568f9ad793e82ea702f) (2018-06-08)


### Changes

chore(amazon): Bump to 0.0.103 [#5438](https://github.com/spinnaker/deck/pull/5438) ([6b2b882f](https://github.com/spinnaker/deck/commit/6b2b882f90fdf3bb35360568f9ad793e82ea702f))  
feat(amazon/loadBalancers): Support authenticate-oidc actions [#5437](https://github.com/spinnaker/deck/pull/5437) ([7c963e5d](https://github.com/spinnaker/deck/commit/7c963e5d960a53e966bf6a42588cdd8c06aa38b4))  



## [0.0.102](https://www.github.com/spinnaker/deck/compare/c7e1ed61e3f8891dd7743f03a62f5ebb527cc8b8...9a5ef9e8ba88eddd37d3363f2aa1e7fb334f02d4) (2018-06-07)


### Changes

chore(amazon): bump package to 0.0.102 ([9a5ef9e8](https://github.com/spinnaker/deck/commit/9a5ef9e8ba88eddd37d3363f2aa1e7fb334f02d4))  
feat(core/presentation): Make CollapsibleSection less style opinionated [#5427](https://github.com/spinnaker/deck/pull/5427) ([4e891fea](https://github.com/spinnaker/deck/commit/4e891fea096b56e0fd897d0a67a0bb5b2e804221))  
fix(amazon): update help text [#5426](https://github.com/spinnaker/deck/pull/5426) ([fef19443](https://github.com/spinnaker/deck/commit/fef19443b8ae238f14081ecfb0f2d2b5baf332e9))  



## [0.0.101](https://www.github.com/spinnaker/deck/compare/b66b2b565268cfc33481dd55267d7ff176967e77...c7e1ed61e3f8891dd7743f03a62f5ebb527cc8b8) (2018-05-30)


### Changes

chore(amazon): bump package to 0.0.101 [#5407](https://github.com/spinnaker/deck/pull/5407) ([c7e1ed61](https://github.com/spinnaker/deck/commit/c7e1ed61e3f8891dd7743f03a62f5ebb527cc8b8))  
fix(amazon): filter app load balancer options by account/region in cluster config [#5403](https://github.com/spinnaker/deck/pull/5403) ([b724b9ee](https://github.com/spinnaker/deck/commit/b724b9eebb2a7ca500063dd9db763c9d902c2587))  



## [0.0.100](https://www.github.com/spinnaker/deck/compare/4ecf70d34120783ba7aaf5531047602d43947d0d...b66b2b565268cfc33481dd55267d7ff176967e77) (2018-05-25)


### Changes

chore(*): package bumps: core to 230, amazon to 100, titus to 31 [#5392](https://github.com/spinnaker/deck/pull/5392) ([b66b2b56](https://github.com/spinnaker/deck/commit/b66b2b565268cfc33481dd55267d7ff176967e77))  
refactor(amazon): de-angularize services [#5391](https://github.com/spinnaker/deck/pull/5391) ([445147da](https://github.com/spinnaker/deck/commit/445147dad6ef59d0befbf33f3347d5e6f0493260))  
refactor(core) de-angularize more services [#5390](https://github.com/spinnaker/deck/pull/5390) ([ca5df990](https://github.com/spinnaker/deck/commit/ca5df990b30a9208a682831803d376781a4cba87))  
refactor(core): de-angularize services [#5385](https://github.com/spinnaker/deck/pull/5385) ([37a96b16](https://github.com/spinnaker/deck/commit/37a96b168cae0cb5517c269e858bc16020f753c2))  
fix(amazon/loadBalancers): Fix instance health counts in load balancers view [#5386](https://github.com/spinnaker/deck/pull/5386) ([d646ad35](https://github.com/spinnaker/deck/commit/d646ad3536b51eaf4219dd5b4e9aa1bc93364455))  



## [0.0.99](https://www.github.com/spinnaker/deck/compare/6505931b46776e39e3132d695d5ddb95e422999f...4ecf70d34120783ba7aaf5531047602d43947d0d) (2018-05-24)


### Changes

chore(amazon): Bump to 0.0.99 ([4ecf70d3](https://github.com/spinnaker/deck/commit/4ecf70d34120783ba7aaf5531047602d43947d0d))  
refactor(core): De-angularize application read service ([96ddb67a](https://github.com/spinnaker/deck/commit/96ddb67a331f11ba292fd65111ef45eecdfbb0c4))  
refactor(core): de-angularize services [#5377](https://github.com/spinnaker/deck/pull/5377) ([bda420f9](https://github.com/spinnaker/deck/commit/bda420f98933852e734452a40d9ab788912dbb42))  



## [0.0.98](https://www.github.com/spinnaker/deck/compare/ee8eeba3406b261ae4cf8cf2c5a41589c0b191aa...6505931b46776e39e3132d695d5ddb95e422999f) (2018-05-23)


### Changes

chore(*): bump core/amazon/titus packages [#5375](https://github.com/spinnaker/deck/pull/5375) ([6505931b](https://github.com/spinnaker/deck/commit/6505931b46776e39e3132d695d5ddb95e422999f))  
refactor(core): de-angularize services [#5365](https://github.com/spinnaker/deck/pull/5365) ([5d159622](https://github.com/spinnaker/deck/commit/5d159622f43fb2aa859a46b47665c8c60165224e))  
feat(*/instance): add moniker info + env to instance link templates [#5367](https://github.com/spinnaker/deck/pull/5367) ([b70c6d98](https://github.com/spinnaker/deck/commit/b70c6d98a92adc3f2ddabb4d449c6ced3249480c))  



## [0.0.97](https://www.github.com/spinnaker/deck/compare/6e20b41114db7ebba7a7dabd55818349871c5595...ee8eeba3406b261ae4cf8cf2c5a41589c0b191aa) (2018-05-21)


### Changes

chore(*): bump core/amazon/titus packages [#5363](https://github.com/spinnaker/deck/pull/5363) ([ee8eeba3](https://github.com/spinnaker/deck/commit/ee8eeba3406b261ae4cf8cf2c5a41589c0b191aa))  
fix(amazon): unbreak firewall creation button [#5362](https://github.com/spinnaker/deck/pull/5362) ([bd6681f5](https://github.com/spinnaker/deck/commit/bd6681f5903f8fa0c96af13ff59b06f7c4daa5b4))  
refactor(core): de-angularize services [#5354](https://github.com/spinnaker/deck/pull/5354) ([ab380a10](https://github.com/spinnaker/deck/commit/ab380a105abd46116de1ea0b70c560f066732644))  
fix(aws): Show CLB cert selector when listener changes to SSL [#5355](https://github.com/spinnaker/deck/pull/5355) ([c2467e50](https://github.com/spinnaker/deck/commit/c2467e5097245c2c4d1db01bd725a5b9dd5f0390))  



## [0.0.96](https://www.github.com/spinnaker/deck/compare/24b11526b12f7085d15f7e611523c2038714fedc...6e20b41114db7ebba7a7dabd55818349871c5595) (2018-05-18)


### Changes

chore(*): bump packages for amazon/appengine/core/google/k8s/titus [#5353](https://github.com/spinnaker/deck/pull/5353) ([6e20b411](https://github.com/spinnaker/deck/commit/6e20b41114db7ebba7a7dabd55818349871c5595))  
refactor(*): de-angular-ize task reader/writer/executor [#5352](https://github.com/spinnaker/deck/pull/5352) ([56ede9d2](https://github.com/spinnaker/deck/commit/56ede9d28f704926f4fadf60af42612138e5b4ce))  



## [0.0.95](https://www.github.com/spinnaker/deck/compare/54b927975ca86b2a41e6bb3cce02252c23767a17...24b11526b12f7085d15f7e611523c2038714fedc) (2018-05-17)


### Changes

chore(*): Bump core/amazon/docker/titus/kayenta [#5344](https://github.com/spinnaker/deck/pull/5344) ([24b11526](https://github.com/spinnaker/deck/commit/24b11526b12f7085d15f7e611523c2038714fedc))  
refactor(*): De-angular pipelineConfigProvider and rename to PipelineRegistry [#5340](https://github.com/spinnaker/deck/pull/5340) ([40d11f8c](https://github.com/spinnaker/deck/commit/40d11f8c5a48284bca56e639e46cf846311a5dd4))  
fix(amazon): Make sure to show the target group from the right region ([27c84095](https://github.com/spinnaker/deck/commit/27c8409576d1aa6f6875c0d7a6dbea2ce4a40c8b))  
fix(amazon): remove listener certs when switching to HTTP [#5310](https://github.com/spinnaker/deck/pull/5310) ([b19bfb80](https://github.com/spinnaker/deck/commit/b19bfb803e8a77b848755bdaa72fde823bb2244b))  



## [0.0.94](https://www.github.com/spinnaker/deck/compare/5de32bfc0f7c093206db3a4694137c1923f183fc...54b927975ca86b2a41e6bb3cce02252c23767a17) (2018-05-09)


### Changes

chore(amazon): Bump to 0.0.94 ([54b92797](https://github.com/spinnaker/deck/commit/54b927975ca86b2a41e6bb3cce02252c23767a17))  
refactor(core): rename Security Groups to Firewalls [#5284](https://github.com/spinnaker/deck/pull/5284) ([d9291085](https://github.com/spinnaker/deck/commit/d929108509898833b535d20be01179dffaf187bf))  
feat(amazon): Cluster dialog - only preload load balancers associated with app [#5289](https://github.com/spinnaker/deck/pull/5289) ([0d97b3d6](https://github.com/spinnaker/deck/commit/0d97b3d6f584f6d63c06116c540d635088863cc6))  



## [0.0.93](https://www.github.com/spinnaker/deck/compare/e151bb1eab67cc7d3c6253505678db9f405b4b32...5de32bfc0f7c093206db3a4694137c1923f183fc) (2018-05-04)


### Changes

chore(core/amazon/titus): bump packages [#5267](https://github.com/spinnaker/deck/pull/5267) ([5de32bfc](https://github.com/spinnaker/deck/commit/5de32bfc0f7c093206db3a4694137c1923f183fc))  
perf(*): transpile to latest two modern browsers only [#5260](https://github.com/spinnaker/deck/pull/5260) ([caf1a8a8](https://github.com/spinnaker/deck/commit/caf1a8a84139fb4e5fe4c12959e02a9309d4a7db))  



## [0.0.92](https://www.github.com/spinnaker/deck/compare/8e8cee4bb137cb3b302b6aaa9a154b0abf7eeb18...e151bb1eab67cc7d3c6253505678db9f405b4b32) (2018-04-23)


### Changes

chore(amazon): Bump package to 0.0.92 ([e151bb1e](https://github.com/spinnaker/deck/commit/e151bb1eab67cc7d3c6253505678db9f405b4b32))  
refactor(*): De-angularize account service ([cc6d3332](https://github.com/spinnaker/deck/commit/cc6d333254159ab713a83bc89f13938d4c98e256))  
refactor(*): De-angularize API service ([cc8adc9d](https://github.com/spinnaker/deck/commit/cc8adc9df3f191ff2590a0bb5eea3f794cc85544))  
refactor(*): De-angularize authentication service ([a4d96cd3](https://github.com/spinnaker/deck/commit/a4d96cd340b49203f453afafd8d92512da6c831b))  
refactor(*): De-angularize cloud provider registry ([5aaf40d8](https://github.com/spinnaker/deck/commit/5aaf40d8599e372b3f49ba2db3dffbd711bf437e))  



## [0.0.91](https://www.github.com/spinnaker/deck/compare/b09bc91822d8694070260c51dce62d17ca87a0c5...8e8cee4bb137cb3b302b6aaa9a154b0abf7eeb18) (2018-04-17)


### Changes

chore(*): bump packages for de-angularized help contents [#5200](https://github.com/spinnaker/deck/pull/5200) ([8e8cee4b](https://github.com/spinnaker/deck/commit/8e8cee4bb137cb3b302b6aaa9a154b0abf7eeb18))  
refactor(*): de-angularize help contents/registry [#5199](https://github.com/spinnaker/deck/pull/5199) ([d6bfa5c2](https://github.com/spinnaker/deck/commit/d6bfa5c22c2196942230721ecc38ddb68e56874f))  



## [0.0.90](https://www.github.com/spinnaker/deck/compare/d2ea520a26bc3891c23def31a0fc84b9544f86c7...b09bc91822d8694070260c51dce62d17ca87a0c5) (2018-04-12)


### Changes

chore(amazon/titus): bump packages to 90, 10 [#5166](https://github.com/spinnaker/deck/pull/5166) ([b09bc918](https://github.com/spinnaker/deck/commit/b09bc91822d8694070260c51dce62d17ca87a0c5))  
fix(amazon): ask if reboot should consider amazon health only [#5156](https://github.com/spinnaker/deck/pull/5156) ([ecb84af6](https://github.com/spinnaker/deck/commit/ecb84af6ae09a24fe43318892834cbc649b3d686))  
refactor(*): De-angularize caches [#5161](https://github.com/spinnaker/deck/pull/5161) ([2f654733](https://github.com/spinnaker/deck/commit/2f6547336c43fdf5ced72dc029700e214d07c1b9))  



## [0.0.89](https://www.github.com/spinnaker/deck/compare/d0433c6b38152668aa378d9c7339c0605f80b0cc...d2ea520a26bc3891c23def31a0fc84b9544f86c7) (2018-04-11)


### Changes

chore(amazon): Bump to 0.0.89 ([d2ea520a](https://github.com/spinnaker/deck/commit/d2ea520a26bc3891c23def31a0fc84b9544f86c7))  
refactor(titus/loadBalancer): Refactor to use AmazonLoadBalancersTag ([04d0a882](https://github.com/spinnaker/deck/commit/04d0a882aaf204f5285438600671f52e8ba2d2d6))  
refactor(core/entityTag): Convert clusterTargetBuilder.service to plain JS ([378f228a](https://github.com/spinnaker/deck/commit/378f228aec8cdb4a02990785505a67c1b9ad2adc))  
refactor(core/naming): Convert angular naming.service to plain NameUtils ([d9f313bd](https://github.com/spinnaker/deck/commit/d9f313bd36508961f2a6be8d32c5155e0b5b893d))  
chore(tslint): ❯ npx tslint --fix -p tsconfig.json ([b1ddb67c](https://github.com/spinnaker/deck/commit/b1ddb67c2c7a74f451baac070a65c985c2b6fb8e))  
chore(tslint): Add prettier-tslint rules, manually fix lint errors that don't have --fix ([e74be825](https://github.com/spinnaker/deck/commit/e74be825f0f0c3e8ed24717188b0e76d6cc99bd8))  
Just Use Prettier™ ([532ab778](https://github.com/spinnaker/deck/commit/532ab7784ca93569308c8f2ab80a18d313b910f9))  



## [0.0.88](https://www.github.com/spinnaker/deck/compare/24c49bc7a5a5315385b79515351cc2097827d1fd...d0433c6b38152668aa378d9c7339c0605f80b0cc) (2018-04-04)


### Changes

chore(amazon/titus): bump packages to 0.0.88, 0.0.6 [#5112](https://github.com/spinnaker/deck/pull/5112) ([d0433c6b](https://github.com/spinnaker/deck/commit/d0433c6b38152668aa378d9c7339c0605f80b0cc))  
feat(aws): offer rollback when user enables an older server group [#5109](https://github.com/spinnaker/deck/pull/5109) ([555e7466](https://github.com/spinnaker/deck/commit/555e7466fe8bec03bb0b5dc53a7fde73a2dfd99a))  
feat(titus): Support rollback of disabled server groups (parity with aws) [#5100](https://github.com/spinnaker/deck/pull/5100) ([f742ffc4](https://github.com/spinnaker/deck/commit/f742ffc4898ce1f8e5e230db5535cc272a414d32))  



## [0.0.87](https://www.github.com/spinnaker/deck/compare/3435ed2c352c061056ea5be7c4d6b6186b2e9a5c...24c49bc7a5a5315385b79515351cc2097827d1fd) (2018-03-29)


### Changes

chore(amazon): bump package to 0.0.87 [#5097](https://github.com/spinnaker/deck/pull/5097) ([24c49bc7](https://github.com/spinnaker/deck/commit/24c49bc7a5a5315385b79515351cc2097827d1fd))  
Updating to use auto-generated files from icomoon.app [#5086](https://github.com/spinnaker/deck/pull/5086) ([c96f74b7](https://github.com/spinnaker/deck/commit/c96f74b720624631b65405e847593c0e52b4a5fb))  
feat(aws): Support rollback of a disabled server group [#5077](https://github.com/spinnaker/deck/pull/5077) ([ccdfe60a](https://github.com/spinnaker/deck/commit/ccdfe60a033e055a7a50a272d053d570a7f31576))  



## [0.0.86](https://www.github.com/spinnaker/deck/compare/fb7c39867fe26875022b751be0e33e82fec34f29...3435ed2c352c061056ea5be7c4d6b6186b2e9a5c) (2018-03-26)


### Changes

chore(core/amazon): bump packages to 0.0.179, 0.0.86 [#5076](https://github.com/spinnaker/deck/pull/5076) ([3435ed2c](https://github.com/spinnaker/deck/commit/3435ed2c352c061056ea5be7c4d6b6186b2e9a5c))  
fix(amazon): show launch configuration for empty server groups [#5074](https://github.com/spinnaker/deck/pull/5074) ([1f40b880](https://github.com/spinnaker/deck/commit/1f40b880c626128aade88e70ef14319b97f06545))  
chore(package): minify package bundles in production mode only ([a5bde826](https://github.com/spinnaker/deck/commit/a5bde826f2c641c6075fbb3900f740050892eb72))  



## [0.0.85](https://www.github.com/spinnaker/deck/compare/843c4be98f9efdcf0bdce5a1added5a9ded8aa66...fb7c39867fe26875022b751be0e33e82fec34f29) (2018-03-21)


### Changes

chore(core/amazon): bump core to 0.0.173, amazon to 0.0.85 [#5042](https://github.com/spinnaker/deck/pull/5042) ([fb7c3986](https://github.com/spinnaker/deck/commit/fb7c39867fe26875022b751be0e33e82fec34f29))  
fix(*): update icons to font-awesome 5 equivalents [#5040](https://github.com/spinnaker/deck/pull/5040) ([3a5e51da](https://github.com/spinnaker/deck/commit/3a5e51dab4950cc768503eb45eb8fb6f0922e089))  
chore(webpack): update webpack configurations for webpack 4 ([40981eae](https://github.com/spinnaker/deck/commit/40981eae4c404cd833cf186a9df50d3a56b5c927))  



## [0.0.84](https://www.github.com/spinnaker/deck/compare/93809653e043672d42294b4fe84632e3c49bcad3...843c4be98f9efdcf0bdce5a1added5a9ded8aa66) (2018-03-20)


### Changes

chore(core/amazon): bump core to 0.0.171, amazon to 0.0.84 [#5033](https://github.com/spinnaker/deck/pull/5033) ([843c4be9](https://github.com/spinnaker/deck/commit/843c4be98f9efdcf0bdce5a1added5a9ded8aa66))  
chore(core): upgrade to font-awesome 5 [#5029](https://github.com/spinnaker/deck/pull/5029) ([c2bdbf72](https://github.com/spinnaker/deck/commit/c2bdbf727746223e1c9e0a1d7fc56018a0e81736))  
fix(amazon/loadBalancers): Don't allow to create if duplicate target group names ([2a657a29](https://github.com/spinnaker/deck/commit/2a657a29c21aa092deefdfaac7a089b66173c57c))  
fix(amazon/loadBalancers): Disable editing target group names ([d1bab477](https://github.com/spinnaker/deck/commit/d1bab4775f2d5886ae5c99df9b254d15d7e47ca4))  



## [0.0.83](https://www.github.com/spinnaker/deck/compare/12f8df1347feb6713451ac461f4aa264560e000a...93809653e043672d42294b4fe84632e3c49bcad3) (2018-03-19)


### Changes

chore(amazon): bump package to 0.0.83 [#5024](https://github.com/spinnaker/deck/pull/5024) ([93809653](https://github.com/spinnaker/deck/commit/93809653e043672d42294b4fe84632e3c49bcad3))  
fix(amazon/serverGroup): Add spelLoadbalancers to IAmazonServerGroupCommand [#5025](https://github.com/spinnaker/deck/pull/5025) ([c2fc1e3e](https://github.com/spinnaker/deck/commit/c2fc1e3ed6b3269a4cf615c58356097440b19aa8))  
refactor(core/task+amazon/common): Reactify UserVerification and AwsModalFooter [#5015](https://github.com/spinnaker/deck/pull/5015) ([5bd7e6d2](https://github.com/spinnaker/deck/commit/5bd7e6d2a9c897a9976cd549362306019c02ea16))  
fix(amazon/deploy): Do not destroy SpEL based load balancers in deploy config [#5016](https://github.com/spinnaker/deck/pull/5016) ([be99e7fe](https://github.com/spinnaker/deck/commit/be99e7fe3315d20279559a2677bd7e408b32303d))  
fix(amazon/securityGroup): ingress selector would try to eagerly load vpcs so set empty default [#5017](https://github.com/spinnaker/deck/pull/5017) ([77bd7521](https://github.com/spinnaker/deck/commit/77bd7521e99c185094703986bbdfe89d6b486244))  
fix(amazon/serverGroups): Unable to open "Edit Scheduled Actions" modal [#5020](https://github.com/spinnaker/deck/pull/5020) ([1ba68716](https://github.com/spinnaker/deck/commit/1ba68716db57267817b923c952ab30796c742e81))  
fix(amazon/loadBalancers): Remove cert and ssl policies when submitting http listener [#5011](https://github.com/spinnaker/deck/pull/5011) ([1852a0b3](https://github.com/spinnaker/deck/commit/1852a0b30f8a8d254fd8a48cff4382a828ea60b0))  
fix(amazon/loadBalancers): Show target group sticky status appropriately [#5010](https://github.com/spinnaker/deck/pull/5010) ([99bf884f](https://github.com/spinnaker/deck/commit/99bf884f0b04e009cf733ae99da9d4574884a11d))  



## [0.0.82](https://www.github.com/spinnaker/deck/compare/cca2f070ec0ad1466775892015ca85e39f215b4b...12f8df1347feb6713451ac461f4aa264560e000a) (2018-03-14)


### Changes

chore(amazon): bump package to 0.0.82 [#5007](https://github.com/spinnaker/deck/pull/5007) ([12f8df13](https://github.com/spinnaker/deck/commit/12f8df1347feb6713451ac461f4aa264560e000a))  
feat(amazon/serverGroup): Change default capacity constraint to off [#5001](https://github.com/spinnaker/deck/pull/5001) ([965959c4](https://github.com/spinnaker/deck/commit/965959c465360219d07b154d9c03091fe3c5091d))  
refactor(amazon/core): move targetHealthyPercentage component to core [#4997](https://github.com/spinnaker/deck/pull/4997) ([bb33107f](https://github.com/spinnaker/deck/commit/bb33107f424c483ea77b1a447b3f1693c2a0c6bb))  



## [0.0.81](https://www.github.com/spinnaker/deck/compare/13d1749ecf2e07e01062578625199643972cff45...cca2f070ec0ad1466775892015ca85e39f215b4b) (2018-03-12)


### Changes

chore(*): Bump core and amazon [#4987](https://github.com/spinnaker/deck/pull/4987) ([cca2f070](https://github.com/spinnaker/deck/commit/cca2f070ec0ad1466775892015ca85e39f215b4b))  



## [0.0.80](https://www.github.com/spinnaker/deck/compare/e9444d4c5b878d25eb53a4bf29cf3bb5835b8d7e...13d1749ecf2e07e01062578625199643972cff45) (2018-03-07)


### Changes

chore(amazon): bump package to 0.0.80 ([13d1749e](https://github.com/spinnaker/deck/commit/13d1749ecf2e07e01062578625199643972cff45))  
feat(core/serverGroup): Refactor to smaller components and make them Overridable [#4941](https://github.com/spinnaker/deck/pull/4941) ([73e002cd](https://github.com/spinnaker/deck/commit/73e002cddcc9dd0512e870c06f805570411f7459))  
fix(core): Fix dismissal of new server group template modal [#4954](https://github.com/spinnaker/deck/pull/4954) ([f52d61a7](https://github.com/spinnaker/deck/commit/f52d61a7c1e440259c0b9addce8fb4e9183ff792))  



## [0.0.79](https://www.github.com/spinnaker/deck/compare/915f3cb16ce29df5b608efa697bc392cdb9f5682...e9444d4c5b878d25eb53a4bf29cf3bb5835b8d7e) (2018-03-05)


### Changes

chore(amazon): Bump to 0.0.79 [#4959](https://github.com/spinnaker/deck/pull/4959) ([e9444d4c](https://github.com/spinnaker/deck/commit/e9444d4c5b878d25eb53a4bf29cf3bb5835b8d7e))  
fix(amazon/loadBalancer): Validate target group name when region/account changes [#4955](https://github.com/spinnaker/deck/pull/4955) ([3abae77a](https://github.com/spinnaker/deck/commit/3abae77aefe4225f337c8d1b2a531ab51b400754))  



## [0.0.78](https://www.github.com/spinnaker/deck/compare/8cc1dbf30af1801756d8ffb1aac0165dad4eff38...915f3cb16ce29df5b608efa697bc392cdb9f5682) (2018-03-02)


### Changes

chore(amazon): Bump to 0.0.78 ([915f3cb1](https://github.com/spinnaker/deck/commit/915f3cb16ce29df5b608efa697bc392cdb9f5682))  
chore(*): Prepare for upgrading to react 16 [#4947](https://github.com/spinnaker/deck/pull/4947) ([0b9ebbaa](https://github.com/spinnaker/deck/commit/0b9ebbaacd9635efba39758f214e9e562d5efc2a))  



## [0.0.77](https://www.github.com/spinnaker/deck/compare/64080a5478fa2bab9f049d47bc0738f571ddbf6a...8cc1dbf30af1801756d8ffb1aac0165dad4eff38) (2018-03-01)


### Changes

chore(amazon): Bump to 0.0.77 ([8cc1dbf3](https://github.com/spinnaker/deck/commit/8cc1dbf30af1801756d8ffb1aac0165dad4eff38))  
chore(amazon/serverGroup): Export server group config interfaces ([6dcedb74](https://github.com/spinnaker/deck/commit/6dcedb74003c8704cb072c9722e9d1c3fec5477b))  



## [0.0.76](https://www.github.com/spinnaker/deck/compare/53435648b21664a14e0627b9805add137bebb68a...64080a5478fa2bab9f049d47bc0738f571ddbf6a) (2018-02-28)


### Changes

chore(amazon): Bump to 0.0.76 [#4929](https://github.com/spinnaker/deck/pull/4929) ([64080a54](https://github.com/spinnaker/deck/commit/64080a5478fa2bab9f049d47bc0738f571ddbf6a))  
feat(aws): allow setting the target type for target groups [#4915](https://github.com/spinnaker/deck/pull/4915) ([29fa10fd](https://github.com/spinnaker/deck/commit/29fa10fd9494bfcf8cc49d62e2782c0c3d37e8f3))  
feat(amazon/loadBalancers): Disable create button until security groups are refreshed ([6809acd6](https://github.com/spinnaker/deck/commit/6809acd68cca4048618023b81c5bb50d794f5ba4))  
fix(amazon/loadBalancers): Stop showing stale data in load balancer edit dialog [#4917](https://github.com/spinnaker/deck/pull/4917) ([c59d85ca](https://github.com/spinnaker/deck/commit/c59d85ca3b626a5e7dda683a8e41f49be882892a))  



## [0.0.75](https://www.github.com/spinnaker/deck/compare/c463fbc96aa6391201d9974a259da313a043aac8...53435648b21664a14e0627b9805add137bebb68a) (2018-02-20)


### Changes

chore(amazon): bump package to 0.0.75 [#4888](https://github.com/spinnaker/deck/pull/4888) ([53435648](https://github.com/spinnaker/deck/commit/53435648b21664a14e0627b9805add137bebb68a))  
fix(core/entityTag): Fix error message after successful entity tag updates [#4873](https://github.com/spinnaker/deck/pull/4873) ([56cd1c01](https://github.com/spinnaker/deck/commit/56cd1c0130a055a4454341aba0a8e68c37a42dd0))  
fix(core/overrideRegistry): Handle Override component synchronously, when possible [#4868](https://github.com/spinnaker/deck/pull/4868) ([d4a75982](https://github.com/spinnaker/deck/commit/d4a7598275a9bee9b89b8cdb3c79cca1c94d578d))  
fix(amazon): make security groups in server group details links [#4862](https://github.com/spinnaker/deck/pull/4862) ([555431c2](https://github.com/spinnaker/deck/commit/555431c225d7be8afea138c39e85a815b5f780e0))  
feat(amazon/loadBalancer): Export react load balancer components from `@spinnaker/amazon` [#4865](https://github.com/spinnaker/deck/pull/4865) ([a6b52fff](https://github.com/spinnaker/deck/commit/a6b52fffb1022399fe75dee18426040d5d8f35bc))  



## [0.0.74](https://www.github.com/spinnaker/deck/compare/eb317f55af6b62ebea9d86f0b9c4bc23a3608ac1...c463fbc96aa6391201d9974a259da313a043aac8) (2018-02-15)


### Changes

chore(amazon): bump to 0.0.74 ([c463fbc9](https://github.com/spinnaker/deck/commit/c463fbc96aa6391201d9974a259da313a043aac8))  
fix(amazon): Fix lint ([d7f1c613](https://github.com/spinnaker/deck/commit/d7f1c6138304a376d4389a2aff07267642d9af4a))  
fix(aws): filters target groups to only instance type ones [#4859](https://github.com/spinnaker/deck/pull/4859) ([87cb73d2](https://github.com/spinnaker/deck/commit/87cb73d2eefd4b835f61c89743bca7650b31e013))  
fix(amazon/loadBalancers): Remove clear all from security group select [#4854](https://github.com/spinnaker/deck/pull/4854) ([22dd423f](https://github.com/spinnaker/deck/commit/22dd423f7223386fb5c141b1beef553f0f0c6ae9))  



## [0.0.73](https://www.github.com/spinnaker/deck/compare/0e44d2a5da26f6862e8d7d59f1c5bd6d63d5d235...eb317f55af6b62ebea9d86f0b9c4bc23a3608ac1) (2018-02-13)


### Changes

chore(amazon): bump package to 0.0.73 [#4841](https://github.com/spinnaker/deck/pull/4841) ([eb317f55](https://github.com/spinnaker/deck/commit/eb317f55af6b62ebea9d86f0b9c4bc23a3608ac1))  
fix(amazon/serverGroup): Fix opening of Advanced Settings modal [#4840](https://github.com/spinnaker/deck/pull/4840) ([ac2fe386](https://github.com/spinnaker/deck/commit/ac2fe38611afeb1220c563e722a9429bf3a14ab8))  



## [0.0.72](https://www.github.com/spinnaker/deck/compare/63e3a560801a379660c0ccbd1333300cc46190e2...0e44d2a5da26f6862e8d7d59f1c5bd6d63d5d235) (2018-02-12)


### Changes

chore(amazon): bump package to 0.0.72 [#4835](https://github.com/spinnaker/deck/pull/4835) ([0e44d2a5](https://github.com/spinnaker/deck/commit/0e44d2a5da26f6862e8d7d59f1c5bd6d63d5d235))  
fix(amazon/vpc): Fix initial render of vpc [#4834](https://github.com/spinnaker/deck/pull/4834) ([fbc40c21](https://github.com/spinnaker/deck/commit/fbc40c21093fea02dd2abb9481c49a997b81b3d9))  
refactor(amazon): remove local storage caches: instance types, load balancers [#4777](https://github.com/spinnaker/deck/pull/4777) ([4cf3239d](https://github.com/spinnaker/deck/commit/4cf3239d43703d52a33f045fc9c41512f035b9a2))  
Fix a couple server group details issues [#4828](https://github.com/spinnaker/deck/pull/4828) ([ca478a05](https://github.com/spinnaker/deck/commit/ca478a05b7a24f2caba38e2eceb8f85d248384c0))  



## [0.0.71](https://www.github.com/spinnaker/deck/compare/8bab99fbca4d3c833e613714194eda86b5bbe7c8...63e3a560801a379660c0ccbd1333300cc46190e2) (2018-02-08)


### Changes

chore(amazon): Bump to 0.0.71 ([63e3a560](https://github.com/spinnaker/deck/commit/63e3a560801a379660c0ccbd1333300cc46190e2))  
fix(amazon/serverGroup): Fix edit scaling processes button [#4823](https://github.com/spinnaker/deck/pull/4823) ([cdaa660c](https://github.com/spinnaker/deck/commit/cdaa660c8464662a12398218f40f5d2265fdb044))  
fix(amazon/serverGroup): Make sure image exists before showing details [#4821](https://github.com/spinnaker/deck/pull/4821) ([90e72780](https://github.com/spinnaker/deck/commit/90e72780c682db5174176ff5a17183c3b73898cf))  
fix(amazon/loadBalancer): Handle when ALB listener actions may not have a target group [#4810](https://github.com/spinnaker/deck/pull/4810) ([f5a8b48f](https://github.com/spinnaker/deck/commit/f5a8b48fd8994e10c4ef5f28a34d2747366c4083))  
fix(core/loadBalancer): Only parse load balancer health state if exists [#4807](https://github.com/spinnaker/deck/pull/4807) ([66acd311](https://github.com/spinnaker/deck/commit/66acd311e687678eb7859bb4dd317ebbf030179b))  



## [0.0.69](https://www.github.com/spinnaker/deck/compare/f3b056edaa72f1fcd836d9a277f9541d87da6c24...8bab99fbca4d3c833e613714194eda86b5bbe7c8) (2018-02-06)


### Changes

chore(amazon): Bump to 0.0.69 ([8bab99fb](https://github.com/spinnaker/deck/commit/8bab99fbca4d3c833e613714194eda86b5bbe7c8))  



## [0.0.68](https://www.github.com/spinnaker/deck/compare/ff73aa897df43d4db1039d95cd2c54e441278852...f3b056edaa72f1fcd836d9a277f9541d87da6c24) (2018-02-06)


### Changes

chore(amazon): Bump to 0.0.68 ([f3b056ed](https://github.com/spinnaker/deck/commit/f3b056edaa72f1fcd836d9a277f9541d87da6c24))  
feat(amazon/loadBalancer): Better target group validation ([f20026b3](https://github.com/spinnaker/deck/commit/f20026b3d2c83f3806f8ced61d7b2bc1ee37b249))  
fix(amazon/serverGroup): Show enable server group when appropriate [#4803](https://github.com/spinnaker/deck/pull/4803) ([9453c927](https://github.com/spinnaker/deck/commit/9453c927f0a223b0b3137724f9472c1b84ad3579))  



## [0.0.67](https://www.github.com/spinnaker/deck/compare/01e54dfc16c5fc14d8667f630a5551d8c32fea3e...ff73aa897df43d4db1039d95cd2c54e441278852) (2018-02-05)


### Changes

chore(amazon): bump package to 0.0.67 [#4795](https://github.com/spinnaker/deck/pull/4795) ([ff73aa89](https://github.com/spinnaker/deck/commit/ff73aa897df43d4db1039d95cd2c54e441278852))  
fix(amazon/serverGroup): Fix showing server group actions [#4791](https://github.com/spinnaker/deck/pull/4791) ([5e4eebb0](https://github.com/spinnaker/deck/commit/5e4eebb00cead4285694a117c5802febce2af29e))  
fix(amazon/serverGroup): Close the server group details if server group cannot be found [#4789](https://github.com/spinnaker/deck/pull/4789) ([9e5c261d](https://github.com/spinnaker/deck/commit/9e5c261d1c3460d5766af81cf3d7d6ace402a49e))  
fix(amazon/serverGroup): Guard against missing launchConfig in details [#4788](https://github.com/spinnaker/deck/pull/4788) ([37343565](https://github.com/spinnaker/deck/commit/373435650d59b79b333bd45f286751b847dcd653))  
fix(amazon/loadBalancer): Disallow editing port/protocol [#4782](https://github.com/spinnaker/deck/pull/4782) ([1a435718](https://github.com/spinnaker/deck/commit/1a435718e962c05c2dfe0621252f5c0e67146732))  



## [0.0.66](https://www.github.com/spinnaker/deck/compare/4ba125dc8736c11472dd0f3a0b79cf02195d439f...01e54dfc16c5fc14d8667f630a5551d8c32fea3e) (2018-02-04)


### Changes

chore(amazon): bump package to 0.0.66 [#4779](https://github.com/spinnaker/deck/pull/4779) ([01e54dfc](https://github.com/spinnaker/deck/commit/01e54dfc16c5fc14d8667f630a5551d8c32fea3e))  
refactor(amazon/loadBalancer): Pull out load balancer create type since it is generic ([4d925dc3](https://github.com/spinnaker/deck/commit/4d925dc37f927b4839393560d968f84e95d9ab07))  



## [0.0.65](https://www.github.com/spinnaker/deck/compare/b6cdb55c044c7d49f7aaf490f91e4e179137934a...4ba125dc8736c11472dd0f3a0b79cf02195d439f) (2018-02-02)


### Changes

chore(amazon): bump package to 0.0.65 [#4767](https://github.com/spinnaker/deck/pull/4767) ([4ba125dc](https://github.com/spinnaker/deck/commit/4ba125dc8736c11472dd0f3a0b79cf02195d439f))  
fix(amazon): restore server group actions [#4765](https://github.com/spinnaker/deck/pull/4765) ([a6e3356a](https://github.com/spinnaker/deck/commit/a6e3356af11d815506bc4a10c2041603ee18fb94))  



## [0.0.64](https://www.github.com/spinnaker/deck/compare/9e936b427dbcfc737588f971b50320b595c3ff97...b6cdb55c044c7d49f7aaf490f91e4e179137934a) (2018-02-01)


### Changes

chore(amazon): Bump module to 0.0.64 ([b6cdb55c](https://github.com/spinnaker/deck/commit/b6cdb55c044c7d49f7aaf490f91e4e179137934a))  
feat(amazon/serverGroups): Convert server group details to react ([12815d9a](https://github.com/spinnaker/deck/commit/12815d9a2f6ffc1d0cca131f18dca7eaf2cd26d9))  
fix(amazon): copy EBS volumes when explicitly cloning an ASG [#4754](https://github.com/spinnaker/deck/pull/4754) ([9555458b](https://github.com/spinnaker/deck/commit/9555458b146ea1d673b8c6e666681d4459931126))  
feat(details): Migrate the following to @Overridable() decorator: [#4749](https://github.com/spinnaker/deck/pull/4749) ([ca2f5a47](https://github.com/spinnaker/deck/commit/ca2f5a4711d049e9c30bf2719bfc3fd16fcb3d39))  
fix(details): allow details dropdowns to wrap (at smaller widths). remove clearfix [#4748](https://github.com/spinnaker/deck/pull/4748) ([46c774d8](https://github.com/spinnaker/deck/commit/46c774d81fdd40edf0ce03f0735d81ceee30f995))  



## [0.0.63](https://www.github.com/spinnaker/deck/compare/fee2623b4b181b72570b5155c5e4bb8f517f6e1c...9e936b427dbcfc737588f971b50320b595c3ff97) (2018-01-25)


### Changes

chore(amazon): Bump to 0.0.63 [#4725](https://github.com/spinnaker/deck/pull/4725) ([9e936b42](https://github.com/spinnaker/deck/commit/9e936b427dbcfc737588f971b50320b595c3ff97))  
feat(amazon): Convert create load balancer modal to react [#4705](https://github.com/spinnaker/deck/pull/4705) ([20b42665](https://github.com/spinnaker/deck/commit/20b42665fd50728109393d631142a58ddf37f076))  



## [0.0.62](https://www.github.com/spinnaker/deck/compare/3b47e26e7ed474d99158091825eb902a7972725d...fee2623b4b181b72570b5155c5e4bb8f517f6e1c) (2018-01-24)


### Changes

chore(amazon): bump package to 0.0.62 [#4717](https://github.com/spinnaker/deck/pull/4717) ([fee2623b](https://github.com/spinnaker/deck/commit/fee2623b4b181b72570b5155c5e4bb8f517f6e1c))  
fix(core/amazon): wrap spinner size in quotes [#4710](https://github.com/spinnaker/deck/pull/4710) ([6838ab02](https://github.com/spinnaker/deck/commit/6838ab0212fcb4cbda0a15313de4ee61719a2438))  



## [0.0.61](https://www.github.com/spinnaker/deck/compare/a19f4377866d1aae35fb82460181d3550360d236...3b47e26e7ed474d99158091825eb902a7972725d) (2018-01-22)


### Changes

chore(amazon): bump package to 0.0.61 [#4702](https://github.com/spinnaker/deck/pull/4702) ([3b47e26e](https://github.com/spinnaker/deck/commit/3b47e26e7ed474d99158091825eb902a7972725d))  
fix(amazon): show load balancer actions menu [#4700](https://github.com/spinnaker/deck/pull/4700) ([67dae2b1](https://github.com/spinnaker/deck/commit/67dae2b12b802625360ffee903f7b8fb59d16ff7))  



## [0.0.60](https://www.github.com/spinnaker/deck/compare/dc818ee0d64b33c00ab9bcdef2c0ef869e37ddb2...a19f4377866d1aae35fb82460181d3550360d236) (2018-01-19)


### Changes

chore(amazon): bump package to 0.0.60 [#4692](https://github.com/spinnaker/deck/pull/4692) ([a19f4377](https://github.com/spinnaker/deck/commit/a19f4377866d1aae35fb82460181d3550360d236))  
refactor(amazon/loadBalancer): Make load balancer actions button a react component ([9bc4bde0](https://github.com/spinnaker/deck/commit/9bc4bde087fd6bce5b9f5ccb740b2fa8bad752d8))  
perf(core/clusters): use ReactVirtualized to render clusters [#4688](https://github.com/spinnaker/deck/pull/4688) ([c8d9d6dd](https://github.com/spinnaker/deck/commit/c8d9d6dd1e1dabe21681f0f493b4c39727be63a9))  
fix(amazon/loadBalancer): Show region and account when deleting a load balancer [#4684](https://github.com/spinnaker/deck/pull/4684) ([2cbb4c44](https://github.com/spinnaker/deck/commit/2cbb4c4424acf2a0e40df9b644f1c070511e6f0e))  
fix(amazon/loadBalancers): Fix editing load balancers that are associated with an app that no longer exists [#4682](https://github.com/spinnaker/deck/pull/4682) ([2fa4d98b](https://github.com/spinnaker/deck/commit/2fa4d98b70bbf19be79771e53efaa2fadeaf0576))  
style(amazon/application/projects/pipeline/google/kubernetes): Replacing fa-cog icons with new spinner [#4630](https://github.com/spinnaker/deck/pull/4630) ([c1f63e87](https://github.com/spinnaker/deck/commit/c1f63e879791473e86d0ecc0a316c3e94a9ba8de))  



## [0.0.59](https://www.github.com/spinnaker/deck/compare/43aa18186147f70ed40c5033ba0e40c325208705...dc818ee0d64b33c00ab9bcdef2c0ef869e37ddb2) (2018-01-17)


### Changes

chore(amazon): bump package to 0.0.59 [#4677](https://github.com/spinnaker/deck/pull/4677) ([dc818ee0](https://github.com/spinnaker/deck/commit/dc818ee0d64b33c00ab9bcdef2c0ef869e37ddb2))  
feat(amazon/serverGroup): warn that scaling policies will not work when capacity is pinned [#4668](https://github.com/spinnaker/deck/pull/4668) ([3155b2fa](https://github.com/spinnaker/deck/commit/3155b2fa811f920770543551fd232be8caf38ca0))  
feat(core): enable highlighting of invalid pristine fields [#4648](https://github.com/spinnaker/deck/pull/4648) ([f818cb90](https://github.com/spinnaker/deck/commit/f818cb900b02633ab80fbd663b78ac22f6fc7a54))  



## [0.0.58](https://www.github.com/spinnaker/deck/compare/904280371f7d04da1d35711dd15c816aebbb9448...43aa18186147f70ed40c5033ba0e40c325208705) (2018-01-04)


### Changes

chore(amazon): bump package to 0.0.58 [#4626](https://github.com/spinnaker/deck/pull/4626) ([43aa1818](https://github.com/spinnaker/deck/commit/43aa18186147f70ed40c5033ba0e40c325208705))  
fix(amazon): Default `copySourceCustomBlockDeviceMappings` to false [#4620](https://github.com/spinnaker/deck/pull/4620) ([740c077b](https://github.com/spinnaker/deck/commit/740c077b2589000d32d8334b6725bac8b4b3b694))  
style(amazon/azure/cloudfoundry/core/dcos/google/kubernetes/openstack/oracle): Added new spinners per designs [#4611](https://github.com/spinnaker/deck/pull/4611) ([47b809c3](https://github.com/spinnaker/deck/commit/47b809c3445b606d6c668ab1657811bf2924ca74))  



## [0.0.57](https://www.github.com/spinnaker/deck/compare/efe9238802962fb09c2a476b98f207f9c40ba57a...904280371f7d04da1d35711dd15c816aebbb9448) (2017-12-20)


### Changes

chore(amazon): bump to 0.0.57 [#4603](https://github.com/spinnaker/deck/pull/4603) ([90428037](https://github.com/spinnaker/deck/commit/904280371f7d04da1d35711dd15c816aebbb9448))  
feat(provider/aws): Pipeline support for rolling back a cluster [#4590](https://github.com/spinnaker/deck/pull/4590) ([36335e33](https://github.com/spinnaker/deck/commit/36335e33b6ee4708a81a09e7c6892efcc97bd045))  
refactor(*): Remove closeable.modal.controller [#4583](https://github.com/spinnaker/deck/pull/4583) ([2a0f1cac](https://github.com/spinnaker/deck/commit/2a0f1cac5f3237d6a97806cabe10c2f7e4b0a1ac))  



## [0.0.56](https://www.github.com/spinnaker/deck/compare/fd895195328d8318b1bf4536b804e3f369e1574e...efe9238802962fb09c2a476b98f207f9c40ba57a) (2017-12-11)


### Changes

chore(amazon): Bump to 0.0.56 [#4576](https://github.com/spinnaker/deck/pull/4576) ([efe92388](https://github.com/spinnaker/deck/commit/efe9238802962fb09c2a476b98f207f9c40ba57a))  
fix(amazon/loadBalancer): Fix target group name duplication validation [#4572](https://github.com/spinnaker/deck/pull/4572) ([dec0ad8e](https://github.com/spinnaker/deck/commit/dec0ad8e2c5030b13e8f1697b7436e74089c1142))  



## [0.0.55](https://www.github.com/spinnaker/deck/compare/8059844c44e29b524338ab984a06cdc31d82f1d8...fd895195328d8318b1bf4536b804e3f369e1574e) (2017-12-05)


### Changes

chore(amazon): bump package to 0.0.55 [#4560](https://github.com/spinnaker/deck/pull/4560) ([fd895195](https://github.com/spinnaker/deck/commit/fd895195328d8318b1bf4536b804e3f369e1574e))  
fix(amazon): omit spinnaker metadata tags when cloning server groups [#4554](https://github.com/spinnaker/deck/pull/4554) ([fca5a703](https://github.com/spinnaker/deck/commit/fca5a703775b7ccba401c92f4f7308275ade2906))  



## [0.0.54](https://www.github.com/spinnaker/deck/compare/ed2cd23f3dd0e755b81c3cf3d41aa74bdda0cb22...8059844c44e29b524338ab984a06cdc31d82f1d8) (2017-12-03)


### Changes

chore(amazon): bump package to 0.0.54 [#4553](https://github.com/spinnaker/deck/pull/4553) ([8059844c](https://github.com/spinnaker/deck/commit/8059844c44e29b524338ab984a06cdc31d82f1d8))  
feat(amazon): Support passing a capacity constraint during resize [#4545](https://github.com/spinnaker/deck/pull/4545) ([773833f6](https://github.com/spinnaker/deck/commit/773833f62e7b0a9b067e561f070395eb27795b1d))  



## [0.0.53](https://www.github.com/spinnaker/deck/compare/2c7b0b47152fd9bc111cbd7d9cebf3895e3e9286...ed2cd23f3dd0e755b81c3cf3d41aa74bdda0cb22) (2017-11-28)


### Changes

chore(amazon): bump to 0.0.53 [#4534](https://github.com/spinnaker/deck/pull/4534) ([ed2cd23f](https://github.com/spinnaker/deck/commit/ed2cd23f3dd0e755b81c3cf3d41aa74bdda0cb22))  
fix(amazon/loadBalancer): Edit load balancers in the context of the application they were created in/with [#4532](https://github.com/spinnaker/deck/pull/4532) ([c4c3a418](https://github.com/spinnaker/deck/commit/c4c3a418b734ba6baf17cbbd2756664ebc2afe4a))  
fix(*/loadBalancer): Fix a few undefined errors with load balancers [#4524](https://github.com/spinnaker/deck/pull/4524) ([ff1597bb](https://github.com/spinnaker/deck/commit/ff1597bbf7b6eb7983b11d7466cd471d87dcc0eb))  
fix(*/loadBalancer): Fix server group show/hide control [#4521](https://github.com/spinnaker/deck/pull/4521) ([727cbbea](https://github.com/spinnaker/deck/commit/727cbbea5f5938e8345f1ab4fedbf322eec4d31b))  
fix(amazon): provide better error message when bake stage fails [#4519](https://github.com/spinnaker/deck/pull/4519) ([a1c73091](https://github.com/spinnaker/deck/commit/a1c7309151c8b1fffa7a292ecec21df01eed0c39))  



## [0.0.52](https://www.github.com/spinnaker/deck/compare/3cf90b5359b04eb04528607c047eee7b0b3a07dd...2c7b0b47152fd9bc111cbd7d9cebf3895e3e9286) (2017-11-26)


### Changes

chore(amazon): bump package to 0.0.52 [#4518](https://github.com/spinnaker/deck/pull/4518) ([2c7b0b47](https://github.com/spinnaker/deck/commit/2c7b0b47152fd9bc111cbd7d9cebf3895e3e9286))  
fix(provider/amazon): Tags not copied when cloning ASG [#4491](https://github.com/spinnaker/deck/pull/4491) ([071618e4](https://github.com/spinnaker/deck/commit/071618e4bcf5b267b31274c71c28135a564bc0c1))  
chore(*): Update typescript and tslint and fix lint errors [#4494](https://github.com/spinnaker/deck/pull/4494) ([baa3155e](https://github.com/spinnaker/deck/commit/baa3155e710b9cde5c224e1e198b1704a6c774e4))  
refactor(*): Convert find ami execution details to react ([1896c7b2](https://github.com/spinnaker/deck/commit/1896c7b25fc18e57b639b6a7e9b3872d0c52b2f8))  
fix(core): omit moniker when caching security groups [#4482](https://github.com/spinnaker/deck/pull/4482) ([dab33173](https://github.com/spinnaker/deck/commit/dab331737b0a223b9bf41840480ec110902de5ab))  



## [0.0.51](https://www.github.com/spinnaker/deck/compare/7ba3763c6c8c333e70be889f489fd85d04a0f094...3cf90b5359b04eb04528607c047eee7b0b3a07dd) (2017-11-14)


### Changes

chore(amazon): bump package to 0.0.51 [#4442](https://github.com/spinnaker/deck/pull/4442) ([3cf90b53](https://github.com/spinnaker/deck/commit/3cf90b5359b04eb04528607c047eee7b0b3a07dd))  
chore(aws): placate linter [#4440](https://github.com/spinnaker/deck/pull/4440) ([7d488a75](https://github.com/spinnaker/deck/commit/7d488a7566532d8ff00e32864c053e9c4416327f))  
feat(amazon): Allow for enabling of a partially disabled server group [#4437](https://github.com/spinnaker/deck/pull/4437) ([777c1cda](https://github.com/spinnaker/deck/commit/777c1cda49fd5bb3a1a7958d55048de54f46fd11))  



## [0.0.50](https://www.github.com/spinnaker/deck/compare/c534a8dbbd62ca43167d3173af30a0b2c81261dd...7ba3763c6c8c333e70be889f489fd85d04a0f094) (2017-11-13)


### Changes

chore(amazon): bump package to 0.0.50 [#4435](https://github.com/spinnaker/deck/pull/4435) ([7ba3763c](https://github.com/spinnaker/deck/commit/7ba3763c6c8c333e70be889f489fd85d04a0f094))  
fix(aws): fall back to naming service on clone stage [#4423](https://github.com/spinnaker/deck/pull/4423) ([53433d13](https://github.com/spinnaker/deck/commit/53433d13c595df122f64e1a70ba1dd1965c29526))  
fix(*): Fix disable cluster execution details config [#4407](https://github.com/spinnaker/deck/pull/4407) ([da303b9c](https://github.com/spinnaker/deck/commit/da303b9c092e5e137e1107d393ca9b28ac0f5864))  
feat(core): Link to failing synthetic stage rather than "No reason provided." [#4381](https://github.com/spinnaker/deck/pull/4381) ([8906216c](https://github.com/spinnaker/deck/commit/8906216c2cf854e138e9d7672cbd23e97984dcd6))  



## [0.0.49](https://www.github.com/spinnaker/deck/compare/547b4408dbeef55ff8e95b4d51b69b9bbe7b4bc0...c534a8dbbd62ca43167d3173af30a0b2c81261dd) (2017-11-02)


### Changes

chore(amazon): bump package to 0.0.49 [#4361](https://github.com/spinnaker/deck/pull/4361) ([c534a8db](https://github.com/spinnaker/deck/commit/c534a8dbbd62ca43167d3173af30a0b2c81261dd))  
refactor(*/pipeline): Convert clone server group execution details to react [#4359](https://github.com/spinnaker/deck/pull/4359) ([25ff3a1a](https://github.com/spinnaker/deck/commit/25ff3a1abbdb72f6d128adba26fcaaa61d13ab90))  



## [0.0.48](https://www.github.com/spinnaker/deck/compare/d67066610cd85bce243f1adea61834451a8a463f...547b4408dbeef55ff8e95b4d51b69b9bbe7b4bc0) (2017-11-01)


### Changes

chore(amazon): bump package to 0.0.48 [#4351](https://github.com/spinnaker/deck/pull/4351) ([547b4408](https://github.com/spinnaker/deck/commit/547b4408dbeef55ff8e95b4d51b69b9bbe7b4bc0))  
fix(amazon): Traffic should be enabled when choosing a non-custom strategy [#4348](https://github.com/spinnaker/deck/pull/4348) ([4f2a14dc](https://github.com/spinnaker/deck/commit/4f2a14dcd5a5df40dac1e07db7510d95ac8b31a7))  
fix(core): revert word-break/overflow-wrap swaps [#4344](https://github.com/spinnaker/deck/pull/4344) ([84223b20](https://github.com/spinnaker/deck/commit/84223b2076d0daccde34e99ed99674dbe92a878a))  



## [0.0.47](https://www.github.com/spinnaker/deck/compare/71cf8b93362998851509d3ae1a84dd85ee65e782...d67066610cd85bce243f1adea61834451a8a463f) (2017-10-28)


### Changes

chore(amazon): bump package to 0.0.47 ([d6706661](https://github.com/spinnaker/deck/commit/d67066610cd85bce243f1adea61834451a8a463f))  
fix(canary): Fix moniker for baseline/canary clusters ([00367ebb](https://github.com/spinnaker/deck/commit/00367ebb176582aa57e3c34b3473e2b57f1dd403))  
fix(core): replace word-break CSS with overflow-wrap [#4334](https://github.com/spinnaker/deck/pull/4334) ([abfbb321](https://github.com/spinnaker/deck/commit/abfbb321fd7656168e9986fa02f903ab91c05779))  
chore(core): remove happypack in favor of thread-loader/cache-loader [#4330](https://github.com/spinnaker/deck/pull/4330) ([c661dccf](https://github.com/spinnaker/deck/commit/c661dccfe04fb44f78f64bcbd2a05debb8d46d43))  



## [0.0.46](https://www.github.com/spinnaker/deck/compare/5a5a84fae47f1f699de917d1e8c1188e5bced662...71cf8b93362998851509d3ae1a84dd85ee65e782) (2017-10-25)


### Changes

chore(amazon): bump package to 0.0.46 [#4331](https://github.com/spinnaker/deck/pull/4331) ([71cf8b93](https://github.com/spinnaker/deck/commit/71cf8b93362998851509d3ae1a84dd85ee65e782))  
fix(amazon): do not set useSourceCapacity on clones [#4329](https://github.com/spinnaker/deck/pull/4329) ([7fb4f331](https://github.com/spinnaker/deck/commit/7fb4f3317996d10ad9b97181a7865adbc612c7d2))  
refactor(*): More execution details refactoring [#4324](https://github.com/spinnaker/deck/pull/4324) ([ababde6d](https://github.com/spinnaker/deck/commit/ababde6dea29347e4bff81840c7e3b1fa685aaa0))  



## [0.0.45](https://www.github.com/spinnaker/deck/compare/f02ea11921121a7f489f5bb0ae998324552eac78...5a5a84fae47f1f699de917d1e8c1188e5bced662) (2017-10-24)


### Changes

chore(amazon): bump to 0.0.45 [#4317](https://github.com/spinnaker/deck/pull/4317) ([5a5a84fa](https://github.com/spinnaker/deck/commit/5a5a84fae47f1f699de917d1e8c1188e5bced662))  
refactor(*): Remove duplicate execution details templates [#4314](https://github.com/spinnaker/deck/pull/4314) ([de1524a8](https://github.com/spinnaker/deck/commit/de1524a8ae7897441d5fd150d5e4189dc67891a2))  



## [0.0.44](https://www.github.com/spinnaker/deck/compare/503b81923930a7f59418c542e78bc93c168984ec...f02ea11921121a7f489f5bb0ae998324552eac78) (2017-10-23)


### Changes

chore(amazon): bump package to 0.0.44 [#4312](https://github.com/spinnaker/deck/pull/4312) ([f02ea119](https://github.com/spinnaker/deck/commit/f02ea11921121a7f489f5bb0ae998324552eac78))  
bugfix(aws): don't show copy capacity options for clone dialog either [#4310](https://github.com/spinnaker/deck/pull/4310) ([7ae50010](https://github.com/spinnaker/deck/commit/7ae50010e57b08b3131cec5f64bab1c33ae378ad))  
fix(core/amazon): don't show copy capacity options for create server group [#4301](https://github.com/spinnaker/deck/pull/4301) ([7280c249](https://github.com/spinnaker/deck/commit/7280c249b405ff292ad30c4d0fc0efd180bd8fc3))  
refactor(*): Consistent bracket spacing [#4307](https://github.com/spinnaker/deck/pull/4307) ([484c91a3](https://github.com/spinnaker/deck/commit/484c91a34374fe06a4c4f52642f204b8f2fa0f78))  
fix(amazon/loadBalancer): Modify shouldComponentUpdate to allow for more specific updates [#4303](https://github.com/spinnaker/deck/pull/4303) ([15e873dd](https://github.com/spinnaker/deck/commit/15e873ddec09b7817ddb6971e4c4b7c4cd4047c1))  
feat(amazon): Add load balancer dns name to target group details [#4300](https://github.com/spinnaker/deck/pull/4300) ([42975b26](https://github.com/spinnaker/deck/commit/42975b265df1e924c91de7cd8e398bbc18b3ce25))  



## [0.0.43](https://www.github.com/spinnaker/deck/compare/d5bb3707b45d92f136a9b95e4034bacfdfc42979...503b81923930a7f59418c542e78bc93c168984ec) (2017-10-20)


### Changes

fix(core/amazon): fix application name on server group command [#4298](https://github.com/spinnaker/deck/pull/4298) ([503b8192](https://github.com/spinnaker/deck/commit/503b81923930a7f59418c542e78bc93c168984ec))  



## [0.0.42](https://www.github.com/spinnaker/deck/compare/0572dbfab6c8c2e32c12ef708c432a5a688444d8...d5bb3707b45d92f136a9b95e4034bacfdfc42979) (2017-10-19)


### Changes

chore(amazon): bump package to 0.0.42 [#4293](https://github.com/spinnaker/deck/pull/4293) ([d5bb3707](https://github.com/spinnaker/deck/commit/d5bb3707b45d92f136a9b95e4034bacfdfc42979))  
chore(core/amazon): make moniker changes library-friendly [#4294](https://github.com/spinnaker/deck/pull/4294) ([f9d0ed52](https://github.com/spinnaker/deck/commit/f9d0ed52586ec0b68a45f9ac34a5a7b12dac3d0d))  
feat(provider/amazon): Rollback support for PREVIOUS_IMAGE strategy [#4291](https://github.com/spinnaker/deck/pull/4291) ([5ab83af2](https://github.com/spinnaker/deck/commit/5ab83af27cfdbc0d0cbc7b6afec2f601175303ac))  
feat(moniker) - adding monikers to load balancers [#4278](https://github.com/spinnaker/deck/pull/4278) ([9141d193](https://github.com/spinnaker/deck/commit/9141d19377c2da19b5336ab5fd8a3af4600ba04b))  
feat(moniker) - adding monikers to the deploy stage [#4268](https://github.com/spinnaker/deck/pull/4268) ([963e3f0d](https://github.com/spinnaker/deck/commit/963e3f0d3890020b9d0f0e6499c3ee2647e5888e))  



## [0.0.41](https://www.github.com/spinnaker/deck/compare/32608b548b6260c4d7003a3877203989184d5d74...0572dbfab6c8c2e32c12ef708c432a5a688444d8) (2017-10-13)


### Changes

chore(amazon): bump package to 0.0.41 [#4262](https://github.com/spinnaker/deck/pull/4262) ([0572dbfa](https://github.com/spinnaker/deck/commit/0572dbfab6c8c2e32c12ef708c432a5a688444d8))  
refactor(aws): make transformScalingPolicy method public [#4261](https://github.com/spinnaker/deck/pull/4261) ([6a8bbbcc](https://github.com/spinnaker/deck/commit/6a8bbbcc6bed78f6c44ae31a108985c8744452be))  
fix(amazon/securityGroup): Fix lint warning [#4255](https://github.com/spinnaker/deck/pull/4255) ([d8bb34ed](https://github.com/spinnaker/deck/commit/d8bb34edabb5c03b55e78b6880cd9771d97f00b9))  



## [0.0.40](https://www.github.com/spinnaker/deck/compare/9b5513487eb6af0d245f4fea04520075ed661f34...32608b548b6260c4d7003a3877203989184d5d74) (2017-10-10)


### Changes

chore(amazon): bump package to 0.0.40 [#4245](https://github.com/spinnaker/deck/pull/4245) ([32608b54](https://github.com/spinnaker/deck/commit/32608b548b6260c4d7003a3877203989184d5d74))  
fix(core): catch modal dismiss [#4242](https://github.com/spinnaker/deck/pull/4242) ([f2f14b3e](https://github.com/spinnaker/deck/commit/f2f14b3e1bedec1e6dbd3ce61deeef388c802e98))  
feat(amazon): clarify naming/description on create load balancer/security group [#4241](https://github.com/spinnaker/deck/pull/4241) ([9100dee9](https://github.com/spinnaker/deck/commit/9100dee9479c940fdc5908d1fc32ea8b3ae9ac5a))  
fix(core/modal): avoid throwing errors on modal $dismiss [#4233](https://github.com/spinnaker/deck/pull/4233) ([ed9c20fb](https://github.com/spinnaker/deck/commit/ed9c20fb61a17b21a0aff8a81706996416dfa796))  



## [0.0.39](https://www.github.com/spinnaker/deck/compare/3f6f982da331764b47084ea6df92c8e49b505b38...9b5513487eb6af0d245f4fea04520075ed661f34) (2017-10-06)


### Changes

chore(amazon): bump package to 0.0.39 [#4231](https://github.com/spinnaker/deck/pull/4231) ([9b551348](https://github.com/spinnaker/deck/commit/9b5513487eb6af0d245f4fea04520075ed661f34))  
feat(moniker): adds monikers to stages that includes cluster-selects [#4220](https://github.com/spinnaker/deck/pull/4220) ([2cd995c9](https://github.com/spinnaker/deck/commit/2cd995c93633f9874c4dcdfe5aaead882614e313))  



## [0.0.38](https://www.github.com/spinnaker/deck/compare/ee2637c5a53d3874a802827739306ea56cce92e8...3f6f982da331764b47084ea6df92c8e49b505b38) (2017-10-04)


### Changes

chore(amazon): bump package to 0.0.38 [#4222](https://github.com/spinnaker/deck/pull/4222) ([3f6f982d](https://github.com/spinnaker/deck/commit/3f6f982da331764b47084ea6df92c8e49b505b38))  
fix(amazon): properly assign credentials when editing load balancers [#4221](https://github.com/spinnaker/deck/pull/4221) ([ace14a6c](https://github.com/spinnaker/deck/commit/ace14a6c5f53e2f055229816a45b24ab1e412786))  



## [0.0.37](https://www.github.com/spinnaker/deck/compare/7940d26171c166119ac6b609b49eafc412becd17...ee2637c5a53d3874a802827739306ea56cce92e8) (2017-10-04)


### Changes

chore(amazon): bump package to 0.0.37 [#4219](https://github.com/spinnaker/deck/pull/4219) ([ee2637c5](https://github.com/spinnaker/deck/commit/ee2637c5a53d3874a802827739306ea56cce92e8))  
style(core/amazon/google/kubernetes): Fixed adhoc hexcode colors to use spinnaker palette [#4206](https://github.com/spinnaker/deck/pull/4206) ([bd5c5c61](https://github.com/spinnaker/deck/commit/bd5c5c6191e37505967f759eaf44e0cc1ed7446b))  
chore(modules): Use webpack-node-externals to exclude node_modules from @spinnaker/* bundles [#4215](https://github.com/spinnaker/deck/pull/4215) ([2a3202f7](https://github.com/spinnaker/deck/commit/2a3202f7931405a57f745b428ded3b616c463905))  
fix(rollbacks): support for tolerating some instance failures [#4144](https://github.com/spinnaker/deck/pull/4144) ([539826bf](https://github.com/spinnaker/deck/commit/539826bf9a99df60092b376c7370e6f347e3299a))  
feat(provider/aws): Add help text to the LB internal checkbox [#4209](https://github.com/spinnaker/deck/pull/4209) ([e7f9ff42](https://github.com/spinnaker/deck/commit/e7f9ff42df538d1e6ff3b8f6f3e60dab1c0f0e07))  
style(all): Removed all less color variables and using CSS4 consolidated colors [#4204](https://github.com/spinnaker/deck/pull/4204) ([3c3eccc9](https://github.com/spinnaker/deck/commit/3c3eccc9c74277576cebbc3e8c5a883d01ebce8e))  
fix(provider/amazon) Enable & fix existing "Create LB" stage [#4184](https://github.com/spinnaker/deck/pull/4184) ([bfb90687](https://github.com/spinnaker/deck/commit/bfb90687893f7c475f8813ecb61b70503090c4fe))  
adds filter to only retrieve target cluster [#4196](https://github.com/spinnaker/deck/pull/4196) ([25669481](https://github.com/spinnaker/deck/commit/25669481200507693d951ff43a28e21c1c041f1d))  



## [0.0.36](https://www.github.com/spinnaker/deck/compare/94439f593f58d063c325541ad1ffe518f25d30ed...7940d26171c166119ac6b609b49eafc412becd17) (2017-10-01)


### Changes

chore(amazon): bump package to 0.0.36 [#4195](https://github.com/spinnaker/deck/pull/4195) ([7940d261](https://github.com/spinnaker/deck/commit/7940d26171c166119ac6b609b49eafc412becd17))  
style(core/amazon/oracle): Updated spinners to use new designs [#4190](https://github.com/spinnaker/deck/pull/4190) ([8574f53b](https://github.com/spinnaker/deck/commit/8574f53b60ce958778f50a6cf5b72c575dde6d23))  
clone stage now uses moniker [#4166](https://github.com/spinnaker/deck/pull/4166) ([720db5f3](https://github.com/spinnaker/deck/commit/720db5f3d67ac57f83af93a2de219eef7731a4b7))  
feat(sourceMaps): Embed sources in sourcemaps for lib builds [#4175](https://github.com/spinnaker/deck/pull/4175) ([14818c96](https://github.com/spinnaker/deck/commit/14818c96450d5d4a96d87cde068944719a5d83ae))  



## [0.0.35](https://www.github.com/spinnaker/deck/compare/f8b7a00a79765b0fd42abf6e4486db0572ecddd7...94439f593f58d063c325541ad1ffe518f25d30ed) (2017-09-28)


### Changes

Bump amazon [#4170](https://github.com/spinnaker/deck/pull/4170) ([94439f59](https://github.com/spinnaker/deck/commit/94439f593f58d063c325541ad1ffe518f25d30ed))  
chore(*): Re-enable source maps [#4169](https://github.com/spinnaker/deck/pull/4169) ([67918b35](https://github.com/spinnaker/deck/commit/67918b351b84ed950e5fe6245e39742a2cc7822e))  



## [0.0.34](https://www.github.com/spinnaker/deck/compare/c9bdcb925fad65a09f1af9e6e248efaca17df3e0...f8b7a00a79765b0fd42abf6e4486db0572ecddd7) (2017-09-27)


### Changes

chore(amazon): bump package to 0.0.34 [#4164](https://github.com/spinnaker/deck/pull/4164) ([f8b7a00a](https://github.com/spinnaker/deck/commit/f8b7a00a79765b0fd42abf6e4486db0572ecddd7))  
refactor(*): Replace class-autobind-decorator with lodash-decorators BindAll [#4150](https://github.com/spinnaker/deck/pull/4150) ([ecc40304](https://github.com/spinnaker/deck/commit/ecc403046e8e556c1892a69acb944c6cc7e04034))  
refactor(*): Remove angular-loader in favor of using `.name` explicitly [#4157](https://github.com/spinnaker/deck/pull/4157) ([f6669e57](https://github.com/spinnaker/deck/commit/f6669e5759cd43ea9e30471c6923945027078aed))  
feat(provider/amazon): Show NLBs in the Load Balancer screen and allow NLB target groups to be selected when deploying [#4149](https://github.com/spinnaker/deck/pull/4149) ([1e95bef1](https://github.com/spinnaker/deck/commit/1e95bef13cf39af324aeaf0fec01ef12b14648f7))  



## [0.0.33](https://www.github.com/spinnaker/deck/compare/7c9a30fe94b92c865714551eb01d68b75b6d184d...c9bdcb925fad65a09f1af9e6e248efaca17df3e0) (2017-09-20)


### Changes

chore(*): Bump core and amazon module versions [#4119](https://github.com/spinnaker/deck/pull/4119) ([c9bdcb92](https://github.com/spinnaker/deck/commit/c9bdcb925fad65a09f1af9e6e248efaca17df3e0))  
fix(provider/aws): Pre-populate spot price field with ancestor value on clone. Send '' instead of null when no spot price is requested. [#4114](https://github.com/spinnaker/deck/pull/4114) ([5c1c8a69](https://github.com/spinnaker/deck/commit/5c1c8a69bf037c7121fe264765805c058a7198df))  
fix(style): fix small regressions on charts, history views [#4112](https://github.com/spinnaker/deck/pull/4112) ([47f70be8](https://github.com/spinnaker/deck/commit/47f70be8bfd51eabf5e4a60f232c270ffe235a26))  
chore(search): move enabled filters [#4107](https://github.com/spinnaker/deck/pull/4107) ([59ed290a](https://github.com/spinnaker/deck/commit/59ed290ab0cb96fe851b1060351f0bef8f9f04d9))  



## [0.0.32](https://www.github.com/spinnaker/deck/compare/5debaad2f46c8c484fc91019c91dbb8f33b9ab5e...7c9a30fe94b92c865714551eb01d68b75b6d184d) (2017-09-08)


### Changes

chore(aws): bump package to 0.0.32 [#4088](https://github.com/spinnaker/deck/pull/4088) ([7c9a30fe](https://github.com/spinnaker/deck/commit/7c9a30fe94b92c865714551eb01d68b75b6d184d))  
fix(aws): fix markDirty call on target group removal in clone dialog [#4085](https://github.com/spinnaker/deck/pull/4085) ([4618b272](https://github.com/spinnaker/deck/commit/4618b272af9a1a8dc8badc9e56bf059bc0bf248e))  
feat(webpack): Improve performance of webpack build [#4081](https://github.com/spinnaker/deck/pull/4081) ([da32e834](https://github.com/spinnaker/deck/commit/da32e834e4df71c4185919c30a72e07039e77038))  



## [0.0.31](https://www.github.com/spinnaker/deck/compare/6b2c0ae9aa234c08e2bca8a56a3fe01979d73587...5debaad2f46c8c484fc91019c91dbb8f33b9ab5e) (2017-09-05)


### Changes

chore(amazon): bump package to 0.0.31 [#4074](https://github.com/spinnaker/deck/pull/4074) ([5debaad2](https://github.com/spinnaker/deck/commit/5debaad2f46c8c484fc91019c91dbb8f33b9ab5e))  
fix(provider/amazon): Fix AmazonLoadBalancerTag from exceptions when a target group cannot be found [#4068](https://github.com/spinnaker/deck/pull/4068) ([95abd0a2](https://github.com/spinnaker/deck/commit/95abd0a22aa7bedab610901c86a6ef35e607d590))  



## [0.0.30](https://www.github.com/spinnaker/deck/compare/720ffb73a5eca56a81e646f52023c4053a17e13b...6b2c0ae9aa234c08e2bca8a56a3fe01979d73587) (2017-09-01)


### Changes

chore(amazon): bump package to 0.0.30 [#4067](https://github.com/spinnaker/deck/pull/4067) ([6b2c0ae9](https://github.com/spinnaker/deck/commit/6b2c0ae9aa234c08e2bca8a56a3fe01979d73587))  
feat(aws): add feature flag to disable spot price field [#4062](https://github.com/spinnaker/deck/pull/4062) ([8b79fe79](https://github.com/spinnaker/deck/commit/8b79fe79167e267eb798a875d56609386c5aca25))  
feat(provider/aws): Added support for setting spot price [#4043](https://github.com/spinnaker/deck/pull/4043) ([81499f3b](https://github.com/spinnaker/deck/commit/81499f3bdb75ca5eaf0d3669a0a52e80ed3252a4))  



## [0.0.29](https://www.github.com/spinnaker/deck/compare/2065fd7324ca75724bf3a49244ce4552b1ba77a7...720ffb73a5eca56a81e646f52023c4053a17e13b) (2017-08-25)


### Changes

chore(amazon): bump package to 0.0.29 [#4048](https://github.com/spinnaker/deck/pull/4048) ([720ffb73](https://github.com/spinnaker/deck/commit/720ffb73a5eca56a81e646f52023c4053a17e13b))  
feat(amazon): implement preferSourceCapacity flag in deploy config [#4044](https://github.com/spinnaker/deck/pull/4044) ([63afeffc](https://github.com/spinnaker/deck/commit/63afeffc8aee5a1934a4eec580d187bfb0699ff5))  
feat(amazon): make root volume size configurable on bake stage [#4045](https://github.com/spinnaker/deck/pull/4045) ([b6a35765](https://github.com/spinnaker/deck/commit/b6a357650de61c031dbe02e4a787c65194f6110d))  



## [0.0.28](https://www.github.com/spinnaker/deck/compare/86a3265c305b2a4366e8f848fe19b590e670166c...2065fd7324ca75724bf3a49244ce4552b1ba77a7) (2017-08-23)


### Changes

chore(amazon): bump package to 0.0.28 [#4041](https://github.com/spinnaker/deck/pull/4041) ([2065fd73](https://github.com/spinnaker/deck/commit/2065fd7324ca75724bf3a49244ce4552b1ba77a7))  



## [0.0.27](https://www.github.com/spinnaker/deck/compare/4d45e542effda8ab58483e52a6f914f344ba2138...86a3265c305b2a4366e8f848fe19b590e670166c) (2017-08-22)


### Changes

chore(amazon): bump package to 0.0.27 [#4040](https://github.com/spinnaker/deck/pull/4040) ([86a3265c](https://github.com/spinnaker/deck/commit/86a3265c305b2a4366e8f848fe19b590e670166c))  
refactor(aws): consolidate scaling policy update operations [#4035](https://github.com/spinnaker/deck/pull/4035) ([f0daa3cb](https://github.com/spinnaker/deck/commit/f0daa3cb9957ddf4aba8c250952e20adb4d32b8f))  



## [0.0.26](https://www.github.com/spinnaker/deck/compare/b290592ecb29f8f0bccee3aa82f8342acf4363dc...4d45e542effda8ab58483e52a6f914f344ba2138) (2017-08-10)


### Changes

chore(amazon): bump package to 0.0.26 [#4011](https://github.com/spinnaker/deck/pull/4011) ([4d45e542](https://github.com/spinnaker/deck/commit/4d45e542effda8ab58483e52a6f914f344ba2138))  
fix(amazon): do not cache certificate data [#4010](https://github.com/spinnaker/deck/pull/4010) ([1ffaeb96](https://github.com/spinnaker/deck/commit/1ffaeb96084506064049a4a614b908dcfbcc34b8))  



## [0.0.25](https://www.github.com/spinnaker/deck/compare/dc2032f443aa49b321118191c0a4a70205995a98...b290592ecb29f8f0bccee3aa82f8342acf4363dc) (2017-08-07)


### Changes

chore(amazon): bump package to 0.0.25 [#3994](https://github.com/spinnaker/deck/pull/3994) ([b290592e](https://github.com/spinnaker/deck/commit/b290592ecb29f8f0bccee3aa82f8342acf4363dc))  
fix(core/loadBalancers): Fix z-index issues by converting load balancer list to a popover ([8a951be1](https://github.com/spinnaker/deck/commit/8a951be1f80f911075f6f149476eba9a99814d7b))  
fix(aws): Change EBS optimized flag based on AWS defaults [#3991](https://github.com/spinnaker/deck/pull/3991) ([9a728f09](https://github.com/spinnaker/deck/commit/9a728f09f2654286af1dade357daca8904411dc5))  
feat(provider/amazon): Add CRUD support for ALB listener rules [#3985](https://github.com/spinnaker/deck/pull/3985) ([a0e9135e](https://github.com/spinnaker/deck/commit/a0e9135e1f4a54a4fb49b9e4793a9d2866e5d693))  
React clusters view [#3882](https://github.com/spinnaker/deck/pull/3882) ([9d8abc9a](https://github.com/spinnaker/deck/commit/9d8abc9acee1212cd2dfa7dfc765ebd510914afa))  
refactor(aws): convert sg details advanced settings view to React [#3978](https://github.com/spinnaker/deck/pull/3978) ([ba33233e](https://github.com/spinnaker/deck/commit/ba33233ee3ae4c3a3774e8edfdab18d9e3263d98))  
fix(aws/loadbalancer): ensure timeout < interval [#3974](https://github.com/spinnaker/deck/pull/3974) ([a41c8d73](https://github.com/spinnaker/deck/commit/a41c8d73076357e605ef35c22705a24f6ff0861c))  
feat(core): add new tagging widget [#3966](https://github.com/spinnaker/deck/pull/3966) ([73792f44](https://github.com/spinnaker/deck/commit/73792f4430aab1edfe17b0ace9fb012d04dc1f4d))  
feat(provider/amazon): Combine load balancers and target groups in the deploy dialog [#3973](https://github.com/spinnaker/deck/pull/3973) ([43422ce2](https://github.com/spinnaker/deck/commit/43422ce294aedfe8264c2ed00cf6601d24670176))  



## [0.0.24](https://www.github.com/spinnaker/deck/compare/ab9af20efd914840e0fe4390732d817005bf8fec...dc2032f443aa49b321118191c0a4a70205995a98) (2017-07-25)


### Changes

chore(amazon): bump package to 0.0.24 [#3960](https://github.com/spinnaker/deck/pull/3960) ([dc2032f4](https://github.com/spinnaker/deck/commit/dc2032f443aa49b321118191c0a4a70205995a98))  
chore(core/amazon): update webpack configs for sourcemaps/externals [#3959](https://github.com/spinnaker/deck/pull/3959) ([6eb1890f](https://github.com/spinnaker/deck/commit/6eb1890fc95c81e4ba4d516327f498802dd1d83c))  



## [0.0.23](https://www.github.com/spinnaker/deck/compare/47720d976def9f17675c563a0af1ca62acb31e76...ab9af20efd914840e0fe4390732d817005bf8fec) (2017-07-25)


### Changes

chore(amazon): bump package to 0.0.23 [#3958](https://github.com/spinnaker/deck/pull/3958) ([ab9af20e](https://github.com/spinnaker/deck/commit/ab9af20efd914840e0fe4390732d817005bf8fec))  
fix(amazon): properly set disableScaleIn flag on target tracking policies [#3957](https://github.com/spinnaker/deck/pull/3957) ([c50a4f2d](https://github.com/spinnaker/deck/commit/c50a4f2d0e29a792099888862e19245c4ec3353e))  



## [0.0.22](https://www.github.com/spinnaker/deck/compare/1771e740454a69558731198d079de6521fdbbb84...47720d976def9f17675c563a0af1ca62acb31e76) (2017-07-25)


### Changes

chore(amazon): bump package to 0.0.22 [#3955](https://github.com/spinnaker/deck/pull/3955) ([47720d97](https://github.com/spinnaker/deck/commit/47720d976def9f17675c563a0af1ca62acb31e76))  
feat(amazon): implement target tracking policy support [#3948](https://github.com/spinnaker/deck/pull/3948) ([70a03720](https://github.com/spinnaker/deck/commit/70a03720118071bf90285519d3cd0746f2132d1a))  



## [0.0.21](https://www.github.com/spinnaker/deck/compare/982817b71e3188384a07659f7a9d621fb2385cbc...1771e740454a69558731198d079de6521fdbbb84) (2017-07-21)


### Changes

chore(amazon): bump package to 0.0.21 [#3950](https://github.com/spinnaker/deck/pull/3950) ([1771e740](https://github.com/spinnaker/deck/commit/1771e740454a69558731198d079de6521fdbbb84))  
fix(provider/amazon): If ASG only has one target group, tag should say target group [#3946](https://github.com/spinnaker/deck/pull/3946) ([b3674f92](https://github.com/spinnaker/deck/commit/b3674f928395b8787cae85e1b5fb0942315cc354))  
feat(provider/amazon): Support add/remove instance from target group [#3945](https://github.com/spinnaker/deck/pull/3945) ([9af5192c](https://github.com/spinnaker/deck/commit/9af5192cc531f4cf6f2394184333d1c0328bb578))  



## [0.0.20](https://www.github.com/spinnaker/deck/compare/5a1a1cdbc42aa8331626f6e5363cb5de7c9feb27...982817b71e3188384a07659f7a9d621fb2385cbc) (2017-07-21)


### Changes

chore(amazon): bump package to 0.0.20 [#3941](https://github.com/spinnaker/deck/pull/3941) ([982817b7](https://github.com/spinnaker/deck/commit/982817b71e3188384a07659f7a9d621fb2385cbc))  
feat(provider/amazon): Add ability to delete dependent ingress rules when deleting security group [#3939](https://github.com/spinnaker/deck/pull/3939) ([fe1d1060](https://github.com/spinnaker/deck/commit/fe1d106082bf68a42129a86e44b073b64dd2d7ba))  



## [0.0.19](https://www.github.com/spinnaker/deck/compare/4bc3a70baa55e2157d4089fb0baf5a61ba75de43...5a1a1cdbc42aa8331626f6e5363cb5de7c9feb27) (2017-07-18)


### Changes

chore(amazon): bump package to 0.0.19 [#3932](https://github.com/spinnaker/deck/pull/3932) ([5a1a1cdb](https://github.com/spinnaker/deck/commit/5a1a1cdbc42aa8331626f6e5363cb5de7c9feb27))  
feat(amazon): allow default VPC specification for security group creation [#3924](https://github.com/spinnaker/deck/pull/3924) ([a5893295](https://github.com/spinnaker/deck/commit/a5893295d4a7db7e062c4fed5306dcbda2fb454c))  
refactor(*): Update typescript to 2.4 and fix breaking changes ([072c1eff](https://github.com/spinnaker/deck/commit/072c1eff41a81fc933b2d6438d86e673e125587a))  
refactor(core): use common component for deploy initialization [#3889](https://github.com/spinnaker/deck/pull/3889) ([8754fcb5](https://github.com/spinnaker/deck/commit/8754fcb559561390d7bd7ee541a28166007ecea3))  



## [0.0.18](https://www.github.com/spinnaker/deck/compare/99cf6d0e41c898c2b0d72a795382477ffe881948...4bc3a70baa55e2157d4089fb0baf5a61ba75de43) (2017-07-13)


### Changes

chore(amazon): bump package to 0.0.18 [#3915](https://github.com/spinnaker/deck/pull/3915) ([4bc3a70b](https://github.com/spinnaker/deck/commit/4bc3a70baa55e2157d4089fb0baf5a61ba75de43))  
chore(*): update @types/react to latest [#3908](https://github.com/spinnaker/deck/pull/3908) ([21348d44](https://github.com/spinnaker/deck/commit/21348d448e4e40b1030ed6bf58161a8f22abc14b))  
feat(core) - ui for rolling red black push [#3904](https://github.com/spinnaker/deck/pull/3904) ([7e5091bf](https://github.com/spinnaker/deck/commit/7e5091bf7fed43f009802d46377c76929d06a12e))  
chore(core/amazon/docker/google): update configs [#3893](https://github.com/spinnaker/deck/pull/3893) ([a0649c3e](https://github.com/spinnaker/deck/commit/a0649c3ed3c239900329c90536294f245139c056))  



## [0.0.17](https://www.github.com/spinnaker/deck/compare/47c40a4c764ed58ff456cf6bd845ed12713e86f2...99cf6d0e41c898c2b0d72a795382477ffe881948) (2017-07-07)


### Changes

chore(aws): bump package version [#3896](https://github.com/spinnaker/deck/pull/3896) ([99cf6d0e](https://github.com/spinnaker/deck/commit/99cf6d0e41c898c2b0d72a795382477ffe881948))  
fix(aws): guard against missing buildInfo on server group details [#3895](https://github.com/spinnaker/deck/pull/3895) ([1f9a0bc0](https://github.com/spinnaker/deck/commit/1f9a0bc009a66edfb8dd4c98fef0e51473eef569))  



## [0.0.16](https://www.github.com/spinnaker/deck/compare/3247d1ace100002d28e88152cf2fb0cb0258cd9d...47c40a4c764ed58ff456cf6bd845ed12713e86f2) (2017-07-06)


### Changes

feat(aws): allow search on LB listener cert select [#3892](https://github.com/spinnaker/deck/pull/3892) ([47c40a4c](https://github.com/spinnaker/deck/commit/47c40a4c764ed58ff456cf6bd845ed12713e86f2))  



## [0.0.15](https://www.github.com/spinnaker/deck/compare/14db9c52f3d3f2fe36aadea74de5897fcf36b2fa...3247d1ace100002d28e88152cf2fb0cb0258cd9d) (2017-07-06)


### Changes

feat(core/amazon): add build info to changes [#3884](https://github.com/spinnaker/deck/pull/3884) ([3247d1ac](https://github.com/spinnaker/deck/commit/3247d1ace100002d28e88152cf2fb0cb0258cd9d))  
refactor(core): Refactor security group types to make sense [#3883](https://github.com/spinnaker/deck/pull/3883) ([60ab92fe](https://github.com/spinnaker/deck/commit/60ab92fe5c975ed1d0371c46f1ce5f06360c21f0))  



## [0.0.14](https://www.github.com/spinnaker/deck/compare/0e5533373d00bccba73bec621191768bd3b5054c...14db9c52f3d3f2fe36aadea74de5897fcf36b2fa) (2017-06-27)


### Changes

fix(provider/amazon): Only enforce TG naming to be based on appname [#3877](https://github.com/spinnaker/deck/pull/3877) ([14db9c52](https://github.com/spinnaker/deck/commit/14db9c52f3d3f2fe36aadea74de5897fcf36b2fa))  



## [0.0.13](https://www.github.com/spinnaker/deck/compare/cedf57824a785e5f76fae1dca41108ce500c47c7...0e5533373d00bccba73bec621191768bd3b5054c) (2017-06-27)


### Changes

feat(react): Route to react components; use react UISref components ([0e553337](https://github.com/spinnaker/deck/commit/0e5533373d00bccba73bec621191768bd3b5054c))  
feat(provider/amazon): Load certificates on demand [#3874](https://github.com/spinnaker/deck/pull/3874) ([eec53580](https://github.com/spinnaker/deck/commit/eec53580922ad8ab5a8787f6d5ccdd9f8fbb130f))  
fix(provider/amazon): Force certificate type if only one is available [#3873](https://github.com/spinnaker/deck/pull/3873) ([f1ae2587](https://github.com/spinnaker/deck/commit/f1ae2587276419d1748a94ba5bb28044e1eac304))  



## [0.0.12](https://www.github.com/spinnaker/deck/compare/369c718e09399fe7fdad6d583b9b3804cf7daf02...cedf57824a785e5f76fae1dca41108ce500c47c7) (2017-06-26)


### Changes

chore(amazon, core): rev packages [#3872](https://github.com/spinnaker/deck/pull/3872) ([cedf5782](https://github.com/spinnaker/deck/commit/cedf57824a785e5f76fae1dca41108ce500c47c7))  
feat(provider/amazon): Enforce target group naming convention [#3869](https://github.com/spinnaker/deck/pull/3869) ([f768f18f](https://github.com/spinnaker/deck/commit/f768f18fb2b0c4fc961b0d0a2cbf6d656be3d589))  



## [0.0.11](https://www.github.com/spinnaker/deck/compare/5d5c8ad217a434eec50fc67ca193591cb9323ac9...369c718e09399fe7fdad6d583b9b3804cf7daf02) (2017-06-22)


### Changes

fix(provider/amazon): Fix deleting ALBs [#3862](https://github.com/spinnaker/deck/pull/3862) ([369c718e](https://github.com/spinnaker/deck/commit/369c718e09399fe7fdad6d583b9b3804cf7daf02))  



## [0.0.10](https://www.github.com/spinnaker/deck/compare/ac77afb020bcaa8b0607beb33c7d74564440b70d...5d5c8ad217a434eec50fc67ca193591cb9323ac9) (2017-06-21)


### Changes

chore: bump core to 0.0.26 and amazon to 0.0.10 [#3861](https://github.com/spinnaker/deck/pull/3861) ([5d5c8ad2](https://github.com/spinnaker/deck/commit/5d5c8ad217a434eec50fc67ca193591cb9323ac9))  
feat(provider/amazon): Cleanup ALB listeners CRUD UI [#3857](https://github.com/spinnaker/deck/pull/3857) ([80e98757](https://github.com/spinnaker/deck/commit/80e987573c04061f1df0ed33ddd79b01cb715e73))  
feat(core): require app name in appModelBuilder.createApplication [#3850](https://github.com/spinnaker/deck/pull/3850) ([3f20407e](https://github.com/spinnaker/deck/commit/3f20407ed2e25c2d6e6c7512cc24ffc4422a0740))  
fix(provider/amazon): Fix missing information for ALB target groups [#3852](https://github.com/spinnaker/deck/pull/3852) ([92d434a9](https://github.com/spinnaker/deck/commit/92d434a9ae3f4ac1bff26f866c883ff3b2068505))  



## [0.0.9](https://www.github.com/spinnaker/deck/compare/d3b94ebd812ebd6fcacfe4897ef4d48ac6787fb0...ac77afb020bcaa8b0607beb33c7d74564440b70d) (2017-06-16)


### Changes

fix(aws): change "deprecated" to "not recommended" on rolling push [#3849](https://github.com/spinnaker/deck/pull/3849) ([ac77afb0](https://github.com/spinnaker/deck/commit/ac77afb020bcaa8b0607beb33c7d74564440b70d))  
fix(aws): provide explanatory text when no target groups present [#3847](https://github.com/spinnaker/deck/pull/3847) ([5bfe31d8](https://github.com/spinnaker/deck/commit/5bfe31d85ec8ece659a6cbe5a6c641bc5a473f83))  
fix(aws): restrict rolling push strategy to AWS ([c9012455](https://github.com/spinnaker/deck/commit/c90124553b5eebb4d414161357ccdddffa257618))  
refactor(provider/amazon): Convert createClassicLoadBalancer to TS and extract common functions [#3818](https://github.com/spinnaker/deck/pull/3818) ([44e6291f](https://github.com/spinnaker/deck/commit/44e6291ffbc22384be9c5a5dde95243ebce94d3a))  



## [0.0.8](https://www.github.com/spinnaker/deck/compare/17fce373c4908c45b40602d3746c19c0054d02e2...d3b94ebd812ebd6fcacfe4897ef4d48ac6787fb0) (2017-06-12)


### Changes

refactor(aws): make scaling policies customizable ([d3b94ebd](https://github.com/spinnaker/deck/commit/d3b94ebd812ebd6fcacfe4897ef4d48ac6787fb0))  
fix(provider/amazon): Require target groups to have unique names ([e03f5981](https://github.com/spinnaker/deck/commit/e03f59819f87e1466c3e00441f08d39e195d0eab))  
refactor(provider/amazon): convert createApplicationLoadBalancer to TS [#3816](https://github.com/spinnaker/deck/pull/3816) ([a2d75789](https://github.com/spinnaker/deck/commit/a2d75789e380c36675cfe3a2a16839af58751d2f))  
fix(provider/amazon): Fix instance health setting in target groups [#3813](https://github.com/spinnaker/deck/pull/3813) ([c7f3c76a](https://github.com/spinnaker/deck/commit/c7f3c76ae76245803516a8684b824e94da19c429))  
refactor(provider/amazon): Separate load balancer types [#3810](https://github.com/spinnaker/deck/pull/3810) ([b66dc0c3](https://github.com/spinnaker/deck/commit/b66dc0c3766b863ed1422721b872619e6cf5e1c1))  
refactor(provider/amazon): Convert load balancer transformer to TS [#3806](https://github.com/spinnaker/deck/pull/3806) ([af9afa1c](https://github.com/spinnaker/deck/commit/af9afa1cb7fd32dcaeeac35653887a52207289a7))  
feat(provider/amazon): CRUD for ALBs [#3803](https://github.com/spinnaker/deck/pull/3803) ([dd5abf4b](https://github.com/spinnaker/deck/commit/dd5abf4bada440aa6870fbcf644d63dd056479d1))  



## [0.0.7](https://www.github.com/spinnaker/deck/compare/d969763178e82a330fa5b4d181c163c762f7ae2e...17fce373c4908c45b40602d3746c19c0054d02e2) (2017-06-07)


### Changes

chore(docker): rev package to 0.0.17 ([17fce373](https://github.com/spinnaker/deck/commit/17fce373c4908c45b40602d3746c19c0054d02e2))  
feat(provider/amazon): Add link back to load balancer from target group details [#3796](https://github.com/spinnaker/deck/pull/3796) ([065ba9cc](https://github.com/spinnaker/deck/commit/065ba9cc9f1409c06fe196125d5716d6094e56cb))  
feat(provider/amazon): Expose ipAddressType in the ALB details panel [#3795](https://github.com/spinnaker/deck/pull/3795) ([a4ebc375](https://github.com/spinnaker/deck/commit/a4ebc375df09810ed6cf42b8236fb10986340527))  
fix(provider/amazon): Add server groups to target group details [#3794](https://github.com/spinnaker/deck/pull/3794) ([41092270](https://github.com/spinnaker/deck/commit/4109227031127aae12a68855efd2d38e96c9a8a0))  
fix(provider/amazon): fix load balancer vpc id ([053aa698](https://github.com/spinnaker/deck/commit/053aa69885604e49c656ea818d6462655a043a74))  
chore(core): Update core/tsconfig.json to include all typescript files ([d42e0f2e](https://github.com/spinnaker/deck/commit/d42e0f2e459216719ed369121817ff7483b24dd3))  
chore(core): Update to @uirouter/angularjs@1.0.3 ([d6e7e3d8](https://github.com/spinnaker/deck/commit/d6e7e3d86d342f08d186e268713dce3194def491))  



## [0.0.6](https://www.github.com/spinnaker/deck/compare/84836eeaacfad1fc529170fbf96bf618f9ac17a6...d969763178e82a330fa5b4d181c163c762f7ae2e) (2017-06-01)


### Changes

chore(provider/amazon): Bump amazon to 0.0.6 [#3782](https://github.com/spinnaker/deck/pull/3782) ([d9697631](https://github.com/spinnaker/deck/commit/d969763178e82a330fa5b4d181c163c762f7ae2e))  
feat(core/entityTag): Add categories to alerts popovers [#3773](https://github.com/spinnaker/deck/pull/3773) ([20fd6a13](https://github.com/spinnaker/deck/commit/20fd6a1343260e0708bd63303d061f1cea4da9ce))  
refactor(provider/amazon): Convert serverGroupConfiguration.service to TS [#3774](https://github.com/spinnaker/deck/pull/3774) ([b1a390fd](https://github.com/spinnaker/deck/commit/b1a390fd4a3da8a9c989a57fba301c014242dafa))  
refactor(strategies): make strategies provider-pluggable, convert to TS ([63c19be3](https://github.com/spinnaker/deck/commit/63c19be32ce38a0ecc188532c5252bb58c9b1b77))  
refactor(provider/amazon): Convert serverGroup.transformer to TS and type ScalingPolicies [#3770](https://github.com/spinnaker/deck/pull/3770) ([d837abd3](https://github.com/spinnaker/deck/commit/d837abd3a688e76d2baebd6fbafe6c43340482a6))  
fix(provider/amazon): Add originl refresh time back to load balancer selector ([fd1a375b](https://github.com/spinnaker/deck/commit/fd1a375b3f4c9e14bc6a4b19f10b84340b5f488f))  
refactor(provider/amazon): Convert load balancer selector to TS ([ab9e234a](https://github.com/spinnaker/deck/commit/ab9e234a776c423c2d71481cdc8273bbb61db7e1))  
chore(amazon): ensure module names all start with "spinnaker.amazon" ([7df151be](https://github.com/spinnaker/deck/commit/7df151be3490f3f85cbed79323638ebe3cfd6e14))  



## [0.0.5](https://www.github.com/spinnaker/deck/compare/81dc32621ed3ea3b2266ef0545d92246de43b400...84836eeaacfad1fc529170fbf96bf618f9ac17a6) (2017-05-26)


### Changes

chore(*): Bump amazon and core package versions and fix a couple things to fix module publishing [#3761](https://github.com/spinnaker/deck/pull/3761) ([84836eea](https://github.com/spinnaker/deck/commit/84836eeaacfad1fc529170fbf96bf618f9ac17a6))  
feat:(amazon): Add ALB Support [#3757](https://github.com/spinnaker/deck/pull/3757) ([d98ab724](https://github.com/spinnaker/deck/commit/d98ab724b1a77f06657a4191095713f191e0c664))  



## [0.0.3](https://www.github.com/spinnaker/deck/compare/d93efd99028be0a032ab68dc9845087132f3aad4...81dc32621ed3ea3b2266ef0545d92246de43b400) (2017-05-25)


### Changes

chore(core): rev version ([81dc3262](https://github.com/spinnaker/deck/commit/81dc32621ed3ea3b2266ef0545d92246de43b400))  
fix(provider/aws): Edit Advanced Settings of asg w/ enabledMetrics should copy over just the names of the metrics. [#3750](https://github.com/spinnaker/deck/pull/3750) ([e22e387e](https://github.com/spinnaker/deck/commit/e22e387e32a28584c8c2101759d65cd5bd4be195))  
fix(provider/aws): Clone of asg w/ enabledMetrics should copy over just the names of the metrics. ([bc0a7589](https://github.com/spinnaker/deck/commit/bc0a7589d7cf20392658c6ad7c142fd7dc4e4e7b))  



## [0.0.2](https://www.github.com/spinnaker/deck/compare/57557f5f016d6233f6ac72e3989bbe6752269c72...d93efd99028be0a032ab68dc9845087132f3aad4) (2017-05-22)


### Changes

chore(core/amazon): rev package.json versions ([d93efd99](https://github.com/spinnaker/deck/commit/d93efd99028be0a032ab68dc9845087132f3aad4))  
fix(amazon): fix ng-if in security group refresh dom element ([ee04582b](https://github.com/spinnaker/deck/commit/ee04582b13833667cafc8b280e97fd0c8016e80b))  
chore(*): convert refresh icons to fa-refresh ([c4ca0c5c](https://github.com/spinnaker/deck/commit/c4ca0c5c111fffbacd333c3dcecf3ff70fdc8631))  
refactor(*): replace glyphicon-asterisk with fa-cog ([04807cb4](https://github.com/spinnaker/deck/commit/04807cb4a3d3410cbe7c31c2e03d09df30a6c02f))  
chore(*): replace some glyphicons with font-awesome ([070fb88a](https://github.com/spinnaker/deck/commit/070fb88a8c8f67ac34dbbac8a316cab8ef7bf05a))  
fix(aws): restore $onInit to ingress security group selector ([bc5da6dd](https://github.com/spinnaker/deck/commit/bc5da6dd9d7e1290e3d06abdfc3187bee031b4ce))
