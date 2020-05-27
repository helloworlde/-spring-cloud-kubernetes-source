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

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

/**
 * Kubernetes 服务发现实现
 * Kubeneretes implementation of {@link DiscoveryClient}.
 *
 * @author Ioannis Canellos
 */
public class KubernetesDiscoveryClient implements DiscoveryClient {

	private static final Log log = LogFactory.getLog(KubernetesDiscoveryClient.class);

	private final KubernetesDiscoveryProperties properties;

	private final DefaultIsServicePortSecureResolver isServicePortSecureResolver;

	private final KubernetesClientServicesFunction kubernetesClientServicesFunction;

	private final SpelExpressionParser parser = new SpelExpressionParser();

	private final SimpleEvaluationContext evalCtxt = SimpleEvaluationContext
		.forReadOnlyDataBinding().withInstanceMethods().build();

	private KubernetesClient client;

	public KubernetesDiscoveryClient(KubernetesClient client,
	                                 KubernetesDiscoveryProperties kubernetesDiscoveryProperties,
	                                 KubernetesClientServicesFunction kubernetesClientServicesFunction) {

		this(client, kubernetesDiscoveryProperties, kubernetesClientServicesFunction,
			new DefaultIsServicePortSecureResolver(kubernetesDiscoveryProperties));
	}

	KubernetesDiscoveryClient(KubernetesClient client,
	                          KubernetesDiscoveryProperties kubernetesDiscoveryProperties,
	                          KubernetesClientServicesFunction kubernetesClientServicesFunction,
	                          DefaultIsServicePortSecureResolver isServicePortSecureResolver) {

		this.client = client;
		this.properties = kubernetesDiscoveryProperties;
		this.kubernetesClientServicesFunction = kubernetesClientServicesFunction;
		this.isServicePortSecureResolver = isServicePortSecureResolver;
	}

	public KubernetesClient getClient() {
		return this.client;
	}

	public void setClient(KubernetesClient client) {
		this.client = client;
	}

	@Override
	public String description() {
		return "Kubernetes Discovery Client";
	}

	/**
	 * 根据 serviceId 获取实例列表
	 *
	 * @param serviceId
	 * @return
	 */
	@Override
	public List<ServiceInstance> getInstances(String serviceId) {
		Assert.notNull(serviceId, "[Assertion failed] - the object argument must not be null");

		// 判断是否查询所有命名空间下的服务，如果是则根据 metadata.name 查询，
		// 否则根据服务名称查询
		List<Endpoints> endpointsList = this.properties.isAllNamespaces()
			? this.client.endpoints()
			             .inAnyNamespace()
			             .withField("metadata.name", serviceId)
			             .list()
			             .getItems()
			: Collections.singletonList(this.client.endpoints().withName(serviceId).get());

		List<EndpointSubsetNS> subsetsNS = endpointsList.stream()
		                                                .map(this::getSubsetsFromEndpoints)
		                                                .collect(Collectors.toList());

		// 获取所有的实例
		List<ServiceInstance> instances = new ArrayList<>();
		if (!subsetsNS.isEmpty()) {
			for (EndpointSubsetNS es : subsetsNS) {
				instances.addAll(this.getNamespaceServiceInstances(es, serviceId));
			}
		}

		return instances;
	}

	/**
	 * 查询指定命名空间下的服务
	 *
	 * @param es
	 * @param serviceId
	 * @return
	 */
	private List<ServiceInstance> getNamespaceServiceInstances(EndpointSubsetNS es,
	                                                           String serviceId) {
		String namespace = es.getNamespace();
		List<EndpointSubset> subsets = es.getEndpointSubset();
		List<ServiceInstance> instances = new ArrayList<>();
		if (!subsets.isEmpty()) {
			// 查询指定命名空间下的服务
			final Service service = this.client.services()
			                                   .inNamespace(namespace)
			                                   .withName(serviceId)
			                                   .get();

			// 获取 metadata
			final Map<String, String> serviceMetadata = this.getServiceMetadata(service);
			KubernetesDiscoveryProperties.Metadata metadataProps = this.properties.getMetadata();

			for (EndpointSubset s : subsets) {
				// Extend the service metadata map with per-endpoint port information (if
				// requested)
				Map<String, String> endpointMetadata = new HashMap<>(serviceMetadata);
				if (metadataProps.isAddPorts()) {
					// 获取端口信息
					Map<String, String> ports = s.getPorts()
					                             .stream()
					                             .filter(port -> !StringUtils.isEmpty(port.getName()))
					                             .collect(toMap(EndpointPort::getName, port -> Integer.toString(port.getPort())));
					// 端口数据转为 map
					Map<String, String> portMetadata = getMapWithPrefixedKeys(ports, metadataProps.getPortsPrefix());

					if (log.isDebugEnabled()) {
						log.debug("Adding port metadata: " + portMetadata);
					}

					// 添加到 metadata 中
					endpointMetadata.putAll(portMetadata);
				}

				List<EndpointAddress> addresses = s.getAddresses();
				for (EndpointAddress endpointAddress : addresses) {
					String instanceId = null;
					// 获取实例id
					if (endpointAddress.getTargetRef() != null) {
						instanceId = endpointAddress.getTargetRef().getUid();
					}

					// 获取实例端口
					EndpointPort endpointPort = findEndpointPort(s);

					// 创建服务实例并添加到列表中
					instances.add(
						new KubernetesServiceInstance(
							instanceId,
							serviceId,
							endpointAddress,
							endpointPort,
							endpointMetadata,
							// 判断是否是安全的
							this.isServicePortSecureResolver.resolve(
								// 创建input对象，作为resolve的参数
								new DefaultIsServicePortSecureResolver.Input(
									endpointPort.getPort(),
									service.getMetadata().getName(),
									service.getMetadata().getLabels(),
									service.getMetadata().getAnnotations()
								)
							)
						)
					);
				}
			}
		}

		return instances;
	}

