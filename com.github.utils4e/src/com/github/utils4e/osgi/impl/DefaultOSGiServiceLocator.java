/*******************************************************************************
 * Copyright (c) 2014 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.github.utils4e.osgi.impl;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import com.github.utils4e.osgi.OSGiFramework;
import com.github.utils4e.osgi.OSGiServiceLocator;

/**
 * @author Mikolaj Izdebski
 */
public class DefaultOSGiServiceLocator implements OSGiServiceLocator {
	private final OSGiFramework framework;

	public DefaultOSGiServiceLocator(OSGiFramework framework) {
		this.framework = framework;
	}

	@Override
	public <T> T getService(Class<T> clazz) {
		BundleContext context = framework.getBundleContext();

		ServiceReference<?> serviceReference = null;
		try {
			serviceReference = context
					.getAllServiceReferences(clazz.getName(), null)[0];
		} catch (InvalidSyntaxException e) {
			// filter syntax is valid
		}

		if (serviceReference == null)
			throw new RuntimeException("OSGi service for " + clazz.getName()
					+ " was not found");

		return (T) context.getService(serviceReference);
	}
}