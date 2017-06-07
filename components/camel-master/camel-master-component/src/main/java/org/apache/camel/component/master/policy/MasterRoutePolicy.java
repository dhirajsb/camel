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
package org.apache.camel.component.master.policy;

import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.master.ContainerIdFactory;
import org.apache.camel.component.master.DefaultContainerIdFactory;
import org.apache.camel.component.master.group.GroupListenerStrategy;
import org.apache.camel.component.master.group.GroupListenerStrategyFactory;
import org.apache.camel.component.master.group.NodeState;
import org.apache.camel.component.master.impl.GroupListenerStrategyHelper;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * {@link org.apache.camel.spi.RoutePolicy} to run the route in master/slave mode.
 * <p/>
 * <b>Important:</b> Make sure to set the route to autoStartup=false as the route lifecycle
 * is controlled by this route policy which will start/stop the route in accord with it's state.
 */
@ManagedResource(description = "Managed MasterRoutePolicy")
public class MasterRoutePolicy extends RoutePolicySupport implements CamelContextAware {

    private String groupName;

    private ContainerIdFactory containerIdFactory = new DefaultContainerIdFactory();

    private GroupListenerStrategyFactory groupListenerStrategyFactory;

    // state if the consumer has been started
    private final AtomicBoolean masterConsumer = new AtomicBoolean();

    private volatile NodeState thisNodeState;
    private CamelContext camelContext;
    private Route route;
    private GroupListenerStrategy<? extends NodeState> groupListenerStrategy;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @ManagedAttribute(description = "The name of the cluster group to use")
    public String getGroupName() {
        return groupName;
    }

    /**
     * The name of the cluster group to use
     */
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public ContainerIdFactory getContainerIdFactory() {
        return containerIdFactory;
    }

    public GroupListenerStrategyFactory getGroupListenerStrategyFactory() {
        return groupListenerStrategyFactory;
    }

    /**
     * The {@link GroupListenerStrategyFactory} to use, if it's null, looks up factory in registry.
     */
    public void setGroupListenerStrategyFactory(GroupListenerStrategyFactory groupListenerStrategyFactory) {
        this.groupListenerStrategyFactory = groupListenerStrategyFactory;
    }

    /**
     * Unique Id for this route container, used to identify members of route group.
     * @param containerIdFactory
     */
    public void setContainerIdFactory(ContainerIdFactory containerIdFactory) {
        this.containerIdFactory = containerIdFactory;
    }

    @ManagedAttribute(description = "Are we connected to provider")
    public boolean isConnected() {
        if (groupListenerStrategy == null) {
            return false;
        }
        return groupListenerStrategy.getGroup().isConnected();
    }

    @ManagedAttribute(description = "Are we the master")
    public boolean isMaster() {
        if (groupListenerStrategy == null) {
            return false;
        }
        return groupListenerStrategy.getGroup().isMaster();
    }

    @ManagedOperation(description = "Information about all the slaves")
    public String slaves() {
        if (groupListenerStrategy == null) {
            return null;
        }
        try {
            return new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .writeValueAsString(groupListenerStrategy.getGroup().slaves());
        } catch (Exception e) {
            return null;
        }
    }

    @ManagedOperation(description = "Information about the last event in the cluster group")
    public String lastEvent() {
        if (groupListenerStrategy == null) {
            return null;
        }
        Object event = groupListenerStrategy.getGroup().getLastState();
        return event != null ? event.toString() : null;
    }

    @ManagedOperation(description = "Information about this node")
    public String thisNode() {
        return thisNodeState != null ? thisNodeState.toString() : null;
    }

    @Override
    public void onInit(Route route) {
        super.onInit(route);
        this.route = route;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ObjectHelper.notNull(camelContext, "CamelContext");
        ObjectHelper.notEmpty("groupName", groupName);

        // get listener factory
        final GroupListenerStrategyFactory<? extends NodeState> factory;
        if (groupListenerStrategyFactory == null) {
            factory = GroupListenerStrategyHelper.findFactory(camelContext);
        } else {
            factory = groupListenerStrategyFactory;
        }
        this.groupListenerStrategy = factory.newInstance(groupName,
            containerIdFactory.newContainerId(), route.getEndpoint().getEndpointUri(),
            this::onLockOwned, this::onDisconnected);
        ServiceHelper.startService(groupListenerStrategy);

        log.info("Attempting to become master for endpoint: " + route.getEndpoint() + " in " + getCamelContext() + " with singletonID: " + getGroupName());
        thisNodeState = this.groupListenerStrategy.createNodeState();
        groupListenerStrategy.updateState(thisNodeState);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        ServiceHelper.stopAndShutdownServices(groupListenerStrategy);
        masterConsumer.set(false);
    }

    // called by GroupListener to start consumer
    protected boolean onLockOwned() {
        boolean result = false;
        if (masterConsumer.compareAndSet(false, true)) {
            try {
                // ensure endpoint is also started
                log.info("Elected as master. Starting consumer: {}", route.getEndpoint());
                startConsumer(route.getConsumer());

                // Lets show we are starting the consumer.
                thisNodeState = this.groupListenerStrategy.createNodeState();
                thisNodeState.setStarted(true);
                groupListenerStrategy.updateState(thisNodeState);

                result = true;
            } catch (Exception e) {
                log.error("Failed to start master consumer for: " + route.getEndpoint(), e);
            }

            log.info("Elected as master. Consumer started: {}", route.getEndpoint());
        }

        return result;
    }

    // called by GroupListener to stop consumer
    protected boolean onDisconnected() {

        boolean result = false;
        masterConsumer.set(false);
        try {
            stopConsumer(route.getConsumer());

            result = true;
        } catch (Exception e) {
            log.warn("Failed to stop master consumer: " + route.getEndpoint(), e);
        }

        return result;
    }
}
