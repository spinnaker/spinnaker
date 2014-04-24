package com.netflix.front50

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.simpledb.AmazonSimpleDB
import com.amazonaws.services.simpledb.AmazonSimpleDBClient
import com.amazonaws.services.simpledb.model.SelectRequest
import spock.lang.Specification

/**
 * Created by aglover on 4/22/14.
 */
class ReadOnlySimpleDBTest extends Specification {
    static AmazonSimpleDB client
    static String domain = "RESOURCE_REGISTRY"

    def setupSpec() {
        client = new AmazonSimpleDBClient(new BasicAWSCredentials(
                System.properties["aws.key"], System.properties["aws.secret"]
        ))
    }

    void 'should list items in a table via name'() {
        String qry = "select * from `" + domain + "` where itemName()='SAMPLEAPP'";
        SelectRequest selectRequest = new SelectRequest(qry);

        def itms = client.select(selectRequest).getItems()
        def item = itms[0]
        expect:
        itms != null
        itms.size() == 1
        item.name == 'SAMPLEAPP'
    }

    void 'should list items in a table'() {
        String qry = "select * from `" + domain + "` limit 2500";
        SelectRequest selectRequest = new SelectRequest(qry);

        def itms = client.select(selectRequest).getItems()

        expect:
        itms != null
        itms.size() >= 1416
    }

}
