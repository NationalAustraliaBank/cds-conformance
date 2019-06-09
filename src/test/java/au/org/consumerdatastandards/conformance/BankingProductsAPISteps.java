package au.org.consumerdatastandards.conformance;

import au.org.consumerdatastandards.api.banking.models.*;
import au.org.consumerdatastandards.support.ResponseCode;
import au.org.consumerdatastandards.support.data.CustomDataType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.util.DateTime;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import net.thucydides.core.annotations.Step;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static au.org.consumerdatastandards.api.banking.BankingProductsAPI.ParamEffective;
import static au.org.consumerdatastandards.conformance.ConformanceError.Type.DATA_NOT_MATCHING_CRITERIA;
import static net.serenitybdd.rest.SerenityRest.given;
import static org.junit.Assert.*;

public class BankingProductsAPISteps {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private PayloadValidator payloadValidator = new PayloadValidator();

    private String apiBasePath = "http://localhost:8080/cds-au/v1";

    private Response listProductsResponse;

    private Response getProductDetailResponse;

    private String requestUrl;

    private ResponseBankingProductList responseBankingProductList;

    @Step("Setup API base path to {0}")
    void setupApiBasePath(String apiBasePath) {
        this.apiBasePath = apiBasePath;
    }

    @Step("Call listProducts")
    void listProducts(String effective,
                      String updatedSince,
                      String brand,
                      String productCategory,
                      Integer page,
                      Integer pageSize) {
        String url = apiBasePath + "/banking/products";
        requestUrl = url;
        boolean paramAdded = false;
        RequestSpecification given = given().accept(ContentType.JSON);
        if (!StringUtils.isBlank(effective)) {
            given.queryParam("effective", effective);
            requestUrl += "?effective=" + effective;
            paramAdded = true;
        }
        if (!StringUtils.isBlank(updatedSince)) {
            given.queryParam("updated-since", updatedSince);
            requestUrl += (paramAdded ? "&" : "?") + "updated-since=" + updatedSince;
            paramAdded = true;
        }
        if (!StringUtils.isBlank(brand)) {
            given.queryParam("brand", brand);
            requestUrl += (paramAdded ? "&" : "?") + "brand=" + brand;
            paramAdded = true;
        }
        if (!StringUtils.isBlank(productCategory)) {
            given.queryParam("product-category", productCategory);
            requestUrl += (paramAdded ? "&" : "?") + "product-category=" + productCategory;
            paramAdded = true;
        }
        if (page != null) {
            given.queryParam("page", page);
            requestUrl += (paramAdded ? "&" : "?") + "page=" + page;
            paramAdded = true;
        }
        if (pageSize != null) {
            given.queryParam("page-size", pageSize);
            requestUrl += (paramAdded ? "&" : "?") + "page-size=" + pageSize;
        }

        listProductsResponse = given.when().get(url).then().log().body().extract().response();
    }

    @Step("Validate listProducts response")
    void validateListProductsResponse(String effective,
                                      String updatedSince,
                                      String brand,
                                      String productCategory,
                                      Integer page,
                                      Integer pageSize) {
        boolean paramsValid = validateListProductsParams(effective, updatedSince, productCategory, page, pageSize);
        int statusCode = listProductsResponse.statusCode();
        if (!paramsValid) {
            assertEquals(statusCode, ResponseCode.BAD_REQUEST.getCode());
        } else {
            assertEquals(statusCode, ResponseCode.OK.getCode());
            List<ConformanceError> conformanceErrors = new ArrayList<>();
            String contentType = listProductsResponse.contentType();
            if (!"application/json".equals(contentType)) {
                conformanceErrors.add(new ConformanceError().errorType(DATA_NOT_MATCHING_CRITERIA)
                    .errorMessage("missing content-type application/json in response header"));
            }
            String json = listProductsResponse.getBody().asString();
            ObjectMapper objectMapper = payloadValidator.createObjectMapper();
            try {
                responseBankingProductList = objectMapper.readValue(json, ResponseBankingProductList.class);
                conformanceErrors.addAll(payloadValidator.validateResponse(this.requestUrl, responseBankingProductList, "listProducts", statusCode));
                ResponseBankingProductListData data = getProductListData(responseBankingProductList);
                List<BankingProduct> products = getProducts(data);
                if (products != null && !products.isEmpty()) {
                    for (BankingProduct bankingProduct : products) {
                        conformanceErrors.addAll(checkDataAgainstCriteria(bankingProduct, effective, updatedSince, brand, productCategory));
                    }
                }
                for (ConformanceError error : conformanceErrors) {
                    logger.error(error.getDescription());
                }
                assertTrue("Conformance errors found in response payload", conformanceErrors.isEmpty());
            } catch (IOException e) {
                fail(e.getMessage());
            }
        }
    }

    private List<ConformanceError> checkDataAgainstCriteria(BankingProduct bankingProduct, String effective, String updatedSince, String brand, String productCategory) {
        List<ConformanceError> errors = new ArrayList<>();
        checkEffectiveDate(bankingProduct, effective, errors);
        checkUpdatedSince(bankingProduct, updatedSince, errors);
        checkBrand(bankingProduct, brand, errors);
        checkProductCategory(bankingProduct, productCategory, errors);
        return errors;
    }

