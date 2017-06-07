/**
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
package org.apache.camel.component.master;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.master.group.GroupListenerStrategyFactory;
import org.apache.camel.impl.DefaultComponent;

/**
 * The master camel component ensures that only a single endpoint in a cluster is active at any
 * point in time with all other JVMs being hot standbys which wait until the master JVM dies before
 * taking over to provide high availability of a single consumer.
 */
public class MasterComponent extends DefaultComponent {

    private GroupListenerStrategyFactory<?> groupListenerStrategyFactory;
    private ContainerIdFactory containerIdFactory = new DefaultContainerIdFactory();

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> params) throws Exception {
        int idx = remaining.indexOf(':');
        if (idx <= 0) {
            throw new IllegalArgumentException("Missing : in URI so cannot split the group name from the actual URI for '" + remaining + "'");
        }
        // we are registering a regular endpoint
        String name = remaining.substring(0, idx);
        String childUri = remaining.substring(idx + 1);
        // we need to apply the params here
        if (params != null && params.size() > 0) {
            childUri = childUri + "?" + uri.substring(uri.indexOf('?') + 1);
        }
        MasterEndpoint answer = new MasterEndpoint(uri, this, name, childUri);
        return answer;
    }

    public GroupListenerStrategyFactory<?> getGroupListenerStrategyFactory() {
        return groupListenerStrategyFactory;
    }

    /**
     * Group listener factory to use for master election.
     */
    public void setGroupListenerStrategyFactory(GroupListenerStrategyFactory<?> groupListenerStrategyFactory) {
        this.groupListenerStrategyFactory = groupListenerStrategyFactory;
    }

    public ContainerIdFactory getContainerIdFactory() {
        return containerIdFactory;
    }

    /**
     * To use a custom ContainerIdFactory for creating container ids.
     */
    public void setContainerIdFactory(ContainerIdFactory containerIdFactory) {
        this.containerIdFactory = containerIdFactory;
    }
}
