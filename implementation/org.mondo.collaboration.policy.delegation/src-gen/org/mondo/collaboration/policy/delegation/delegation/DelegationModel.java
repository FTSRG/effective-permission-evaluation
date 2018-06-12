/**
 * generated by Xtext 2.13.0
 */
package org.mondo.collaboration.policy.delegation.delegation;

import org.eclipse.emf.ecore.EObject;

import org.mondo.collaboration.policy.rules.Import;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Model</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link org.mondo.collaboration.policy.delegation.delegation.DelegationModel#getImport <em>Import</em>}</li>
 *   <li>{@link org.mondo.collaboration.policy.delegation.delegation.DelegationModel#getPolicy <em>Policy</em>}</li>
 * </ul>
 *
 * @see org.mondo.collaboration.policy.delegation.delegation.DelegationPackage#getDelegationModel()
 * @model
 * @generated
 */
public interface DelegationModel extends EObject
{
  /**
   * Returns the value of the '<em><b>Import</b></em>' containment reference.
   * <!-- begin-user-doc -->
   * <p>
   * If the meaning of the '<em>Import</em>' containment reference isn't clear,
   * there really should be more of a description here...
   * </p>
   * <!-- end-user-doc -->
   * @return the value of the '<em>Import</em>' containment reference.
   * @see #setImport(Import)
   * @see org.mondo.collaboration.policy.delegation.delegation.DelegationPackage#getDelegationModel_Import()
   * @model containment="true"
   * @generated
   */
  Import getImport();

  /**
   * Sets the value of the '{@link org.mondo.collaboration.policy.delegation.delegation.DelegationModel#getImport <em>Import</em>}' containment reference.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @param value the new value of the '<em>Import</em>' containment reference.
   * @see #getImport()
   * @generated
   */
  void setImport(Import value);

  /**
   * Returns the value of the '<em><b>Policy</b></em>' containment reference.
   * <!-- begin-user-doc -->
   * <p>
   * If the meaning of the '<em>Policy</em>' containment reference isn't clear,
   * there really should be more of a description here...
   * </p>
   * <!-- end-user-doc -->
   * @return the value of the '<em>Policy</em>' containment reference.
   * @see #setPolicy(Policy)
   * @see org.mondo.collaboration.policy.delegation.delegation.DelegationPackage#getDelegationModel_Policy()
   * @model containment="true"
   * @generated
   */
  Policy getPolicy();

  /**
   * Sets the value of the '{@link org.mondo.collaboration.policy.delegation.delegation.DelegationModel#getPolicy <em>Policy</em>}' containment reference.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @param value the new value of the '<em>Policy</em>' containment reference.
   * @see #getPolicy()
   * @generated
   */
  void setPolicy(Policy value);

} // DelegationModel