    private void checkProductCategory(BankingProduct bankingProduct, String productCategory, List<ConformanceError> errors) {
        if (!StringUtils.isBlank(productCategory)) {
            BankingEnumProductCategory bankingProductCategory = getProductCategory(bankingProduct);
            if (bankingProductCategory == null || !bankingProductCategory.name().equals(productCategory)) {
                errors.add(new ConformanceError().errorType(DATA_NOT_MATCHING_CRITERIA)
                    .errorField(FieldUtils.getField(BankingProduct.class, "effectiveFrom", true))
                    .modelClass(BankingProduct.class)
                    .dataObject(bankingProduct)
                    .errorMessage(String.format("BankingProduct productCategory %s does not match productCategory query %s", bankingProductCategory, productCategory))
                );
            }
        }
    }

    private BankingEnumProductCategory getProductCategory(BankingProduct bankingProduct) {
        Field dataField = FieldUtils.getField(bankingProduct.getClass(), "productCategory", true);
        return (BankingEnumProductCategory) ReflectionUtils.getField(dataField, bankingProduct);
    }

    private void checkBrand(BankingProduct bankingProduct, String brand, List<ConformanceError> errors) {
        if (!StringUtils.isBlank(brand)) {
            String productBrand = getProductBrand(bankingProduct);
            if (StringUtils.isBlank(productBrand) || !productBrand.contains(brand)) {
                errors.add(new ConformanceError().errorType(DATA_NOT_MATCHING_CRITERIA)
                    .errorField(FieldUtils.getField(BankingProduct.class, "effectiveFrom", true))
                    .modelClass(BankingProduct.class)
                    .dataObject(bankingProduct)
                    .errorMessage(String.format("BankingProduct brand %s does not match brand query %s", productBrand, brand))
                );
            }
        }
    }

    private String getProductBrand(BankingProduct bankingProduct) {
        Field dataField = FieldUtils.getField(bankingProduct.getClass(), "brand", true);
        return (String) ReflectionUtils.getField(dataField, bankingProduct);
    }

    private void checkUpdatedSince(BankingProduct bankingProduct, String updatedSince, List<ConformanceError> errors) {
        if (!StringUtils.isBlank(updatedSince)) {
            DateTime updatedSinceTime = DateTime.parseRfc3339(updatedSince);
            DateTime lastUpdatedTime = getLastUpdatedTime(bankingProduct);
            if (updatedSinceTime.getValue() > lastUpdatedTime.getValue()) {
                errors.add(new ConformanceError().errorType(DATA_NOT_MATCHING_CRITERIA)
                    .errorField(FieldUtils.getField(BankingProduct.class, "effectiveFrom", true))
                    .modelClass(BankingProduct.class)
                    .dataObject(bankingProduct)
                    .errorMessage(String.format("BankingProduct lastUpdated %s is before updatedSince %s", lastUpdatedTime, updatedSinceTime))
                );
            }
        }
    }

    private DateTime getLastUpdatedTime(BankingProduct bankingProduct) {
        return getDateTimeFieldValue(bankingProduct, "lastUpdated");
    }

    @Nullable
    private DateTime getDateTimeFieldValue(Object dataObject, String fieldName) {
        Field dataField = FieldUtils.getField(dataObject.getClass(), fieldName, true);
        String fieldValue = (String) ReflectionUtils.getField(dataField, dataObject);
        if (StringUtils.isBlank(fieldValue)) return null;
        return DateTime.parseRfc3339(fieldValue);
    }

    private void checkEffectiveDate(BankingProduct bankingProduct, String effective, List<ConformanceError> errors) {
        long now = System.currentTimeMillis();
        DateTime effectiveFromDate = getEffectiveFromDate(bankingProduct);
        DateTime effectiveToDate = getEffectiveToDate(bankingProduct);
        if (StringUtils.isBlank(effective) || effective.equals(ParamEffective.CURRENT)) {
            if (effectiveFromDate != null && effectiveFromDate.getValue() > now) {
                errors.add(new ConformanceError().errorType(DATA_NOT_MATCHING_CRITERIA)
                    .errorField(FieldUtils.getField(BankingProduct.class, "effectiveFrom", true))
                    .modelClass(BankingProduct.class)
                    .dataObject(bankingProduct)
                    .errorMessage(String.format("BankingProduct effectiveFrom %s is after current time %s", effectiveFromDate, new Date(now)))
                );
            }
            if (effectiveToDate != null && effectiveToDate.getValue() < now) {
                errors.add(new ConformanceError().errorType(DATA_NOT_MATCHING_CRITERIA)
                    .errorField(FieldUtils.getField(BankingProduct.class, "effectiveTo", true))
                    .modelClass(BankingProduct.class)
                    .dataObject(bankingProduct)
                    .errorMessage(String.format("BankingProduct effectiveTo %s is before current time %s", effectiveFromDate, new Date(now)))
                );
            }
        }
        if (ParamEffective.FUTURE.equals(effective)) {
            if (effectiveFromDate == null || effectiveFromDate.getValue() <= now) {
                errors.add(new ConformanceError().errorType(DATA_NOT_MATCHING_CRITERIA)
                    .errorField(FieldUtils.getField(BankingProduct.class, "effectiveFrom", true))
                    .modelClass(BankingProduct.class)
                    .dataObject(bankingProduct)
                    .errorMessage(String.format("BankingProduct effectiveFrom %s is not after current time %s", effectiveFromDate, new Date(now)))
                );
            }
        }
    }

