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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IProvidedCapability;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.query.CollectionResult;
import org.eclipse.equinox.p2.query.Collector;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.IQueryable;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

public class P2Query {

	private static IMetadataRepositoryManager metadataRM;
	private static final IQueryable<IInstallableUnit> EMPTY_IU_QUERYABLE = new IQueryable<IInstallableUnit>() {
		public IQueryResult<IInstallableUnit> query(IQuery<IInstallableUnit> query, IProgressMonitor monitor) {
			return Collector.emptyCollector();
		}
	};

	public void executeQuery(String [] args) {
		String cmd = args[0];
		switch (cmd) {
		case "diff":
			diff(args[1], args[2]);
			return;
		case "graph":
			boolean detail = args[1].equals("-v") ? true : false;
			Set<IMetadataRepository> repos = new LinkedHashSet<>();
			for (int i = (detail ? 2 : 1); i < args.length; i++) {
				IMetadataRepository repo = loadRepository(args[i]);
				if (repo == null) {
					return;
				}
				repos.add(repo);
			}
			graph(repos, detail);
			return;
		default:
			break;
		}

		cmd = args[1];
		IQueryable<IInstallableUnit> metaRepo = loadRepositories(args[0]);
		if (metaRepo == null) {
			return;
		}
		switch (cmd) {
		case "provides":
			provides(metaRepo, args[2]);
			break;
		case "whatprovides":
			whatprovides(metaRepo, args[2]);
			break;
		case "requires":
			requires(metaRepo, args[2]);
			break;
		case "whatrequires":
			whatrequires(metaRepo, args[2]);
			break;
		case "transitive-closure":
			transitiveClosure(metaRepo, args[2]);
			break;
		case "dangling":
			dangling(metaRepo);
			break;
		case "overlap":
			overlapping(metaRepo);
		default:
			break;
		}
	}

	private void overlapping(IQueryable<IInstallableUnit> metaRepo) {
		Map<String, List<String>> provideToUnit = new HashMap<> ();
		IQueryResult<IInstallableUnit> qRes = metaRepo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor());
		Set<IInstallableUnit> units = qRes.toUnmodifiableSet();
		for (IInstallableUnit u : units) {
			for (IProvidedCapability p : u.getProvidedCapabilities()) {
				String key = p.toString();
				if (p.getNamespace().equals("java.package")) {
					if (provideToUnit.get(key) == null) {
						provideToUnit.put(key, new ArrayList<String>());
					}
					provideToUnit.get(key).add(u.toString());
				}
			}
		}