	/**
	 * 获取服务的 Metadata(label 和 annotation)
	 *
	 * @param service
	 * @return
	 */
	private Map<String, String> getServiceMetadata(Service service) {
		final Map<String, String> serviceMetadata = new HashMap<>();
		KubernetesDiscoveryProperties.Metadata metadataProps = this.properties.getMetadata();

		if (metadataProps.isAddLabels()) {
			Map<String, String> labelMetadata = getMapWithPrefixedKeys(service.getMetadata().getLabels(), metadataProps.getLabelsPrefix());
			if (log.isDebugEnabled()) {
				log.debug("Adding label metadata: " + labelMetadata);
			}
			serviceMetadata.putAll(labelMetadata);
		}
		if (metadataProps.isAddAnnotations()) {
			Map<String, String> annotationMetadata = getMapWithPrefixedKeys(service.getMetadata().getAnnotations(), metadataProps.getAnnotationsPrefix());
			if (log.isDebugEnabled()) {
				log.debug("Adding annotation metadata: " + annotationMetadata);
			}
			serviceMetadata.putAll(annotationMetadata);
		}

		return serviceMetadata;
	}


	// 获取服务端口
	private EndpointPort findEndpointPort(EndpointSubset s) {
		List<EndpointPort> ports = s.getPorts();
		EndpointPort endpointPort;
		// 如果只有一个，则直接获取
		if (ports.size() == 1) {
			endpointPort = ports.get(0);
		} else {
			Predicate<EndpointPort> portPredicate;
			// 如果主端口不为空，则生成判断主端口的条件
			if (!StringUtils.isEmpty(properties.getPrimaryPortName())) {
				portPredicate = port -> properties.getPrimaryPortName()
				                                  .equalsIgnoreCase(port.getName());
			} else {
				// 主端口为空，则任意一个都可以
				portPredicate = port -> true;
			}
			// 查找端口
			endpointPort = ports.stream()
			                    .filter(portPredicate)
			                    .findAny()
			                    .orElseThrow(IllegalStateException::new);
		}
		return endpointPort;
	}

	// Endpoint pod的ip和端口映射，代表 REST API 端点，用于访问pod
	// Endpoints 是 subset 的组合，
	// { Addresses: [{"ip": "10.10.1.1"}, {"ip": "10.10.2.2"}], Ports: [{"name": "a", "port": 8675}, {"name": "b", "port": 309}] }
	// The resulting set of endpoints can be viewed as: a: [ 10.10.1.1:8675, 10.10.2.2:8675 ], b: [ 10.10.1.1:309, 10.10.2.2:309 ]
	// 参考 https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.12/#endpointsubset-v1-core
	private EndpointSubsetNS getSubsetsFromEndpoints(Endpoints endpoints) {
		EndpointSubsetNS es = new EndpointSubsetNS();
		// 设置命名空间
		es.setNamespace(this.client.getNamespace()); // start with the default that comes
		// with the client
		if (endpoints != null && endpoints.getSubsets() != null) {
			es.setNamespace(endpoints.getMetadata().getNamespace());
			es.setEndpointSubset(endpoints.getSubsets());
		}

		return es;
	}

	// 返回给key加上前缀的 map
	// returns a new map that contain all the entries of the original map
	// but with the keys prefixed
	// if the prefix is null or empty, the map itself is returned (unchanged of course)
	private Map<String, String> getMapWithPrefixedKeys(Map<String, String> map,
	                                                   String prefix) {
		if (map == null) {
			return new HashMap<>();
		}

		// when the prefix is empty just return an map with the same entries
		if (!StringUtils.hasText(prefix)) {
			return map;
		}

		final Map<String, String> result = new HashMap<>();
		map.forEach((k, v) -> result.put(prefix + k, v));

		return result;
	}

	/**
	 * 获取服务列表
	 *
	 * @return
	 */
	@Override
	public List<String> getServices() {
		// 过滤表达式
		String spelExpression = this.properties.getFilter();
		Predicate<Service> filteredServices;
		// 如果过滤表达式为空，则直接返回
		if (spelExpression == null || spelExpression.isEmpty()) {
			filteredServices = (Service instance) -> true;
		} else {
			// 表达式部位可能够， 则解析并过滤
			Expression filterExpr = this.parser.parseExpression(spelExpression);
			filteredServices = (Service instance) -> {
				Boolean include = filterExpr.getValue(this.evalCtxt, instance, Boolean.class);

				if (include == null) {
					return false;
				}
				return include;
			};
		}
		return getServices(filteredServices);
	}

	// 根据条件过滤服务，返回服务的名称集合
	public List<String> getServices(Predicate<Service> filter) {
		return this.kubernetesClientServicesFunction.apply(this.client)
		                                            .list()
		                                            .getItems()
		                                            .stream()
		                                            .filter(filter)
		                                            .map(s -> s.getMetadata().getName())
		                                            .collect(Collectors.toList());
	}

	@Override
	public int getOrder() {
		return this.properties.getOrder();
	}

}
