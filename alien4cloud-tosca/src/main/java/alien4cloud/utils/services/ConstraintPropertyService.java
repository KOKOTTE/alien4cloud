package alien4cloud.utils.services;

import static alien4cloud.utils.AlienUtils.safe;

import java.beans.IntrospectionException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.alien4cloud.tosca.exceptions.ConstraintTechnicalException;
import org.alien4cloud.tosca.exceptions.ConstraintValueDoNotMatchPropertyTypeException;
import org.alien4cloud.tosca.exceptions.ConstraintViolationException;
import org.alien4cloud.tosca.model.definitions.PropertyConstraint;
import org.alien4cloud.tosca.model.definitions.PropertyDefinition;
import org.alien4cloud.tosca.model.definitions.PropertyValue;
import org.alien4cloud.tosca.model.types.DataType;
import org.alien4cloud.tosca.model.types.PrimitiveDataType;
import org.alien4cloud.tosca.normative.types.IPropertyType;
import org.alien4cloud.tosca.normative.types.ToscaTypes;

import alien4cloud.exception.InvalidArgumentException;
import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.tosca.properties.constraints.ConstraintUtil;
import alien4cloud.tosca.properties.constraints.ConstraintUtil.ConstraintInformation;
import alien4cloud.utils.VersionUtil;
import alien4cloud.utils.version.InvalidVersionException;
import lombok.extern.slf4j.Slf4j;

/**
 * Property value validation utility. It checks that a given property value is matching the required property definition.
 *
 * This includes checking that it's type matches the property definition and that if any constraints are specified the value is actually matching constraints.
 */
@Slf4j
public final class ConstraintPropertyService {
    private ConstraintPropertyService() {
    }

    /**
     * Check the constraints on an unwrapped property value (basically a string, map or list).
     * 
     * @param propertyName The name of the property.
     * @param propertyValue The value of the property to check.
     * @param propertyDefinition The property definition that defines the property to check.
     * @throws ConstraintValueDoNotMatchPropertyTypeException In case the value type doesn't match the type of the property as defined.
     * @throws ConstraintViolationException In case the value doesn't match one of the constraints defined on the property.
     */
    public static void checkPropertyConstraint(String propertyName, Object propertyValue, PropertyDefinition propertyDefinition)
            throws ConstraintValueDoNotMatchPropertyTypeException, ConstraintViolationException {
        checkPropertyConstraint(propertyName, propertyValue, propertyDefinition, null);
    }

    /**
     * Check the constraints on an unwrapped property value (basically a string, map or list) and get events through the given consumer parameter when missing
     * properties on complex data type are found.
     * Note that the property value cannot be null and the required characteristic of the initial property definition will NOT be checked.
     *
     * @param propertyName The name of the property.
     * @param propertyValue The value of the property to check.
     * @param propertyDefinition The property definition that defines the property to check.
     * @param missingPropertyConsumer A consumer to receive events when a required property is not defined on a complex type sub-field.
     * @throws ConstraintValueDoNotMatchPropertyTypeException In case the value type doesn't match the type of the property as defined.
     * @throws ConstraintViolationException In case the value doesn't match one of the constraints defined on the property.
     */
    public static void checkPropertyConstraint(String propertyName, Object propertyValue, PropertyDefinition propertyDefinition,
            Consumer<String> missingPropertyConsumer) throws ConstraintValueDoNotMatchPropertyTypeException, ConstraintViolationException {
        Object value = propertyValue;
        if (propertyValue instanceof PropertyValue) {
            value = ((PropertyValue) propertyValue).getValue();
        }
        boolean isPrimitiveType = false;
        boolean isTypeDerivedFromPrimitive = false;
        DataType dataType = null;
        String typeName = propertyDefinition.getType();
        if (ToscaTypes.isPrimitive(typeName)) {
            isPrimitiveType = true;
        } else {
            dataType = ToscaContext.get(DataType.class, typeName);
            if (dataType instanceof PrimitiveDataType) {
                // the type is derived from a primitive type
                isTypeDerivedFromPrimitive = true;
            }
        }

        if (value instanceof String) {
            if (isPrimitiveType) {
                checkSimplePropertyConstraint(propertyName, (String) value, propertyDefinition);
            } else if (isTypeDerivedFromPrimitive) {
                checkComplexPropertyDerivedFromPrimitiveTypeConstraints(propertyName, (String) value, propertyDefinition, dataType);
            } else {
                throw new ConstraintValueDoNotMatchPropertyTypeException(
                        "Property value is a String while the expected data type is a complex type " + value.getClass().getName());
            }
        } else if (value instanceof Map) {
            if (ToscaTypes.MAP.equals(propertyDefinition.getType())) {
                checkMapPropertyConstraint(propertyName, (Map<String, Object>) value, propertyDefinition, missingPropertyConsumer);
            } else {
                checkDataTypePropertyConstraint(propertyName, (Map<String, Object>) value, propertyDefinition, missingPropertyConsumer);
            }
        } else if (value instanceof List) {
            // Range type is a specific primitive type that is actually wrapped
            if (ToscaTypes.RANGE.equals(propertyDefinition.getType())) {
                checkRangePropertyConstraint(propertyName, (List<Object>) value, propertyDefinition);
            } else {
                checkListPropertyConstraint(propertyName, (List<Object>) value, propertyDefinition, missingPropertyConsumer);
            }
        } else {
            throw new InvalidArgumentException(
                    "Not expecting to receive constraint validation for other types than String, Map or List as " + value.getClass().getName());
        }
    }

