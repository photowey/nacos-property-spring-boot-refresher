/*
 * Copyright © 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.photowey.nacos.property.refresher.listener;

import com.alibaba.nacos.api.config.ConfigChangeEvent;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.client.config.listener.impl.AbstractConfigChangeListener;
import com.alibaba.nacos.spring.context.event.config.NacosConfigReceivedEvent;
import com.alibaba.nacos.spring.factory.NacosServiceFactory;
import com.alibaba.nacos.spring.util.NacosBeanUtils;
import io.github.photowey.nacos.property.refresher.core.domain.meta.ConfigMeta;
import io.github.photowey.nacos.property.refresher.core.formatter.StringFormatter;
import io.github.photowey.nacos.property.refresher.dynamic.NacosDynamicRefresher;
import io.github.photowey.nacos.property.refresher.registry.DynamicConfigMetaNacosRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code AbstractNacosDynamicRefreshListener}
 *
 * @author photowey
 * @date 2024/03/29
 * @since 1.0.0
 */
public abstract class AbstractNacosDynamicRefreshListener extends AbstractConfigChangeListener implements
        ApplicationListener<NacosConfigReceivedEvent>, DynamicConfigMetaNacosRegistry, InitializingBean, BeanFactoryAware {

    protected static final Logger log = LoggerFactory.getLogger(AbstractNacosDynamicRefreshListener.class);

    protected static final String DEFAULT_GROUP = "DEFAULT_GROUP";
    protected static final String DEFAULT_CONFIG_TYPE = "yaml";
    protected static final String APP_KEY = "spring.application.name";

    protected final Set<ConfigMeta> configMetas = new HashSet<>();
    protected final Set<String> configDataIds = new HashSet<>();

    protected BeanFactory beanFactory;

    protected final AtomicInteger atomic = new AtomicInteger();

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        NacosServiceFactory factory = NacosBeanUtils.getNacosServiceFactoryBean();
        Collection<ConfigService> configServices = factory.getConfigServices();

        this.registerListener(configServices);
    }

    @Override
    public void onApplicationEvent(NacosConfigReceivedEvent event) {
        if (this.configMetas.isEmpty()) {
            return;
        }

        if (!this.configDataIds.contains(event.getDataId())) {
            return;
        }

        this.onEvent(event);
    }

    @Override
    public void receiveConfigChange(ConfigChangeEvent event) {
        this.onEvent(event);
    }

    // ----------------------------------------------------------------

    protected NacosDynamicRefresher nacosDynamicRefresher() {
        return this.beanFactory.getBean(NacosDynamicRefresher.class);
    }

    // ----------------------------------------------------------------

    /**
     * This method can be `Overridden` by subclasses if necessary.
     * <p>
     * Notes: {@link NacosConfigReceivedEvent} is executed before {@link ConfigChangeEvent}.
     *
     * @param event {@link NacosConfigReceivedEvent}
     */
    protected void onEvent(NacosConfigReceivedEvent event) {
        if (this.preRefresh(event)) {
            log.info("Dynamic.refresher: spring.nacos.dynamic.refresh.listener onchange.event(NacosConfigReceivedEvent):[{}:{}:{}]",
                    event.getGroupId(), event.getDataId(), event.getType());
            this.nacosDynamicRefresher().refresh();
            this.posRefresh(event);
        }
    }

    /**
     * This method can be `Overridden` by subclasses if necessary.
     *
     * @param event {@link ConfigChangeEvent}
     */
    protected void onEvent(ConfigChangeEvent event) {
        if (this.preRefresh(event)) {
            log.info("Dynamic.refresher: spring.nacos.dynamic.refresh.listener onchange.event(ConfigChangeEvent), counter:[{}]", this.atomic.getAndIncrement());
            this.nacosDynamicRefresher().refresh();
            this.posRefresh(event);
        }
    }

    // ----------------------------------------------------------------

    @Override
    public void registerConfigMeta(ConfigMeta meta) {
        this.configMetas.add(meta);
        this.configDataIds.add(meta.getDataId());
    }

    @Override
    public Set<ConfigMeta> tryAcquireConfigMetas() {
        return Collections.unmodifiableSet(this.configMetas);
    }

    // ----------------------------------------------------------------

    protected boolean preRefresh(NacosConfigReceivedEvent event) {
        // do nothing
        return this.determineHandleNacosConfigReceivedEvent(event);
    }

    protected void posRefresh(NacosConfigReceivedEvent event) {
        // do nothing
    }

    // ----------------------------------------------------------------

    protected boolean preRefresh(ConfigChangeEvent event) {
        // do nothing
        return this.determineHandleConfigChangeEvent(event);
    }

    protected void posRefresh(ConfigChangeEvent event) {
        // do nothing
    }

    protected boolean determineHandleNacosConfigReceivedEvent(NacosConfigReceivedEvent event) {
        return false;
    }

    protected boolean determineHandleConfigChangeEvent(ConfigChangeEvent event) {
        return true;
    }

    // ----------------------------------------------------------------

    public abstract void registerListener(Collection<ConfigService> configServices);

    public void addTemplateListener(ConfigService configService, String template) {
        this.addTemplateListener(configService, DEFAULT_GROUP, template);
    }

    public void addTemplateListener(ConfigService configService, String groupId, String template) {
        Environment environment = this.beanFactory.getBean(Environment.class);
        String app = environment.getProperty(APP_KEY);
        String dataId = StringFormatter.format(template, app);

        this.addListener(configService, groupId, dataId);
        this.doRegisterMeta(groupId, dataId);
    }

    public void addListener(ConfigService configService, String dataId) {
        this.addListener(configService, DEFAULT_GROUP, dataId);
    }

    public void addListener(ConfigService configService, String group, String dataId) {
        try {
            configService.addListener(dataId, group, this);
            if (log.isInfoEnabled()) {
                log.info("Dynamic.refresher: register nacos dynamic refresh listener, meta:[dataId:{},group:{}]", dataId, group);
            }
        } catch (Exception e) {
            String message = StringFormatter.format(
                    "Dynamic.refresher: register nacos dynamic refresh listener failed, meta:[dataId:{},group:{}]", dataId, group);
            throw new RuntimeException(message, e);
        }
    }

    // ----------------------------------------------------------------

    private void doRegisterMeta(String groupId, String dataId) {
        ConfigMeta meta = ConfigMeta.builder()
                .groupId(groupId)
                .dataId(dataId)
                .type(DEFAULT_CONFIG_TYPE)
                .build();

        this.registerConfigMeta(meta);
    }
}