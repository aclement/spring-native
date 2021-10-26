/*
 * Copyright 2019-2021 the original author or authors.
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

package org.springframework.boot.actuate.endpoint.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aot.context.bootstrap.generator.infrastructure.nativex.BeanFactoryNativeConfigurationProcessor;
import org.springframework.aot.context.bootstrap.generator.infrastructure.nativex.NativeConfigurationRegistry;
import org.springframework.aot.context.bootstrap.generator.infrastructure.nativex.NativeProxyEntry;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.nativex.hint.ProxyBits;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * A {@link BeanFactoryNativeConfigurationProcessor} that registers the need for
 * ahead of time proxy classes (that must be generated at build time) when it
 * recognizes the use of certain annotations within components.
 *
 * @author Andy Clement
 */
class AotProxyConfigurationProcessor implements BeanFactoryNativeConfigurationProcessor {

	private static Log logger = LogFactory.getLog(AotProxyConfigurationProcessor.class);

	@Override
	public void process(ConfigurableListableBeanFactory beanFactory, NativeConfigurationRegistry registry) {
		new Processor().process(beanFactory, registry);
	}

	private static class Processor {

		public static List<String> METHOD_LEVEL_ANNOTATIONS = List
				.of("org.springframework.scheduling.annotation.Async");

		void process(ConfigurableListableBeanFactory beanFactory, NativeConfigurationRegistry registry) {
			findCandidates(beanFactory).forEach((beanName, beanType) -> registerProxy(registry, beanType));
		}

		private Map<String, Class<?>> findCandidates(ConfigurableListableBeanFactory beanFactory) {
			Map<String, Class<?>> candidates = new HashMap<>();
			beanFactory.getBeanNamesIterator().forEachRemaining((beanName) -> {
				Class<?> beanType = beanFactory.getType(beanName);
				MergedAnnotation<Component> componentAnnotation = MergedAnnotations.from(beanType).get(Component.class);
				if (componentAnnotation.isPresent()) {
					searchBeanMethods(beanFactory, candidates, beanName, beanType);
				}
			});
			return candidates;
		}

		@SuppressWarnings("unchecked")
		private void searchBeanMethods(ConfigurableListableBeanFactory beanFactory, Map<String, Class<?>> candidates,
				String beanName, Class<?> beanType) {
			for (String methodLevelAnnotation : METHOD_LEVEL_ANNOTATIONS) {
				try {
					Class<? extends Annotation> annotationClass = 
							(Class<? extends Annotation>) ClassUtils.forName(methodLevelAnnotation, beanFactory.getBeanClassLoader());
					for (Method method : beanType.getDeclaredMethods()) {
						if (method.getDeclaredAnnotation(annotationClass) != null) {
							logger.debug("adding AOT proxy for bean '" + beanName + "' of type "+beanType.getName()+" due to usage of " + methodLevelAnnotation);
							candidates.put(beanName, beanType);
							break;
						}
					}
				} catch (ClassNotFoundException | LinkageError e) {
					// Assume problems with the annotation class mean it cannot be annotated with it
				}
			}
		}

		private void registerProxy(NativeConfigurationRegistry registry, Class<?> type) {
			registry.proxy().add(NativeProxyEntry.ofClass(type, ProxyBits.IS_STATIC));
		}
	}

}
