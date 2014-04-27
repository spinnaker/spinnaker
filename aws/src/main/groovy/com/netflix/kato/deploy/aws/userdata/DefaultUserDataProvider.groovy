package com.netflix.kato.deploy.aws.userdata

import com.netflix.frigga.Names
import org.springframework.stereotype.Component

@Component
class DefaultUserDataProvider implements UserDataProvider {
  @Override
  String getUserData(String asgName, String launchConfigName, String region) {
    Names names = Names.parseName(asgName)

    def exports = []
    exports << "export NETFLIX_APP=${names.app}"
    exports << "export NETFLIX_STACK=${names.stack}"
    exports << "export NETFLIX_CLUSTER=${names.cluster}"
    exports << "export NETFLIX_AUTO_SCALE_GROUP=${asgName}"
    exports << "export NETFLIX_LAUNCH_CONFIG=${launchConfigName}"
    exports << "export EC2_REGION=${region}"

    if (names.redBlackSwap) exports << "export NETFLIX_RED_BLACK_SWAP=${names.redBlackSwap}"
    if (names.countries) exports << "export NETFLIX_COUNTRIES=${names.countries}"
    if (names.devPhase) exports << "export NETFLIX_DEV_PHASE=${names.devPhase}"
    if (names.hardware) exports << "export NETFLIX_HARDWARE=${names.hardware}"
    if (names.partners) exports << "export NETFLIX_PARTNERS=${names.partners}"
    if (names.revision) exports << "export NETFLIX_REVISION=${names.revision}"
    if (names.usedBy) exports << "export NETFLIX_USED_BY=${names.usedBy}"
    if (names.zone) exports << "export NETFLIX_ZONE=${names.zone}"

    exports.join("\n")
  }
}
