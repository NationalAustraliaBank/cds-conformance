package au.org.consumerdatastandards.conformance;

import au.org.consumerdatastandards.support.data.CDSDataType;
import au.org.consumerdatastandards.support.data.CustomDataType;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;

public class ConformanceError {

    private Class<?> modelClass;

    private Object dataObject;

    private ConformanceErrorType errorType;

    private Field errorField;

    private String message;

    public ConformanceError modelClass(Class<?> modelClass) {
        this.modelClass = modelClass;
        return this;
    }

    public ConformanceError dataObject(Object dataObject) {
        this.dataObject = dataObject;
        return this;
    }

    public ConformanceError errorType(ConformanceErrorType errorType) {
        this.errorType = errorType;
        return this;
    }

    public ConformanceError errorField(Field errorField) {
        this.errorField = errorField;
        return this;
    }

    public ConformanceError errorMessage(String message) {
        this.message = message;
        return this;
    }

    public String getDescription() {
        switch (errorType) {
            case MISSING_VALUE:
                return String.format("Required field '%s' in '%s' has NULL value", errorField.getName(), modelClass.getSimpleName());
            case MISSING_PROPERTY:
                return String.format("Required field '%s' is missing in %s", errorField.getName(), dataObject);
            case PATTERN_NOT_MATCHED:
                CustomDataType customDataType = errorField.getAnnotation(CDSDataType.class).value();
                return String.format("'%s' value in %s does not conform to CDS type %s format",
                    errorField.getName(), dataObject, customDataType.getName());
            case NUMBER_TOO_SMALL:
                CustomDataType customType = errorField.getAnnotation(CDSDataType.class).value();
                return String.format("'%s' value in %s is smaller than CDS type %s minimum value %s",
                    errorField.getName(), dataObject, customType.getName(), customType.getMin());
            case NUMBER_TOO_BIG:
                CustomDataType dataType = errorField.getAnnotation(CDSDataType.class).value();
                return String.format("'%s' value in %s is bigger than CDS type %s max value %s",
                    errorField.getName(), dataObject, dataType.getName(), dataType.getMax());
            default:
                if (StringUtils.isBlank(message)) return message;
                else return "Unknown error";
        }
    }
}
