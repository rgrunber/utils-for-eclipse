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
	static final List<String> NO_LIMIT = Arrays.asList(new String [] {"graph"});

	public static void main (String [] args) {
		if (args.length < 4) {
			printUsage();
			return;
		}
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
        Object p2ql = locator.getService(P2Query.class);
        try {
			Method exec = p2ql.getClass().getMethod("executeQuery", String[].class);
			exec.invoke(p2ql, new Object [] {cmdArgs});
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
				+ "transitive-closure "
				+ "overlap");
		System.out.println("Usage : p2ql REPO COMMAND");
		System.out.println("Supported Commands: ");
		System.out.println("dangling");
		System.out.println("Usage : p2ql COMMAND OLD_REPO NEW_REPO");
		System.out.println("Supported Commands : ");
		System.out.println("diff");
		System.out.println("Usage : p2ql COMMAND REPO_1 REPO_2 REPO_3, .. ,REPO_N");
		System.out.println("Supported Commands : ");
		System.out.println("graph");
	}

}
