/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.igor.wercker.model

class QualifiedPipelineName {
    static String SPLITOR = "/";

    String ownerName;
    String appName;
    String pipelineName;

    QualifiedPipelineName(String ownerName, String appName, String pipelineName) {
        this.ownerName = ownerName;
        this.appName = appName;
        this.pipelineName = pipelineName;
    }

    String toString() {
        return this.ownerName + SPLITOR + this.appName + SPLITOR + this.pipelineName
    }

    static QualifiedPipelineName of(String qualifiedPipelineName) {
        String[] split = qualifiedPipelineName.split(SPLITOR)
        return new QualifiedPipelineName(split[0], split[1], split[2])
    }
}
