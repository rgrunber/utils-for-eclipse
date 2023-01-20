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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.index.JavaIndexer;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.core.search.TypeNameRequestor;
import org.eclipse.jdt.internal.core.index.EntryResult;
import org.eclipse.jdt.internal.core.index.FileIndexLocation;
import org.eclipse.jdt.internal.core.index.Index;
import org.eclipse.jdt.internal.core.index.IndexLocation;

public class JDTQuery {

	private static final char[][] ALL_CATEGORIES = { REF, ANNOTATION_REF, METHOD_REF, CONSTRUCTOR_REF, SUPER_REF,
			TYPE_DECL, METHOD_DECL, METHOD_DECL_PLUS, CONSTRUCTOR_DECL, FIELD_DECL, MODULE_DECL, MODULE_REF, OBJECT,
			MODULE_INFO };

	public void executeQuery(String [] args) {
		switch (args[0]) {
		case "index":
		case "indexer":
			performIndexing(args);
			break;
		case "ast":
			performPrintAST(args);
			break;
		case "search":
			performSearch(args);
		default:
			break;
		}
	}

	private void performPrintAST(String[] args) {
		try (BufferedReader buff = new BufferedReader(new FileReader(new File(args[1])))) {
			ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
			char [] cbuf = new char [2048];
			StringBuilder source = new StringBuilder();
			while(buff.read(cbuf) > 0) {
				source.append(cbuf);
				cbuf = new char [2048];
			}
			parser.setSource(source.toString().toCharArray());
			CompilationUnit cuRoot = (CompilationUnit) parser.createAST(null);

			ASTVisitor visitor = new ASTVisitor() {
				@Override
				public void preVisit(ASTNode node) {
					int level = getLevel(node);
					System.out.println("  ".repeat(level) + node.getClass());
					super.preVisit(node);
				}

				private int getLevel(ASTNode node) {
					int lvl = 0;
					ASTNode curr = node;
					while (curr.getParent() != null) {
						lvl++;
						curr = curr.getParent();
					}
					return lvl;
				}
			};
			cuRoot.accept(visitor);
		} catch (IOException e) {
		}
	}

	private void performIndexing(String[] args) {
		String pathToJar = args[1];
		String pathToIndex = args[2];
		try {
			JavaIndexer.generateIndexForJar(pathToJar, pathToIndex);
			IndexLocation indexLocation = new FileIndexLocation(new File(pathToIndex));
			Index index = new Index(indexLocation, pathToJar, true);

			for (char[] category : ALL_CATEGORIES) {
				EntryResult[] matches = index.query(new char[][] { category }, "*".toCharArray(),
						SearchPattern.R_PATTERN_MATCH);
				System.out.println("INDEX CATEGORY : " + new String(category));
				if (matches != null) {
					for (EntryResult match : matches) {
						String[] docNames = match.getDocumentNames(index);
						String message = String.format("%s ===> %s", new String(match.getWord()),
								String.join(",", docNames));
						System.out.println(message);
					}
				}
			}
		} catch (IOException e) {
		}
	}

	private void performSearch(String[] args) {
		String projectPath = args[1];
		String pattern = args[2];
		int searchFor = Integer.valueOf(args[3]);
		int limitTo = Integer.valueOf(args[4]);
		int matchRule = Integer.valueOf(args[5]);

		String projectName = Paths.get(projectPath).toFile().getName();
		File projectFolder = new File(projectPath);
		IJavaProject javaProject = createJavaProject(projectName);
		try {
			addLibrariesToProject(javaProject, projectFolder);
			waitUntilOldIndexReady();
			performIndexSearch(pattern, searchFor, limitTo, matchRule);
		} catch (JavaModelException e) {
			e.printStackTrace();
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	private void performIndexSearch(String pattern, int searchFor, int limitTo, int matchRule) throws CoreException {
		SearchEngine engine = new SearchEngine();
		SearchPattern searchPattern = SearchPattern.createPattern(pattern, searchFor, limitTo, matchRule);
		SearchParticipant[] participant = new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() };

		SearchRequestor requestor = new SearchRequestor() {
			@Override
			public void acceptSearchMatch(SearchMatch match) throws CoreException {
				System.out.println(match.toString());
			}
		};
		System.out.println(String.format("Searching for pattern: '%s', searchFor: %d, limitTo: %d, matchRule: %d",
		pattern, searchFor, limitTo, matchRule));
		engine.search(searchPattern, participant, SearchEngine.createWorkspaceScope(), requestor, null);
	}

	private IJavaProject createJavaProject(String projectName) {
		try {
			IProject project = createProject(projectName);
			addJavaNature(project);
			return JavaCore.create(project);
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return null;
	}

	private IProject createProject(String projectName) throws CoreException {
		IProject project = getWorkspaceRoot().getProject(projectName);
		project.create(null);
		project.open(null);
		return project;
	}

	private void addJavaNature(IProject project) throws CoreException {
		IProjectDescription description = project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		project.setDescription(description, null);
	}

	private void addLibrariesToProject(IJavaProject jProject, File projectFolder) throws JavaModelException {
		List<IClasspathEntry> newCPE = new ArrayList<>(Arrays.asList(jProject.getRawClasspath()));
		List<File> sysJars = new ArrayList<>();

		getAllLibraries(projectFolder, sysJars);

		for (File f : sysJars) {
			IPath binJarPath = Path.fromOSString(f.getAbsolutePath());
			IClasspathEntry jarCPE = JavaCore.newLibraryEntry(binJarPath, null, null);
			newCPE.add(jarCPE);
		}

		jProject.setRawClasspath(newCPE.toArray(new IClasspathEntry[0]), new NullProgressMonitor());
	}

	private static void getAllLibraries(File root, List<File> res) {
		for (File f : root.listFiles()) {
			if (f.getName().endsWith(".jar")) {
				res.add(f);
			} else if (f.isDirectory() && f.canRead()) {
				getAllLibraries(f, res);
			}
		}
	}

	private static void waitUntilOldIndexReady() {
		SearchEngine engine = new SearchEngine();
		IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
		try {
			engine.searchAllTypeNames(
					null,
					SearchPattern.R_EXACT_MATCH,
					"!@$#!@".toCharArray(),
					SearchPattern.R_PATTERN_MATCH | SearchPattern.R_CASE_SENSITIVE,
					IJavaSearchConstants.CLASS,
					scope,
					new TypeNameRequestor() {
						public void acceptType(
								int modifiers,
								char[] packageName,
								char[] simpleTypeName,
								char[][] enclosingTypeNames,
								String path) {
						}
					},
					IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH,
					null);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	private IWorkspaceRoot getWorkspaceRoot() {
		return ResourcesPlugin.getWorkspace().getRoot();
	}

}
