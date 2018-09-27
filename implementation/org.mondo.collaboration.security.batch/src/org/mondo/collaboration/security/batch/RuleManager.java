package org.mondo.collaboration.security.batch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.viatra.query.patternlanguage.emf.vql.Pattern;
import org.eclipse.viatra.query.patternlanguage.emf.vql.PatternModel;
import org.eclipse.viatra.query.patternlanguage.emf.vql.Variable;
import org.eclipse.viatra.query.runtime.api.AdvancedViatraQueryEngine;
import org.eclipse.viatra.query.runtime.api.IPatternMatch;
import org.eclipse.viatra.query.runtime.api.IQuerySpecification;
import org.eclipse.viatra.query.runtime.api.ViatraQueryMatcher;
import org.eclipse.viatra.query.runtime.emf.EMFScope;
import org.eclipse.viatra.query.runtime.exception.ViatraQueryException;
import org.mondo.collaboration.policy.delegation.delegation.Delegation;
import org.mondo.collaboration.policy.delegation.delegation.DelegationModel;
import org.mondo.collaboration.policy.rules.AccessControlModel;
import org.mondo.collaboration.policy.rules.AccessibilityLevel;
import org.mondo.collaboration.policy.rules.Binding;
import org.mondo.collaboration.policy.rules.EnumValue;
import org.mondo.collaboration.policy.rules.Group;
import org.mondo.collaboration.policy.rules.OperationType;
import org.mondo.collaboration.policy.rules.ResolutionType;
import org.mondo.collaboration.policy.rules.Role;
import org.mondo.collaboration.policy.rules.Rule;
import org.mondo.collaboration.policy.rules.User;
import org.mondo.collaboration.security.batch.Asset.AttributeAsset;
import org.mondo.collaboration.security.batch.Asset.Factory;
import org.mondo.collaboration.security.batch.Asset.ObjectAsset;
import org.mondo.collaboration.security.batch.Asset.ReferenceAsset;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

public class RuleManager {
	private Logger LOGGER = Logger.getLogger(RuleManager.class);

	private JudgementStorage judgementStorage;

	private Collection<Consequence> weakConsequences = Consequence.DefaultConsequenceTypes.DefaultWeakConsequences;
	private Collection<Consequence> strongConsequences = Consequence.DefaultConsequenceTypes.DefaultStrongConsequences;

	private Resource instanceModel;
	private AccessControlModel accessControlModel;
	private DelegationModel delegationModel;

	private AdvancedViatraQueryEngine advancedQueryEngine;
	private Map<Pattern, ViatraQueryMatcher<IPatternMatch>> matchers = Maps.newHashMap();
	private Map<String, IQuerySpecification<ViatraQueryMatcher<IPatternMatch>>> querySpecifications;

	private Map<Rule, Map<Variable, Object>> bindingMap = Maps.newHashMap();
	private Map<Delegation, Map<Variable, Object>> delegationBindingMap = Maps.newHashMap();
	private Multimap<EObject, ReferenceAsset> incomingReferenceMap = LinkedHashMultimap.create();
	private Multimap<EObject, ReferenceAsset> outgoingReferenceMap = LinkedHashMultimap.create();

	private int numOfConsequences;
	private int numOfAssets;
	private int numOfExplicits;
	private int numOfDelegations;

	public RuleManager(Resource model, AccessControlModel rules, DelegationModel delegationModel) {
		this.instanceModel = model;
		this.accessControlModel = rules;
		this.delegationModel = delegationModel;
	}

	public void setQuerySpecifications(
			Collection<IQuerySpecification<ViatraQueryMatcher<IPatternMatch>>> specifications) {

		querySpecifications = Maps.newHashMap();
		for (IQuerySpecification<ViatraQueryMatcher<IPatternMatch>> specification : specifications) {
			querySpecifications.put(specification.getFullyQualifiedName(), specification);
		}
	}

	public void dispose() {
		judgementStorage.dispose();
		advancedQueryEngine.dispose();
	}

	public void setWeakConsequences(Collection<Consequence> weakConsequences) {
		this.weakConsequences = weakConsequences;
	}

	public void setStrongConsequences(Collection<Consequence> strongConsequences) {
		this.strongConsequences = strongConsequences;
	}

