package au.org.consumerdatastandards.conformance.util;

import au.org.consumerdatastandards.codegen.util.ReflectionUtil;
import au.org.consumerdatastandards.conformance.ConformanceError;
import au.org.consumerdatastandards.support.data.*;
import net.sf.cglib.beans.BeanGenerator;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ConformanceUtil {

    public static final String GENERATED_CLASS_SUFFIX = "$ByCDS";

    public static void checkAgainstModel(Object data, Class<?> model, List<ConformanceError> errors) {
        String[] anyOfProperties = getAnyOfProperties(model);
        if (anyOfProperties != null && anyOfProperties.length > 1) {
            Map<String, Object> propertyValues = getPropertyValues(data, anyOfProperties);
            if (propertyValues.isEmpty()) {
                errors.add(new ConformanceError()
                    .errorType(ConformanceError.Type.BROKEN_CONSTRAINT)
                    .dataObject(data)
                    .errorMessage(buildAnyOfErrorMessage(data, anyOfProperties, propertyValues))
                );
            }
        }
        List<Field> properties = getAllProperties(model);
        Map<String, Field> propertyMap = buildPropertyMap(properties);
        for (Field modelField : properties) {
            Object dataFieldValue = getDataFieldValue(data, modelField);
            Property property = modelField.getAnnotation(Property.class);
            if (property.required() && dataFieldValue == null) {
                errors.add(new ConformanceError()
                    .errorType(ConformanceError.Type.MISSING_VALUE)
                    .dataObject(data).modelClass(model)
                    .errorField(modelField));
            } else if (dataFieldValue != null && modelField.isAnnotationPresent(CDSDataType.class)) {
                CDSDataType cdsDataType = modelField.getAnnotation(CDSDataType.class);
                checkAgainstCDSDataType(data, model, modelField, dataFieldValue, cdsDataType, errors);
            }
            Condition[] conditions = property.requiredIf();
            if (conditions.length > 0) {
                boolean conditionsMet = true;
                Condition condition = conditions[0];
                Field relatedProperty = propertyMap.get(condition.propertyName());
                Object relatedPropertyValue = null;
                if (relatedProperty != null) {
                    relatedPropertyValue = getDataFieldValue(data, relatedProperty);
                    if (!isValueSpecified(relatedPropertyValue, condition.values())) {
                        conditionsMet = false;
                    }
                }
                if (conditionsMet && dataFieldValue == null) {
                    errors.add(new ConformanceError()
                        .errorType(ConformanceError.Type.MISSING_VALUE)
                        .dataObject(data).modelClass(model)
                        .errorField(modelField)
                        .errorMessage(String.format("%s is required given %s value is %s",
                            modelField.getName(), relatedProperty.getName(), relatedPropertyValue)));
                } else if (conditionsMet) {
                    CDSDataType requiredCDSDataType = null;
                    ConditionalCDSDataType[] conditionalCDSDataTypes = condition.conditionalCDSDataTypes();
                    if (conditionalCDSDataTypes.length > 0) {
                        for (ConditionalCDSDataType conditionalCDSDataType : conditionalCDSDataTypes) {
                            if (conditionalCDSDataType.value().equals("" + relatedPropertyValue)) {
                                requiredCDSDataType = conditionalCDSDataType.cdsDataType();
                            }
                        }
                    }
                    if (requiredCDSDataType != null) {
                        checkAgainstCDSDataType(data, model, modelField, dataFieldValue, requiredCDSDataType, errors);
                    }
                }
            }
            Class<?> modelFieldType = modelField.getType();
            if (modelFieldType.isArray()) {
                if (modelFieldType.getComponentType().isAnnotationPresent(DataDefinition.class)
                    && dataFieldValue != null
                    && Array.getLength(dataFieldValue) > 0) {
                    Object[] values = unpack(dataFieldValue);
                    for (Object value : values) {
                        checkAgainstModel(value, modelFieldType.getComponentType(), errors);
                    }
                }
            } else if (ReflectionUtil.isSetOrList(modelFieldType)) {
                Class<?> itemType = ReflectionUtil.getItemType(modelFieldType, modelField.getGenericType());
                if (itemType.isAnnotationPresent(DataDefinition.class)
                    && dataFieldValue != null) {
                    if (dataFieldValue.getClass().isArray()) {
                        Object[] values = unpack(dataFieldValue);
                        for (Object value : values) {
                            checkAgainstModel(value, itemType, errors);
                        }
                    } else {
                        for (Object value : (Collection) dataFieldValue) {
                            checkAgainstModel(value, itemType, errors);
                        }
                    }
                }
            }
            if (dataFieldValue != null && modelFieldType.isAnnotationPresent(DataDefinition.class)) {
                checkAgainstModel(dataFieldValue, modelFieldType, errors);
            }
        }
    }

    private static boolean isValueSpecified(Object relatedPropertyValue, String[] values) {
        if (relatedPropertyValue == null) return false;
        for (String value : values) {
            if (value.equals(relatedPropertyValue.toString())) {
                return true;
            }
        }
        return false;
    }

    private static void checkAgainstCDSDataType(Object data, Class<?> model, Field modelField, Object dataFieldValue, CDSDataType cdsDataType, List<ConformanceError> errors) {
        CustomDataType customDataType = cdsDataType.value();
        if (customDataType.getPattern() != null) {
            if (!dataFieldValue.toString().matches(customDataType.getPattern())) {
                errors.add(new ConformanceError()
                    .errorType(ConformanceError.Type.PATTERN_NOT_MATCHED)
                    .dataObject(data).modelClass(model)
                    .errorField(modelField)
                );
            }
        }
        Number min = customDataType.getMin();
        if (min != null && new BigDecimal(min.toString()).compareTo(new BigDecimal(dataFieldValue.toString())) > 0) {
            errors.add(new ConformanceError()
                .errorType(ConformanceError.Type.NUMBER_TOO_SMALL)
                .dataObject(data).modelClass(model)
                .errorField(modelField)
            );
        }
        Number max = customDataType.getMax();
        if (max != null && new BigDecimal(max.toString()).compareTo(new BigDecimal(dataFieldValue.toString())) < 0) {
            errors.add(new ConformanceError()
                .errorType(ConformanceError.Type.NUMBER_TOO_BIG)
                .dataObject(data).modelClass(model)
                .errorField(modelField)
            );
        }
    }

    private static String buildAnyOfErrorMessage(Object data, String[] anyOfProperties, Map<String, Object> values) {
        StringBuilder sb = new StringBuilder("At least one of the [");
        for (int i = 0; i < anyOfProperties.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(anyOfProperties[i]);
        }
        sb.append("] properties should have value, but none of them have values");
        return sb.toString();
    }

    private static List<Field> getAllProperties(Class<?> model) {
        List<Field> properties = FieldUtils.getFieldsListWithAnnotation(model, Property.class);
        DataDefinition dataDefinition = model.getAnnotation(DataDefinition.class);
        if (dataDefinition != null && dataDefinition.allOf().length > 0) {
            for (Class<?> clazz : dataDefinition.allOf()) {
                properties.addAll(FieldUtils.getFieldsListWithAnnotation(clazz, Property.class));
            }
        }
        return properties;
    }

    private static Map<String, Field> buildPropertyMap(List<Field> properties) {
        Map<String, Field> map = new HashMap<>();
        properties.forEach(p -> map.put(p.getName(), p));
        return map;
    }

    private static String[] getAnyOfProperties(Class<?> model) {
        DataDefinition dataDefinition = model.getAnnotation(DataDefinition.class);
        if (dataDefinition != null) {
            return dataDefinition.anyOf();
        }
        return null;
    }

    private static Map<String, Object> getPropertyValues(Object data, String[] properties) {
        Map<String, Object> values = new HashMap<>();
        for (String property : properties) {
            Object value = getDataFieldValue(data, property);
            if (value != null) {
                values.put(property, value);
            }
        }
        return values;
    }

    private static Object getDataFieldValue(Object data, Field modelField) {
        String fieldName = modelField.getName();
        return getDataFieldValue(data, fieldName);
    }

    private static Object getDataFieldValue(Object data, String fieldName) {
        if (isGeneratedClass(data.getClass())) {
            fieldName = "$cglib_prop_" + fieldName;
        }
        Field dataField = FieldUtils.getField(data.getClass(), fieldName, true);
        dataField.setAccessible(true);
        Object dataFieldValue;
        try {
            dataFieldValue = dataField.get(data);
        } catch (IllegalAccessException e) {
            throw new Error(e); // should never happen
        }
        return dataFieldValue;
    }

    private static boolean isGeneratedClass(Class<?> clazz) {
        return clazz.getSimpleName().endsWith(GENERATED_CLASS_SUFFIX);
    }

    static Class<?> combine(Class<?> primaryClass, Class<?>[] allOf) {

        final BeanGenerator beanGenerator = new BeanGenerator();
        beanGenerator.setNamingPolicy((s, s1, o, predicate) -> primaryClass.getName() + GENERATED_CLASS_SUFFIX);
        addProperties(beanGenerator, primaryClass);
        for (Class<?> clazz : allOf) {
            addProperties(beanGenerator, clazz);
        }
        return (Class<?>) beanGenerator.createClass();
    }


    private static void addProperties(BeanGenerator beanGenerator, Class<?> clazz) {
        Field[] allFields = FieldUtils.getAllFields(clazz);
        for (Field field : allFields) {
            beanGenerator.addProperty(field.getName(), getFieldType(field));
        }
    }

    private static Class<?> getFieldType(Field field) {
        if (ReflectionUtil.isSetOrList(field.getType())) {
            Class<?> itemType = ReflectionUtil.getItemType(field.getType(), field.getGenericType());
            return Array.newInstance(itemType, 0).getClass();
        }
        return field.getType();
    }


    public static Object[] unpack(Object array) {
        Object[] values = new Object[Array.getLength(array)];
        for (int i = 0; i < values.length; i++)
            values[i] = Array.get(array, i);
        return values;
    }
}
