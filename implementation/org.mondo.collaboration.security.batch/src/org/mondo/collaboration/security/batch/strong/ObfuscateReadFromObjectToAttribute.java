package org.mondo.collaboration.security.batch.strong;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.mondo.collaboration.policy.rules.AccessibilityLevel;
import org.mondo.collaboration.policy.rules.ResolutionType;
import org.mondo.collaboration.security.batch.Asset;
import org.mondo.collaboration.security.batch.Asset.AttributeAsset;
import org.mondo.collaboration.security.batch.Asset.ObjectAsset;
import org.mondo.collaboration.security.batch.BoundType;
import org.mondo.collaboration.security.batch.Consequence;
import org.mondo.collaboration.security.batch.Judgement;

import com.google.common.collect.Sets;

public class ObfuscateReadFromObjectToAttribute extends Consequence {

	private ObfuscateReadFromObjectToAttribute() {
	}

	public static Consequence instance = new ObfuscateReadFromObjectToAttribute();

	@Override
	public Set<Judgement> propagate(Judgement judgement, ResolutionType resolution) {
		HashSet<Judgement> consequences = Sets.newLinkedHashSet();

		if (judgement.getAsset() instanceof ObjectAsset) {
			if (judgement.getAccess() == AccessibilityLevel.OBFUSCATE) {
				EObject object = ((ObjectAsset) judgement.getAsset()).getObject();
				EList<EAttribute> eAllAttributes = object.eClass().getEAllAttributes();
				for (EAttribute eAttribute : eAllAttributes) {
					AttributeAsset attrAsset = new Asset.AttributeAsset(object, eAttribute);
					if (eAttribute.isID()) {
						consequences.add(new Judgement(AccessibilityLevel.OBFUSCATE, judgement.getOperation(),
								attrAsset, judgement.getPriority(), judgement.getBound()));
						consequences.add(new Judgement(AccessibilityLevel.OBFUSCATE, judgement.getOperation(),
								attrAsset, judgement.getPriority(), judgement.getBound()));
					} else {
						int priority = judgement.getPriority();
						if (resolution.equals(ResolutionType.PERMISSIVE)) {
							priority -= 1;
						} else if (resolution.equals(ResolutionType.RESTRICTIVE)) {
							priority += 1;
						}
						consequences.add(new Judgement(AccessibilityLevel.DENY, judgement.getOperation(), attrAsset,
								priority, judgement.getBound()));
					}
				}
			}
		}

		return consequences;
	}

}
