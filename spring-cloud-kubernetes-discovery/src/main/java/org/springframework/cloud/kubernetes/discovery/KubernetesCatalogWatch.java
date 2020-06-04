/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.discovery;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Kubernetes 服务监听
 *
 * @author Oleg Vyukov
 */
public class KubernetesCatalogWatch implements ApplicationEventPublisherAware {

	private static final Logger logger = LoggerFactory.getLogger(KubernetesCatalogWatch.class);

	private final KubernetesClient kubernetesClient;

	// 保存pod名称列表
	private final AtomicReference<List<String>> catalogEndpointsState = new AtomicReference<>();

	private ApplicationEventPublisher publisher;

	public KubernetesCatalogWatch(KubernetesClient kubernetesClient) {
		this.kubernetesClient = kubernetesClient;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	// 定时任务拉取
	// 依赖于 @EnableScheduling，默认不会生效
	@Scheduled(fixedDelayString = "${spring.cloud.kubernetes.discovery.catalogServicesWatchDelay:30000}")
	public void catalogServicesWatch() {
		try {
			List<String> previousState = this.catalogEndpointsState.get();

			// not all pods participate in the service discovery. only those that have
			// endpoints.
			// 仅有 endpoint 的服务参与服务发现
			// Endpoints 是一个资源对象，存储在Etcd中，用于记录一个 Service对应的Pod的访问地址

			List<Endpoints> endpoints = this.kubernetesClient.endpoints()
			                                                 .list()
			                                                 .getItems();

			// 将 endpoint 转为pod名称
			// TODO pod名称有重复，可能是个bug，需要去重
			List<String> endpointsPodNames = endpoints.stream()
			                                          .map(Endpoints::getSubsets)
			                                          .filter(Objects::nonNull)
			                                          .flatMap(Collection::stream)
			                                          .map(EndpointSubset::getAddresses)
			                                          .filter(Objects::nonNull)
			                                          .flatMap(Collection::stream)
			                                          .map(EndpointAddress::getTargetRef)
			                                          .filter(Objects::nonNull)
			                                          .map(ObjectReference::getName) // pod name
			                                          // unique in
			                                          // namespace
			                                          .sorted(String::compareTo)
			                                          .collect(Collectors.toList());

			this.catalogEndpointsState.set(endpointsPodNames);

			// 如果 pod 列表发生变化，则发送 HeartbeatEvent 事件
			if (!endpointsPodNames.equals(previousState)) {
				logger.trace("Received endpoints update from kubernetesClient: {}", endpointsPodNames);
				this.publisher.publishEvent(new HeartbeatEvent(this, endpointsPodNames));
			}
		} catch (Exception e) {
			logger.error("Error watching Kubernetes Services", e);
		}
	}

}
