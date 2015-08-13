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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;

import org.fedoraproject.p2query.osgi.OSGiConfigurator;

/**
 * @author Mikolaj Izdebski
 */
public class DefaultOSGiConfigurator implements OSGiConfigurator {
	private Path eclipseHome;
	private Path [] jarPaths;
	
	public DefaultOSGiConfigurator(Path eclipseHome, Path [] jarPaths) {
		this.eclipseHome = eclipseHome;
		this.jarPaths = jarPaths;
	}

	@Override
	public Collection<Path> getBundles() {
		Collection<Path> res = new LinkedHashSet<>();
		res.addAll(Arrays.asList(jarPaths));
		Path pluginsDir = eclipseHome.resolve("plugins");
		try {
			for (Path bundle : Files.newDirectoryStream(pluginsDir)) {
				if (!bundle.getFileName().startsWith("org.eclipse.osgi_")) {
					res.add(bundle);
				}
			}
		} catch (IOException e) {
		}
		return res;
	}

	@Override
	public Collection<String> getExportedPackages() {
		return Arrays.asList("org.slf4j");
	}

    @Override
    public String[] getArguments() {
    	return new String[0];
    }
}
