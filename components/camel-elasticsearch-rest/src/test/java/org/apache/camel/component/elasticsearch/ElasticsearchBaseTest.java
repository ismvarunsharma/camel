/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.elasticsearch;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.HttpHost;
import org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner;
import org.elasticsearch.client.RestClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import static org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner.newConfigs;

public class ElasticsearchBaseTest extends CamelTestSupport {


    public static ElasticsearchClusterRunner runner;
    public static String clusterName;
    public static RestClient client;

    protected static final int ES_BASE_TRANSPORT_PORT = AvailablePortFinder.getNextAvailable();
    protected static final int ES_BASE_HTTP_PORT = AvailablePortFinder.getNextAvailable();

    @SuppressWarnings("resource")
    @BeforeClass
    public static void cleanupOnce() throws Exception {
        deleteDirectory("target/testcluster/");

        clusterName = "es-cl-run-" + System.currentTimeMillis();

        runner = new ElasticsearchClusterRunner();
        runner.setMaxHttpPort(-1);
        runner.setMaxTransportPort(-1);

        // create ES nodes
        runner.onBuild((number, settingsBuilder) -> {
            settingsBuilder.put("http.cors.enabled", true);
            settingsBuilder.put("http.cors.allow-origin", "*");
        }).build(newConfigs()
            .clusterName(clusterName)
            .numOfNode(1)
            .baseHttpPort(ES_BASE_HTTP_PORT - 1) // ElasticsearchClusterRunner add node id to port, so set it to ES_BASE_HTTP_PORT-1 to start node 1 exactly on ES_BASE_HTTP_PORT
            .baseTransportPort(ES_BASE_TRANSPORT_PORT - 1) // ElasticsearchClusterRunner add node id to port, so set it to ES_BASE_TRANSPORT_PORT-1 to start node 1 exactly on ES_BASE_TRANSPORT_PORT
            .basePath("target/testcluster/"));

        // wait for green status
        runner.ensureGreen();
        client = RestClient.builder(new HttpHost(InetAddress.getByName("localhost"), ES_BASE_HTTP_PORT)).build();
    }

    @AfterClass
    public static void teardownOnce() throws IOException {
        if (client != null) {
            client.close();
        }
        if (runner != null) {
            runner.close();
        }
    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        // let's speed up the tests using the same context
        return true;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        final ElasticsearchComponent elasticsearchComponent = new ElasticsearchComponent();
        elasticsearchComponent.setHostAddresses("localhost:" + ES_BASE_HTTP_PORT);
        context.addComponent("elasticsearch-rest", elasticsearchComponent);
        return context;
    }

    /**
     * As we don't delete the {@code target/data} folder for <b>each</b> test
     * below (otherwise they would run much slower), we need to make sure
     * there's no side effect of the same used data through creating unique
     * indexes.
     */
    Map<String, String> createIndexedData(String... additionalPrefixes) {
        String prefix = createPrefix();

        // take over any potential prefixes we may have been asked for
        if (additionalPrefixes.length > 0) {
            StringBuilder sb = new StringBuilder(prefix);
            for (String additionalPrefix : additionalPrefixes) {
                sb.append(additionalPrefix).append("-");
            }
            prefix = sb.toString();
        }

        String key = prefix + "key";
        String value = prefix + "value";
        log.info("Creating indexed data using the key/value pair {} => {}", key, value);

        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    String createPrefix() {
        // make use of the test method name to avoid collision
        return getTestMethodName().toLowerCase() + "-";
    }
    
    RestClient getClient() {
        return client;
    }
}
