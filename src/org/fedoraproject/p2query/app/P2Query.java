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

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IProvidedCapability;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

public class P2Query {

	public void executeQuery(String arg1, String arg2, String arg3) {
		String cmd = arg1;
		switch (cmd) {
		case "diff":
			diff(arg2, arg3);
			return;
		default:
			break;
		}

		cmd = arg2;
		IMetadataRepository metaRepo = loadRepository(arg1);
		if (metaRepo == null) {
			return;
		}
		switch (cmd) {
		case "provides":
			provides(metaRepo, arg3);
			break;
		case "whatprovides":
			whatprovides(metaRepo, arg3);
			break;
		case "requires":
			requires(metaRepo, arg3);
			break;
		case "whatrequires":
			whatrequires(metaRepo, arg3);
			break;
		case "transitive-closure":
			transitiveClosure(metaRepo, arg3);
			break;
		case "dangling":
			dangling(metaRepo);
			break;
		default:
			break;
		}
	}

	private void diff(String oldLoc, String newLoc) {
		IMetadataRepository oldRepo = loadRepository(oldLoc);
		IMetadataRepository newRepo = loadRepository(newLoc);
		if (oldRepo == null || newRepo == null) {
			return;
		}
		Set<IInstallableUnit> oldUnits = oldRepo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor()).toUnmodifiableSet();
		Set<IInstallableUnit> newUnits = newRepo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor()).toUnmodifiableSet();

		Set<IInstallableUnit> added = new LinkedHashSet<IInstallableUnit>(newUnits);
		added.removeAll(oldUnits);
		Set<IInstallableUnit> removed = new LinkedHashSet<IInstallableUnit>(oldUnits);
		removed.removeAll(newUnits);
		Set<IInstallableUnit> unchanged = new LinkedHashSet<IInstallableUnit>(oldUnits);
		unchanged.retainAll(newUnits);

		System.out.println("=== UNCHANGED ===");
		for (IInstallableUnit u : unchanged) {
			printIU(u);
		}
		System.out.println("=== REMOVED ===");
		for (IInstallableUnit u : removed) {
			printIU(u);
		}
		System.out.println("=== ADDED ===");
		for (IInstallableUnit u : added) {
			printIU(u);
		}
	}

	private void dangling(IMetadataRepository metaRepo) {
		Set<IInstallableUnit> units = metaRepo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor()).toUnmodifiableSet();
		for (IInstallableUnit u : units) {
			if (isDangling(u, units, metaRepo)) {
				printIU(u);
			}
		}
	}

	private boolean isDangling(IInstallableUnit u, Set<IInstallableUnit> units,
			IMetadataRepository metaRepo) {
		for (IInstallableUnit v : units) {
			for (IRequirement req : v.getRequirements()) {
				IQueryResult<IInstallableUnit> matches = metaRepo.query(QueryUtil.createMatchQuery(req.getMatches()), new NullProgressMonitor());
				for (IInstallableUnit m : matches) {
					if (m.equals(u)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private void transitiveClosure(IMetadataRepository metaRepo, String arg) {
		Stack<IInstallableUnit> toVisit = new Stack<>();
		Set<IInstallableUnit> res = new LinkedHashSet<>();
		
		IQueryResult<IInstallableUnit> qUnit = metaRepo.query(QueryUtil.createIUQuery(arg), new NullProgressMonitor());
		if (qUnit.toUnmodifiableSet().size() != 1) {
			return;
		}
		toVisit.push(qUnit.iterator().next());
		while (!toVisit.isEmpty()) {
			IInstallableUnit u = toVisit.pop();
			for (IRequirement req : u.getRequirements()) {
				IQueryResult<IInstallableUnit> matches = metaRepo.query(QueryUtil.createMatchQuery(req.getMatches()), new NullProgressMonitor());
				if (!matches.isEmpty()) {
					IInstallableUnit matched = matches.iterator().next();
					if (!res.contains(matched)) {
						res.add(matched);
						toVisit.push(matched);
					}
				}
			}
		}
		for (IInstallableUnit u : res) {
			printIU(u);
		}
		
	}

	private void whatrequires(IMetadataRepository metaRepo, String arg) {
		IQueryResult<IInstallableUnit> qRes = metaRepo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor());
		Set<IInstallableUnit> units = qRes.toUnmodifiableSet();
		Map<IInstallableUnit,Set<IRequirement>> res = new HashMap<>();
		for (IInstallableUnit u : units) {
			Set<IRequirement> tmp = new LinkedHashSet<>();
			for (IRequirement req : u.getRequirements()) {
				if (req.toString().contains(arg)) {
					tmp.add(req);
				}
			}
			if (!tmp.isEmpty()) {
				res.put(u, tmp);
			}
		}
		for (Entry<IInstallableUnit,Set<IRequirement>> e : res.entrySet()) {
			printIU(e.getKey());
			for (IRequirement req : e.getValue()) {
				printReq(req);
			}
		}
	}

	private void whatprovides(IMetadataRepository metaRepo, String arg) {
		IQueryResult<IInstallableUnit> qRes = metaRepo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor());
		Set<IInstallableUnit> units = qRes.toUnmodifiableSet();
		Map<IInstallableUnit,Set<IProvidedCapability>> res = new HashMap<>();
		for (IInstallableUnit u : units) {
			Set<IProvidedCapability> tmp = new LinkedHashSet<>();
			for (IProvidedCapability cap : u.getProvidedCapabilities()) {
				if (cap.toString().contains(arg)) {
					tmp.add(cap);
				}
			}
			if (!tmp.isEmpty()) {
				res.put(u, tmp);
			}
		}
		for (Entry<IInstallableUnit,Set<IProvidedCapability>> e : res.entrySet()) {
			printIU(e.getKey());
			for (IProvidedCapability cap : e.getValue()) {
				printProv(cap);
			}
		}
	}

	private void requires(IMetadataRepository metaRepo, String arg) {
		IQueryResult<IInstallableUnit> qRes = metaRepo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor());
		Set<IInstallableUnit> units = qRes.toUnmodifiableSet();
		Map<IInstallableUnit, Set<IRequirement>> res = new HashMap<>();
		for (IInstallableUnit u : units) {
			Set<IRequirement> tmp = new LinkedHashSet<>();
			if (u.getId().contains(arg)) {
				for (IRequirement req : u.getRequirements()) {
					tmp.add(req);
				}
			}
			if (!tmp.isEmpty()) {
				res.put(u, tmp);
			}
		}
		for (Entry<IInstallableUnit,Set<IRequirement>> e : res.entrySet()) {
			printIU(e.getKey());
			for (IRequirement req : e.getValue()) {
				printReq(req);
			}
		}
		
	}

	private void provides(IMetadataRepository metaRepo, String arg) {
		IQueryResult<IInstallableUnit> qRes = metaRepo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor());
		Set<IInstallableUnit> units = qRes.toUnmodifiableSet();
		Map<IInstallableUnit,Set<IProvidedCapability>> res = new HashMap<>();
		for (IInstallableUnit u : units) {
			Set<IProvidedCapability> tmp = new LinkedHashSet<>();
			if (u.getId().contains(arg)) {
				for (IProvidedCapability cap : u.getProvidedCapabilities()) {
					tmp.add(cap);
				}
			}
			if (!tmp.isEmpty()) {
				res.put(u, tmp);
			}
		}
		for (Entry<IInstallableUnit,Set<IProvidedCapability>> e : res.entrySet()) {
			printIU(e.getKey());
			for (IProvidedCapability cap : e.getValue()) {
				printProv(cap);
			}
		}
	}

	private IMetadataRepository loadRepository(String repo) {
		IMetadataRepository res = null;
		BundleContext bc = Platform.getBundle("org.fedoraproject.p2query").getBundleContext();
		ServiceReference<?> sr = (ServiceReference<?>) bc.getServiceReference(IProvisioningAgentProvider.SERVICE_NAME);
		IProvisioningAgentProvider pr = (IProvisioningAgentProvider) bc.getService(sr);
		IProvisioningAgent agent;
		try {
			agent = pr.createAgent(null);
			IMetadataRepositoryManager metadataRM = (IMetadataRepositoryManager) agent.getService(IMetadataRepositoryManager.SERVICE_NAME);
			res = metadataRM.loadRepository(new URI(repo), new NullProgressMonitor());
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
		return res;
	}
	
	private void printIU (IInstallableUnit u) {
		System.out.println("IU: " + u.toString());
	}
	
	private void printReq (IRequirement req) {
		System.out.println("-> " + req.toString());
	}
	
	private void printProv (IProvidedCapability cap) {
		System.out.println("* " + cap.toString());
	}

}
