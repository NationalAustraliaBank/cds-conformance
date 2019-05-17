package au.org.consumerdatastandards.conformance;

import java.lang.reflect.Field;

public class ConformanceError {

    private Class<?> modelClass;

    private Object dataObject;

    private ConformanceErrorType errorType;

    private Field errorField;

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

    public String getDescription() {
        switch (errorType) {
            case MISSING_VALUE:
                return String.format("Required field '%s' in '%s' has NULL value", errorField.getName(), modelClass.getSimpleName());
            case MISSING_PROPERTY:
                return String.format("Required field '%s' is missing in %s", errorField.getName(), dataObject);
            default:
                return "Unknown error";
        }
    }
}
