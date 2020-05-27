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

package org.springframework.cloud.kubernetes;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Auto configuration for Kubernetes.
 * <p>
 * Kubernetes 自动配置
 *
 * @author Ioannis Canellos
 * @author Eddú Meléndez
 * @author Tim Ysewyn
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnKubernetesEnabled
@EnableConfigurationProperties(KubernetesClientProperties.class)
public class KubernetesAutoConfiguration {

	private static final Log LOG = LogFactory.getLog(KubernetesAutoConfiguration.class);

	/**
	 * 如果 dis 不为空，则返回 dis，否则返回 dat
	 *
	 * @param dis
	 * @param dat
	 * @param <D>
	 * @return
	 */
	private static <D> D or(D dis, D dat) {
		if (dis != null) {
			return dis;
		} else {
			return dat;
		}
	}

	/**
	 * 如果 dis 不为空，则转为 Integer 返回 dis，否则返回 dat
	 *
	 * @param dis
	 * @param dat
	 * @return
	 */
	private static Integer orDurationInt(Duration dis, Integer dat) {
		if (dis != null) {
			return (int) dis.toMillis();
		} else {
			return dat;
		}
	}

	/**
	 * 如果 dis 不为空，则转为 Long 返回 dis，否则返回 dat
	 *
	 * @param dis
	 * @param dat
	 * @return
	 */
	private static Long orDurationLong(Duration dis, Long dat) {
		if (dis != null) {
			return dis.toMillis();
		} else {
			return dat;
		}
	}

	/**
	 * 加载 Kubernetes 基本配置
	 *
	 * @param kubernetesClientProperties
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(Config.class)
	public Config kubernetesClientConfig(KubernetesClientProperties kubernetesClientProperties) {
		// 先尝试分别加载 ~/.kube 下面的配置，ServiceAccount 和 Namespace 文件
		// 当配置文件中的配置缺失时使用基本的配置
		Config base = Config.autoConfigure(null);
		Config properties = new ConfigBuilder(base)
			// Only set values that have been explicitly specified
			.withMasterUrl(or(kubernetesClientProperties.getMasterUrl(), base.getMasterUrl()))
			.withApiVersion(or(kubernetesClientProperties.getApiVersion(), base.getApiVersion()))
			.withNamespace(or(kubernetesClientProperties.getNamespace(), base.getNamespace()))
			.withUsername(or(kubernetesClientProperties.getUsername(), base.getUsername()))
			.withPassword(or(kubernetesClientProperties.getPassword(), base.getPassword()))
			.withCaCertFile(or(kubernetesClientProperties.getCaCertFile(), base.getCaCertFile()))
			.withCaCertData(or(kubernetesClientProperties.getCaCertData(), base.getCaCertData()))
			.withClientKeyFile(or(kubernetesClientProperties.getClientKeyFile(), base.getClientKeyFile()))
			.withClientKeyData(or(kubernetesClientProperties.getClientKeyData(), base.getClientKeyData()))
			.withClientCertFile(or(kubernetesClientProperties.getClientCertFile(), base.getClientCertFile()))
			.withClientCertData(or(kubernetesClientProperties.getClientCertData(), base.getClientCertData()))
			// No magic is done for the properties below so we leave them as is.
			.withClientKeyAlgo(or(kubernetesClientProperties.getClientKeyAlgo(), base.getClientKeyAlgo()))
			.withClientKeyPassphrase(or(kubernetesClientProperties.getClientKeyPassphrase(), base.getClientKeyPassphrase()))
			.withConnectionTimeout(orDurationInt(kubernetesClientProperties.getConnectionTimeout(), base.getConnectionTimeout()))
			.withRequestTimeout(orDurationInt(kubernetesClientProperties.getRequestTimeout(), base.getRequestTimeout()))
			.withRollingTimeout(orDurationLong(kubernetesClientProperties.getRollingTimeout(), base.getRollingTimeout()))
			.withTrustCerts(or(kubernetesClientProperties.isTrustCerts(), base.isTrustCerts()))
			.withHttpProxy(or(kubernetesClientProperties.getHttpProxy(), base.getHttpProxy()))
			.withHttpsProxy(or(kubernetesClientProperties.getHttpsProxy(), base.getHttpsProxy()))
			.withProxyUsername(or(kubernetesClientProperties.getProxyUsername(), base.getProxyUsername()))
			.withPassword(or(kubernetesClientProperties.getProxyPassword(), base.getProxyPassword()))
			.withNoProxy(or(kubernetesClientProperties.getNoProxy(), base.getNoProxy()))
			.build();

		if (properties.getNamespace() == null || properties.getNamespace().isEmpty()) {
			LOG.warn("No namespace has been detected. Please specify "
				+ "KUBERNETES_NAMESPACE env var, or use a later kubernetes version (1.3 or later)");
		}
		return properties;
	}

	/**
	 * 初始化 Kubernetes 客户端
	 *
	 * @param config
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean
	public KubernetesClient kubernetesClient(Config config) {
		return new DefaultKubernetesClient(config);
	}

	/**
	 * 初始化 Pod 工具类
	 *
	 * @param client
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean
	public StandardPodUtils kubernetesPodUtils(KubernetesClient client) {
		return new StandardPodUtils(client);
	}

	/**
	 * 初始化健康检查
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(HealthIndicator.class)
	protected static class KubernetesActuatorConfiguration {

		/**
		 * 初始化健康检查
		 *
		 * @param podUtils
		 * @return
		 */
		@Bean
		@ConditionalOnEnabledHealthIndicator("kubernetes")
		public KubernetesHealthIndicator kubernetesHealthIndicator(PodUtils podUtils) {
			return new KubernetesHealthIndicator(podUtils);
		}

		/**
		 * 初始化基本信息
		 *
		 * @param podUtils
		 * @return
		 */
		@Bean
		public KubernetesInfoContributor kubernetesInfoContributor(PodUtils podUtils) {
			return new KubernetesInfoContributor(podUtils);
		}

	}

}