    /**
     * Check constraints defined on a property for a specified value
     *
     * @param propertyName Property name (mainly used to create a comprehensive error message)
     * @param stringValue Tested property value
     * @param propertyDefinition Full property definition with type, constraints, default value,...
     * @throws ConstraintViolationException
     * @throws ConstraintValueDoNotMatchPropertyTypeException
     */
    public static void checkSimplePropertyConstraint(final String propertyName, final String stringValue, final PropertyDefinition propertyDefinition)
            throws ConstraintViolationException, ConstraintValueDoNotMatchPropertyTypeException {
        ConstraintInformation consInformation = null;

        // check any property definition without constraints (type/value)
        checkBasicType(propertyName, propertyDefinition.getType(), stringValue);

        if (propertyDefinition.getConstraints() != null && !propertyDefinition.getConstraints().isEmpty()) {
            IPropertyType<?> toscaType = ToscaTypes.fromYamlTypeName(propertyDefinition.getType());
            for (PropertyConstraint constraint : propertyDefinition.getConstraints()) {
                try {
                    consInformation = ConstraintUtil.getConstraintInformation(constraint);
                    consInformation.setPath(propertyName + ".constraints[" + consInformation.getName() + "]");
                    constraint.initialize(toscaType);
                    constraint.validate(toscaType, stringValue);
                } catch (ConstraintViolationException e) {
                    throw new ConstraintViolationException(e.getMessage(), e, consInformation);
                } catch (IntrospectionException e) {
                    // ConstraintValueDoNotMatchPropertyTypeException is not supposed to be raised here (only in constraint definition validation)
                    log.info("Constraint introspection error for property <" + propertyName + "> value <" + stringValue + ">", e);
                    throw new ConstraintTechnicalException("Constraint introspection error for property <" + propertyName + "> value <" + stringValue + ">", e);
                }
            }
        }
    }

    private static void checkRangePropertyConstraint(final String propertyName, final List<Object> rangeValue, final PropertyDefinition propertyDefinition)
            throws ConstraintValueDoNotMatchPropertyTypeException, ConstraintViolationException {
        if (rangeValue.size() == 2) {
            try {
                Long.parseLong((String) rangeValue.get(0));
                Long.parseLong((String) rangeValue.get(1));
            } catch (ClassCastException | NumberFormatException e) {
                rangeTypeError(propertyName, rangeValue);
            }
            String rangeAsString = String.format("[%s,%s]", rangeValue.get(0), rangeValue.get(1));
            checkSimplePropertyConstraint(propertyName, rangeAsString, propertyDefinition);
        } else {
            rangeTypeError(propertyName, rangeValue);
        }
    }

