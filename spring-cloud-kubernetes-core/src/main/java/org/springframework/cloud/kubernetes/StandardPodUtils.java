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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.nio.file.Paths;
import java.util.function.Supplier;

/**
 * Utility class to work with pods.
 *
 * @author Ioannis Canellos
 */
public class StandardPodUtils implements PodUtils {

	/**
	 * Hostname environment variable name.
	 */
	public static final String HOSTNAME = "HOSTNAME";

	private static final Log LOG = LogFactory.getLog(StandardPodUtils.class);

	private final KubernetesClient client;

	private final String hostName;

	private Supplier<Pod> current;

	public StandardPodUtils(KubernetesClient client) {
		if (client == null) {
			throw new IllegalArgumentException("Must provide an instance of KubernetesClient");
		}

		this.client = client;
		this.hostName = System.getenv(HOSTNAME);
		// 懒加载 pod
		this.current = LazilyInstantiate.using(this::internalGetPod);
	}

	@Override
	public Supplier<Pod> currentPod() {
		return this.current;
	}

	/**
	 * 判断是否在容器中
	 * 根据是否获取到 pod 判断，如果 pod 不为空，则是在容器中
	 * 问题：在 Mac 和 Win 下的路径不一样，无法判断是否在容器中，只能在 Linux 上判断，
	 * 如果部署到 pod 中，也无法判断
	 *
	 * @return
	 */
	@Override
	public Boolean isInsideKubernetes() {
		return currentPod().get() != null;
	}

	/**
	 * 获取名称为 hostname 的 pod
	 *
	 * @return
	 */
	private synchronized Pod internalGetPod() {
		try {
			// 如果 ServiceAccount 存在，且 hostname 也存在，则根据 hostname 获取 pod
			if (isServiceAccountFound() && isHostNameEnvVarPresent()) {
				return this.client.pods().withName(this.hostName).get();
			} else {
				return null;
			}
		} catch (Throwable t) {
			LOG.warn("Failed to get pod with name:[" + this.hostName
					+ "]. You should look into this if things aren't"
					+ " working as you expect. Are you missing serviceaccount permissions?",
				t);
			return null;
		}
	}

	/**
	 * 判断 hostname 是否存在
	 *
	 * @return
	 */
	private boolean isHostNameEnvVarPresent() {
		return this.hostName != null && !this.hostName.isEmpty();
	}

	/**
	 * 查找 ServiceAccount，根据路径判断 token 和 ca.crt 是否存在，如果存在，则是 ServiceAccount
	 *
	 * @return
	 */
	private boolean isServiceAccountFound() {
		return Paths.get(Config.KUBERNETES_SERVICE_ACCOUNT_TOKEN_PATH).toFile().exists()
			&& Paths.get(Config.KUBERNETES_SERVICE_ACCOUNT_CA_CRT_PATH).toFile()
			        .exists();
	}

}
