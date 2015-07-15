/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.libdiffs

import spock.lang.Specification

class LibraryDiffSpec extends Specification {
  void "diffJars"() {
    given:
    Library lib1 = new Library("/apps/jre/lib/1.6.0/ext/IngrianLog4j.jar", "IngrianLog4j", "", "", "")
    Library lib2 = new Library("/usr/lib/jvm/java-7-oracle-1.7.0.80/jre/lib/ext/IngrianNAE-5.4.0.jar", "IngrianNAE", "5.4.0", "Ingrian Provider 5.4.0.000006", "-")
    Library lib3 = new Library("/apps/apache-tomcat-7.0.59/bin/tomcat-juli.jar", "tomcat-juli", "7.0.59", "Apache Tomcat", "-")
    Library lib4 = new Library("/apps/tomcat/webapps/ROOT/WEB-INF/lib/DataModel-119.54.jar", "DataModel", "119.54", "netflix", "-")
    Library lib5 = new Library("/apps/tomcat/webapps/ROOT/WEB-INF/lib/astyanax-entity-mapper-3.6.0.jar", "astyanax-entity-mapper", "3.6.0", "com.netflix.astyanax", "-")

    Library targetLib1 = new Library("/apps/jre/lib/1.6.0/ext/IngrianLog4j.jar", "IngrianLog4j", "", "", "")
    Library targetLib2 = new Library("/usr/lib/jvm/java-7-oracle-1.7.0.80/jre/lib/ext/IngrianNAE-5.5.0.jar", "IngrianNAE", "5.5.0", "Ingrian Provider 5.5.0.000006", "-")
    Library targetLib3 = new Library("/apps/apache-tomcat-7.0.55/bin/tomcat-juli.jar", "tomcat-juli", "7.0.55", "Apache Tomcat", "-")
    Library targetLib4 = new Library("/apps/tomcat/webapps/ROOT/WEB-INF/lib/DataModel-119.54.jar", "DataModel", "119.54", "netflix", "-")
    Library targetLib5 = new Library("/apps/tomcat/webapps/ROOT/WEB-INF/lib/DataModel-119.54.jar", "DataModel", "119.55", "netflix", "-")
    Library targetLib6 = new Library("apps/tomcat/webapps/ROOT/WEB-INF/lib/aws-java-sdk-cognitosync-1.9.3.jar", "aws-java-sdk-cognitosync", "1.9.3", "Apache Tomcat", "-")

    def source = [lib1, lib2, lib3, lib4, lib5]
    def target = [targetLib1, targetLib2, targetLib3, targetLib4, targetLib5, targetLib6]

    when:
    LibraryDiff libraryDiff = new LibraryDiff(source, target)
    def result = libraryDiff.diffJars()
    println result.dump()

    then:
    result.downgraded[0].displayDiff == "tomcat-juli: 7.0.59 -> 7.0.55"
    result.unknown[0].displayDiff == "IngrianLog4j"
    result.duplicates[0].displayDiff =="DataModel: 119.54, 119.55"
    result.upgraded[0].displayDiff == "IngrianNAE: 5.4.0 -> 5.5.0"
    result.removed[0].displayDiff == "astyanax-entity-mapper: 3.6.0"
    result.added[0].displayDiff == "aws-java-sdk-cognitosync: 1.9.3"
  }
}