	public void initialize() throws ViatraQueryException {
		LOGGER.info("Initialize ViatraQueryEngine");
		long start = System.nanoTime();
		advancedQueryEngine = AdvancedViatraQueryEngine.createUnmanagedEngine(new EMFScope(instanceModel));

		for (Rule rule : accessControlModel.getPolicy().getRules()) {
			IQuerySpecification<ViatraQueryMatcher<IPatternMatch>> querySpecification = querySpecifications
					.get(patternQualifiedName(rule.getPattern()));
			ViatraQueryMatcher<IPatternMatch> queryMatcher = advancedQueryEngine.getMatcher(querySpecification);
			matchers.put(rule.getPattern(), queryMatcher);
			initializeBindings(rule);
		}

		for (Delegation delegation : delegationModel.getDelegations()) {
			IQuerySpecification<ViatraQueryMatcher<IPatternMatch>> querySpecification = querySpecifications
					.get(patternQualifiedName(delegation.getPattern()));
			ViatraQueryMatcher<IPatternMatch> queryMatcher = advancedQueryEngine.getMatcher(querySpecification);
			matchers.put(delegation.getPattern(), queryMatcher);
			initializeBindings(delegation);
		}

		for (Consequence consequence : weakConsequences) {
			consequence.setRuleManager(this);
		}
		for (Consequence consequence : strongConsequences) {
			consequence.setRuleManager(this);
		}

		long end = System.nanoTime();
		LOGGER.info(String.format("ViatraQueryEngine is initialized in %d nanosec", end - start));
	}

	public Collection<Judgement> calculateEffectivePermissions(User user) throws ViatraQueryException {
		LOGGER.info("Started calculating effective permissions of " + user.getName());
		ResolutionType resolution = accessControlModel.getPolicy().getResolution();
		judgementStorage = new JudgementStorage(resolution);

		addInitialPermissions(user);
		LOGGER.info(String.format("Initial permissions of %s: %d", user.getName(), judgementStorage.size()));
		long start = System.nanoTime();

		numOfConsequences = 0;
		while (!judgementStorage.allJudgementProcessed()) {
			Judgement dominant = judgementStorage.chooseDominant();
			if (dominant.getPriority() > Constants.WEAK_PRIORITY) {
				propagateStrongConsequences(dominant, resolution);
			}
			if (dominant.getPriority() > Constants.DEFAULT_PRIORITY) {
				propagateWeakConsequences(dominant, resolution);
			}
			judgementStorage.resolveConflict(dominant);
		}
		long end = System.nanoTime();
		LOGGER.info(String.format("Effective permissions of " + user.getName() + " are calculated in %d nanosec",
				end - start));
		Collection<Judgement> effectiveJudgements = judgementStorage.getEffectiveJudgements();
		LOGGER.info(String.format("Effective judgements of %s: %d", user.getName(), effectiveJudgements.size()));
		return effectiveJudgements;
	}

	private void addInitialPermissions(User user) throws ViatraQueryException {
		addExplicitPermissions(user);
		addDelegatedPermissions(user);
		addDefaultPermissions();
	}

	private void addExplicitPermissions(User user) throws ViatraQueryException {
		numOfExplicits = 0;
		for (Rule rule : accessControlModel.getPolicy().getRules()) {
			for (Role role : rule.getRoles()) {
				if (getRolesOfUser(accessControlModel, user).contains(role)) {
					for (IPatternMatch match : matchesOf(rule)) {
						Factory factory = AssetFactory.factoryFrom(rule);
						for (Asset asset : factory.apply(match)) {
							addExplicitPermission(rule, asset, accessControlModel.getPolicy().getResolution());
						}
					}
					break;
				}
			}
		}
		LOGGER.info(String.format("Explicit judgements of %s: %d", user.getName(), numOfExplicits));
	}

