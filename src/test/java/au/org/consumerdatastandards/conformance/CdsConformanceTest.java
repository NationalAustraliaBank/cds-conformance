package au.org.consumerdatastandards.conformance;

import au.org.consumerdatastandards.client.ApiClient;
import au.org.consumerdatastandards.client.ApiException;
import au.org.consumerdatastandards.client.api.BankingProductsAPI;
import au.org.consumerdatastandards.client.model.*;
import au.org.consumerdatastandards.codegen.ModelBuilder;
import au.org.consumerdatastandards.codegen.generator.Options;
import au.org.consumerdatastandards.codegen.util.ReflectionUtil;
import au.org.consumerdatastandards.conformance.util.ConformanceUtil;
import au.org.consumerdatastandards.conformance.util.ModelConformanceConverter;
import au.org.consumerdatastandards.support.ResponseCode;
import au.org.consumerdatastandards.support.data.DataDefinition;
import au.org.consumerdatastandards.support.data.Property;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class CdsConformanceTest {

    private final static Logger LOGGER = LogManager.getLogger(CdsConformanceTest.class);

    private final static String serverUrl = "http://localhost:8080/cds-au/v1";

    private static BankingProductsAPI api;

    private static ConformanceModel conformanceModel;

    @BeforeAll
    public static void setup() {
        ModelBuilder modelBuilder = new ModelBuilder(loadModelBuilderOptions());
        conformanceModel = ModelConformanceConverter.convert(modelBuilder.build());
        ApiClient client = new ApiClient();
        client.setBasePath(serverUrl);
        api = new BankingProductsAPI(client);
    }

    private static Options loadModelBuilderOptions() {
        List<String> includedSectionList = new ArrayList<>();
        List<String> excludedSectionList = new ArrayList<>();
        Properties props = new Properties();
        InputStream is = CdsConformanceTest.class.getResourceAsStream("/conformance.properties");

        try {
            props.load(is);
            String includedSectionsCSV = props.getProperty("included.sections");
            if (!StringUtils.isBlank(includedSectionsCSV)) {
                String[] includedSections = getTrimmedValues(includedSectionsCSV.split(","));
                includedSectionList = Arrays.asList(includedSections);
            }
            String excludedSectionsCSV = props.getProperty("excluded.sections");
            if (!StringUtils.isBlank(excludedSectionsCSV)) {
                String[] excludedSections = getTrimmedValues(excludedSectionsCSV.split(","));
                excludedSectionList = Arrays.asList(excludedSections);
            }
            return new Options(includedSectionList, excludedSectionList);
        } catch (IOException e) {
            throw new Error("Failed to load conformance.properties");
        }
    }

    private static String[] getTrimmedValues(String[] values) {

        String[] trimmedValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            trimmedValues[i] = values[i].trim();
        }
        return trimmedValues;
    }

    @Test
    public void positiveCases() throws ApiException {

        ResponseBankingProductList allProducts = api.listProducts(ParamEffective.ALL, null, null, null, null, null);
        assertAll("Proper responses",
            () -> checkAgainstModel(allProducts, conformanceModel.getResponse("listProducts", ResponseCode.OK).content()),
            () -> {
                if (!allProducts.getData().getProducts().isEmpty()) {
                    checkProductListData(allProducts.getData());
                }
            }
        );
    }

    private void checkProductListData(ResponseBankingProductListData data) throws ApiException, IllegalAccessException {
        for (BankingProduct product : data.getProducts()) {
            ResponseBankingProductById productDetail = api.getProductDetail(product.getProductId());
            checkAgainstModel(productDetail, conformanceModel.getResponse("getProductDetail", ResponseCode.OK).content());
        }
    }

    private void checkAgainstModel(Object data, Class<?> model) throws IllegalAccessException {
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
