/*
 * Copyright 2012-2019 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.EndpointSubset;

/**
 * @author Haytham Mohamed
 **/
public class EndpointSubsetNS {

	private String namespace;

	// EndpointSubset 是一组带有端口号的地址，扩展Endpoint集是由地址和端口号构造成的坐标集
	// {
	// 	Addresses: [{"ip": "10.10.1.1"}, {"ip": "10.10.2.2"}],
	// 	Ports: [{"name": "a", "port": 8675}, {"name": "b", "port": 309}]
	// }
	// 上面的endpoint集最终可以写成如下形式：
	// a: [ 10.10.1.1:8675, 10.10.2.2:8675 ],
	// b: [ 10.10.1.1:309, 10.10.2.2:309 ]

	//Endpoints用来支撑service的endpoints集合，实际的请求都是要通过service分发到不同的endpoints去处理。例如：
	// Name: "mysvc",
	// Subsets: [
	// {
	// Addresses: [{"ip": "10.10.1.1"}, {"ip": "10.10.2.2"}],
	// Ports: [{"name": "a", "port": 8675}, {"name": "b", "port": 309}]
	// },
	// {
	// Addresses: [{"ip": "10.10.3.3"}],
	// Ports: [{"name": "a", "port": 93}, {"name": "b", "port": 76}]
	// },
	// ]
	private List<EndpointSubset> endpointSubset;

	public EndpointSubsetNS() {
		endpointSubset = new ArrayList<>();
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public List<EndpointSubset> getEndpointSubset() {
		return endpointSubset;
	}

	public void setEndpointSubset(List<EndpointSubset> endpointSubset) {
		this.endpointSubset = endpointSubset;
	}

	public boolean equals(Object o) {
		return this.endpointSubset.equals(o);
	}

	public int hashCode() {
		return this.endpointSubset.hashCode();
	}

}
