package au.org.consumerdatastandards.conformance;

import au.org.consumerdatastandards.codegen.ModelBuilder;
import au.org.consumerdatastandards.codegen.generator.Options;
import au.org.consumerdatastandards.conformance.util.ConformanceUtil;
import au.org.consumerdatastandards.conformance.util.ModelConformanceConverter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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

    public boolean validateFile(File jsonFile) {
        System.out.println("\nValidating " + jsonFile.getAbsolutePath());
        byte[] jsonData;
        try {
            jsonData = Files.readAllBytes(Paths.get(jsonFile.getCanonicalPath()));
            return validatePayload(jsonData);
        } catch (IOException e) {
            System.out.println("Failed to load file " + jsonFile.getAbsolutePath());
            return false;
        }
    }

    public boolean validatePayload(String json) {
        if (StringUtils.isBlank(json)) {
            System.out.println("Blank json text... Ignored.");
            return false;
        }
        return validatePayload(json.getBytes());
    }

    private boolean validatePayload(byte[] jsonData) {
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
            } catch (IOException e) {
                // ignored
            }
        }
        return false;
    }
}
