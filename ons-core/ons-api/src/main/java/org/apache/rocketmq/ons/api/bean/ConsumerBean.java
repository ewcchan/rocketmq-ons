/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.ons.api.bean;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import org.apache.rocketmq.ons.api.Consumer;
import org.apache.rocketmq.ons.api.ExpressionType;
import org.apache.rocketmq.ons.api.MessageListener;
import org.apache.rocketmq.ons.api.MessageSelector;
import org.apache.rocketmq.ons.api.ONSFactory;
import org.apache.rocketmq.ons.api.exception.ONSClientException;


public class ConsumerBean implements Consumer {

    private Properties properties;


    private Map<Subscription, MessageListener> subscriptionTable;

    private Consumer consumer;


    @Override
    public void start() {
        if (null == this.properties) {
            throw new ONSClientException("properties not set");
        }

        if (null == this.subscriptionTable) {
            throw new ONSClientException("subscriptionTable not set");
        }

        this.consumer = ONSFactory.createConsumer(this.properties);

        Iterator<Entry<Subscription, MessageListener>> it = this.subscriptionTable.entrySet().iterator();
        while (it.hasNext()) {
            Entry<Subscription, MessageListener> next = it.next();
            if ("com.aliyun.openservices.ons.api.impl.notify.ConsumerImpl".equals(this.consumer.getClass().getCanonicalName())
                && (next.getKey() instanceof SubscriptionExt)) {
                SubscriptionExt subscription = (SubscriptionExt) next.getKey();
                for (Method method : this.consumer.getClass().getMethods()) {
                    if ("subscribeNotify".equals(method.getName())) {
                        try {
                            method.invoke(consumer, subscription.getTopic(), subscription.getExpression(),
                                    subscription.isPersistence(), next.getValue());
                        } catch (Exception e) {
                            throw new ONSClientException("subscribeNotify invoke exception", e);
                        }
                        break;
                    }
                }

            } else {
                Subscription subscription = next.getKey();
                if (subscription.getType() == null || ExpressionType.TAG.name().equals(subscription.getType())) {

                    this.subscribe(subscription.getTopic(), subscription.getExpression(), next.getValue());

                } else if (ExpressionType.SQL92.name().equals(subscription.getType())) {

                    this.subscribe(subscription.getTopic(), MessageSelector.bySql(subscription.getExpression()), next.getValue());
                } else {

                    throw new ONSClientException(String.format("Expression type %s is unknown!", subscription.getType()));
                }
            }

        }

        this.consumer.start();
    }

    @Override
    public void updateCredential(Properties credentialProperties) {
        if (this.consumer != null) {
            this.consumer.updateCredential(credentialProperties);
        }
    }


    @Override
    public void shutdown() {
        if (this.consumer != null) {
            this.consumer.shutdown();
        }
    }

    @Override
    public void subscribe(String topic, String subExpression, MessageListener listener) {
        if (null == this.consumer) {
            throw new ONSClientException("subscribe must be called after consumerBean started");
        }
        this.consumer.subscribe(topic, subExpression, listener);
    }

    @Override
    public void subscribe(final String topic, final MessageSelector selector, final MessageListener listener) {
        if (null == this.consumer) {
            throw new ONSClientException("subscribe must be called after consumerBean started");
        }
        this.consumer.subscribe(topic, selector, listener);
    }

    @Override
    public void unsubscribe(String topic) {
        if (null == this.consumer) {
            throw new ONSClientException("unsubscribe must be called after consumerBean started");
        }
        this.consumer.unsubscribe(topic);
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public Map<Subscription, MessageListener> getSubscriptionTable() {
        return subscriptionTable;
    }

    public void setSubscriptionTable(Map<Subscription, MessageListener> subscriptionTable) {
        this.subscriptionTable = subscriptionTable;
    }

    @Override
    public boolean isStarted() {
        return this.consumer.isStarted();
    }

    @Override
    public boolean isClosed() {
        return this.consumer.isClosed();
    }
}
