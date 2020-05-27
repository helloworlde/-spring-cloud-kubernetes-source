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

import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.ConditionalOnBlockingDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.ConditionalOnKubernetesEnabled;
import org.springframework.cloud.kubernetes.KubernetesAutoConfiguration;
import org.springframework.cloud.kubernetes.registry.KubernetesRegistration;
import org.springframework.cloud.kubernetes.registry.KubernetesServiceRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 服务发现自动配置
 * Auto configuration for discovery clients.
 *
 * @author Mauricio Salatino
 * @author Tim Ysewyn
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@ConditionalOnKubernetesEnabled
@AutoConfigureBefore({SimpleDiscoveryClientAutoConfiguration.class, CommonsClientAutoConfiguration.class})
@AutoConfigureAfter({KubernetesAutoConfiguration.class})
public class KubernetesDiscoveryClientAutoConfiguration {

	/**
	 * 初始化判断是否安全的类
	 *
	 * @param properties
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean
	public DefaultIsServicePortSecureResolver isServicePortSecureResolver(KubernetesDiscoveryProperties properties) {
		return new DefaultIsServicePortSecureResolver(properties);
	}

	@Bean
	public KubernetesClientServicesFunction servicesFunction(KubernetesDiscoveryProperties properties) {

		// 如果 serviceLabel 是空的，
		if (properties.getServiceLabels().isEmpty()) {
			// 如果是所有的 namespace, 则使用 anyNamespace
			if (properties.isAllNamespaces()) {
				return (client) -> client.services()
				                         .inAnyNamespace();
			} else {
				// 否则返回根据client的namespace决定
				return KubernetesClient::services;
			}
		} else {
			// 如果 serviceLabel 不为空
			// 且指定是所有命名空间下，则同时指定使用serviceLabel
			if (properties.isAllNamespaces()) {
				return (client) -> client.services()
				                         .inAnyNamespace()
				                         .withLabels(properties.getServiceLabels());
			} else {
				// 不是所有命名空间，则只指定 serviceLabel
				return (client) -> client.services()
				                         .withLabels(properties.getServiceLabels());
			}
		}
	}

	/**
	 * 服务注册类
	 *
	 * @return
	 */
	@Bean
	public KubernetesServiceRegistry getServiceRegistry() {
		return new KubernetesServiceRegistry();
	}

	/**
	 * 服务注册信息
	 *
	 * @param client
	 * @param properties
	 * @return
	 */
	@Bean
	public KubernetesRegistration getRegistration(KubernetesClient client,
	                                              KubernetesDiscoveryProperties properties) {
		return new KubernetesRegistration(client, properties);
	}

	/**
	 * 属性配置
	 *
	 * @return
	 */
	@Bean
	public KubernetesDiscoveryProperties getKubernetesDiscoveryProperties() {
		return new KubernetesDiscoveryProperties();
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnBlockingDiscoveryEnabled
	@ConditionalOnKubernetesDiscoveryEnabled
	public static class KubernetesDiscoveryClientConfiguration {

		/**
		 * 初始化服务发现类
		 *
		 * @param client
		 * @param properties
		 * @param kubernetesClientServicesFunction
		 * @param isServicePortSecureResolver
		 * @return
		 */
		@Bean
		@ConditionalOnMissingBean
		public KubernetesDiscoveryClient kubernetesDiscoveryClient(
			KubernetesClient client,
			KubernetesDiscoveryProperties properties,
			KubernetesClientServicesFunction kubernetesClientServicesFunction,
			DefaultIsServicePortSecureResolver isServicePortSecureResolver) {
			return new KubernetesDiscoveryClient(client, properties,
				kubernetesClientServicesFunction, isServicePortSecureResolver);
		}

	}

}