    private static void rangeTypeError(final String propertyName, final List<Object> rangeValue) throws ConstraintValueDoNotMatchPropertyTypeException {
        log.debug("The property value for property {} is not of type {}: {}", propertyName, ToscaTypes.RANGE, rangeValue);
        ConstraintInformation consInformation = new ConstraintInformation(propertyName, null, rangeValue.toString(), ToscaTypes.RANGE);
        throw new ConstraintValueDoNotMatchPropertyTypeException("Range type must define numeric min and max values of the range.", null, consInformation);
    }

    /**
     * Check constraints defined on a property which has a type derived from a primitive.
     */
    private static void checkComplexPropertyDerivedFromPrimitiveTypeConstraints(final String propertyName, final String stringValue,
            final PropertyDefinition propertyDefinition, final DataType dataType)
            throws ConstraintViolationException, ConstraintValueDoNotMatchPropertyTypeException {
        ConstraintInformation consInformation = null;
        boolean hasDefinitionConstraints = propertyDefinition.getConstraints() != null && !propertyDefinition.getConstraints().isEmpty();
        boolean hasTypeConstraints = false;
        if (dataType instanceof PrimitiveDataType && ((PrimitiveDataType) dataType).getConstraints() != null
                && !((PrimitiveDataType) dataType).getConstraints().isEmpty()) {
            hasTypeConstraints = true;
        }
        String derivedFromPrimitiveType = dataType.getDerivedFrom().get(0);
        // Check the type of the property even if there is no constraints.
        checkBasicType(propertyName, derivedFromPrimitiveType, stringValue);
        if (hasDefinitionConstraints || hasTypeConstraints) { // check the constraints if there is any defined
            if (hasDefinitionConstraints) {
                checkConstraints(propertyName, stringValue, derivedFromPrimitiveType, propertyDefinition.getConstraints());
            }
            if (hasTypeConstraints) {
                checkConstraints(propertyName, stringValue, derivedFromPrimitiveType, ((PrimitiveDataType) dataType).getConstraints());
            }
        }
    }

    private static void checkConstraints(final String propertyName, final String stringValue, final String typeName, List<PropertyConstraint> constraints)
            throws ConstraintViolationException, ConstraintValueDoNotMatchPropertyTypeException {
        ConstraintInformation consInformation = null;
        for (PropertyConstraint constraint : constraints) {
            IPropertyType<?> toscaType = ToscaTypes.fromYamlTypeName(typeName);
            try {
                consInformation = ConstraintUtil.getConstraintInformation(constraint);
                consInformation.setPath(propertyName + ".constraints[" + consInformation.getName() + "]");
                constraint.initialize(toscaType);
                constraint.validate(toscaType, stringValue);
            } catch (ConstraintViolationException e) {
                throw new ConstraintViolationException(e.getMessage(), e, consInformation);
            } catch (IntrospectionException e) {
                // ConstraintValueDoNotMatchPropertyTypeException is not supposed to be raised here (only in constraint definition validation)
                log.info("Constraint introspection error for property <" + propertyName + "> value <" + stringValue + ">", e);
                throw new ConstraintTechnicalException("Constraint introspection error for property <" + propertyName + "> value <" + stringValue + ">", e);
            }
        }
    }

