package au.org.consumerdatastandards.conformance;

import au.org.consumerdatastandards.codegen.ModelBuilder;
import au.org.consumerdatastandards.codegen.generator.Options;
import au.org.consumerdatastandards.codegen.util.ReflectionUtil;
import au.org.consumerdatastandards.conformance.util.ConformanceUtil;
import au.org.consumerdatastandards.conformance.util.ModelConformanceConverter;
import au.org.consumerdatastandards.support.data.DataDefinition;
import au.org.consumerdatastandards.support.data.Property;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PayloadValidator {

    private ConformanceModel conformanceModel;
    private final static Logger LOGGER = LogManager.getLogger(PayloadValidator.class);


    public PayloadValidator() {
        ModelBuilder modelBuilder = new ModelBuilder(new Options());
        conformanceModel = ModelConformanceConverter.convert(modelBuilder.build());
        for (Class<?> clazz : conformanceModel.getPayloadModels()) {
            if (conformanceModel.getPlayload(clazz).getDataClass() == null) {
                System.out.println(clazz.getSimpleName());
            }
        }
    }

    public boolean validateFile(File jsonFile) throws IOException {
        System.out.println("\nValidating " + jsonFile.getAbsolutePath());
        byte[] jsonData = Files.readAllBytes(Paths.get(jsonFile.getCanonicalPath()));
        return validatePayload(jsonData);
    }

    public boolean validatePayload(byte[] jsonData) throws IOException {
        for (Class<?> modelClass : conformanceModel.getPayloadModels()) {
            try {
                ObjectMapper objectMapper = new ObjectMapper().registerModule(new ParameterNamesModule())
                        .registerModule(new Jdk8Module()).registerModule(new JavaTimeModule())
                        .registerModule(new SimpleModule().setDeserializerModifier(new CglibBeanDeserializerModifier()))
                        .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                        .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
                Payload payload = conformanceModel.getPlayload(modelClass);
                Object data = objectMapper.readValue(jsonData, payload.getDataClass());
                List<ConformanceError> errors = new ArrayList<>();
                ConformanceUtil.checkAgainstModel(data, modelClass, errors);
                if (errors.isEmpty()) {
                    System.out.println(payload.getDescription());
                } else {
                    System.out.println("Errors found:");
                    errors.forEach(conformanceError -> System.out.println(conformanceError.getDescription()));
                }
                System.out.println("Found matching model " + modelClass.getSimpleName());
                return true;
            } catch (JsonMappingException e) {
                // ignored
            }
        }
        return false;
    }
    
    public void checkAgainstModel(Object data, Class<?> model) throws IllegalAccessException {
        LOGGER.info("Checking {} against {}", data, model);
        List<Field> properties = ConformanceUtil.getAllProperties(model);
        for (Field modelField : properties) {
            Field dataField = FieldUtils.getField(data.getClass(), modelField.getName(), true);
            assertNotNull(dataField, model.getSimpleName() + "." + modelField.getName() + " is missing from data " + data);
            dataField.setAccessible(true);
            Object dataFieldValue = dataField.get(data);
            if (modelField.getAnnotation(Property.class).required()) {
                assertNotNull(dataFieldValue, model.getSimpleName() + "." + modelField.getName() + " is required");
            }
            Class<?> modelFieldType = modelField.getType();
            if (modelFieldType.isArray()) {
                if (modelFieldType.getComponentType().isAnnotationPresent(DataDefinition.class)
                    && dataFieldValue != null
                    && Array.getLength(dataFieldValue) > 0) {
                    Object[] values = ConformanceUtil.unpack(dataFieldValue);
                    for (Object value : values) {
                        checkAgainstModel(value, modelFieldType.getComponentType());
                    }
                }
            } else if (ReflectionUtil.isSetOrList(modelFieldType)) {
                Class<?> itemType = ReflectionUtil.getItemType(modelFieldType, modelField.getGenericType());
                if (itemType.isAnnotationPresent(DataDefinition.class)
                    && dataFieldValue != null && !((Collection) dataFieldValue).isEmpty()) {
                    for (Object value : (Collection) dataFieldValue) {
                        checkAgainstModel(value, itemType);
                    }
                }

            } else if (dataFieldValue != null && modelFieldType.isAnnotationPresent(DataDefinition.class)) {
                checkAgainstModel(dataFieldValue, modelFieldType);
            }
        }
    }

}