	private void addExplicitPermission(Rule rule, Asset asset, ResolutionType resolution) {
		AccessibilityLevel access = rule.getAccess();
		int priority = calculatePriority(rule.getPriority(), resolution, access);
		if (access == AccessibilityLevel.OBFUSCATE) {
			judgementStorage.add(new Judgement(access, OperationType.READ, asset, priority, BoundType.LOWER));
			judgementStorage.add(new Judgement(access, OperationType.READ, asset, priority, BoundType.UPPER));
			numOfExplicits += 2;
		} else {
			OperationType operation = rule.getOperation();
			if (operation == OperationType.READWRITE) {
				judgementStorage.add(new Judgement(access, OperationType.READ, asset, priority, BoundType.LOWER));
				judgementStorage.add(new Judgement(access, OperationType.READ, asset, priority, BoundType.UPPER));
				judgementStorage.add(new Judgement(access, OperationType.WRITE, asset, priority, BoundType.LOWER));
				judgementStorage.add(new Judgement(access, OperationType.WRITE, asset, priority, BoundType.UPPER));
				numOfExplicits += 4;
			} else if (operation == OperationType.READ || operation == OperationType.WRITE) {
				judgementStorage.add(new Judgement(access, operation, asset, priority, BoundType.LOWER));
				judgementStorage.add(new Judgement(access, operation, asset, priority, BoundType.UPPER));
				numOfExplicits += 2;
			}
		}
	}

	private void addDelegatedPermissions(User user) throws ViatraQueryException {
		LOGGER.info("Started calculating delegated permissions of " + user.getName());
		numOfDelegations = 0;
		for (Delegation delegation : delegationModel.getDelegations()) {
			for (Role role : delegation.getTargets()) {
				if (getRolesOfUser(accessControlModel, user).contains(role)) {
					Map<Asset, Map<OperationType, AccessibilityLevel>> permissionsOfSource = calculatePermissionsOfSource(
							delegation.getSource());
					for (IPatternMatch match : matchesOf(delegation)) {
						Factory factory = AssetFactory.factoryFrom(delegation);
						for (Asset asset : factory.apply(match)) {
							if (checkPermissionsOfSource(delegation, asset, permissionsOfSource)) {
								addDelegatedPermission(delegation, asset);
							}
						}
					}
					break;
				}
			}
		}
		LOGGER.info(String.format("Delegated judgements of %s: %s", user.getName(), numOfDelegations));
	}

	private void addDelegatedPermission(Delegation delegation, Asset asset) {
		AccessibilityLevel access = delegation.getAccess();
		int priority = Constants.DELEGATION_PRIORITY;
		if (access == AccessibilityLevel.OBFUSCATE) {
			judgementStorage.add(new Judgement(access, OperationType.READ, asset, priority, BoundType.LOWER));
			judgementStorage.add(new Judgement(access, OperationType.READ, asset, priority, BoundType.UPPER));
			numOfDelegations += 2;
		} else {
			OperationType operation = delegation.getOperation();
			if (operation == OperationType.READWRITE) {
				judgementStorage.add(new Judgement(access, OperationType.READ, asset, priority, BoundType.LOWER));
				judgementStorage.add(new Judgement(access, OperationType.READ, asset, priority, BoundType.UPPER));
				judgementStorage.add(new Judgement(access, OperationType.WRITE, asset, priority, BoundType.LOWER));
				judgementStorage.add(new Judgement(access, OperationType.WRITE, asset, priority, BoundType.UPPER));
				numOfDelegations += 4;
			} else if (operation == OperationType.READ || operation == OperationType.WRITE) {
				judgementStorage.add(new Judgement(access, operation, asset, priority, BoundType.LOWER));
				judgementStorage.add(new Judgement(access, operation, asset, priority, BoundType.UPPER));
				numOfDelegations += 2;
			}
		}
	}

	private Map<Asset, Map<OperationType, AccessibilityLevel>> calculatePermissionsOfSource(User user)
			throws ViatraQueryException {
		JudgementStorage tempStorage = judgementStorage;
		Collection<Judgement> sourcePermissionsCollection = calculateEffectivePermissions(user);
		Map<Asset, Map<OperationType, AccessibilityLevel>> sourcePermissionsMap = Maps.newHashMap();
		for (Judgement judgement : sourcePermissionsCollection) {
			Map<OperationType, AccessibilityLevel> operationMap = sourcePermissionsMap.get(judgement.getAsset());
			if (operationMap == null) {
				operationMap = Maps.newHashMap();
				sourcePermissionsMap.put(judgement.getAsset(), operationMap);
			}
			if (!operationMap.containsKey(judgement.getOperation())) {
				operationMap.put(judgement.getOperation(), judgement.getAccess());
			}
		}
		judgementStorage = tempStorage;
		return sourcePermissionsMap;
	}