    private DateTime getEffectiveFromDate(BankingProduct bankingProduct) {
        return getDateTimeFieldValue(bankingProduct, "effectiveFrom");
    }

    private DateTime getEffectiveToDate(BankingProduct bankingProduct) {
        return getDateTimeFieldValue(bankingProduct, "effectiveTo");
    }

    private boolean validateListProductsParams(String effective, String updatedSince, String productCategory, Integer page, Integer pageSize) {
        if (!StringUtils.isBlank(effective)) {
            try {
                ParamEffective.valueOf(effective);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        if (!StringUtils.isBlank(productCategory)) {
            try {
                ParamProductCategory.valueOf(productCategory);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        if (!StringUtils.isBlank(updatedSince)) {
            try {
                DateTime.parseRfc3339(updatedSince);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return (page == null || page >= 1) && (pageSize == null || pageSize >= 1);
    }

    private ResponseBankingProductListData getProductListData(ResponseBankingProductList productList) {
        Field dataField = FieldUtils.getField(ResponseBankingProductList.class, "data", true);
        return (ResponseBankingProductListData) ReflectionUtils.getField(dataField, productList);
    }

    private List<BankingProduct> getProducts(ResponseBankingProductListData productListData) {
        Field dataField = FieldUtils.getField(ResponseBankingProductListData.class, "products", true);
        return (List<BankingProduct>) ReflectionUtils.getField(dataField, productListData);
    }

    public List<String> getProductIds() {
        if (responseBankingProductList != null) {
            List<BankingProduct> products = getProducts(getProductListData(responseBankingProductList));
            List<String> productIds = new ArrayList<>();
            for (BankingProduct product : products) {
                productIds.add(getProductId(product));
            }
            return productIds;
        }
        return null;
    }

    @Step("Call getProductDetail")
    void getProductDetail(String productId) {
        String url = apiBasePath + "/banking/products/" + productId;
        getProductDetailResponse = given().accept(ContentType.JSON).when().get(url).then().log().body().extract().response();
    }

    @Step("Validate getProductDetail response")
    void validateGetProductDetailResponse(String productId) {
        int statusCode = getProductDetailResponse.statusCode();
        if (!productId.matches(CustomDataType.ASCII.getPattern())) {
            assertEquals(statusCode, ResponseCode.BAD_REQUEST.getCode());
        } else {
            assertEquals(statusCode, ResponseCode.OK.getCode());
            List<ConformanceError> conformanceErrors = new ArrayList<>();
            String contentType = getProductDetailResponse.contentType();
            if (!"application/json".equals(contentType)) {
                conformanceErrors.add(new ConformanceError().errorType(DATA_NOT_MATCHING_CRITERIA)
                    .errorMessage("missing content-type application/json in response header"));
            }
            String json = getProductDetailResponse.getBody().asString();
            ObjectMapper objectMapper = payloadValidator.createObjectMapper();
            try {
                ResponseBankingProductById responseBankingProductById = objectMapper.readValue(json, ResponseBankingProductById.class);
                conformanceErrors.addAll(payloadValidator.validateResponse(this.requestUrl, responseBankingProductById, "getProductDetail", statusCode));
                Object data = getBankingProductDetail(responseBankingProductById);
                String id = getProductId(data);
                if (!id.equals(productId)) {
                    conformanceErrors.add(new ConformanceError().errorType(DATA_NOT_MATCHING_CRITERIA)
                        .dataObject(responseBankingProductById)
                        .modelClass(ResponseBankingProductById.class)
                        .errorMessage(String.format("Response productId %s does not match request productId %s", id, productId))
                    );
                }
                for (ConformanceError error : conformanceErrors) {
                    logger.error(error.getDescription());
                }
                assertTrue("Conformance errors found in response payload", conformanceErrors.isEmpty());
            } catch (IOException e) {
                fail(e.getMessage());
            }
        }
    }

    private Object getBankingProductDetail(ResponseBankingProductById responseBankingProductById) {
        Field dataField = FieldUtils.getField(ResponseBankingProductById.class, "data", true);
        return ReflectionUtils.getField(dataField, responseBankingProductById);
    }

    private String getProductId(Object data) {
        Field idField = FieldUtils.getField(data.getClass(), "productId", true);
        return (String)ReflectionUtils.getField(idField, data);
    }

    public ResponseBankingProductList getResponseBankingProductList() {
        return responseBankingProductList;
    }
}
