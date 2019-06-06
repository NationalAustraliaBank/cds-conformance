package au.org.consumerdatastandards.conformance.util;

import au.org.consumerdatastandards.codegen.util.ReflectionUtil;
import au.org.consumerdatastandards.conformance.ConformanceError;
import au.org.consumerdatastandards.conformance.ConformanceErrorType;
import au.org.consumerdatastandards.support.data.DataDefinition;
import au.org.consumerdatastandards.support.data.Property;
import net.sf.cglib.beans.BeanGenerator;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ConformanceUtil {

    public static final String GENERATED_CLASS_SUFFIX = "$ByCDS";

    public static void checkAgainstModel(Object data, Class<?> model, List<ConformanceError> errors) {
        String[] oneOfProperties = getOneOfProperties(model);
        if (oneOfProperties != null && oneOfProperties.length > 1) {
            Map<String, Object> propertyValues = getPropertyValues(data, oneOfProperties);
            if (propertyValues.size() != 1) {
                errors.add(new ConformanceError()
                    .errorType(ConformanceErrorType.ONE_OF_CONSTRAINT)
                    .dataObject(data)
                    .errorMessage(buildOneOfErrorMessage(data, oneOfProperties, propertyValues))
                );
            }
        }
        List<Field> properties = getAllProperties(model);
        for (Field modelField : properties) {
            Object dataFieldValue = getDataFieldValue(data, modelField);
            if (modelField.getAnnotation(Property.class).required() && dataFieldValue == null) {
                errors.add(new ConformanceError()
                    .errorType(ConformanceErrorType.MISSING_VALUE)
                    .dataObject(data).modelClass(model)
                    .errorField(modelField));
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

    private static String buildOneOfErrorMessage(Object data, String[] oneOfProperties, Map<String, Object> values) {
        StringBuilder sb = new StringBuilder("One of the [");
        for (int i = 0; i < oneOfProperties.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(oneOfProperties[i]);
        }
        sb.append("] properties should have value, but ").append(values.size()).append(" properties ");
        if (!values.isEmpty()) {
            sb.append("[");
            int count = 0;
            for (String property : values.keySet()) {
                if (count > 0) sb.append(", ");
                sb.append(property);
                count++;
            }
            sb.append("] ");
        }
        sb.append("have values");
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

    private static String[] getOneOfProperties(Class<?> model) {
        DataDefinition dataDefinition = model.getAnnotation(DataDefinition.class);
        if (dataDefinition != null) {
            return dataDefinition.oneOf();
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
