package org.alien4cloud.tosca.editor.operations.relationshiptemplate;

/**
 * Allows to remove get_input function to the property of a node.
 */
public class UnsetRelationshipPropertyAsInputOperation extends AbstractRelationshipOperation {
    /** Id of the property */
    private String propertyName;

    @Override
    public String commitMessage() {
        return "property <" + propertyName + "> from relationship <" + getRelationshipName() + "> in node <" + getNodeName()
                + "> is not tied to an input anymore.";
    }
}