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
package com.github.utils4e.app;

import java.util.Arrays;
import java.util.List;

import com.github.utils4e.osgi.OSGiApplication;

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
		OSGiApplication.main(args, P2Query.class);
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
