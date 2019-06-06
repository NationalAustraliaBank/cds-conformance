package au.org.consumerdatastandards.conformance;

import au.org.consumerdatastandards.codegen.ModelBuilder;
import au.org.consumerdatastandards.codegen.generator.Options;
import au.org.consumerdatastandards.conformance.util.ConformanceUtil;
import au.org.consumerdatastandards.conformance.util.ModelConformanceConverter;
import au.org.consumerdatastandards.support.EndpointResponse;
import au.org.consumerdatastandards.support.ResponseCode;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PayloadValidator {

    private static Logger LOGGER = LoggerFactory.getLogger(PayloadValidator.class);

    private ConformanceModel conformanceModel;

    public PayloadValidator() {
        ModelBuilder modelBuilder = new ModelBuilder(new Options());
        conformanceModel = ModelConformanceConverter.convert(modelBuilder.build());
    }

    public List<ConformanceError> validateFile(File jsonFile) {
        LOGGER.info("Validating " + jsonFile.getAbsolutePath());
        byte[] jsonData;
        try {
            jsonData = Files.readAllBytes(Paths.get(jsonFile.getCanonicalPath()));
            return validatePayload(jsonData);
        } catch (IOException e) {
            return Collections.singletonList(new ConformanceError().errorMessage(
                "Failed to load file " + jsonFile.getAbsolutePath()
            ));
        }
    }

    public List<ConformanceError> validatePayload(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.singletonList(
                new ConformanceError().errorMessage("Blank json text... Ignored."));
        }
        return validatePayload(json.getBytes());
    }

    private List<ConformanceError> validatePayload(byte[] jsonData) {
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
                LOGGER.info("Found matching model " + modelClass.getSimpleName());
                return errors;
            } catch (IOException e) {
                // ignored
            }
        }
        return Collections.singletonList(new ConformanceError()
            .errorType(ConformanceErrorType.NO_MATCHING_MODEL)
            .errorMessage("No matching model found"));
    }

    public List<ConformanceError> validateResponse(Object response, String operationId, ResponseCode responseCode) {
        List<ConformanceError> errors = new ArrayList<>();
        EndpointResponse endpointResponse = conformanceModel.getResponse(operationId, responseCode);
        if (endpointResponse == null) {
            return Collections.singletonList(new ConformanceError().errorMessage(
                String.format("No response model found for operation %s with response code %s", operationId, responseCode)));
        }
        Class<?> responseModel = endpointResponse.content();
        ConformanceUtil.checkAgainstModel(response, responseModel, errors);
        return errors;
    }
}
