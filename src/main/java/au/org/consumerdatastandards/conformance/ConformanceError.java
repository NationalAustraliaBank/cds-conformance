package au.org.consumerdatastandards.conformance;

import au.org.consumerdatastandards.support.data.CDSDataType;
import au.org.consumerdatastandards.support.data.CustomDataType;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;

public class ConformanceError {

    private String dataJson;

    private Type errorType;

    private Field errorField;

    private Object errorFieldValue;

    private CDSDataType cdsDataType;

    private String message;

    public ConformanceError dataJson(String dataJson) {
        this.dataJson = dataJson;
        return this;
    }

    public ConformanceError errorType(Type errorType) {
        this.errorType = errorType;
        return this;
    }

    public ConformanceError errorField(Field errorField) {
        this.errorField = errorField;
        return this;
    }

    public ConformanceError errorFieldValue(Object errorFieldValue) {
        this.errorFieldValue = errorFieldValue;
        return this;
    }

    public ConformanceError cdsDataType(CDSDataType cdsDataType) {
        this.cdsDataType = cdsDataType;
        return this;
    }

    public ConformanceError errorMessage(String message) {
        this.message = message;
        return this;
    }

    public String getDescription() {
        switch (errorType) {
            case MISSING_VALUE:
                return String.format("Required field '%s' has NULL value in\n%s", errorField.getName(), dataJson);
            case MISSING_PROPERTY:
                return String.format("Required field '%s' is missing in\n%s", errorField.getName(), dataJson);
            case PATTERN_NOT_MATCHED:
                CustomDataType customDataType = cdsDataType.value();
                return String.format("%s '%s' in\n%s\ndoes not conform to CDS type %s",
                    errorField.getName(), errorFieldValue, dataJson, customDataType.getName());
            case NUMBER_TOO_SMALL:
                CustomDataType customType = errorField.getAnnotation(CDSDataType.class).value();
                return String.format("%s '%s' in\n%s\nis smaller than CDS type %s minimum value %s",
                    errorField.getName(), errorFieldValue, dataJson, customType.getName(), customType.getMin());
            case NUMBER_TOO_BIG:
                CustomDataType dataType = errorField.getAnnotation(CDSDataType.class).value();
                return String.format("%s '%s' in\n%s\nis bigger than CDS type %s max value %s",
                    errorField.getName(), errorFieldValue, dataJson, dataType.getName(), dataType.getMax());
            default:
                if (!StringUtils.isBlank(message)) return message;
                else return "Unknown error";
        }
    }

    public enum Type {

        MISSING_PROPERTY,
        MISSING_VALUE,
        NO_MATCHING_MODEL,
        BROKEN_CONSTRAINT,
        PATTERN_NOT_MATCHED,
        NUMBER_TOO_SMALL,
        NUMBER_TOO_BIG,
        DATA_NOT_MATCHING_CRITERIA
    }
}
