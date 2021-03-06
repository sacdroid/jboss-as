/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat, Inc. and/or its affiliates, and individual
 * contributors by the @authors tag. See the copyright.txt in the
 * distribution for a full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.weld.ejb;

import org.jboss.as.weld.WeldContainer;
import org.jboss.invocation.Interceptor;
import org.jboss.invocation.InterceptorContext;
import org.jboss.invocation.InterceptorFactory;
import org.jboss.invocation.InterceptorFactoryContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.weld.context.ejb.EjbLiteral;
import org.jboss.weld.context.ejb.EjbRequestContext;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import java.io.Serializable;

/**
 * Interceptor for activating the CDI request scope on some EJB invocations.
 * <p/>
 * For efficency reasons we do not check to see if the scope is already active. For this reason this interceptor
 * can only be used for request paths where we know that the scope is not active, such as MDB's and the timer service.
 * <p/>
 * Remote EJB invocations must also have the reqest scope active, but it may already be active for in-VM requests.
 * <p/>
 * This interceptor is largely stateless, and can be re-used
 *
 * @author Stuart Douglas
 */
public class EjbRequestScopeActivationInterceptor implements Serializable, org.jboss.invocation.Interceptor {

    private final EjbRequestContext requestContext;

    public EjbRequestScopeActivationInterceptor(final EjbRequestContext context) {
        this.requestContext = context;
    }

    @Override
    public Object processInvocation(final InterceptorContext context) throws Exception {
        try {
            requestContext.associate(context.getInvocationContext());
            requestContext.activate();
            return context.proceed();
        } finally {
            requestContext.deactivate();
            requestContext.dissociate(context.getInvocationContext());
        }
    }


    public static class Factory implements InterceptorFactory {

        private final InjectedValue<WeldContainer> weldContainer = new InjectedValue<WeldContainer>();
        private final ClassLoader classLoader;
        private volatile Interceptor interceptor = null;

        public Factory(final ClassLoader classLoader) {
            this.classLoader = classLoader;
        }


        @Override
        public org.jboss.invocation.Interceptor create(final InterceptorFactoryContext context) {
            if (interceptor == null) {
                final ClassLoader tccl = SecurityActions.getContextClassLoader();
                try {
                    SecurityActions.setContextClassLoader(classLoader);
                    //it does not matter if this happens twice
                    final BeanManager beanManager = weldContainer.getValue().getBeanManager();
                    final Bean<?> bean = beanManager.resolve(beanManager.getBeans(EjbRequestContext.class, EjbLiteral.INSTANCE));
                    final CreationalContext<?> ctx = beanManager.createCreationalContext(bean);
                    EjbRequestContext requestContext = (EjbRequestContext) beanManager.getReference(bean, EjbRequestContext.class, ctx);
                    interceptor = new EjbRequestScopeActivationInterceptor(requestContext);
                } finally {
                    SecurityActions.setContextClassLoader(tccl);
                }
            }
            return interceptor;
        }

        public InjectedValue<WeldContainer> getWeldContainer() {
            return weldContainer;
        }
    }

}