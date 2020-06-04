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

package org.springframework.cloud.kubernetes.config.reload;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * reload 配置属性
 * General configuration for the configuration reload.
 *
 * @author Nicola Ferraro
 */
@ConfigurationProperties(prefix = "spring.cloud.kubernetes.reload")
public class ConfigReloadProperties {

	/**
	 * Enables the Kubernetes configuration reload on change.
	 */
	private boolean enabled = false;

	/**
	 * Enables monitoring on config maps to detect changes.
	 */
	private boolean monitoringConfigMaps = true;

	/**
	 * Enables monitoring on secrets to detect changes.
	 */
	private boolean monitoringSecrets = false;

	/**
	 * Sets the reload strategy for Kubernetes configuration reload on change.
	 */
	private ReloadStrategy strategy = ReloadStrategy.REFRESH;

	/**
	 * Sets the detection mode for Kubernetes configuration reload.
	 */
	private ReloadDetectionMode mode = ReloadDetectionMode.EVENT;

	/**
	 * Sets the polling period to use when the detection mode is POLLING.
	 */
	private Duration period = Duration.ofMillis(15000L);

	/**
	 * If Restart or Shutdown strategies are used, Spring Cloud Kubernetes waits a random
	 * amount of time before restarting. This is done in order to avoid having all
	 * instances of the same application restart at the same time. This property
	 * configures the maximum of amount of wait time from the moment the signal is
	 * received that a restart is needed until the moment the restart is actually
	 * triggered
	 */
	private Duration maxWaitForRestart = Duration.ofSeconds(2);

	public ConfigReloadProperties() {
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isMonitoringConfigMaps() {
		return this.monitoringConfigMaps;
	}

	public void setMonitoringConfigMaps(boolean monitoringConfigMaps) {
		this.monitoringConfigMaps = monitoringConfigMaps;
	}

	public boolean isMonitoringSecrets() {
		return this.monitoringSecrets;
	}

	public void setMonitoringSecrets(boolean monitoringSecrets) {
		this.monitoringSecrets = monitoringSecrets;
	}

	public ReloadStrategy getStrategy() {
		return this.strategy;
	}

	public void setStrategy(ReloadStrategy strategy) {
		this.strategy = strategy;
	}

	public ReloadDetectionMode getMode() {
		return this.mode;
	}

	public void setMode(ReloadDetectionMode mode) {
		this.mode = mode;
	}

	public Duration getPeriod() {
		return this.period;
	}

	public void setPeriod(Duration period) {
		this.period = period;
	}

	public Duration getMaxWaitForRestart() {
		return maxWaitForRestart;
	}

	public void setMaxWaitForRestart(Duration maxWaitForRestart) {
		this.maxWaitForRestart = maxWaitForRestart;
	}

	/**
	 * 重新加载策略
	 * Reload strategies.
	 */
	public enum ReloadStrategy {

		/**
		 * 更新 @ConfigurationProperties 或 @RefreshScope 修饰的 Bean
		 * Fire a refresh of beans annotated with @ConfigurationProperties
		 * or @RefreshScope.
		 */
		REFRESH,

		/**
		 * 当配置更新时重新启动应用
		 * Restarts the Spring ApplicationContext to apply the new configuration.
		 */
		RESTART_CONTEXT,

		/**
		 * 在容器中关闭应用，并重新启动一个，需要确定非 daemon 线程绑定在应用上下文中，并有相应的
		 * 重启策略
		 * Shuts down the Spring ApplicationContext to activate a restart of the
		 * container. Make sure that the lifecycle of all non-daemon threads is bound to
		 * the ApplicationContext and that a replication controller or replica set is
		 * configured to restart the pod.
		 */
		SHUTDOWN

	}

	/**
	 * 重新加载探测模式
	 * Reload detection modes.
	 */
	public enum ReloadDetectionMode {

		/**
		 * 启动一个拉取任务，定时拉取属性并重新加载
		 * Enables a polling task that retrieves periodically all external properties and
		 * fire a reload when they change.
		 */
		POLLING,

		/**
		 * 监听 Kubernetes 的事件并检查是否需要重新加载
		 * Listens to Kubernetes events and checks if a reload is needed when configmaps
		 * or secrets change.
		 */
		EVENT

	}

}