	private boolean checkPermissionsOfSource(Delegation delegation, Asset asset,
			Map<Asset, Map<OperationType, AccessibilityLevel>> permissionsOfSource) throws ViatraQueryException {
		if (delegation.getAccess() == AccessibilityLevel.ALLOW) {
			if (delegation.getOperation() == OperationType.READWRITE) {
				return permissionsOfSource.get(asset).values().stream()
						.noneMatch(access -> access != AccessibilityLevel.ALLOW);
			} else {
				return permissionsOfSource.get(asset).get(delegation.getOperation()) == AccessibilityLevel.ALLOW;
			}
		} else {
			if (delegation.getOperation() == OperationType.READWRITE) {
				return permissionsOfSource.get(asset).values().stream()
						.anyMatch(access -> access == AccessibilityLevel.DENY);
			} else {
				return permissionsOfSource.get(asset).get(delegation.getOperation()) != AccessibilityLevel.DENY;
			}
		}
	}

	private void addDefaultPermissions() {
		numOfAssets = 0;
		TreeIterator<EObject> allContents = instanceModel.getAllContents();
		while (allContents.hasNext()) {
			EObject object = allContents.next();

			// object
			ObjectAsset objAsset = new Asset.ObjectAsset(object);
			numOfAssets++;
			addDefaultPermission(objAsset);

			// attributes
			for (EAttribute attribute : object.eClass().getEAllAttributes()) {
				AttributeAsset attrAsset = new Asset.AttributeAsset(object, attribute);
				numOfAssets++;
				addDefaultPermission(attrAsset);
			}

			// references
			for (EReference reference : object.eClass().getEAllReferences()) {
				if (reference.isMany()) {
					@SuppressWarnings("unchecked")
					EList<EObject> targets = (EList<EObject>) object.eGet(reference);
					for (EObject target : targets) {
						ReferenceAsset refAsset = new Asset.ReferenceAsset(object, reference, target);
						numOfAssets++;
						addDefaultPermission(refAsset);
					}
				} else {
					EObject target = (EObject) object.eGet(reference);
					if (target != null) {
						ReferenceAsset refAsset = new Asset.ReferenceAsset(object, reference, target);
						numOfAssets++;
						addDefaultPermission(refAsset);
					}
				}
			}
		}
		LOGGER.info(String.format("Number of assets: %d", numOfAssets));
	}

	private void addDefaultPermission(Asset asset) {
		AccessibilityLevel access = accessControlModel.getPolicy().getAccess();
		judgementStorage
				.add(new Judgement(access, OperationType.READ, asset, Constants.DEFAULT_PRIORITY, BoundType.LOWER));
		judgementStorage
				.add(new Judgement(access, OperationType.READ, asset, Constants.DEFAULT_PRIORITY, BoundType.UPPER));
		judgementStorage
				.add(new Judgement(access, OperationType.WRITE, asset, Constants.DEFAULT_PRIORITY, BoundType.LOWER));
		judgementStorage
				.add(new Judgement(access, OperationType.WRITE, asset, Constants.DEFAULT_PRIORITY, BoundType.UPPER));

		if (asset instanceof ReferenceAsset) {
			ReferenceAsset referenceAsset = (ReferenceAsset) asset;
			incomingReferenceMap.put(referenceAsset.getTarget(), referenceAsset);
			outgoingReferenceMap.put(referenceAsset.getSource(), referenceAsset);
		}
	}

	private void propagateWeakConsequences(Judgement judgement, ResolutionType resolution) {
		for (Consequence weakConsequence : weakConsequences) {
			Set<Judgement> consequences = weakConsequence.propagate(judgement, resolution);
			for (Judgement j : consequences) {
				if (!judgementStorage.conflictWithProcessed(j)) {
					judgementStorage.add(j);
					numOfConsequences++;
				}
			}
		}
	}

	private void propagateStrongConsequences(Judgement judgement, ResolutionType resolution) {
		for (Consequence strongConsequence : strongConsequences) {
			Set<Judgement> consequences = strongConsequence.propagate(judgement, resolution);
			for (Judgement j : consequences) {
				judgementStorage.add(j);
				numOfConsequences++;
			}
		}
	}

	private List<Role> getRolesOfUser(AccessControlModel rules, User user) {
		List<Role> roleList = new ArrayList<Role>();
		roleList.add(user);
		for (Role role : rules.getRoles()) {
			if (role instanceof Group) {
				for (User u : getUsersOfGroup((Group) role)) {
					if (u.equals(user)) {
						roleList.add(role);
					}
				}
			}
		}
		return roleList;
	}