    private static void checkDataTypePropertyConstraint(String propertyName, Map<String, Object> complexPropertyValue, PropertyDefinition propertyDefinition,
            Consumer<String> missingPropertyConsumer) throws ConstraintViolationException, ConstraintValueDoNotMatchPropertyTypeException {
        DataType dataType = ToscaContext.get(DataType.class, propertyDefinition.getType());
        if (dataType == null) {
            throw new ConstraintViolationException(
                    "Complex type " + propertyDefinition.getType() + " is not complex or it cannot be found in the archive nor in Alien");
        }
        for (Map.Entry<String, Object> complexPropertyValueEntry : complexPropertyValue.entrySet()) {
            if (!safe(dataType.getProperties()).containsKey(complexPropertyValueEntry.getKey())) {
                throw new ConstraintViolationException("Complex type " + propertyDefinition.getType() + " do not have nested property with name "
                        + complexPropertyValueEntry.getKey() + " for property " + propertyName);
            } else {
                Object nestedPropertyValue = complexPropertyValueEntry.getValue();
                PropertyDefinition nestedPropertyDefinition = dataType.getProperties().get(complexPropertyValueEntry.getKey());
                checkPropertyConstraint(propertyName + "." + complexPropertyValueEntry.getKey(), nestedPropertyValue, nestedPropertyDefinition,
                        missingPropertyConsumer);
            }
        }
        // check if the data type has required missing properties
        if (missingPropertyConsumer != null) {
            for (Map.Entry<String, PropertyDefinition> dataTypeDefinition : safe(dataType.getProperties()).entrySet()) {
                if (dataTypeDefinition.getValue().isRequired() && !complexPropertyValue.containsKey(dataTypeDefinition.getKey())) {
                    // A required property is missing
                    String missingPropertyName = propertyName + "." + dataTypeDefinition.getKey();
                    missingPropertyConsumer.accept(missingPropertyName);
                }
            }
        }
    }

    private static void checkListPropertyConstraint(String propertyName, List<Object> listPropertyValue, PropertyDefinition propertyDefinition,
            Consumer<String> missingPropertyConsumer) throws ConstraintValueDoNotMatchPropertyTypeException, ConstraintViolationException {
        PropertyDefinition entrySchema = propertyDefinition.getEntrySchema();
        if (entrySchema == null) {
            throw new ConstraintValueDoNotMatchPropertyTypeException("value is a list but type actually is <" + propertyDefinition.getType() + ">");
        }
        for (int i = 0; i < listPropertyValue.size(); i++) {
            checkPropertyConstraint(propertyName + "[" + String.valueOf(i) + "]", listPropertyValue.get(i), entrySchema, missingPropertyConsumer);
        }
    }

    private static void checkMapPropertyConstraint(String propertyName, Map<String, Object> complexPropertyValue, PropertyDefinition propertyDefinition,
            Consumer<String> missingPropertyConsumer) throws ConstraintValueDoNotMatchPropertyTypeException, ConstraintViolationException {
        PropertyDefinition entrySchema = propertyDefinition.getEntrySchema();
        if (entrySchema == null) {
            throw new ConstraintValueDoNotMatchPropertyTypeException("value is a map but type actually is <" + propertyDefinition.getType() + ">");
        }
        for (Map.Entry<String, Object> complexPropertyValueEntry : complexPropertyValue.entrySet()) {
            checkPropertyConstraint(propertyName + "." + complexPropertyValueEntry.getKey(), complexPropertyValueEntry.getValue(), entrySchema,
                    missingPropertyConsumer);
        }
    }

    /**
     * Check that a given value is matching the native type defined on the property definition.
     *
     * @param propertyName The name of the property under validation
     * @param primitiveType The primitive type to check the value against.
     * @param propertyValue The value to check.
     * @throws ConstraintValueDoNotMatchPropertyTypeException in case the value does not match the primitive type.
     */
    private static void checkBasicType(final String propertyName, final String primitiveType, final String propertyValue)
            throws ConstraintValueDoNotMatchPropertyTypeException {
        // check basic type value : "boolean" (not handled, no exception thrown)
        // "string" (basic case, no exception), "float", "integer", "version"
        try {
            switch (primitiveType) {
            case "integer":
                Long.parseLong(propertyValue);
                break;
            case "float":
                Float.parseFloat(propertyValue);
                break;
            case "version":
                VersionUtil.parseVersion(propertyValue);
                break;
            default:
                // last type "string" can have any format
                break;
            }
        } catch (NumberFormatException | InvalidVersionException e) {
            log.debug("The property value for property {} is not of type {}: {}", propertyName, primitiveType, propertyValue, e);
            ConstraintInformation consInformation = new ConstraintInformation(propertyName, null, propertyValue, primitiveType);
            throw new ConstraintValueDoNotMatchPropertyTypeException(e.getMessage(), e, consInformation);
        }
    }
}
