package com.netflix.front50

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.simpledb.AmazonSimpleDB
import com.amazonaws.services.simpledb.AmazonSimpleDBClient
import com.amazonaws.services.simpledb.model.CreateDomainRequest
import com.amazonaws.services.simpledb.model.Item
import com.amazonaws.services.simpledb.model.SelectRequest
import spock.lang.Specification

/**
 * Created by aglover on 4/8/14.
 */
class SimpleDBExploreTest extends Specification {
    static AmazonSimpleDB client
    static String domain = "RESOURCE_REGISTRY"

    def setupSpec() {
        client = new AmazonSimpleDBClient(new BasicAWSCredentials(
                System.properties["aws.key"], System.properties["aws.secret"]
        ))
        if (System.properties['env'].equals('local-test')) {
            client.setEndpoint("http://localhost:8080")
            this.createLocalTable()
        }

    }

    void createLocalTable() {
        client.createDomain(new CreateDomainRequest(domain));
    }

    def 'should list items in a table'() {
        String qry = "select * from `" + domain + "`";
        SelectRequest selectRequest = new SelectRequest(qry);

        expect:
        client.select(selectRequest).getItems() != null
    }

    def 'should count items in a table'() {
        String qry = "select count(*) from `" + domain + "`";
        SelectRequest selectRequest = new SelectRequest(qry);

        List<Item> items = client.select(selectRequest).getItems()

        expect:
        items != null
        items.size() == 1

        def item = items[0]
        item.attributes != null
        item.attributes.size() == 1

        def attribute = item.attributes[0]
        attribute.name == "Count"
        attribute.value.toInteger() > 0
    }

}
