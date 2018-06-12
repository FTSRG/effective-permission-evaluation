/*
 * generated by Xtext 2.13.0
 */
package org.mondo.collaboration.policy.delegation.validation

import org.eclipse.xtext.validation.Check
import org.mondo.collaboration.policy.delegation.delegation.Rule
import org.mondo.collaboration.policy.rules.AccessibilityLevel

/**
 * This class contains custom validation rules. 
 *
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation
 */
class DelegationValidator extends AbstractDelegationValidator {
	
	public static val INVALID_NAME = 'invalidName'

	@Check
	def checkDelegationOfDeny(Rule rule) {
		val access = rule.eClass.EAllStructuralFeatures.findFirst[x | x.name.equals("access")]
		if(rule.access == AccessibilityLevel::DENY){
			error("Deny cannot be delegated", rule, access)
		}
	}
	
}