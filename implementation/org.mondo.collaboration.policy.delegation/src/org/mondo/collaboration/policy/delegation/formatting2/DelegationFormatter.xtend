/*
 * generated by Xtext 2.13.0
 */
package org.mondo.collaboration.policy.delegation.formatting2

import com.google.inject.Inject
import org.eclipse.xtext.formatting2.IFormattableDocument
import org.mondo.collaboration.policy.delegation.delegation.DelegationModel
import org.mondo.collaboration.policy.delegation.delegation.Policy
import org.mondo.collaboration.policy.delegation.services.DelegationGrammarAccess
import org.mondo.collaboration.policy.formatting2.RulesFormatter
import org.mondo.collaboration.policy.rules.Rule

class DelegationFormatter extends RulesFormatter {
	
	@Inject extension DelegationGrammarAccess

	def dispatch void format(DelegationModel delegationModel, extension IFormattableDocument document) {
		// TODO: format HiddenRegions around keywords, attributes, cross references, etc. 
		delegationModel.getImport.format;
		delegationModel.getPolicy.format;
	}

	def dispatch void format(Policy policy, extension IFormattableDocument document) {
		// TODO: format HiddenRegions around keywords, attributes, cross references, etc. 
		for (Rule rule : policy.getRules()) {
			rule.format;
		}
	}
	
	// TODO: implement for Rule
}