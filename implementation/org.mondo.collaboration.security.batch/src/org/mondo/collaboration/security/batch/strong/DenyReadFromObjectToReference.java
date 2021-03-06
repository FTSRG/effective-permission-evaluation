package org.mondo.collaboration.security.batch.strong;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.mondo.collaboration.policy.rules.AccessibilityLevel;
import org.mondo.collaboration.policy.rules.OperationType;
import org.mondo.collaboration.security.batch.Consequence;
import org.mondo.collaboration.security.batch.Asset.ObjectAsset;
import org.mondo.collaboration.security.batch.Asset.ReferenceAsset;
import org.mondo.collaboration.security.batch.Judgement;
import org.mondo.collaboration.security.batch.RuleManager;

import com.google.common.collect.Sets;

public class DenyReadFromObjectToReference extends Consequence{
	private DenyReadFromObjectToReference() {
	}
	
	public static Consequence instance = new DenyReadFromObjectToReference();
	private RuleManager manager;

	@Override
	public void setRuleManager(RuleManager manager) {
		this.manager = manager;
		super.setRuleManager(manager);
	}
	
	@Override
	public Set<Judgement> propagate(Judgement judgement) {
		HashSet<Judgement> consequences = Sets.newHashSet();

		if(judgement.getAsset() instanceof ObjectAsset) {
			if(judgement.getAccess() == AccessibilityLevel.DENY) {
				if(judgement.getOperation() == OperationType.READ) {
					EObject object = ((ObjectAsset)judgement.getAsset()).getObject();
				    Collection<ReferenceAsset> incomingReferences = manager.getIncomingReferences(object);
					for (ReferenceAsset referenceAsset : incomingReferences) {
				        consequences.add(new Judgement(judgement.getAccess(), judgement.getOperation(), referenceAsset, judgement.getPriority()));
					}
				}
			}
		}
		    
		return consequences;
	}

}
