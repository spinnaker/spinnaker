package com.netflix.spinnaker.kato.config

import com.netflix.spinnaker.kato.gce.model.GoogleDisk
import com.netflix.spinnaker.kato.gce.model.GoogleInstanceTypeDisk
import groovy.transform.ToString
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@ConditionalOnProperty('google.enabled')
@Configuration
@ComponentScan('com.netflix.spinnaker.kato.gce')
class GceConfig {
  private static final String DEFAULT_KEY = "default"
  private static final String DISK_TYPE = "pd-standard"
  private static final long DISK_SIZE_GB = 10

  @Bean
  @ConfigurationProperties('google.defaults')
  DeployDefaults gceDeployDefaults() {
    new DeployDefaults()
  }

  @ToString(includeNames = true)
  static class DeployDefaults {
    List<GoogleInstanceTypeDisk> instanceTypeDisks = []

    GoogleInstanceTypeDisk determineInstanceTypeDisk(String instanceType) {
      GoogleInstanceTypeDisk instanceTypeDisk = instanceTypeDisks.find {
        it.instanceType == instanceType
      }

      if (!instanceTypeDisk) {
        instanceTypeDisk = instanceTypeDisks.find {
          it.instanceType == DEFAULT_KEY
        }
      }

      if (!instanceTypeDisk) {
        instanceTypeDisk =
          new GoogleInstanceTypeDisk(instanceType: DEFAULT_KEY,
                                     disks: [new GoogleDisk(type: DISK_TYPE,
                                                            sizeGb: DISK_SIZE_GB)])
      }

      return instanceTypeDisk
    }
  }
}
