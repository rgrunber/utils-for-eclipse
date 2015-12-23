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
import java.util.Arrays;
import java.util.List;

import org.fedoraproject.p2query.osgi.OSGiConfigurator;
import org.fedoraproject.p2query.osgi.OSGiFramework;
import org.fedoraproject.p2query.osgi.OSGiServiceLocator;
import org.fedoraproject.p2query.osgi.impl.DefaultOSGiConfigurator;
import org.fedoraproject.p2query.osgi.impl.DefaultOSGiFramework;
import org.fedoraproject.p2query.osgi.impl.DefaultOSGiServiceLocator;

public class P2QueryApp {

	static final List<String> FOUR = Arrays.asList(new String [] {"dangling"});
	static final List<String> FIVE = Arrays.asList(
			new String [] {"provides", "whatprovides",
					"requires", "whatrequires", "transitive-closure", "diff"});

	public static void main (String [] args) {
		if (args.length < 4 || args.length > 5
				|| (args.length == 4 && !FOUR.contains(args[3]))
				|| (args.length == 5 && !FIVE.contains(args[3]) && !FIVE.contains(args[2]))) {
			printUsage();
			return;
		}

		Path eclipseHome = Paths.get(args[0]);
		String [] jarsEntries = args[1].split(":");
		Path [] jarPaths = new Path[jarsEntries.length];
		for (int i = 0; i < jarPaths.length; i++) {
			jarPaths[i] = Paths.get(jarsEntries[i]);
		}

        OSGiConfigurator configurator = new DefaultOSGiConfigurator(eclipseHome, jarPaths);
        OSGiFramework framework = new DefaultOSGiFramework(configurator);
        OSGiServiceLocator locator = new DefaultOSGiServiceLocator(framework);
        Object p2ql = locator.getService(P2Query.class);
        try {
			Method exec = p2ql.getClass().getMethod("executeQuery", String.class, String.class, String.class);
			exec.invoke(p2ql, args[2], args[3], args.length == 5 ? args[4] : null);
		} catch (Exception e) {
			e.printStackTrace();
		}
        framework.shutdown();

    }

	private static void printUsage() {
		System.out.println("Usage : p2ql REPO COMMAND ARGUMENT");
		System.out.println("Supported Commands : ");
		System.out.println("provides, "
				+ "whatprovides, "
				+ "requires, "
				+ "whatrequires, "
				+ "transitive-closure");
		System.out.println("Usage : p2ql REPO COMMAND");
		System.out.println("Supported Commands: ");
		System.out.println("dangling");
		System.out.println("Usage : p2ql OLD_REPO NEW_REPO");
		System.out.println("Supported Commands : ");
		System.out.println("diff");
	}

}
