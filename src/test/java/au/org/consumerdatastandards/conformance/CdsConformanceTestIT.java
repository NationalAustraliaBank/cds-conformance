package au.org.consumerdatastandards.conformance;

import au.org.consumerdatastandards.client.ApiClient;
import au.org.consumerdatastandards.client.ApiException;
import au.org.consumerdatastandards.client.api.BankingProductsAPI;
import au.org.consumerdatastandards.client.model.*;
import au.org.consumerdatastandards.codegen.ModelBuilder;
import au.org.consumerdatastandards.codegen.generator.Options;
import au.org.consumerdatastandards.conformance.util.ModelConformanceConverter;
import au.org.consumerdatastandards.support.ResponseCode;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertAll;

public class CdsConformanceTestIT {

    private final static Logger LOGGER = LogManager.getLogger(CdsConformanceTestIT.class);

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
        InputStream is = CdsConformanceTestIT.class.getResourceAsStream("/conformance.properties");

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
        
        PayloadValidator payloadValidator = new PayloadValidator();

        ResponseBankingProductList allProducts = api.listProducts(ParamEffective.ALL, null, null, null, null, null);
        assertAll("Proper responses",
            () -> payloadValidator.checkAgainstModel(allProducts, conformanceModel.getResponse("listProducts", ResponseCode.OK).content()),
            () -> {
                if (!allProducts.getData().getProducts().isEmpty()) {
                    checkProductListData(allProducts.getData());
                }
            }
        );
    }

    private void checkProductListData(ResponseBankingProductListData data) throws ApiException, IllegalAccessException {
        PayloadValidator payloadValidator = new PayloadValidator();

        for (BankingProduct product : data.getProducts()) {
            ResponseBankingProductById productDetail = api.getProductDetail(product.getProductId());
            payloadValidator.checkAgainstModel(productDetail, conformanceModel.getResponse("getProductDetail", ResponseCode.OK).content());
        }
    }

}
