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
package org.fedoraproject.p2query.osgi.impl;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.runtime.adaptor.EclipseStarter;
import org.fedoraproject.p2query.osgi.OSGiConfigurator;
import org.fedoraproject.p2query.osgi.OSGiFramework;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mikolaj Izdebski
 */
public class DefaultOSGiFramework implements OSGiFramework {
	private final Logger logger = LoggerFactory
			.getLogger(DefaultOSGiFramework.class);

	private final OSGiConfigurator equinoxConfig;
	
	private BundleContext bundleContext;

	public DefaultOSGiFramework(OSGiConfigurator equinoxConfig) {
		this.equinoxConfig = equinoxConfig;
	}

	private BundleContext launchEquinox() throws Exception {
		Map<String, String> properties = new LinkedHashMap<>();

		if (logger.isDebugEnabled()) {
			properties.put("osgi.debug", "true");
			properties.put("eclipse.consoleLog", "true");
		}

		StringBuffer buf = new StringBuffer();
		for (Path bundle : equinoxConfig.getBundles()) {
			buf.append(",");
			buf.append(bundle);
		}
		properties.put("osgi.bundles", buf.substring(1));

		properties.put("osgi.parentClassloader", "fwk");

		buf = new StringBuffer();
		for (String pkg : equinoxConfig.getExportedPackages()) {
			buf.append(",");
			buf.append(pkg);
		}
		properties.put("org.osgi.framework.system.packages.extra", buf.substring(1));

		logger.info("Launching Equinox...");
		System.setProperty("osgi.framework.useSystemProperties", "false");
		EclipseStarter.setInitialProperties(properties);
		EclipseStarter.startup(equinoxConfig.getArguments(), null);
		BundleContext context = EclipseStarter.getSystemBundleContext();

		if (context == null) {
			logger.error("Failed to launch Equinox");
			if (!logger.isDebugEnabled())
				logger.info("You can enable debugging output with -X to see more information.");
			throw new RuntimeException("Failed to launch Equinox");
		}

		tryActivateBundle(context, "org.eclipse.equinox.ds");
		tryActivateBundle(context, "org.eclipse.equinox.registry");
		tryActivateBundle(context, "org.eclipse.core.net");

		logger.debug("Equinox launched successfully");
		return context;
	}

	private void tryActivateBundle(BundleContext bundleContext,
			String symbolicName) {
		logger.debug("Trying to activate {}", symbolicName);

		for (Bundle bundle : bundleContext.getBundles()) {
			if (symbolicName.equals(bundle.getSymbolicName())) {
				try {
					bundle.start(Bundle.START_TRANSIENT);
				} catch (BundleException e) {
					logger.warn("Failed to activate bundle {}/{}",
							symbolicName, bundle.getVersion(), e);
				}
			}
		}
	}

	@Override
	public synchronized BundleContext getBundleContext() {
		if (bundleContext != null)
			return bundleContext;

		ClassLoader classLoader = Thread.currentThread()
				.getContextClassLoader();

		try {
			bundleContext = launchEquinox();
			return bundleContext;
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(classLoader);
		}
	}

	@Override
	public void shutdown() {
		try {
			EclipseStarter.shutdown();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
