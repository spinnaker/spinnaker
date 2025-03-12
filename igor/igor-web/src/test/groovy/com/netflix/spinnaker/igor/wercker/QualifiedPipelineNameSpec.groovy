/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.igor.wercker

import com.netflix.spinnaker.igor.wercker.model.QualifiedPipelineName
import spock.lang.Specification

class QualifiedPipelineNameSpec extends Specification {
    void 'serializeDeserialize'() {
        setup:
        String origName = 'owner1/someApp/aPipeline'
        QualifiedPipelineName qp = QualifiedPipelineName.of(origName)
        String qpName = qp.toString()

        expect:
        origName.equals(qpName)
        qp.ownerName.equals('owner1')
        qp.appName.equals('someApp')
        qp.pipelineName.equals('aPipeline')
    }

}
