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
package org.apache.camel.component.master.impl;

import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.component.master.group.GroupListenerStrategyFactory;
import org.apache.camel.component.master.group.NodeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Callback interface used to get notifications of changes to a cluster group.
 */
public abstract class GroupListenerStrategyHelper {

    private static final Logger LOG = LoggerFactory.getLogger(GroupListenerStrategyHelper.class);

    public static <T extends NodeState> GroupListenerStrategyFactory<T> findFactory(CamelContext context) {
        // FIXME: lookup in registry to begin with
        // consider extending in the future to find factories using factory finder, environment config, etc.
        // might need to improve GroupListenerFactoryStrategy impls to be auto instantiated
        Set<GroupListenerStrategyFactory> factories = context.getRegistry().findByType(GroupListenerStrategyFactory.class);

        // first factory for now
        if (factories.isEmpty()) {
            throw new IllegalStateException("No factories in registry of type " + GroupListenerStrategyFactory.class.getName());
        }

        GroupListenerStrategyFactory factory = factories.iterator().next();
        LOG.debug("Using group listener factory {}", factory.getClass());
        return factory;
    }
}
