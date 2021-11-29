/*******************************************************************************
 * Copyright (c) 2021 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.github.utils4e.osgi;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.github.utils4e.osgi.impl.DefaultOSGiConfigurator;
import com.github.utils4e.osgi.impl.DefaultOSGiFramework;
import com.github.utils4e.osgi.impl.DefaultOSGiServiceLocator;

public class OSGiApplication {

	public static void main(String[] args, Class<?> clazz) {
		Path eclipseHome = Paths.get(args[0]);
		String [] jarsEntries = args[1].split(":");
		Path [] jarPaths = new Path[jarsEntries.length];
		for (int i = 0; i < jarPaths.length; i++) {
			jarPaths[i] = Paths.get(jarsEntries[i]);
		}
		String [] cmdArgs = new String [args.length - 2];
		for (int i = 2; i < args.length; i++) {
			cmdArgs[i-2] = args[i];
		}

        OSGiConfigurator configurator = new DefaultOSGiConfigurator(eclipseHome, jarPaths);
        OSGiFramework framework = new DefaultOSGiFramework(configurator);
        OSGiServiceLocator locator = new DefaultOSGiServiceLocator(framework);
        Object p2ql = locator.getService(clazz);
        try {
			Method exec = p2ql.getClass().getMethod("executeQuery", String[].class);
			exec.invoke(p2ql, new Object [] {cmdArgs});
		} catch (Exception e) {
			e.printStackTrace();
		}
        framework.shutdown();
	}

}