	public List<User> getUsersOfGroup(Group group) {
		List<User> userList = new ArrayList<User>();
		for (Role role : group.getRoles()) {
			if (role instanceof User) {
				userList.add((User) role);
			} else {
				userList.addAll(getUsersOfGroup((Group) role));
			}
		}
		return userList;
	}

	private Collection<IPatternMatch> matchesOf(Rule rule) throws ViatraQueryException {
		ViatraQueryMatcher<IPatternMatch> queryMatcher = matchers.get(rule.getPattern());
		IPatternMatch filterMatch = buildFilterMatch(rule);
		return queryMatcher.getAllMatches(filterMatch);
	}

	private Collection<IPatternMatch> matchesOf(Delegation delegation) throws ViatraQueryException {
		ViatraQueryMatcher<IPatternMatch> queryMatcher = matchers.get(delegation.getPattern());
		IPatternMatch filterMatch = buildFilterMatch(delegation);
		return queryMatcher.getAllMatches(filterMatch);
	}

	private static String patternQualifiedName(Pattern pattern) {
		PatternModel patternModel = (PatternModel) pattern.eContainer();
		return patternModel.getPackageName() + "." + pattern.getName();
	}

	private IPatternMatch buildFilterMatch(Rule rule) {
		ViatraQueryMatcher<IPatternMatch> queryMatcher = matchers.get(rule.getPattern());
		IPatternMatch filterMatch = queryMatcher.newEmptyMatch();
		Map<Variable, Object> bindings = bindingMap.get(rule);
		for (Variable variable : bindings.keySet()) {
			filterMatch.set(variable.getName(), bindings.get(variable));
		}
		return filterMatch.toImmutable();
	}

	private IPatternMatch buildFilterMatch(Delegation delegation) {
		ViatraQueryMatcher<IPatternMatch> queryMatcher = matchers.get(delegation.getPattern());
		IPatternMatch filterMatch = queryMatcher.newEmptyMatch();
		Map<Variable, Object> bindings = delegationBindingMap.get(delegation);
		for (Variable variable : bindings.keySet()) {
			filterMatch.set(variable.getName(), bindings.get(variable));
		}
		return filterMatch.toImmutable();
	}

	private void initializeBindings(Rule rule) {
		Map<Variable, Object> bindings = Maps.newHashMap();
		for (Binding binding : rule.getBindings()) {
			bindings.put(binding.getVariable(), getBoundValue(binding));
		}
		bindingMap.put(rule, bindings);
	}

	private void initializeBindings(Delegation delegation) {
		Map<Variable, Object> bindings = Maps.newHashMap();
		for (Binding binding : delegation.getBindings()) {
			bindings.put(binding.getVariable(), getBoundValue(binding));
		}
		delegationBindingMap.put(delegation, bindings);
	}

	private Object getBoundValue(Binding binding) {
		String valueString = binding.getBind().getValueString();
		if (valueString != null)
			return valueString;

		EnumValue enumLiteral = binding.getBind().getValueEnumLiteral();
		if (enumLiteral != null)
			return enumLiteral.getLiteral().getInstance();

		final int valueInteger = binding.getBind().getValueInteger();
		return valueInteger;
	}

	public Collection<ReferenceAsset> getIncomingReferences(EObject obj) {
		return incomingReferenceMap.get(obj);
	}

	public Collection<ReferenceAsset> getOutgoingReferences(EObject obj) {
		return outgoingReferenceMap.get(obj);
	}

	public int getNumOfAssets() {
		return numOfAssets;
	}

	public int getNumOfConsequences() {
		return numOfConsequences;
	}

	public int getNumOfExplicits() {
		return numOfExplicits;
	}

	public int calculatePriority(int priority, ResolutionType resolution, AccessibilityLevel access) {
		int newPrio = priority * 3;
		if (resolution == ResolutionType.PERMISSIVE) {
			if (access == AccessibilityLevel.OBFUSCATE)
				newPrio -= 1;
			if (access == AccessibilityLevel.DENY)
				newPrio -= 2;
		}
		if (resolution == ResolutionType.RESTRICTIVE) {
			if (access == AccessibilityLevel.OBFUSCATE)
				newPrio -= 1;
			if (access == AccessibilityLevel.ALLOW)
				newPrio -= 2;
		}
		return newPrio;
	}
}