		for (Entry<String, List<String>> e : provideToUnit.entrySet()) {
			List<String> overlaps = e.getValue();
			if (overlaps.size() > 1) {
				StringBuffer buff = new StringBuffer();
				for (String o : overlaps) {
					buff.append(',');
					buff.append(o);
				}
				System.out.println("Overlaps for " + e.getKey());
				System.out.println(buff.substring(1));
			}
		}
	}

	private void graph(Set<IMetadataRepository> repos, boolean detail) {
		// Combine all repositories so we can query them together
		IQueryable<IInstallableUnit> allRepo = EMPTY_IU_QUERYABLE;
		for (IMetadataRepository repo : repos) {
			IQueryResult<IInstallableUnit> res = repo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor());
			allRepo = QueryUtil.compoundQueryable(allRepo, res);
		}

		Set<IInstallableUnit> allUnits = allRepo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor()).toUnmodifiableSet();
		for (IInstallableUnit u : allUnits) {
			Set<IInstallableUnit> requires = getRequires(u, allRepo);
			for (IInstallableUnit v : requires) {
				IMetadataRepository uRepo = whichRepo(u, repos);
				IMetadataRepository vRepo = whichRepo(v, repos);
				String uRepoName = uRepo.getLocation().toString();
				String vRepoName = vRepo.getLocation().toString();
				if (!uRepoName.equals(vRepoName) &&
						uRepo.query(QueryUtil.createIUQuery(v), new NullProgressMonitor()).isEmpty()) {
					printEdge(uRepoName, vRepoName, u, v, detail);
				}
			}
		}
		return;
	}

	private static IMetadataRepository whichRepo(IInstallableUnit u, Set<IMetadataRepository> repos) {
		for (IMetadataRepository repo : repos) {
			if (!repo.query(QueryUtil.createIUQuery(u), new NullProgressMonitor()).isEmpty()) {
				return repo;
			}
		}
		return null;
	}

	private static Set<IInstallableUnit> getRequires (IInstallableUnit u, IQueryable<IInstallableUnit> repo) {
		Set<IRequirement> requirements = new LinkedHashSet<>(u.getRequirements());
		requirements.addAll(u.getMetaRequirements());

		Set<IInstallableUnit> matches = new LinkedHashSet<>();
		for (IRequirement r : requirements) {
			if (r.getMax() != 0 && r.getMin() != 0) {
				IQuery<IInstallableUnit> mquery = QueryUtil.createMatchQuery(r.getMatches());
				matches.addAll(repo.query(mquery, new NullProgressMonitor()).toUnmodifiableSet());
			}
		}
		return matches;
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

	private void dangling(IQueryable<IInstallableUnit> metaRepo) {
		Set<IInstallableUnit> units = metaRepo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor()).toUnmodifiableSet();
		for (IInstallableUnit u : units) {
			if (isDangling(u, units, metaRepo)) {
				printIU(u);
			}
		}
	}

	private boolean isDangling(IInstallableUnit u, Set<IInstallableUnit> units,
			IQueryable<IInstallableUnit> metaRepo) {
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

	private void transitiveClosure(IQueryable<IInstallableUnit> metaRepo, String arg) {
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

	private void whatrequires(IQueryable<IInstallableUnit> metaRepo, String arg) {
		IQueryResult<IInstallableUnit> qRes = metaRepo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor());
		Set<IInstallableUnit> units = qRes.toUnmodifiableSet();
		Map<IInstallableUnit,Set<IRequirement>> res = new HashMap<>();
		for (IInstallableUnit u : units) {
			Set<IRequirement> tmp = new LinkedHashSet<>();
			for (IRequirement req : u.getRequirements()) {
				if (req.toString().matches(arg)) {
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

	private void whatprovides(IQueryable<IInstallableUnit> metaRepo, String arg) {
		IQueryResult<IInstallableUnit> qRes = metaRepo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor());
		Set<IInstallableUnit> units = qRes.toUnmodifiableSet();
		Map<IInstallableUnit,Set<IProvidedCapability>> res = new HashMap<>();
		for (IInstallableUnit u : units) {
			Set<IProvidedCapability> tmp = new LinkedHashSet<>();
			for (IProvidedCapability cap : u.getProvidedCapabilities()) {
				if (cap.toString().matches(arg)) {
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

	private void requires(IQueryable<IInstallableUnit> metaRepo, String arg) {
		IQueryResult<IInstallableUnit> qRes = metaRepo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor());
		Set<IInstallableUnit> units = qRes.toUnmodifiableSet();
		Map<IInstallableUnit, Set<IRequirement>> res = new HashMap<>();
		for (IInstallableUnit u : units) {
			Set<IRequirement> tmp = new LinkedHashSet<>();
			if (u.getId().matches(arg)) {
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

	private void provides(IQueryable<IInstallableUnit> metaRepo, String arg) {
		IQueryResult<IInstallableUnit> qRes = metaRepo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor());
		Set<IInstallableUnit> units = qRes.toUnmodifiableSet();
		Map<IInstallableUnit,Set<IProvidedCapability>> res = new HashMap<>();
		for (IInstallableUnit u : units) {
			Set<IProvidedCapability> tmp = new LinkedHashSet<>();
			if (u.getId().matches(arg)) {
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

	public static IMetadataRepository loadRepository(String repo) {
		IMetadataRepository res = null;
		try {
			if (metadataRM == null) {
				BundleContext bc = Platform.getBundle("org.fedoraproject.p2query").getBundleContext();
				ServiceReference<?> sr = (ServiceReference<?>) bc.getServiceReference(IProvisioningAgentProvider.SERVICE_NAME);
				IProvisioningAgentProvider pr = (IProvisioningAgentProvider) bc.getService(sr);
				IProvisioningAgent agent = pr.createAgent(null);
				metadataRM = (IMetadataRepositoryManager) agent.getService(IMetadataRepositoryManager.SERVICE_NAME);
			}
			res = metadataRM.loadRepository(new URI(repo), new NullProgressMonitor());
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
		return res;
	}

	public static IQueryable<IInstallableUnit> loadRepositories(String input) {
		Set<IInstallableUnit> units = new LinkedHashSet<IInstallableUnit>();
		String [] repos = input.split(",");
		for (String repo : repos) {
			IMetadataRepository mRepo = loadRepository(repo);
			IQueryResult<IInstallableUnit> res = mRepo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor());
			units.addAll(res.toUnmodifiableSet());
		}
		return new CollectionResult<>(units);
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

	private void printEdge(String uRepo, String vRepo, IInstallableUnit u,
			IInstallableUnit v, boolean detail) {
		if (detail) {
			System.out.println(String.format("(%s) %s -> %s (%s)", u, uRepo, vRepo, v));
		} else {
			System.out.println(String.format("%s -> %s", uRepo, vRepo));
		}
	}

}
