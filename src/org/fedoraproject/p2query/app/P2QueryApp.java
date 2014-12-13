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
package org.fedoraproject.p2query.app;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.fedoraproject.p2query.osgi.OSGiConfigurator;
import org.fedoraproject.p2query.osgi.OSGiFramework;
import org.fedoraproject.p2query.osgi.OSGiServiceLocator;
import org.fedoraproject.p2query.osgi.impl.DefaultOSGiConfigurator;
import org.fedoraproject.p2query.osgi.impl.DefaultOSGiFramework;
import org.fedoraproject.p2query.osgi.impl.DefaultOSGiServiceLocator;

public class P2QueryApp {

	public static void main (String [] args) {
		if (args.length != 5) {
			System.out.println("Usage : p2ql REPO COMMAND ARGUMENT");
			return;
		}

		Path eclipseHome = Paths.get(args[0]);
		Path p2queryJar = Paths.get(args[1]);
		String repo = args[2];
		String cmd = args[3];
		String query = args[4];

        OSGiConfigurator configurator = new DefaultOSGiConfigurator(eclipseHome, p2queryJar);
        OSGiFramework framework = new DefaultOSGiFramework(configurator);
        OSGiServiceLocator locator = new DefaultOSGiServiceLocator(framework);
        Object p2ql = locator.getService(P2Query.class);
        try {
			Method exec = p2ql.getClass().getMethod("executeQuery", String.class, String.class, String.class);
			exec.invoke(p2ql, repo, cmd, query);
		} catch (Exception e) {
			e.printStackTrace();
		}
        framework.shutdown();

    }

}
