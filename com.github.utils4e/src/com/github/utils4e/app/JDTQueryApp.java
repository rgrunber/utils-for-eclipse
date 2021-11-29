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
package com.github.utils4e.app;

import com.github.utils4e.osgi.OSGiApplication;

public class JDTQueryApp {

	public static void main (String [] args) {
		OSGiApplication.main(args, JDTQuery.class);
    }
}
