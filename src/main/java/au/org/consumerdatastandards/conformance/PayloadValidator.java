package au.org.consumerdatastandards.conformance;

import au.org.consumerdatastandards.codegen.ModelBuilder;
import au.org.consumerdatastandards.codegen.generator.Options;
import au.org.consumerdatastandards.conformance.util.ConformanceUtil;
import au.org.consumerdatastandards.conformance.util.ModelConformanceConverter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@ShellComponent
public class PayloadValidator {

    private static ConformanceModel conformanceModel;

    @PostConstruct
    public void init() {
        ModelBuilder modelBuilder = new ModelBuilder(new Options());
        conformanceModel = ModelConformanceConverter.convert(modelBuilder.build());
        for (Class<?> clazz : conformanceModel.getPayloadModels()) {
            if (conformanceModel.getPlayload(clazz).getDataClass() == null) {
                System.out.println(clazz.getSimpleName());
            }
        }
    }

    @ShellMethod("Validate json payload(s) against cds-model")
    public void validate(@ShellOption(value = "-f", help = "payload file or folder") String fileOrFolder) throws IOException {
        File file = new File(fileOrFolder);
        if (!file.exists()) {
            System.out.println("Cannot find " + fileOrFolder);
        } else if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (File oneFile : files) {
                validate(oneFile.getAbsolutePath());
            }
        } else {
            if (!validate(file)) {
                System.out.println("No matching model found");
            }
        }
    }

    private boolean validate(File jsonFile) throws IOException {
        System.out.println("\nValidating " + jsonFile.getAbsolutePath());
        byte[] jsonData = Files.readAllBytes(Paths.get(jsonFile.getCanonicalPath()));
        for(Class<?> modelClass : conformanceModel.getPayloadModels()) {
            try {
                ObjectMapper objectMapper = new ObjectMapper()
                    .registerModule(new ParameterNamesModule())
                    .registerModule(new Jdk8Module())
                    .registerModule(new JavaTimeModule())
                    .registerModule(new SimpleModule().setDeserializerModifier(new CglibBeanDeserializerModifier()))
                    .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                    .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
                Payload payload = conformanceModel.getPlayload(modelClass);
                Object data = objectMapper.readValue(jsonData, payload.getDataClass());
                System.out.println("Found matching model " + modelClass.getSimpleName());
                List<ConformanceError> errors = new ArrayList<>();
                ConformanceUtil.checkAgainstModel(data, modelClass, errors);
                if (errors.isEmpty()) {
                    System.out.println(payload.getDescription());
                } else {
                    System.out.println("Errors found:");
                    errors.forEach(conformanceError -> System.out.println(conformanceError.getDescription()));
                }
                return true;
            } catch (JsonMappingException e) {
                // ignored
            }
        }
        return false;
    }
}
