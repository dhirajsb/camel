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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.SuspendableService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.master.group.GroupListenerStrategy;
import org.apache.camel.component.master.group.GroupListenerStrategyFactory;
import org.apache.camel.component.master.group.NodeState;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A consumer which is only really active while it holds the master lock
 */
@ManagedResource(description = "Managed Master Consumer")
public class MasterConsumer extends DefaultConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(MasterConsumer.class);

    private GroupListenerStrategy<?> groupListenerStrategy;
    private final MasterEndpoint endpoint;
    private final Processor processor;
    private Consumer delegate;
    private SuspendableService delegateService;
    private volatile NodeState thisNodeState;

    public MasterConsumer(MasterEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.processor = processor;
    }

    @ManagedAttribute(description = "Are we connected to Provider")
    public boolean isConnected() {
        return groupListenerStrategy.getGroup().isConnected();
    }

    @ManagedAttribute(description = "Are we the master")
    public boolean isMaster() {
        return groupListenerStrategy.getGroup().isMaster();
    }

    @ManagedOperation(description = "Information about all the slaves")
    public String slaves() {
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
        Object event = groupListenerStrategy.getGroup().getLastState();
        return event != null ? event.toString() : null;
    }

    @ManagedOperation(description = "Information about this node")
    public String thisNode() {
        return thisNodeState != null ? thisNodeState.toString() : null;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // get group listener for this endpoint
        GroupListenerStrategyFactory<?> factory = endpoint.getComponent().getGroupListenerStrategyFactory();
        this.groupListenerStrategy = factory.newInstance(endpoint.getGroupName(),
            endpoint.getComponent().getContainerIdFactory().newContainerId(),
            endpoint.getEndpointUri(),
            this::onLockOwned,
            this::onDisconnected);

        this.groupListenerStrategy.setCamelContext(endpoint.getCamelContext());
        ServiceHelper.startService(groupListenerStrategy);

        LOG.info("Attempting to become master for endpoint: " + endpoint + " in " + endpoint.getCamelContext() + " with singletonID: " + endpoint.getGroupName());
        thisNodeState = this.groupListenerStrategy.createNodeState();
        groupListenerStrategy.updateState(thisNodeState);
    }

    @Override
    protected void doStop() throws Exception {
        try {
            stopConsumer();
        } finally {
            ServiceHelper.stopAndShutdownServices(groupListenerStrategy);
        }
        super.doStop();
    }

    private void stopConsumer() throws Exception {
        ServiceHelper.stopAndShutdownServices(delegate);
        ServiceHelper.stopAndShutdownServices(endpoint.getConsumerEndpoint());
        delegate = null;
        delegateService = null;
        thisNodeState = null;
    }

    @Override
    protected void doResume() throws Exception {
        if (delegateService != null) {
            delegateService.resume();
        }
        super.doResume();
    }

    @Override
    protected void doSuspend() throws Exception {
        if (delegateService != null) {
            delegateService.suspend();
        }
        super.doSuspend();
    }

    protected Boolean onLockOwned() {
        Boolean result = false;
        if (delegate == null) {
            try {
                // ensure endpoint is also started
                LOG.info("Elected as master. Starting consumer: {}", endpoint.getConsumerEndpoint());
                ServiceHelper.startService(endpoint.getConsumerEndpoint());

                delegate = endpoint.getConsumerEndpoint().createConsumer(processor);
                delegateService = null;
                if (delegate instanceof SuspendableService) {
                    delegateService = (SuspendableService) delegate;
                }

                // Lets show we are starting the consumer.
                thisNodeState = this.groupListenerStrategy.createNodeState();
                thisNodeState.setStarted(true);
                groupListenerStrategy.updateState(thisNodeState);

                ServiceHelper.startService(delegate);

                result = true;
            } catch (Exception e) {
                LOG.error("Failed to start master consumer for: " + endpoint, e);
            }

            LOG.info("Elected as master. Consumer started: {}", endpoint.getConsumerEndpoint());
        }
        return result;
    }

    protected Boolean onDisconnected() {
        Boolean result = false;
        try {
            stopConsumer();
            result = true;
        } catch (Exception e) {
            LOG.warn("Failed to stop master consumer for: " + endpoint, e);
        }
        return result;
    }

}
