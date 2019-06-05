package au.org.consumerdatastandards.conformance;

import au.org.consumerdatastandards.client.ApiClient;
import au.org.consumerdatastandards.client.ApiException;
import au.org.consumerdatastandards.client.api.BankingProductsAPI;
import au.org.consumerdatastandards.client.model.*;
import au.org.consumerdatastandards.codegen.ModelBuilder;
import au.org.consumerdatastandards.codegen.generator.Options;
import au.org.consumerdatastandards.conformance.util.ConformanceUtil;
import au.org.consumerdatastandards.conformance.util.ModelConformanceConverter;
import au.org.consumerdatastandards.support.ResponseCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class APIValidator {

    private static Logger LOGGER = LoggerFactory.getLogger(APIValidator.class);

    private BankingProductsAPI bankingProductsAPI;

    private ConformanceModel conformanceModel;

    public APIValidator(String serverUrl) {
        ModelBuilder modelBuilder = new ModelBuilder(new Options());
        conformanceModel = ModelConformanceConverter.convert(modelBuilder.build());
        ApiClient client = new ApiClient();
        client.setBasePath(serverUrl);
        bankingProductsAPI = new BankingProductsAPI(client);
    }

    public void validateAPI() {
        validateBankingProductsAPI();
    }

    public void validateAPI(String apiName) {
        if ("BankingProducts".equals(apiName)) {
            validateBankingProductsAPI();
        }
    }

    public void validateBankingProductsAPI() {
        try {
            ResponseBankingProductList allProducts = bankingProductsAPI.listProducts(ParamEffective.ALL, null, null, null, null, null);
            List<ConformanceError> errors = new ArrayList<>();
            Class<?> responseModel = conformanceModel.getResponse("listProducts", ResponseCode.OK).content();
            ConformanceUtil.checkAgainstModel(allProducts, responseModel, errors);
            for (BankingProduct product : allProducts.getData().getProducts()) {
                validateProductListData(product);
            }
        } catch (ApiException e) {
            LOGGER.error("API error: " + e.getMessage(), e);
        }
    }

    private void validateProductListData(BankingProduct product) {
        try {
            ResponseBankingProductById productDetail = bankingProductsAPI.getProductDetail(product.getProductId());
            Class<?> responseModel = conformanceModel.getResponse("getProductDetail", ResponseCode.OK).content();
            List<ConformanceError> errors = new ArrayList<>();
            ConformanceUtil.checkAgainstModel(productDetail, responseModel, errors);
        } catch (ApiException e) {
            LOGGER.error("API error: " + e.getMessage(), e);
        }
    }
}
