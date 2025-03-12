package com.netflix.spinnaker.keel

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.kork.exceptions.SystemException

fun String.parseAppVersionOrNull(): AppVersion? =
  runCatching {
    AppVersion.parseName(this)
  }
    .getOrElse { ex ->
      throw SystemException("Error parsing appVersion string from '$this'", ex)
    }

fun String.parseAppVersion(): AppVersion =
  parseAppVersionOrNull() ?: throw SystemException("Invalid appVersion string: '$this'")
