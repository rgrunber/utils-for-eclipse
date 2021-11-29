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

import static org.eclipse.jdt.internal.core.search.indexing.IIndexConstants.ANNOTATION_REF;
import static org.eclipse.jdt.internal.core.search.indexing.IIndexConstants.CONSTRUCTOR_DECL;
import static org.eclipse.jdt.internal.core.search.indexing.IIndexConstants.CONSTRUCTOR_REF;
import static org.eclipse.jdt.internal.core.search.indexing.IIndexConstants.FIELD_DECL;
import static org.eclipse.jdt.internal.core.search.indexing.IIndexConstants.METHOD_DECL;
import static org.eclipse.jdt.internal.core.search.indexing.IIndexConstants.METHOD_DECL_PLUS;
import static org.eclipse.jdt.internal.core.search.indexing.IIndexConstants.METHOD_REF;
import static org.eclipse.jdt.internal.core.search.indexing.IIndexConstants.MODULE_DECL;
import static org.eclipse.jdt.internal.core.search.indexing.IIndexConstants.MODULE_INFO;
import static org.eclipse.jdt.internal.core.search.indexing.IIndexConstants.MODULE_REF;
import static org.eclipse.jdt.internal.core.search.indexing.IIndexConstants.OBJECT;
import static org.eclipse.jdt.internal.core.search.indexing.IIndexConstants.REF;
import static org.eclipse.jdt.internal.core.search.indexing.IIndexConstants.SUPER_REF;
import static org.eclipse.jdt.internal.core.search.indexing.IIndexConstants.TYPE_DECL;

import java.io.File;
import java.io.IOException;

import org.eclipse.jdt.core.index.JavaIndexer;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.core.index.EntryResult;
import org.eclipse.jdt.internal.core.index.FileIndexLocation;
import org.eclipse.jdt.internal.core.index.Index;
import org.eclipse.jdt.internal.core.index.IndexLocation;

public class JDTQuery {

	private static final char[][] ALL_CATEGORIES = { REF, ANNOTATION_REF, METHOD_REF, CONSTRUCTOR_REF, SUPER_REF,
			TYPE_DECL, METHOD_DECL, METHOD_DECL_PLUS, CONSTRUCTOR_DECL, FIELD_DECL, MODULE_DECL, MODULE_REF, OBJECT,
			MODULE_INFO };

	public void executeQuery(String [] args) {
		String pathToJar = args[0];
		String pathToIndex = args[1];
		try {
			JavaIndexer.generateIndexForJar(pathToJar, pathToIndex);
			IndexLocation indexLocation = new FileIndexLocation(new File(pathToIndex));
			Index index = new Index(indexLocation, pathToJar, true);

			for (char [] category : ALL_CATEGORIES) {
				EntryResult[] matches = index.query(new char [][] { category }, "*".toCharArray(), SearchPattern.R_PATTERN_MATCH);
				System.out.println("INDEX CATEGORY : " + new String(category));
				if (matches != null) {
					for (EntryResult match : matches) {
						String [] docNames = match.getDocumentNames(index);
						String message = String.format("%s ===> %s", new String(match.getWord()), String.join(",", docNames));
						System.out.println(message);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
